package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Synergy: Refining + Fuel Production on an airless world. While both work, the colony owner's fleets in the
 * system get +2 burn. If either is ever disrupted or shut down the reactor melts down: every industry on the planet is
 * disrupted for 180 days and the planet gains +100% hazard until they recover. The monitoring itself is done by
 * roguePlanets.AdditiveReactorScript; this class holds the hazard penalty and the tooltip.
 */
public class VolatileAdditiveReactorCondition extends BaseMarketConditionPlugin {

    public static final String KEY_MELTDOWN = "$additive_meltdown";
    public static final float BURN_BONUS = 2f;
    public static final float MELTDOWN_DAYS = 180f;
    public static final float MELTDOWN_HAZARD = 1.0f;

    private boolean meltdown() {
        return market != null && market.getMemoryWithoutUpdate().getBoolean(KEY_MELTDOWN);
    }

    private boolean refiningOk() {
        return SynergyTooltip.isFunctional(market, Industries.REFINING);
    }

    private boolean fuelOk() {
        return SynergyTooltip.isFunctional(market, Industries.FUELPROD);
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        if (meltdown()) {
            market.getHazard().modifyFlat(id, MELTDOWN_HAZARD, "Reactor meltdown");
        } else {
            market.getHazard().unmodifyFlat(id);
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) market.getHazard().unmodifyFlat(id);
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Volatile Additive Reactor");
        tooltip.addPara(
            "Veins of a pale crystalline mineral run through this world's crust. Nothing else known will hold a fuel additive this volatile in check: lined with it, a reactor can run refinery output straight into the fuel plant and dose it with an unstable additive that lets a drive burn harder than any regulated mixture. The additive is nowhere near sectorwide fuel compliance, so it can only be used locally. But the crystal only holds while its calibration is watched around the clock. Let that attention slip for even a moment, and the process turns extremely violent.",
            10f);

        SynergyTooltip.heading(tooltip, "Additive Reactor Status");

        if (SynergyTooltip.isColonized(market)) {
            if (meltdown()) {
                SynergyTooltip.status(tooltip, "Additive Reactor", false, "MELTDOWN IN PROGRESS");
                tooltip.addPara("Hazard rating: %s", 5f, Misc.getNegativeHighlightColor(), "+100%");
                SynergyTooltip.note(tooltip,
                    "Every industry on the planet is disrupted until the reactor is brought back under control.");
            } else if (refiningOk() && fuelOk()) {
                SynergyTooltip.status(tooltip, "Additive Reactor", true, "OPERATIONAL");
                tooltip.addPara("Burn level of %s fleets in this system: %s", 5f,
                    Misc.getPositiveHighlightColor(), "this colony's faction's", "+2");
                SynergyTooltip.note(tooltip,
                    "Stacks with Nav Buoys, but not with another Volatile Additive Reactor in the same system. "
                    + "If Refining or Fuel Production is ever disrupted or shut down, the reactor melts down and disrupts every industry on the planet for 180 days, with +100% hazard while it lasts.");
            } else {
                SynergyTooltip.status(tooltip, "Additive Reactor", false, "INACTIVE (Requires Refining & Fuel Production)");
                SynergyTooltip.note(tooltip,
                    "Running both a Refining and a Fuel Production industry starts the reactor. Beware: a disruption to either one, or shutting either one down, causes a meltdown.");
            }
            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Refining", refiningOk());
            SynergyTooltip.prerequisite(tooltip, "Fuel Production", fuelOk());
        } else {
            SynergyTooltip.uncolonized(tooltip, "Reactor Potential",
                "When colonized with both Refining and Fuel Production, the colony owner's fleets in this system gain +2 burn. If either industry is disrupted or shut down, the reactor melts down and disrupts every industry on the planet for 180 days.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
