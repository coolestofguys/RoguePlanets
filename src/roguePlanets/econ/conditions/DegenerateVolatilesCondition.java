package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Synergy: Mining + Orbital Works. While both are working, the Orbital Works makes extra Heavy Armaments and
 * Heavy Machinery, at the price of lower stability and accessibility (the containment protocols for the volatiles).
 */
public class DegenerateVolatilesCondition extends BaseMarketConditionPlugin {

    public static final int ARMAMENTS_SUPPLY = 2;
    public static final int MACHINERY_SUPPLY = 2;
    public static final int STABILITY_PENALTY = 1;
    public static final float ACCESSIBILITY_PENALTY = 0.15f;

    /** Re-checks once a day; the economy also re-applies the condition whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private boolean isColonized() {
        return SynergyTooltip.isColonized(market);
    }

    private boolean isMiningActive() {
        return SynergyTooltip.isFunctional(market, Industries.MINING);
    }

    private boolean isOrbitalWorksActive() {
        return SynergyTooltip.isFunctional(market, Industries.ORBITALWORKS);
    }

    public boolean isSynergyActive() {
        return isMiningActive() && isOrbitalWorksActive();
    }

    private void clearSupplyBonuses(String id) {
        Industry works = market.getIndustry(Industries.ORBITALWORKS);
        if (works != null) {
            works.getSupply(Commodities.HAND_WEAPONS).getQuantity().unmodify(id);
            works.getSupply(Commodities.HEAVY_MACHINERY).getQuantity().unmodify(id);
        }
    }

    private void applyModifiers(String id) {
        if (market == null) return;

        if (isSynergyActive()) {
            Industry works = market.getIndustry(Industries.ORBITALWORKS);
            // Commodities.HAND_WEAPONS is the internal ID for Heavy Armaments in Starsector
            works.getSupply(Commodities.HAND_WEAPONS).getQuantity().modifyFlat(id, ARMAMENTS_SUPPLY, "Degenerate Volatiles");
            works.getSupply(Commodities.HEAVY_MACHINERY).getQuantity().modifyFlat(id, MACHINERY_SUPPLY, "Degenerate Volatiles");
            market.getStability().modifyFlat(id, -STABILITY_PENALTY, "Degenerate Volatiles");
            market.getAccessibilityMod().modifyFlat(id, -ACCESSIBILITY_PENALTY, "Degenerate Volatiles");
        } else {
            clearSupplyBonuses(id);
            market.getStability().unmodify(id);
            market.getAccessibilityMod().unmodify(id);
        }
    }

    @Override
    public void apply(String id) {
        applyModifiers(id);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        clearSupplyBonuses(id);
        market.getStability().unmodify(id);
        market.getAccessibilityMod().unmodify(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            applyModifiers(getModId());
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (!Global.CODEX_TOOLTIP_MODE) {
            createTooltipBody(tooltip, expanded);
            return;
        }
        // Codex page: the codex shows the name itself, and there may be no real colony behind it
        MarketAPI real = market;
        try {
            if (market == null) market = CodexTooltip.placeholderMarket();
            createTooltipBody(tooltip, expanded);
        } catch (Throwable t) {
            CodexTooltip.fallback(tooltip);
        } finally {
            market = real;
        }
    }

    private void createTooltipBody(TooltipMakerAPI tooltip, boolean expanded) {
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Degenerate Volatiles");
        tooltip.addPara(
            "Deep within the crushing gravity wells of this world, volatile gases have been compressed into an ultra-reactive degenerate state. When extracted by mining operations and fed directly into orbital foundries, these unstable catalysts dramatically boost heavy armaments and machinery output. The stringent transport and containment protocols that handling them requires strain colony stability and restrict accessibility.",
            10f
        );

        SynergyTooltip.heading(tooltip, "Degenerate Extraction Status");

        if (isColonized()) {
            boolean active = isSynergyActive();

            if (active) {
                SynergyTooltip.status(tooltip, "Volatile Catalyst Feed", true,
                    "OPERATIONAL (Mining & Orbital Works Synchronized)");
                tooltip.addPara(
                    "Orbital Works output: +%s %s, +%s %s.",
                    5f,
                    Misc.getPositiveHighlightColor(),
                    "" + ARMAMENTS_SUPPLY, "Heavy Armaments",
                    "" + MACHINERY_SUPPLY, "Heavy Machinery"
                );
                tooltip.addPara("Colony stability reduced by %s.", 5f, Misc.getNegativeHighlightColor(), "" + STABILITY_PENALTY);
                tooltip.addPara("Colony accessibility reduced by %s.", 5f, Misc.getNegativeHighlightColor(), "15%");
            } else {
                SynergyTooltip.status(tooltip, "Volatile Catalyst Feed", false,
                    "INACTIVE (Requires Mining & Orbital Works)");
                SynergyTooltip.note(tooltip,
                    "Without both a working Mining operation and an Orbital Works, the catalysts stay unrefined. There is no output bonus, and none of the containment penalties apply.");
            }

            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Mining Industry", isMiningActive());
            SynergyTooltip.prerequisite(tooltip, "Orbital Works", isOrbitalWorksActive());
        } else {
            SynergyTooltip.uncolonized(tooltip, "Extraction Potential",
                "When colonized with both Mining and an Orbital Works, the Orbital Works produces +" + ARMAMENTS_SUPPLY
                + " Heavy Armaments and +" + MACHINERY_SUPPLY + " Heavy Machinery, at the cost of -" + STABILITY_PENALTY
                + " stability and -15% accessibility. If either industry is missing or disrupted, the condition has no effect.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
