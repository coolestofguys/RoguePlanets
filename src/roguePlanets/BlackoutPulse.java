package roguePlanets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * The system-wide effect of the Resonant Signal Trap's pulse: every hostile fleet is slowed, and ordinary patrols with a home port
 * also lose track of the player. Guardians, station fleets and quest fleets are slowed only.
 */
public final class BlackoutPulse {

    public static final float SLOW_DAYS = 5f;
    public static final float SPEED_MULT = 0.5f;

    private static final Color PING_COLOR = new Color(170, 90, 255, 255);

    private BlackoutPulse() {
    }

    /** Only ordinary fleets with a home market to go back to may be sent home; everything else keeps its job. */
    private static boolean canLoseTrack(CampaignFleetAPI fleet) {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (mem.getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)) return false;
        if (mem.getBoolean(MemFlags.STATION_FLEET) || mem.getBoolean(MemFlags.STATION_BASE_FLEET)) return false;
        return Misc.getSourceMarket(fleet) != null;
    }

    /** Returns how many hostile fleets were hit. */
    public static int fire(StarSystemAPI system, CampaignFleetAPI player, CampaignFleetAPI encounterFleet) {
        int hit = 0;
        if (system == null || player == null) return 0;
        for (CampaignFleetAPI fleet : system.getFleets()) {
            try {
                if (fleet == null || fleet == player || !fleet.isAlive()) continue;
                if (fleet.getFaction() == null || !fleet.getFaction().isHostileTo(player.getFaction())) continue;

                fleet.getStats().addTemporaryModMult(SLOW_DAYS, "rp_signal_blackout_" + fleet.getId(), "Signal blackout",
                    SPEED_MULT, fleet.getStats().getFleetwideMaxBurnMod());
                hit++;
                try {
                    Global.getSector().addPing(fleet, "interdict", PING_COLOR);
                } catch (Throwable ignored) {
                }

                // the fleet that caught the player is dealt with by the encounter itself
                if (fleet == encounterFleet) continue;
                // guardians, station fleets, quest fleets and fleets with no home port are only slowed, never reassigned
                if (!canLoseTrack(fleet)) continue;
                fleet.clearAssignments();
                try {
                    Misc.giveStandardReturnToSourceAssignments(fleet, false);
                } catch (Throwable t) {
                    fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, system.getCenter(), 10f, "patrolling");
                }
            } catch (Throwable t) {
                Global.getLogger(BlackoutPulse.class).warn("Signal blackout could not affect a fleet: " + t);
            }
        }
        return hit;
    }
}
