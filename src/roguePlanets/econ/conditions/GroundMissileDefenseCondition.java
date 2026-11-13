package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Ground-to-space missile defense. Heavy Batteries and a Waystation together make a launch-and-tracking network.
 * The effect itself is carried out in combat by {@link roguePlanets.GroundMissilePlugin}, which asks each colony in the
 * battle's system with this condition whether {@link #synergyActive()} is true. This class only holds the rules and the tooltip.
 */
public class GroundMissileDefenseCondition extends BaseMarketConditionPlugin {

    public static final int WAVE_SIZE = 500;
    public static final int WAVES = 1;
    public static final int WAVE_GAP_SECONDS = 10;
    public static final int MISSILES = WAVE_SIZE * WAVES;
    public static final int DELAY_SECONDS = 30;
    /** Each salvo is spread evenly over this many seconds. */
    public static final int SPREAD_SECONDS = 30;

    private boolean colonized() {
        return SynergyTooltip.isColonized(market);
    }

    public boolean hasBatteries() {
        return SynergyTooltip.isFunctional(market, Industries.HEAVYBATTERIES);
    }

    public boolean hasWaystation() {
        return SynergyTooltip.isFunctional(market, Industries.WAYSTATION);
    }

    public boolean synergyActive() {
        return colonized() && hasBatteries() && hasWaystation();
    }

    @Override
    public void apply(String id) {
    }

    @Override
    public void unapply(String id) {
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Ground-to-Space Missile Defense");
        tooltip.addPara(
            "The weak gravity of this world means a missile leaves its silo already most of the way to orbit, and rail launchers cut into the bedrock can pre-boost them to speed before the motors ever fire. "
            + "A colony here can keep the batteries fed and aimed, but only if a waystation overhead gives them eyes on the whole system.",
            10f);

        SynergyTooltip.heading(tooltip, "Planetary Missile Batteries");

        if (colonized()) {
            boolean bat = hasBatteries();
            boolean way = hasWaystation();
            boolean on = bat && way;
            if (on) {
                tooltip.addPara("Opening salvo: %s", 5f, Misc.getPositiveHighlightColor(), MISSILES + " Pilum missiles");
                tooltip.addPara("Fired: %s into any battle in this system", 3f, Misc.getPositiveHighlightColor(), DELAY_SECONDS + " seconds");
            } else {
                tooltip.addPara("Synergy: %s", 5f, Misc.getNegativeHighlightColor(), "INACTIVE");
            }
            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Heavy Batteries", bat);
            SynergyTooltip.prerequisite(tooltip, "Waystation", way);
            SynergyTooltip.note(tooltip,
                "While both are working, " + DELAY_SECONDS + " seconds into any battle in this system that you take part in, " + MISSILES
                + " Pilum missiles are launched from your side of the map at random enemy ships. Each colony with this synergy adds its own salvo. "
                + "If a hostile faction holds the synergy and fights in the battle, the salvo is launched at you instead.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "With Heavy Batteries and a Waystation both working, a colony here launches " + MISSILES
                + " Pilum missiles at the enemy " + DELAY_SECONDS + " seconds into any battle you fight in this system.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
