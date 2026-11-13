package roguePlanets.hullmods;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * Cosmetic only. A slow violet glow around the Afterlight in combat. No stat changes.
 * Hidden everywhere (see data/hullmods/hull_mods.csv), so it never shows in refit or the codex.
 */
public class AfterlightGlow extends BaseHullMod {

    public static final String ID = "roguePlanets_afterlight_glow";
    private static final Color VIOLET = new Color(150, 90, 255, 255);
    private static final Color HALO = new Color(140, 80, 255, 255);

    private float t = 0f;

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive() || Global.getCombatEngine() == null) return;
        if (Global.getCombatEngine().isPaused()) return;
        t += amount;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.1f);   // about one breath every 6 seconds

        ship.setJitterUnder(this, VIOLET, 0.30f + 0.20f * pulse, 5, 3f, 8f + 4f * pulse);
        ship.getEngineController().fadeToOtherColor(this, VIOLET, null, 1f, 0.5f);

        Vector2f loc = ship.getLocation();
        Global.getCombatEngine().addSmoothParticle(loc, new Vector2f(), ship.getCollisionRadius() * (1.9f + 0.4f * pulse),
            0.16f + 0.10f * pulse, amount * 2f, HALO);
    }

    public boolean isApplicableToShip(ShipAPI ship) { return false; }

    public String getDescriptionParam(int index, com.fs.starfarer.api.combat.ShipAPI.HullSize hullSize) { return null; }

    // ---- campaign-side helpers -------------------------------------------------------------------------------

    public static FleetMemberAPI findAfterlight() {
        CampaignFleetAPI f = Global.getSector().getPlayerFleet();
        if (f == null) return null;
        FleetMemberAPI byName = null;
        for (FleetMemberAPI m : f.getFleetData().getMembersListCopy()) {
            if ("Afterlight".equals(m.getShipName()) && m.getHullSpec().getHullId().startsWith("wolf")) byName = m;
        }
        return byName;
    }

    public static boolean hasGlow(FleetMemberAPI m) {
        return m != null && m.getVariant() != null && m.getVariant().getPermaMods().contains(ID);
    }

    public static void setGlow(FleetMemberAPI m, boolean on) {
        if (m == null || m.getVariant() == null) return;
        boolean has = hasGlow(m);
        if (on && !has) m.getVariant().addPermaMod(ID, false);
        if (!on && has) m.getVariant().removePermaMod(ID);
    }
}
