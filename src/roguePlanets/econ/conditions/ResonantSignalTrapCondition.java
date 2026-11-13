package roguePlanets.econ.conditions;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Resonant Signal Trap, found on dense-atmosphere worlds. A Waystation and a Megaport working together on a colony you own
 * let you, once every 30 days for the whole star system, release the stored pulse from an encounter screen and slip away
 * from the fleet that caught you. The encounter option itself lives in {@link roguePlanets.BlackoutFleetInteractionPlugin};
 * this class holds the rules, the shared recharge clock and the tooltip.
 */
public class ResonantSignalTrapCondition extends BaseMarketConditionPlugin {

    public static final String CONDITION_ID = "resonant_signal_trap";
    public static final float RECHARGE_DAYS = 30f;
    private static final String KEY_PREFIX = "$rp_signal_trap_used_";

    private boolean colonized() {
        return SynergyTooltip.isColonized(market);
    }

    public boolean hasWaystation() {
        return SynergyTooltip.isFunctional(market, Industries.WAYSTATION);
    }

    public boolean hasMegaport() {
        return SynergyTooltip.isFunctional(market, Industries.MEGAPORT);
    }

    public boolean synergyActive() {
        return colonized() && hasWaystation() && hasMegaport();
    }

    // ---- shared by the whole star system -------------------------------------------------------------------------

    /** True if a colony the player owns in this system has the condition with its synergy working. */
    public static boolean hasActive(StarSystemAPI system) {
        if (system == null) return false;
        List<MarketAPI> markets = new ArrayList<MarketAPI>(Global.getSector().getEconomy().getMarkets(system));
        for (MarketAPI m : markets) {
            if (m == null || !m.isPlayerOwned() || !m.hasCondition(CONDITION_ID)) continue;
            if (m.getCondition(CONDITION_ID).getPlugin() instanceof ResonantSignalTrapCondition
                    && ((ResonantSignalTrapCondition) m.getCondition(CONDITION_ID).getPlugin()).synergyActive()) {
                return true;
            }
        }
        return false;
    }

    /** Days until the system's pulse is charged again; 0 if it is ready. */
    public static float daysLeft(StarSystemAPI system) {
        if (system == null) return 0f;
        String key = KEY_PREFIX + system.getId();
        if (!Global.getSector().getMemoryWithoutUpdate().contains(key)) return 0f;
        long used = Global.getSector().getMemoryWithoutUpdate().getLong(key);
        float left = RECHARGE_DAYS - Global.getSector().getClock().getElapsedDaysSince(used);
        return Math.max(0f, left);
    }

    public static boolean isReady(StarSystemAPI system) {
        return daysLeft(system) <= 0f;
    }

    public static void startRecharge(StarSystemAPI system) {
        if (system == null) return;
        Global.getSector().getMemoryWithoutUpdate().set(KEY_PREFIX + system.getId(), Global.getSector().getClock().getTimestamp());
    }

    // ---- condition plumbing -----------------------------------------------------------------------------------

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
        tooltip.addTitle("Resonant Signal Trap");
        tooltip.addPara(
            "The thick atmosphere of this world traps every signal that reaches it, bouncing the energy back and forth until the sky hums with stored charge. "
            + "A Waystation can cut a channel through the air and let that charge escape in one enormous pulse, and a Megaport's "
            + "vast transmitters carry it out across the whole system. For a short while nothing in the system can see anything, and the air takes about a month to fill up again.",
            10f);

        SynergyTooltip.heading(tooltip, "Signal Blackout");

        if (colonized()) {
            boolean way = hasWaystation();
            boolean port = hasMegaport();
            boolean on = way && port;
            if (on) {
                StarSystemAPI system = market != null && market.getContainingLocation() instanceof StarSystemAPI
                    ? (StarSystemAPI) market.getContainingLocation() : null;
                float left = daysLeft(system);
                if (left <= 0f) {
                    tooltip.addPara("Pulse: %s", 5f, Misc.getPositiveHighlightColor(), "CHARGED");
                } else {
                    tooltip.addPara("Pulse: %s", 5f, Misc.getNegativeHighlightColor(), "recharging, " + (int) Math.ceil(left) + " days left");
                }
                tooltip.addPara("In any encounter in this system, choose %s to end it at once.", 3f, Misc.getHighlightColor(),
                    "\"Activate Signal Blackout to disengage freely\"");
            } else {
                tooltip.addPara("Synergy: %s", 5f, Misc.getNegativeHighlightColor(), "INACTIVE");
            }
            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Waystation", way);
            SynergyTooltip.prerequisite(tooltip, "Megaport", port);
            SynergyTooltip.note(tooltip,
                "While both are working, once every " + (int) RECHARGE_DAYS + " days, shared by the whole star system, you can break off any encounter in this system without a fight. "
                + "The pulse also slows every hostile fleet in the system for five days and makes them lose track of you.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "With a Waystation and a Megaport both working, a colony here lets you slip away from one encounter in this system every "
                + (int) RECHARGE_DAYS + " days and slows every hostile fleet in the system for five days.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
