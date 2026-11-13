package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Synergy: Mining + Light Industry. While both are working, this colony's recreational drug exports sell at a
 * premium (high-purity product). The extra income is added to the colony's Drugs line of the monthly income
 * report on every economy tick, so no new commodity is needed.
 */
public class RadiolyticCatalystCondition extends BaseMarketConditionPlugin implements EconomyTickListener {

    /** Drug export income is multiplied by this while the synergy is active (3 = triple). */
    public static final float DRUG_EXPORT_MULT = 3f;

    private boolean isColonized() {
        return SynergyTooltip.isColonized(market);
    }

    private boolean isMiningActive() {
        return SynergyTooltip.isFunctional(market, Industries.MINING);
    }

    private boolean isLightIndustryActive() {
        return SynergyTooltip.isFunctional(market, Industries.LIGHTINDUSTRY);
    }

    public boolean isSynergyActive() {
        return isMiningActive() && isLightIndustryActive();
    }

    /** Current base monthly drug export income of this colony (before the bonus). */
    public int getBaseDrugExportIncome() {
        if (market == null) return 0;
        CommodityOnMarketAPI com = market.getCommodityData(Commodities.DRUGS);
        return com == null ? 0 : com.getExportIncome();
    }

    public float getBonusIncome() {
        return getBaseDrugExportIncome() * (DRUG_EXPORT_MULT - 1f);
    }

    @Override
    public void apply(String id) {
        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this);
        }
    }

    @Override
    public void unapply(String id) {
        // the listener stays registered; it retires itself once the condition is gone from its market
    }

    /** True while this plugin is still the live condition on its market. */
    private boolean isLive() {
        return market != null && market.getCondition("radiolytic_catalyst") != null
            && market.getCondition("radiolytic_catalyst").getPlugin() == this;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!isLive()) {
            Global.getSector().getListenerManager().removeListener(this);
            return;
        }
        if (market == null || !market.isPlayerOwned() || !market.isInEconomy()) return;
        if (!isSynergyActive()) return;

        float bonus = getBonusIncome();
        if (bonus <= 0f) return;

        float f = 1f / Global.getSettings().getFloat("economyIterPerMonth");

        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
        FDNode mNode = report.getNode(marketsNode, market.getId());
        FDNode exportNode = report.getNode(mNode, "exports");
        FDNode bonusNode = report.getNode(exportNode, "radiolytic_catalyst_bonus");
        bonusNode.name = "High-grade drugs (Radiolytic Catalyst)";
        bonusNode.custom = "radiolytic_catalyst_bonus";
        bonusNode.mapEntity = market.getPrimaryEntity();
        bonusNode.income += bonus * f;
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Radiolytic Catalyst");
        tooltip.addPara(
            "Unique ionizing mineral deposits and actinide isotopes embedded within the planetary crust emit a continuous, low-intensity radiolytic emission spectrum. When mined and channeled into bio-chemical synthesis vats, these radiation fields act as an extraordinary catalytic agent, refining crude narcotic precursors into exceptionally potent, ultra-pure designer compounds without requiring expensive industrial reagents.",
            10f
        );

        SynergyTooltip.heading(tooltip, "Catalytic Synthesis Status");

        if (isColonized()) {
            if (isSynergyActive()) {
                SynergyTooltip.status(tooltip, "Catalytic Synthesis", true,
                    "OPERATIONAL (Mining & Light Industry Synchronized)");

                int pct = Math.round((DRUG_EXPORT_MULT - 1f) * 100f);
                tooltip.addPara("Recreational drug export profits: %s", 5f,
                    Misc.getPositiveHighlightColor(), "+" + pct + "%");
                if (getBaseDrugExportIncome() > 0) {
                    tooltip.addPara("Current bonus: %s per month", 3f,
                        Misc.getPositiveHighlightColor(), "+" + Misc.getDGSCredits(getBonusIncome()));
                } else {
                    SynergyTooltip.note(tooltip,
                        "Catalytic synthesis is online, but the colony has no drug export income yet. Drugs must be produced and exported (Free Port is required to export them).");
                }
                SynergyTooltip.note(tooltip,
                    "The premium appears as its own High-grade drugs line under this colony's exports in the monthly income report.");
            } else {
                SynergyTooltip.status(tooltip, "Catalytic Synthesis", false,
                    "INACTIVE (Requires Mining & Light Industry)");
                SynergyTooltip.note(tooltip,
                    "Establishing both a working Mining operation and a Light Industry starts the catalytic processing, raising the profits from this colony's recreational drug exports.");
            }

            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Mining Industry", isMiningActive());
            SynergyTooltip.prerequisite(tooltip, "Light Industry", isLightIndustryActive());
        } else {
            SynergyTooltip.uncolonized(tooltip, "Catalytic Potential",
                "When colonized with both Mining and Light Industry, the colony's recreational drug exports sell at a premium, raising their profits.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
