package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A diamond-bearing core, worked by a diamond-tooled industrial base. It adds nothing to any industry's output.
 * Synergy: when a working Mining industry AND a working Megaport are both present,
 * the colony gets the full effect of hazard pay (growth incentives) built in, at no cost. The hazard pay
 * toggle is held off, since the bonus is already applied.
 */
public class DiamondCoreCondition extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    private boolean colonized() {
        return SynergyTooltip.isColonized(market);
    }

    public boolean hasMining() {
        return SynergyTooltip.isFunctional(market, Industries.MINING);
    }

    public boolean hasMegaport() {
        return SynergyTooltip.isFunctional(market, Industries.MEGAPORT);
    }

    public boolean synergyActive() {
        return colonized() && hasMining() && hasMegaport();
    }

    /** Holds the hazard pay toggle off while the synergy is active, since its effect is already applied by modifyIncoming below. */
    private void update() {
        if (market == null) return;
        if (synergyActive()) {
            if (market.isImmigrationIncentivesOn()) market.setImmigrationIncentivesOn(Boolean.FALSE);
            if (market.getIncentiveCredits() > 0f) market.setIncentiveCredits(0f);
        }
    }

    /** Same growth points the game's hazard pay gives: it cancels the hazard penalty and adds a few points on top. */
    @Override
    public void modifyIncoming(MarketAPI m, PopulationComposition incoming) {
        if (m == null || incoming == null || m != market || !synergyActive()) return;
        if (m.getSize() >= Misc.getMaxMarketSize(m)) return;
        float points = CoreImmigrationPluginImpl.INCENTIVE_POINTS_EXTRA - CoreImmigrationPluginImpl.getImmigrationHazardPenalty(m);
        if (points > 0f) incoming.getWeight().modifyFlat("rp_diamond_core_growth", points, "Diamond Core");
    }

    @Override
    public void apply(String id) {
        if (market != null) market.addTransientImmigrationModifier(this);
        update();
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.removeTransientImmigrationModifier(this);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        update();
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Diamond Core");
        tooltip.addPara(
            "Immense pressure has locked much of this world's interior into ultra-dense diamond. "
            + "With Mining and a Megaport working together, the whole colony becomes one shared enterprise: "
            + "the haul is worked by everyone and sold off-world, and every resident is free to profit from it, "
            + "so no one here asks for hazard pay.",
            10f);

        SynergyTooltip.heading(tooltip, "Shared Diamond Hauls");

        if (colonized()) {
            boolean mine = hasMining();
            boolean port = hasMegaport();
            boolean on = mine && port;
            if (on) {
                tooltip.addPara("Growth: %s", 5f, Misc.getPositiveHighlightColor(), "hazard pay effect, built in");
                tooltip.addPara("Hazard pay cost: %s", 3f, Misc.getPositiveHighlightColor(), "none (toggle held off)");
            } else {
                tooltip.addPara("Synergy: %s", 5f, Misc.getNegativeHighlightColor(), "INACTIVE");
            }
            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Mining", mine);
            SynergyTooltip.prerequisite(tooltip, "Megaport", port);
            SynergyTooltip.note(tooltip,
                "While both are working, the colony grows as if hazard pay were always in force, at no cost to you. "
                + "The Hazard Pay toggle is held off, since the bonus is already applied.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "With Mining and a Megaport both working, a colony here gets the growth effect of hazard pay built in, at no cost.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
