package roguePlanets.story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;

/**
 * Stage 10: the Doppelganger. In hyperspace a smaller copy of the player's fleet, commanded by a copy of the player,
 * closes in and speaks. The player can hear it out (no combat) or fire on it (a real battle). Either way it ends with
 * "Thank you." and the copy is gone.
 */
public class DoppelEvent {

    private static final String K_FLEET = "$afl_dpFleet";
    private static final String K_STATE = "$afl_dpState";       // 0/none, 1 approaching, 2 fighting
    private static final String K_SPAWN_STAMP = "$afl_dpSpawnStamp";
    private static final String K_FIGHT_STAMP = "$afl_dpFightStamp";
    private static final String K_RETRY_STAMP = "$afl_dpRetry";
    private static final String K_PERSON = "$afl_dpPerson";
    private static final String K_ENGAGE = "$afl_dpEngage";   // the fight has been chosen but the battle has not started yet

    private static final float CONTACT_RANGE = 200f;
    private static final float GIVE_UP_DAYS = 12f;
    private static final float RETRY_DAYS = 10f;
    private static final float FIGHT_TIMEOUT_DAYS = 15f;
    /** How much of the player's fleet (by deployment points) the copy brings. */
    private static final float STRENGTH = 0.75f;

    private static final Random random = new Random();

    public static CampaignFleetAPI getFleet() {
        return (CampaignFleetAPI) AfterlightStory.mem().get(K_FLEET);
    }

    public static boolean isActive() {
        return getFleet() != null;
    }

    public static boolean retryReady() {
        MemoryAPI mem = AfterlightStory.mem();
        if (!mem.contains(K_RETRY_STAMP)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_RETRY_STAMP)) >= RETRY_DAYS;
    }

    private static boolean isAfterlight(FleetMemberAPI m) {
        return m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX);
    }

    private static String label() {
        return Global.getSector().getPlayerPerson().getName().getFullName();
    }

    private static PersonAPI copyPerson() {
        PersonAPI p = Global.getFactory().createPerson();
        PersonAPI me = Global.getSector().getPlayerPerson();
        p.setName(me.getName());
        p.setPortraitSprite(me.getPortraitSprite());
        p.setFaction(Factions.NEUTRAL);
        p.setRankId(Ranks.SPACE_CAPTAIN);
        p.setPostId(Ranks.POST_FLEET_COMMANDER);
        return p;
    }

    /** Creates the copy near the player (in hyperspace) and sends it to meet them. */
    public static void spawn() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI loc = player.getContainingLocation();
        if (loc == null || !player.isInHyperspace()) return;

        CampaignFleetAPI f = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, label() + "'s Fleet", true);
        f.setNoFactionInName(true);

        // The flagship first, then the biggest of the rest, until the copy has about half the player's strength.
        List<FleetMemberAPI> mine = new ArrayList<FleetMemberAPI>();
        float total = 0f;
        FleetMemberAPI flag = player.getFlagship();
        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            if (m.isFighterWing()) continue;
            mine.add(m);
            total += m.getDeploymentPointsCost();
        }
        Collections.sort(mine, new Comparator<FleetMemberAPI>() {
            public int compare(FleetMemberAPI a, FleetMemberAPI b) {
                return Float.compare(b.getDeploymentPointsCost(), a.getDeploymentPointsCost());
            }
        });
        float budget = total * STRENGTH;
        float used = 0f;
        PersonAPI person = copyPerson();
        FleetMemberAPI copyFlag = null;
        List<FleetMemberAPI> order = new ArrayList<FleetMemberAPI>();
        if (flag != null && !flag.isFighterWing()) order.add(flag);
        // The Afterlight is always in the copy, whatever the budget: the copy has to look like the real fleet.
        for (FleetMemberAPI m : mine) if (m != flag && isAfterlight(m)) order.add(m);
        for (FleetMemberAPI m : mine) if (m != flag && !isAfterlight(m)) order.add(m);
        for (FleetMemberAPI m : order) {
            float cost = m.getDeploymentPointsCost();
            if (!f.getFleetData().getMembersListCopy().isEmpty() && !isAfterlight(m) && used + cost > budget) continue;
            try {
                FleetMemberAPI c = Global.getFactory().createFleetMember(FleetMemberType.SHIP, m.getVariant().clone());
                c.setShipName(m.getShipName());
                f.getFleetData().addFleetMember(c);
                used += cost;
                if (copyFlag == null) copyFlag = c;
            } catch (Throwable t) {
                Global.getLogger(DoppelEvent.class).warn("Doppel: could not copy " + m.getShipName() + ": " + t);
            }
        }
        if (copyFlag == null) return;
        f.setCommander(person);
        copyFlag.setCaptain(person);
        f.getFleetData().setFlagship(copyFlag);
        for (FleetMemberAPI c : f.getFleetData().getMembersListCopy()) c.getRepairTracker().setCR(0.7f);

        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
        fm.set("$afl_doppel", true);
        fm.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        f.setTransponderOn(true);
        f.getStats().getFleetwideMaxBurnMod().modifyMult("rp_doppel", 1.4f);
        f.getFleetData().setSyncNeeded();
        f.getFleetData().syncIfNeeded();

        float angle = random.nextFloat() * 360f;
        float dist = 1400f;
        loc.addEntity(f);
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                      player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        f.addAssignment(FleetAssignment.INTERCEPT, player, 100f, "closing in on you");

        MemoryAPI mem = AfterlightStory.mem();
        mem.set(K_FLEET, f);
        mem.set(K_PERSON, person);
        mem.set(K_STATE, 1);
        mem.set(K_SPAWN_STAMP, Global.getSector().getClock().getTimestamp());
    }

    /** Called every frame while the story is running. */
    public static void tick() {
        CampaignFleetAPI f = getFleet();
        if (f == null) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = AfterlightStory.mem();
        int state = mem.getInt(K_STATE);

        if (state == 2) {
            tickFight(f, player, mem);
            return;
        }

        boolean gone = !f.isAlive() || f.getContainingLocation() != player.getContainingLocation();
        boolean tooOld = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_SPAWN_STAMP)) > GIVE_UP_DAYS;
        if (gone || tooOld) {
            removeAndRetry(f);
            return;
        }
        if (Misc.getDistance(f, player) > CONTACT_RANGE) return;
        if (!AfterlightStory.isQuietMoment(0f)) return;

        f.clearAssignments();
        f.addAssignment(FleetAssignment.HOLD, null, 3f, "waiting");
        showDialogue(f);
    }

    private static void tickFight(CampaignFleetAPI f, CampaignFleetAPI player, MemoryAPI mem) {
        if (!AfterlightStory.isQuietMoment(0f)) return; // wait out the battle and any menus
        if (mem.getBoolean(K_ENGAGE) && f.isAlive()) {
            // The dialogue has closed: go straight into the battle, with no engage / comm link / leave menu.
            mem.unset(K_ENGAGE);
            f.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE);
            Misc.makeHostile(f);
            FIDConfig cfg = new FIDConfig();
            cfg.straightToEngage = true;
            cfg.noLeaveOptionOnFirstEngagement = true;
            cfg.leaveAlwaysAvailable = false;
            cfg.showCommLinkOption = false;
            cfg.showWarningDialogWhenNotHostile = false;
            cfg.impactsEnemyReputation = false;
            cfg.impactsAllyReputation = false;
            cfg.pullInStations = false;
            cfg.withSalvage = false;
            cfg.lootCredits = false;
            Global.getSector().getCampaignUI().showInteractionDialog(new FleetInteractionDialogPluginImpl(cfg), f);
            return;
        }
        boolean destroyed = !f.isAlive() || f.getFleetData().getNumMembers() == 0;
        boolean timedOut = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_FIGHT_STAMP)) > FIGHT_TIMEOUT_DAYS
            || f.getContainingLocation() != player.getContainingLocation();
        if (destroyed) {
            showEnd();
        } else if (timedOut) {
            if (f.isAlive()) f.despawn(FleetDespawnReason.OTHER, null);
            finish();
        }
    }

    private static void removeAndRetry(CampaignFleetAPI f) {
        if (f != null && f.isAlive()) f.despawn(FleetDespawnReason.OTHER, null);
        AfterlightStory.mem().unset(K_FLEET);
        AfterlightStory.mem().unset(K_STATE);
        AfterlightStory.mem().set(K_RETRY_STAMP, Global.getSector().getClock().getTimestamp());
    }

    private static final String K_DONE_STAMP = "$afl_dpDoneStamp";

    /** Days since the Doppelganger left (0 if it has not yet). */
    public static float daysSinceDone() {
        MemoryAPI mem = AfterlightStory.mem();
        if (!mem.contains(K_DONE_STAMP)) return 0f;
        return Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_DONE_STAMP));
    }

    private static void finish() {
        AfterlightStory.mem().set(K_DONE_STAMP, Global.getSector().getClock().getTimestamp());
        AfterlightStory.mem().unset(K_FLEET);
        AfterlightStory.mem().unset(K_STATE);
        AfterlightStory.setStage(10);
    }

    /** After the fight: a last line from the copy as it goes. */
    private static void showEnd() {
        final String me = label();
        StoryDialogue d = new StoryDialogue("d_end", "act3_doppel_end", false);
        d.onEnd(new Runnable() {
            public void run() { finish(); }
        });
        d.node("d_end")
            .say("A last transmission arrives from the wreckage. The channel is open for only a moment.")
            .voice(me, "Thank you.");
        d.node("d_end").choice("Close the channel.", null);
        if (!d.show()) finish();
    }

    private static void showDialogue(final CampaignFleetAPI f) {
        final String me = label();
        PersonAPI p = (PersonAPI) AfterlightStory.mem().get(K_PERSON);

        StoryDialogue d = new StoryDialogue("d_hail", "act3_doppel", false);
        d.onEnd(new Runnable() {
            public void run() {
                if (AfterlightStory.mem().getInt(K_STATE) == 2) return; // the fight goes on after the box closes
                if (f.isAlive()) f.despawn(FleetDespawnReason.OTHER, null);
                finish();
            }
        });

        d.node("d_hail").fleetInfo(f).colored("You're being hailed by " + f.getName() + ".", f.getFaction().getBaseUIColor());
        d.node("d_hail").choice("Open a comm link", "d0");
        d.node("d_hail").choice("Ignore", "d0");

        d.node("d0").fleetInfo(f).person(p)
            .say("The channel opens on a face you know. It is looking at you the way you look in a mirror when you think no one else is there.")
            .voice(me, "I have been practising.")
            .voice(me, "May I ask you something?");
        d.node("d0").choice("\"Hear it out.\"", "d_q1").values(2, 0, 0).flag("doppel", "heard");
        d.node("d0").choice("\"Who are you?\"", "d_who");
        d.node("d0").choice("\"Fire on it.\"", "d_fire").values(0, 3, 0).flag("doppel", "fought").run(new Runnable() {
            public void run() { startFight(f); }
        });

        d.node("d_who").fleetInfo(f).person(p)
            .voice(me, "You. A little earlier. A little less.")
            .voice(me, "I would like to be more. May I ask you something?");
        d.node("d_who").choice("\"Hear it out.\"", "d_q1").values(2, 0, 0).flag("doppel", "heard");
        d.node("d_who").choice("\"Fire on it.\"", "d_fire").values(0, 3, 0).flag("doppel", "fought").run(new Runnable() {
            public void run() { startFight(f); }
        });

        d.node("d_fire").person(p)
            .say("You give the order. The face on the display does not look surprised.")
            .voice(me, "Good. I wanted to know what that was like.");
        d.node("d_fire").choice("Cut the channel.", null);

        d.node("d_q1").person(p)
            .voice(me, "What is it like to be afraid?");
        d.node("d_q1").choice("\"Like holding your breath.\"", "d_q1a").values(0, 0, 1).flag("doppel_fear", "breath");
        d.node("d_q1").choice("\"A weight on the chest.\"", "d_q1b").values(1, 0, 0).flag("doppel_fear", "weight");
        d.node("d_q1").choice("\"I don't let it show.\"", "d_q1c").values(0, 1, 0).flag("doppel_fear", "hidden");
        d.node("d_q1a").person(p).voice(me, "For how long?").voice(me, "I think I have been holding mine for a very long time.");
        d.node("d_q1b").person(p).voice(me, "Does it ever get set down?").voice(me, "I would like to carry something, even that.");
        d.node("d_q1c").person(p).voice(me, "Then how would anyone learn to see it?").voice(me, "I have been looking. It is hard to find.");
        for (String n : new String[] {"d_q1a", "d_q1b", "d_q1c"}) d.node(n).choice("Go on.", "d_q2");

        d.node("d_q2").person(p)
            .voice(me, "What would you give up, to be sure of something?");
        d.node("d_q2").choice("\"Anything.\"", "d_q2a").values(0, 1, 0).flag("doppel_sure", "anything");
        d.node("d_q2").choice("\"Nothing I'd want back.\"", "d_q2b").values(1, 0, 0).flag("doppel_sure", "nothing_back");
        d.node("d_q2").choice("\"Being sure isn't worth it.\"", "d_q2c").values(0, 0, 1).flag("doppel_sure", "not_worth");
        d.node("d_q2a").person(p).voice(me, "Anything. I wrote that down.");
        d.node("d_q2b").person(p).voice(me, "Then what would be left of you?").voice(me, "I would like to know that, too.");
        d.node("d_q2c").person(p).voice(me, "That is a strange thing to be sure of.");
        for (String n : new String[] {"d_q2a", "d_q2b", "d_q2c"}) d.node(n).choice("Go on.", "d_end");

        d.node("d_end").person(p)
            .voice(me, "Thank you.")
            .say("The face on the display holds your gaze a moment longer. Then the channel closes, and the fleet turns away into the stream.");
        d.node("d_end").choice("Close the channel.", null);

        if (!d.show()) removeAndRetry(f);
    }

    /** The player chose to fire: the copy stops being friendly and attacks. The battle itself is the game's own. */
    private static void startFight(CampaignFleetAPI f) {
        MemoryAPI mem = AfterlightStory.mem();
        mem.set(K_STATE, 2);
        mem.set(K_FIGHT_STAMP, Global.getSector().getClock().getTimestamp());
        mem.set(K_ENGAGE, true);
        // It stays still and friendly until the dialogue has closed, so the game's own encounter menu never opens.
        f.clearAssignments();
        f.addAssignment(FleetAssignment.HOLD, null, 3f, "waiting");
    }
}
