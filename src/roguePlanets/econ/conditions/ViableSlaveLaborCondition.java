package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * A captive workforce held in place by the colony's garrison.
 *  - Synergy: with a working Military Base or High Command AND a working Commerce, colony income is +50% and
 *    stability is -3.
 *  - If stability is 5 or lower at the end of three months in a row, the workforce rebels and a player colony
 *    passes to pirate control.
 */
public class ViableSlaveLaborCondition extends BaseMarketConditionPlugin implements EconomyTickListener {

    public static final float INCOME_BONUS_PERCENT = 50f;
    public static final float STABILITY_PENALTY = -3f;
    public static final float STABILITY_THRESHOLD = 5f;
    public static final int REBELLION_MONTHS = 3;
    public static final String KEY_UNREST_MONTHS = "$slave_labor_unrest_months";
    private static final String NODE_ID = "slave_labor_yield";

    /** Re-checks the stability penalty once a day; the economy also re-applies the condition on every market refresh. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private boolean colonized() {
        return SynergyTooltip.isColonized(market);
    }

    /** True if the colony has a working Military Base or High Command. */
    public boolean hasMilitary() {
        return SynergyTooltip.isFunctional(market, Industries.MILITARYBASE)
            || SynergyTooltip.isFunctional(market, Industries.HIGHCOMMAND);
    }

    /** True if the colony has a working Commerce industry. */
    public boolean hasCommerce() {
        return SynergyTooltip.isFunctional(market, Industries.COMMERCE);
    }

    /** The synergy is active when a Military Base or High Command AND Commerce are both working. */
    public boolean synergyActive() {
        return colonized() && hasMilitary() && hasCommerce();
    }

    public float getIncomeBonusPercent() {
        return INCOME_BONUS_PERCENT;
    }

    /** Income is multiplied by this (1 if the synergy is not active). */
    public float getIncomeMult() {
        return synergyActive() ? 1f + INCOME_BONUS_PERCENT / 100f : 1f;
    }

    /** Monthly gross income of the colony before this condition's adjustment. */
    public float getGrossIncome() {
        if (market == null) return 0f;
        float total = 0f;
        for (Industry ind : market.getIndustries()) {
            total += ind.getIncome().getModifiedValue();
        }
        for (CommodityOnMarketAPI com : market.getAllCommodities()) {
            total += com.getExportIncome();
        }
        return total;
    }

    public float getIncomeAdjustment() {
        return getGrossIncome() * (getIncomeMult() - 1f);
    }

    public int getUnrestMonths() {
        return market == null ? 0 : market.getMemoryWithoutUpdate().getInt(KEY_UNREST_MONTHS);
    }

    private void updateStability(String id) {
        if (market == null) return;
        if (synergyActive()) {
            market.getStability().modifyFlat(id, STABILITY_PENALTY, "Captive workforce");
        } else {
            market.getStability().unmodifyFlat(id);
        }
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        updateStability(id);
        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this);
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) market.getStability().unmodifyFlat(id);
        // the listener stays registered; it retires itself once the condition is gone from its market
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            updateStability(getModId());
        }
    }

    /** True while this plugin is still the live condition on its market. */
    private boolean isLive() {
        return market != null && market.getCondition("viable_slave_labor") != null
            && market.getCondition("viable_slave_labor").getPlugin() == this;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!isLive()) {
            Global.getSector().getListenerManager().removeListener(this);
            return;
        }
        if (!colonized() || !market.isInEconomy()) return;

        if (!market.isPlayerOwned()) return;
        float adjust = getIncomeAdjustment();
        if (Math.abs(adjust) < 0.5f) return;

        float f = 1f / Global.getSettings().getFloat("economyIterPerMonth");
        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
        FDNode mNode = report.getNode(marketsNode, market.getId());
        FDNode node = report.getNode(mNode, NODE_ID);
        node.name = "Captive workforce yield";
        node.custom = NODE_ID;
        node.mapEntity = market.getPrimaryEntity();
        if (adjust > 0f) {
            node.income += adjust * f;
        } else {
            node.upkeep += -adjust * f;
        }
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (!isLive()) return;
        if (!market.isPlayerOwned() || !colonized() || !market.isInEconomy()) return;

        if (market.getStabilityValue() <= STABILITY_THRESHOLD) {
            int months = getUnrestMonths() + 1;
            market.getMemoryWithoutUpdate().set(KEY_UNREST_MONTHS, months);
            if (months >= REBELLION_MONTHS) {
                rebel();
            } else {
                Global.getSector().getCampaignUI().addMessage(
                    "Unrest is building among the workforce on " + market.getName() + " (month " + months + " of "
                    + REBELLION_MONTHS + "). Raise stability above " + (int) STABILITY_THRESHOLD + " or the colony will be lost.",
                    Misc.getNegativeHighlightColor());
            }
        } else {
            market.getMemoryWithoutUpdate().unset(KEY_UNREST_MONTHS);
        }
    }

    private void rebel() {
        String name = market.getName();
        market.getMemoryWithoutUpdate().unset(KEY_UNREST_MONTHS);

        market.setFactionId(Factions.PIRATES);
        market.setPlayerOwned(false);
        market.setAdmin(null);
        market.setFreePort(true);
        market.setUseStockpilesForShortages(false);
        for (SectorEntityToken curr : market.getConnectedEntities()) {
            curr.setFaction(Factions.PIRATES);
        }
        if (!market.hasSubmarket(Submarkets.SUBMARKET_OPEN)) market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        if (!market.hasSubmarket(Submarkets.SUBMARKET_BLACK)) market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.reapplyConditions();
        market.reapplyIndustries();

        Global.getSector().getCampaignUI().addMessage(
            "REBELLION on " + name + ": the workforce has overthrown the garrison. The colony is lost to pirate control.",
            Misc.getNegativeHighlightColor());
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Viable Slave Labor Population");
        tooltip.addPara(
            "A large, skilled population survives here, left behind by a fallen colony and with no power to refuse whoever holds the guns. A colony on this world can be made very profitable, but only for as long as the garrison is stronger than the workforce's patience.",
            10f);

        SynergyTooltip.heading(tooltip, "Captive Workforce");

        if (colonized()) {
            boolean mil = hasMilitary();
            boolean com = hasCommerce();
            boolean on = mil && com;
            if (on) {
                tooltip.addPara("Colony income: %s", 5f, Misc.getPositiveHighlightColor(),
                    String.format("+%.0f%%", INCOME_BONUS_PERCENT));
                tooltip.addPara("Stability: %s", 3f, Misc.getNegativeHighlightColor(), "-3");
            } else {
                tooltip.addPara("Synergy: %s", 5f, Misc.getNegativeHighlightColor(), "INACTIVE");
            }
            int months = getUnrestMonths();
            if (months > 0) {
                tooltip.addPara("Unrest: %s", 3f, Misc.getNegativeHighlightColor(),
                    months + " of " + REBELLION_MONTHS + " months");
            }
            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Military Base or High Command", mil);
            SynergyTooltip.prerequisite(tooltip, "Commerce", com);
            SynergyTooltip.note(tooltip,
                "While both are working, colony income is increased by " + (int) INCOME_BONUS_PERCENT
                + "% and stability is reduced by 3. Whether or not they are, if stability is " + (int) STABILITY_THRESHOLD
                + " or lower at the end of " + REBELLION_MONTHS
                + " months in a row, the workforce rebels and the colony passes to pirate control.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Labor Potential",
                "With a Military Base or High Command and Commerce both working, a colony here earns 50% more income but has -3 stability. Whatever it builds, the workforce will rebel and turn the colony pirate if stability stays at 5 or lower for three months in a row.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
