package roguePlanets;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import roguePlanets.story.Strangeness;

/**
 * Registered in data/config/settings.json. Rarely, during a real campaign battle, shows one false combat message:
 * fake reinforcements (only while the player holds a small but sizeable lead), a ghost ally's chatter, or a ship reported destroyed that is
 * still fighting. Nothing in the battle is changed.
 */
public class StrangeCombatPlugin extends BaseEveryFrameCombatPlugin {

    private static final Random rand = new Random();
    private CombatEngineAPI engine;
    private int type = 0;          // 0 none, 1 reinforcements, 2 ghost ally, 3 destroyed but not
    private boolean fired = false;
    private float elapsed = 0f;
    private float fireAt = 0f;
    private int initialEnemy = -1;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        try {
            if (engine == null || !engine.isInCampaign() || engine.isSimulation()) return;
            if (!Strangeness.combatMayFire()) return;
            type = 1 + rand.nextInt(3);
            fireAt = 25f + rand.nextFloat() * 50f;
        } catch (Throwable t) {
            type = 0;
        }
    }

    private int alive(CombatFleetManagerAPI fm) {
        return fm.getDeployedCopy().size() + fm.getReservesCopy().size();
    }

    /** Total fleet points of the ships still in the fight (out or in reserve). */
    private float strength(CombatFleetManagerAPI fm) {
        float t = 0f;
        for (FleetMemberAPI m : fm.getDeployedCopy()) t += m.getFleetPointCost();
        for (FleetMemberAPI m : fm.getReservesCopy()) t += m.getFleetPointCost();
        return t;
    }

    private FleetMemberAPI readyIcon(String hullVariant) {
        FleetMemberAPI m = Global.getFactory().createFleetMember(com.fs.starfarer.api.fleet.FleetMemberType.SHIP, hullVariant);
        try { m.updateStats(); } catch (Throwable t) { /* fine */ }
        try { m.getRepairTracker().setMothballed(false); } catch (Throwable t) { /* fine */ }
        m.getRepairTracker().setCR(0.7f);
        return m;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (type == 0 || fired || engine == null || engine.isPaused()) return;
        elapsed += amount;
        try {
            CombatFleetManagerAPI mine = engine.getFleetManager(FleetSide.PLAYER);
            CombatFleetManagerAPI theirs = engine.getFleetManager(FleetSide.ENEMY);
            if (mine == null || theirs == null) return;
            if (initialEnemy < 0) initialEnemy = alive(theirs);
            if (elapsed < fireAt) return;
            if (type == 1) {
                // only when the player holds a small but sizeable lead: more fleet points left than the enemy, but not by a landslide
                float me = strength(mine), them = strength(theirs);
                if (them <= 0f || mine.getDeployedCopy().isEmpty()) return;
                float ratio = me / them;
                if (ratio < 1.15f || ratio > 1.8f) return;
                // the game's own "Incoming vessels:" line, in the hostile colour, with two radiants and some other automated ships
                String[] others = {"apex_Hull", "fulgent_Hull", "brilliant_Hull", "scintilla_Hull", "nova_Hull", "glimmer_Hull", "lumen_Hull"};
                List<Object> parts = new java.util.ArrayList<Object>();
                parts.add(com.fs.starfarer.api.util.Misc.getNegativeHighlightColor());
                parts.add("Incoming vessels: ");
                java.util.List<String> hulls = new java.util.ArrayList<String>();
                hulls.add("radiant_Hull");
                hulls.add("radiant_Hull");
                int extra = 3 + rand.nextInt(2);
                for (int i = 0; i < extra; i++) hulls.add(others[rand.nextInt(others.length)]);
                for (String h : hulls) {
                    try {
                        parts.add(readyIcon(h));
                    } catch (Throwable t) { /* skip an icon that cannot be made */ }
                }
                engine.getCombatUI().addMessage(1, parts.toArray());
                fired = true;
            } else if (type == 2) {
                String name = null;
                for (FleetMemberAPI m : mine.getReservesCopy()) { name = m.getShipName(); break; }
                if (name == null) name = "ISS Second Light";
                String[] lines = {"Holding your flank, Captain.", "Still with you.", "On approach. Do not wait for us.", "Covering fire inbound."};
                engine.getCombatUI().addMessage(1, Color.CYAN, name, Color.WHITE, ": " + lines[rand.nextInt(lines.length)]);
                fired = true;
            } else {
                String name = null;
                FleetMemberAPI ally = null;
                for (FleetMemberAPI m : mine.getDeployedCopy()) {
                    ShipAPI s = mine.getShipFor(m);
                    if (s != null && s.isAlive() && !s.isFighter() && s != engine.getPlayerShip()) { name = s.getName(); ally = m; break; }
                }
                if (ally != null) {
                    // the game's own style for a friendly ship: icon, green name with its class, then the verdict
                    String hull = ally.getHullSpec().getHullName();
                    engine.getCombatUI().addMessage(1, ally, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(),
                        name + " (" + hull + " class)", Color.WHITE, " destroyed");
                } else {
                    for (FleetMemberAPI m : theirs.getDeployedCopy()) {
                        ShipAPI s = theirs.getShipFor(m);
                        if (s != null && s.isAlive() && !s.isFighter()) { name = s.getName(); break; }
                    }
                    if (name == null) return;
                    engine.getCombatUI().addMessage(1, Color.ORANGE, name, Color.WHITE, " has been destroyed.");
                }
                fired = true;
            }
            if (fired) Strangeness.markFired();
        } catch (Throwable t) {
            fired = true;
            Global.getLogger(StrangeCombatPlugin.class).warn("Strange combat message failed: " + t);
        }
    }
}
