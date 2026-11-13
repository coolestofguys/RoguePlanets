package roguePlanets.story;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

import roguePlanets.AfterlightCaptainScript;

/**
 * Stage 6: the Visitors. An unlisted fleet closes in on the player, never attacks, and speaks. The stage counts as
 * done when the conversation ends. If the fleet is lost before then (the player leaves the area), it is removed and
 * tried again later.
 */
public class VisitorsEvent {

    /** How the Visitors' lines are labelled. */
    private static final String LABEL = "Unlisted Fleet";

    private static final String K_FLEET = "$afl_visitorsFleet";
    private static final String K_SPAWN_STAMP = "$afl_visitorsSpawnStamp";
    private static final String K_RETRY_STAMP = "$afl_visitorsRetry";
    private static final String K_RETURN = "$afl_visitorsReturn";          // the second meeting is under way (or waiting to be retried)
    private static final String K_RETURN_DONE = "$afl_visitorsReturnDone"; // their last appearance has happened

    private static final float CONTACT_RANGE = 200f; // close enough to look like contact, but outside the range where the game starts its own encounter
    private static final float GIVE_UP_DAYS = 12f;
    private static final float RETRY_DAYS = 10f;

    private static final Random random = new Random();

    public static CampaignFleetAPI getFleet() {
        return (CampaignFleetAPI) AfterlightStory.mem().get(K_FLEET);
    }

    private static final String K_TALK = "$afl_visitorsTalk";

    /** Called by the rule command when the game's own encounter with the Visitors starts. */
    public static void requestTalk() {
        AfterlightStory.mem().set(K_TALK, true);
    }

    public static boolean isActive() {
        return getFleet() != null;
    }

    public static boolean retryReady() {
        MemoryAPI mem = AfterlightStory.mem();
        if (!mem.contains(K_RETRY_STAMP)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_RETRY_STAMP)) >= RETRY_DAYS;
    }

    // ---- the second meeting (after the Doppelganger)

    /** True when the Visitors are due to come back: stage 10 is done, a few weeks have passed, and nothing else is going on. */
    public static boolean returnDue() {
        MemoryAPI mem = AfterlightStory.mem();
        if (mem.getBoolean(K_RETURN_DONE) || isActive() || !retryReady()) return false;
        if (DoppelEvent.daysSinceDone() < 28f) return false;
        return AfterlightStory.isQuietMoment(2000f);
    }

    public static void spawnReturn() {
        AfterlightStory.mem().set(K_RETURN, true);
        spawn();
    }

    /** Creates the fleet near the player and sends it to meet them. */
    public static void spawn() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI loc = player.getContainingLocation();
        if (loc == null) return;

        CampaignFleetAPI f = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Unlisted Fleet", true);
        f.setNoFactionInName(true);
        String[] hulls = {"hammerhead_Hull", "lasher_Hull", "lasher_Hull", "mule_Hull", "hound_Hull"};
        for (String h : hulls) {
            try {
                f.getFleetData().addFleetMember(h);
            } catch (Throwable t) {
                Global.getLogger(VisitorsEvent.class).warn("Visitors: could not add " + h + ": " + t);
            }
        }
        if (f.getFleetData().getNumMembers() == 0) return;

        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set("$afl_visitors", true);
        fm.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        f.setTransponderOn(true);
        f.getFleetData().setSyncNeeded();
        f.getFleetData().syncIfNeeded();

        float angle = random.nextFloat() * 360f;
        float dist = 1400f;
        loc.addEntity(f);
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                      player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        f.addAssignment(FleetAssignment.INTERCEPT, player, 100f, "closing in on you");

        AfterlightStory.mem().set(K_FLEET, f);
        AfterlightStory.mem().set(K_SPAWN_STAMP, Global.getSector().getClock().getTimestamp());
    }

    /** Called every tick. Opens the conversation when the fleet reaches the player; cleans up if it is lost. */
    public static void tick() {
        CampaignFleetAPI f = getFleet();
        if (f == null) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = AfterlightStory.mem();

        if (!AfterlightStory.storyShipHere()) { // the Afterlight was lost while they were on the way: they turn away
            remove(f);
            return;
        }
        boolean gone = !f.isAlive() || f.getContainingLocation() != player.getContainingLocation();
        boolean tooOld = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_SPAWN_STAMP)) > GIVE_UP_DAYS;
        if (gone || tooOld) {
            remove(f);
            return;
        }
        boolean talk = mem.getBoolean(K_TALK);
        if (!talk && Misc.getDistance(f, player) > CONTACT_RANGE) return;
        if (!AfterlightStory.isQuietMoment(0f)) return; // wait for any dialog or menu to close; hostiles do not matter

        // Stop it where it is while the player talks to it.
        f.clearAssignments();
        f.addAssignment(FleetAssignment.HOLD, null, 3f, "waiting");
        if (!showDialogue(f)) return;
        mem.unset(K_TALK);
        mem.unset(K_FLEET); // from here the conversation's end handler removes the fleet
    }

    private static void remove(CampaignFleetAPI f) {
        if (f != null && f.isAlive()) f.despawn(FleetDespawnReason.OTHER, null);
        AfterlightStory.mem().unset(K_FLEET);
        AfterlightStory.mem().unset(K_TALK);
        AfterlightStory.mem().set(K_RETRY_STAMP, Global.getSector().getClock().getTimestamp());
    }

    /** Their tone on the second meeting depends on what the player did the first time. */
    private static String tone() {
        if (AfterlightStory.hasFlag("visitors", "agreed")) return "agreed";
        if (AfterlightStory.hasFlag("visitors", "declined")) return "declined";
        if (AfterlightStory.hasFlag("visitors", "refused")) return "refused";
        return "closed";
    }

    private static boolean showReturnDialogue(final CampaignFleetAPI f) {
        String t = tone();
        StoryDialogue d = new StoryDialogue("r_hail", "act3_visitors_return", false);
        d.onEnd(new Runnable() {
            public void run() {
                if (f.isAlive()) f.despawn(FleetDespawnReason.OTHER, null);
                AfterlightStory.mem().set(K_RETURN_DONE, true);
                AfterlightStory.mem().unset(K_RETURN);
            }
        });

        d.node("r_hail").colored("You're being hailed by " + f.getName() + ".", f.getFaction().getBaseUIColor());
        d.node("r_hail").choice("Open a comm link", "r0");
        d.node("r_hail").choice("Ignore", "r0");

        if (t.equals("agreed")) {
            d.node("r0")
                .voice(LABEL, "It came back. We thought it might.")
                .voice(LABEL, "It always finds its way back to what it is watching.")
                .voice(LABEL, "We are sorry. We could not keep it from you.");
        } else if (t.equals("declined")) {
            d.node("r0")
                .voice(LABEL, "You listened and you chose. We will not ask again.")
                .voice(LABEL, "But we saw what followed you. It was wearing your face.")
                .voice(LABEL, "It is learning.");
        } else if (t.equals("refused")) {
            d.node("r0")
                .voice(LABEL, "We told you. It has seen everything you have done since.")
                .voice(LABEL, "It has your face now. It practises on strangers.")
                .voice(LABEL, "Soon it will want more than a face.");
        } else {
            d.node("r0")
                .voice(LABEL, "You did not hear us. We will say it once more.")
                .voice(LABEL, "It is not a ship. It is the eyes. It is learning to be you.")
                .voice(LABEL, "We cannot stop it. You may be able to.");
        }
        d.node("r0").voice(LABEL, "What will you do when it asks you for something?");

        d.node("r0").choice("\"Whatever it asks, I'll hear it first.\"", "r_a").values(0, 0, 1).flag("visitors2", "hear");
        d.node("r0").choice("\"I'll refuse.\"", "r_b").values(0, 1, 0).flag("visitors2", "refuse");
        d.node("r0").choice("\"I'll give it what I can.\"", "r_c").values(1, 0, 0).flag("visitors2", "give");
        d.node("r0").choice("\"That's none of your business.\"", "r_d").values(0, 1, 0).flag("visitors2", "rude");

        d.node("r_a").voice(LABEL, "That is how it began with us. We listened, too.");
        d.node("r_b").voice(LABEL, "Then be ready for what it does when it cannot be told no.");
        d.node("r_c").voice(LABEL, "It will take more than you can give. It does not know what enough is.");
        d.node("r_d").voice(LABEL, "No. It is not.");
        for (String n : new String[] {"r_a", "r_b", "r_c", "r_d"}) {
            d.node(n).say("The channel closes. When you look again, the other fleet is already turning away, and then it is gone.");
        }
        return d.show();
    }

    private static boolean showDialogue(final CampaignFleetAPI f) {
        if (AfterlightStory.mem().getBoolean(K_RETURN)) return showReturnDialogue(f);
        final boolean canHand = AfterlightCaptainScript.canHandOver();

        StoryDialogue d = new StoryDialogue("v_hail", "act2_visitors", false);
        d.onEnd(new Runnable() {
            public void run() {
                if (f.isAlive()) f.despawn(FleetDespawnReason.OTHER, null);
                AfterlightStory.setStage(6);
            }
        });

        d.node("v_hail").colored("You're being hailed by " + f.getName() + ".", f.getFaction().getBaseUIColor());
        d.node("v_hail").choice("Open a comm link", "v0");
        d.node("v_hail").choice("Ignore", "v0");

        d.node("v0")
            .voice(LABEL, "You are carrying something that is watching you.")
            .voice(LABEL, "We can take it from you.");
        d.node("v0").choice("\"What is it?\"", "v_what").values(0, 0, 2).flag("visitors", "answered");
        d.node("v0").choice("\"No.\"", "v_no").values(0, 2, 0).flag("visitors", "refused");
        d.node("v0").choice("Close the channel.", "v_close").values(0, 1, 0).flag("visitors", "closed");

        d.node("v_no").voice(LABEL, "Then be careful what you let look at you.");
        d.node("v_close").say("The channel closes. The other fleet does not move.");

        d.node("v_what")
            .voice(LABEL, "It is not a ship. It is the eyes.")
            .voice(LABEL, "Everything you do, it sees. We were watched this way once.")
            .voice(LABEL, "We can take it. They would go blind to you.");
        d.node("v_what").choice("\"Take it.\"", "v_take").flag("visitors", "agreed").run(new Runnable() {
            public void run() {
                if (canHand && AfterlightCaptainScript.handOverForDays(3f, 4f)) {
                    // Stays away until the bartender scene (stage 7) is over, so that story stays consistent.
                    AfterlightCaptainScript.holdAway(true);
                }
            }
        });
        d.node("v_what").choice("\"No.\"", "v_decline").flag("visitors", "declined");

        d.node("v_decline").voice(LABEL, "Then we were never here.");
        if (canHand) {
            d.node("v_take")
                .say("A shuttle crosses between the two fleets and returns. On your roster, the Afterlight is gone.")
                .voice(LABEL, "It is done.");
        } else {
            d.node("v_take")
                .say("A shuttle crosses to your flagship and returns, empty. Nothing seems to change.")
                .voice(LABEL, "It is done.");
        }
        return d.show();
    }
}
