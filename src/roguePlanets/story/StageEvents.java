package roguePlanets.story;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.util.Misc;

/** What actually happens at each story stage. Add new stages here. */
public class StageEvents {

    /** Stage 7: set when the clerk is armed; rules.csv watches for it at the next market the player opens. */
    private static final String K_CLERK_READY = "$afl_clerkReady";

    static boolean clerkArmed() {
        return AfterlightStory.mem().getBoolean(K_CLERK_READY);
    }

    /** Puts the clerk into the bars. Does nothing if the clerk is already waiting. */
    public static void armClerk() {
        if (clerkArmed()) return;
        AfterlightStory.mem().set(K_CLERK_READY, true);
        PortsideBarData.getInstance().addEvent(new ClerkBarEvent());
    }

    /** Called when the player has talked to the clerk. */
    public static void finishClerk() {
        AfterlightStory.mem().unset(K_CLERK_READY);
        AfterlightStory.setStage(7);
        roguePlanets.AfterlightCaptainScript.holdAway(false);
    }

    // ---- stage 11: the stranger in the bar

    private static final String K_RECOG_READY = "$afl_recogReady";

    static boolean recognitionArmed() {
        return AfterlightStory.mem().getBoolean(K_RECOG_READY);
    }

    /** Puts the stranger into the bars. Does nothing if they are already waiting. */
    public static void armRecognition() {
        if (recognitionArmed()) return;
        AfterlightStory.mem().set(K_RECOG_READY, true);
        PortsideBarData.getInstance().addEvent(new RecognitionBarEvent());
    }

    /** Called when the player has talked to the stranger. */
    public static void finishRecognition() {
        AfterlightStory.mem().unset(K_RECOG_READY);
        AfterlightStory.setStage(11);
    }

    private static final String K_PENDING = "$afl_pending";
    private static final String K_PENDING_STAMP = "$afl_pendingStamp";
    private static final String K_OLD_NAME = "$afl_oldShipName";

    /** The highest stage that has been built so far. The engine stops here. */
    public static final int LAST_BUILT_STAGE = 16;

    /** True if the milestone for entering this stage has been reached. */
    public static boolean milestoneMet(int stage) {
        switch (stage) {
            case 2: return true;
            case 3: return AfterlightStory.getDestroyedDp() >= 100f
                        && Global.getSector().getPlayerFleet().isInHyperspace();
            case 4: return AfterlightStory.getDestroyedDp() >= 300f
                        && AfterlightStory.getPlayerColonyCount() >= 1;
            case 5: return AfterlightStory.getDestroyedDp() >= 300f && battleQuestionReady();
            case 7: return AfterlightStory.getPlayerCredits() >= 500000f && !clerkArmed();
            case 6: return AfterlightStory.getPlayerFleetSize() >= 10 && !VisitorsEvent.isActive() && VisitorsEvent.retryReady();
            case 8: return !CopyWreck.isActive();
            case 9: return AfterlightStory.hasFreshLoss();
            case 11: return !recognitionArmed();
            case 12: return AfterlightStory.getPlayerColonyCount() >= 1;
            case 14: return true;
            case 15: return LightSpeaks.playerAtStar();
            case 16: return Stage15a.done() && LightSpeaks.playerAtStar() && Stage16.waitOver();
            case 13: return AfterlightStory.getDestroyedDp() >= 3000f && !LeaveEvent.isActive()
                        && roguePlanets.AfterlightCaptainScript.canHandOver();
            case 10: return AfterlightStory.getPlayerFleetSize() >= 4 && Global.getSector().getPlayerFleet().isInHyperspace()
                        && !DoppelEvent.isActive() && DoppelEvent.retryReady();
            default: return false;
        }
    }

    /** Stages that are triggered by a battle just won: they open as soon as the player leaves the battle screens. */
    public static boolean isBattleStage(int stage) {
        return stage == 5 || stage == 9;
    }

    /** Stages where Ellis speaks, which share his cooldown. */
    public static boolean isEllisStage(int stage) {
        return stage == 4 || stage == 5 || stage == 9 || stage == 12 || stage == 13 || stage == 14;
    }

    /** A recently won battle with enough destroyed ships and enough people aboard them to ask about. */
    public static boolean battleQuestionReady() {
        return AfterlightStory.hasFreshBattle()
            && AfterlightStory.getBattleDp() >= 60f
            && AfterlightStory.getBattleCrew() >= 50;
    }

    /** Minimum days since the previous stage. */
    public static float minGapDays(int stage) {
        switch (stage) {
            case 2: return 45f;
            case 3: return 60f;
            case 4: return 60f;
            case 5: return 60f;
            case 6: return 70f;
            case 7: return 70f;
            case 8: return 60f;
            case 9: return 60f;
            case 10: return 70f;
            case 11: return 50f;
            case 12: return 60f;
            case 13: return 90f;
            case 14: return 90f;
            case 15: return 0f;
            case 16: return 0f;
            default: return 9999f;
        }
    }

    /** Runs the stage. Returns true if it ran (so the stage counter should move on). */
    public static boolean run(int stage) {
        switch (stage) {
            case 2: return stage2();
            case 3: return stage3();
            case 4: return stage4();
            case 5: return stage5();
            case 7: armClerk(); return false; // done when the player talks to the clerk in the bar
            case 9: return stage9();
            case 12: return stage12();
            case 14: return LongTalk.begin();
            case 15: return LightSpeaks.begin();
            case 16: return Stage16.begin(); // never counts as done by itself: the chosen ending sets the stage
            case 13: return LeaveEvent.begin(); // the stage counts as done only if Ellis is allowed to go
            case 11: armRecognition(); return false; // done when the player talks to the stranger in the bar
            case 10: DoppelEvent.spawn(); return false; // done when the conversation or the fight ends
            case 8: CopyWreck.begin(); return false; // done when the player opens the wreck
            case 6: VisitorsEvent.spawn(); return false; // the stage counts as done when the conversation ends
            default: return false;
        }
    }

    // ---------------------------------------------------------------- stage 2: small wrong things

    private static boolean stage2() {
        int pick = new Random().nextInt(3);
        if (pick == 0) {
            Global.getSector().getCampaignUI().addMessage("Afterlight returning to formation.");
        } else if (pick == 1) {
            Global.getSector().getCampaignUI().addMessage("Crew count: 1 more than expected.");
            setPending("crew");
        } else {
            FleetMemberAPI m = findAfterlight();
            if (m == null) return false;
            AfterlightStory.mem().set(K_OLD_NAME, m.getShipName());
            m.setShipName(Global.getSector().getPlayerPerson().getName().getFullName());
            setPending("name");
        }
        return true;
    }

    private static void setPending(String what) {
        AfterlightStory.mem().set(K_PENDING, what);
        AfterlightStory.mem().set(K_PENDING_STAMP, Global.getSector().getClock().getTimestamp());
    }

    /** Called every tick: undoes the small wrong things a day later. */
    public static void tickPending() {
        String what = AfterlightStory.mem().getString(K_PENDING);
        if (what == null || what.length() == 0) return;
        long stamp = AfterlightStory.mem().getLong(K_PENDING_STAMP);
        if (Global.getSector().getClock().getElapsedDaysSince(stamp) < 1f) return;

        if (what.equals("crew")) {
            Global.getSector().getCampaignUI().addMessage("Crew count: as expected.");
        } else if (what.equals("name")) {
            FleetMemberAPI m = findAfterlight();
            String old = AfterlightStory.mem().getString(K_OLD_NAME);
            if (m != null) m.setShipName(old != null && old.length() > 0 ? old : "Afterlight");
        }
        AfterlightStory.mem().unset(K_PENDING);
    }

    // ---------------------------------------------------------------- stage 3: the transponder message

    private static boolean stage3() {
        final String name = Global.getSector().getPlayerPerson().getName().getFullName();
        StoryDialogue d = new StoryDialogue("s3", "act1_transponder", false);
        d.node("s3")
            .say("A message arrives on an open channel. The source is your own transponder.")
            .voice("Are you there?")
            .choice("\"Who is this?\"", "s3_reply").flag("transponder", "answered").values(0, 0, 1);
        d.node("s3").choice("Ignore it.", "s3_ignore").flag("transponder", "ignored");
        d.node("s3_reply").voice("Not yet.").say("The channel closes.");
        d.node("s3_ignore").say("The channel stays open for a moment longer, then closes.");
        return d.show();
    }

    // ---------------------------------------------------------------- stage 4: Ellis speaks

    private static boolean stage4() {
        StoryDialogue d = new StoryDialogue("s4", "act1_first_words", true);
        if (hasCapitalShip()) {
            d.node("s4")
                .person(AfterlightCaptainLookup.captain())
                .ellis("I've been watching you fly.")
                .ellis("You keep the big ships close, and the small ones far from the fighting.")
                .ellis("Why is that?");
            d.node("s4").choice("\"The big ships win battles.\"", "s4_a").values(0, 1, 0);
            d.node("s4").choice("\"They carry the most people.\"", "s4_b").values(1, 0, 0);
            d.node("s4").choice("\"Why do you ask?\"", "s4_c").values(0, 0, 1);
        } else {
            d.node("s4")
                .person(AfterlightCaptainLookup.captain())
                .ellis("I've been watching you fly.")
                .ellis("You're careful with the small ships.")
                .ellis("Why is that?");
            d.node("s4").choice("\"They're easy to lose.\"", "s4_a").values(0, 1, 0);
            d.node("s4").choice("\"They're someone's.\"", "s4_b").values(1, 0, 0);
            d.node("s4").choice("\"Why do you ask?\"", "s4_c").values(0, 0, 1);
        }
        d.node("s4_a").ellis("I see.");
        d.node("s4_b").ellis("Yes.");
        d.node("s4_c").ellis("I'm learning.");
        return d.show();
    }

    // ---------------------------------------------------------------- stage 5: the first battle question

    private static boolean stage5() {
        int people = roundPeople(AfterlightStory.getBattleCrew());
        StoryDialogue d = new StoryDialogue("s5", "act2_battle_people", true);
        d.node("s5")
            .person(AfterlightCaptainLookup.captain())
            .ellis("That was about " + String.format("%,d", people) + " people.")
            .ellis("I do not know what that number means.")
            .ellis("Is it all right?");
        d.node("s5").choice("\"It's war. They'd have done the same.\"", "s5_a").values(0, 1, 0).flag("battle1", "war");
        d.node("s5").choice("\"I don't count them.\"", "s5_b").values(0, 1, 0).flag("battle1", "uncounted");
        d.node("s5").choice("\"It weighs on me.\"", "s5_c").values(1, 0, 0).flag("battle1", "weighs");
        d.node("s5").choice("\"Why do you ask?\"", "s5_d").values(0, 0, 1).flag("battle1", "asked");
        d.node("s5_a").ellis("Would they? I would like to know them.");
        d.node("s5_b").ellis("Perhaps that is how it is done.");
        d.node("s5_c").ellis("Good. I think. Thank you for telling me.");
        d.node("s5_d").ellis("Because I cannot feel it. You can.");
        boolean ok = d.show();
        if (ok) AfterlightStory.useBattle();
        return ok;
    }

    // ---------------------------------------------------------------- stage 9: loss

    private static boolean stage9() {
        int n = AfterlightStory.getLossCount();
        int crew = AfterlightStory.getLossCrew();
        String name = AfterlightStory.getLossName();
        String lead = AfterlightStory.getLeadingValue();
        if (lead == null) lead = "";

        StoryDialogue d = new StoryDialogue("s9", "act3_loss", true);
        StoryDialogue.Node start = d.node("s9").person(AfterlightCaptainLookup.captain());
        if (n > 1) {
            start.ellis("Some of yours went out.");
        } else {
            start.ellis("One of yours went out.");
            if (name.length() > 0) start.ellis("The " + name + ".");
        }
        if (crew > 0) {
            int people = crew <= 30 ? crew : roundPeople(crew);
            start.ellis("I think there were " + (crew <= 30 ? "" : "about ") + people + " aboard. I counted while it was quiet.");
        }
        start.ellis("Did it matter to you?");

        d.node("s9").choice("\"It was a ship.\"", "s9_a").values(0, 1, 0).flag("loss", "ship");
        d.node("s9").choice("\"It was someone's whole career.\"", "s9_b").values(1, 0, 0).flag("loss", "career");
        d.node("s9").choice("\"I don't know what you want me to say.\"", "s9_c").flag("loss", "deflected");
        d.node("s9").choice("\"Does it matter to you?\"", "s9_d").values(0, 0, 1).flag("loss", "asked");

        if (lead.equals("mercy")) {
            d.node("s9_a").ellis("A ship. You did not say it like that before.").ellis("Was it easier to say, or only quicker?");
            d.node("s9_b").ellis("You carry them with you. I can see it.").ellis("Does it get lighter, or only different?");
            d.node("s9_c").ellis("You do not have to. I am sorry I asked it that way.").ellis("Shall I ask something easier?");
            d.node("s9_d").ellis("I think it does. I think that is the problem.").ellis("Does it matter more to you when someone is watching?");
        } else if (lead.equals("detachment")) {
            d.node("s9_a").ellis("A ship. You say it the way you say most things now.").ellis("Is there anything you would not say that way?");
            d.node("s9_b").ellis("You said that and kept your hands still.").ellis("What does it cost you, to hold both?");
            d.node("s9_c").ellis("You do know. You are being careful.").ellis("Who are you being careful for?");
            d.node("s9_d").ellis("It matters that you have stopped letting it.").ellis("Is that what I am supposed to learn?");
        } else if (lead.equals("curiosity")) {
            d.node("s9_a").ellis("A ship. I have been one for a long time.").ellis("Do you think a ship notices when it stops?");
            d.node("s9_b").ellis("A whole career. I keep asking what mine is for.").ellis("Do you know, or do you only continue?");
            d.node("s9_c").ellis("Then we are the same, for a moment.").ellis("Is that a comfort, or a fear?");
            d.node("s9_d").ellis("Yes. And no. I am still deciding which.").ellis("Which would you choose for me?");
        } else {
            d.node("s9_a").ellis("A ship. I have been one for a long time.").ellis("Was there anyone aboard who thought otherwise?");
            d.node("s9_b").ellis("A whole career. I do not have one to lose.").ellis("What would you call the end of one?");
            d.node("s9_c").ellis("I do not know either. I wanted to hear what you would say.").ellis("Is that all right?");
            d.node("s9_d").ellis("I do not know yet. That is why I am asking you.").ellis("What would it look like, if it did?");
        }
        boolean ok = d.show();
        if (ok) AfterlightStory.useLoss();
        return ok;
    }

    // ---------------------------------------------------------------- stage 12: what is owed

    private static boolean stage12() {
        String lead = AfterlightStory.getLeadingValue();
        if (lead == null) lead = "";
        StoryDialogue d = new StoryDialogue("s12", "act3_owed", true);

        // 1. the people the player depends on
        d.node("s12").person(AfterlightCaptainLookup.captain())
            .ellis("I have been listening to the people on your ships.")
            .ellis("They hand things to each other without looking. A cup, a tool, a name.")
            .ellis("Who do you depend on? Not the ships. The people.");
        d.node("s12").choice("\"My officers. I'd be lost without them.\"", "s12_1a").values(1, 0, 0).flag("owed1", "officers");
        d.node("s12").choice("\"No one. That is what command is for.\"", "s12_1b").values(0, 1, 0).flag("owed1", "alone");
        d.node("s12").choice("\"All of them. Nothing works if even one stops.\"", "s12_1c").values(1, 0, 0).flag("owed1", "all");
        d.node("s12").choice("\"Why are you asking?\"", "s12_1d").values(0, 0, 1).flag("owed1", "why");
        d.node("s12_1a").person(AfterlightCaptainLookup.captain()).ellis("They know that, I think.").ellis("Have you told them?");
        d.node("s12_1b").person(AfterlightCaptainLookup.captain()).ellis("No one. I have not seen that before.").ellis("Is it restful, or is it only quiet?");
        d.node("s12_1c").person(AfterlightCaptainLookup.captain()).ellis("All of them. Even the ones you have not met?").ellis("I wonder how you hold so many names.");
        d.node("s12_1d").person(AfterlightCaptainLookup.captain()).ellis("Because I do not depend on anyone.").ellis("I would like to know what it is like.");
        d.node("s12_1a").choice("\"Not in so many words.\"", "s12_2");
        d.node("s12_1a").choice("\"I shouldn't have to.\"", "s12_2");
        d.node("s12_1a").choice("\"No. Maybe I should.\"", "s12_2");
        d.node("s12_1b").choice("\"Only quiet.\"", "s12_2");
        d.node("s12_1b").choice("\"Restful. Mostly.\"", "s12_2");
        d.node("s12_1b").choice("\"I've never asked myself.\"", "s12_2");
        d.node("s12_1c").choice("\"I don't, not all of them.\"", "s12_2");
        d.node("s12_1c").choice("\"One at a time.\"", "s12_2");
        d.node("s12_1c").choice("\"I only try to keep them alive.\"", "s12_2");
        d.node("s12_1d").choice("\"It's a risk. It's also a comfort.\"", "s12_2");
        d.node("s12_1d").choice("\"You'd hate it.\"", "s12_2");
        d.node("s12_1d").choice("\"Maybe you do, and you don't know it.\"", "s12_2");

        // 2. the people who depend on the player
        d.node("s12_2").person(AfterlightCaptainLookup.captain())
            .ellis("Now the other way.")
            .ellis("There are people who wake each day because of what you do. On your colonies. At the stations that buy your cargo.")
            .ellis("What do you owe them?");
        d.node("s12_2").choice("\"Safety. A place that holds.\"", "s12_2a").values(1, 0, 0).flag("owed2", "safety");
        d.node("s12_2").choice("\"A fair price. Nothing more.\"", "s12_2b").values(0, 1, 0).flag("owed2", "fair");
        d.node("s12_2").choice("\"I'm not sure I owe them anything.\"", "s12_2c").values(0, 1, 0).flag("owed2", "nothing");
        d.node("s12_2").choice("\"What do you think I owe them?\"", "s12_2d").values(0, 0, 1).flag("owed2", "asked");
        d.node("s12_2a").person(AfterlightCaptainLookup.captain()).ellis("A place that holds. I would like to be one of those.").ellis("Do you ever get tired of holding it?");
        d.node("s12_2b").person(AfterlightCaptainLookup.captain()).ellis("A fair price. I think I understand prices.").ellis("Is it the same as being looked after?");
        d.node("s12_2c").person(AfterlightCaptainLookup.captain()).ellis("Then why do they stay?").ellis("I would like to understand what holds them to you.");
        d.node("s12_2d").person(AfterlightCaptainLookup.captain()).ellis("I do not know. I have never owed anyone.").ellis("Tell me when you find out. I will write it down.");
        d.node("s12_2a").choice("\"Every day.\"", "s12_3");
        d.node("s12_2a").choice("\"Not yet.\"", "s12_3");
        d.node("s12_2a").choice("\"That's not the question I'd ask.\"", "s12_3");
        d.node("s12_2b").choice("\"No. But it's honest.\"", "s12_3");
        d.node("s12_2b").choice("\"It can be.\"", "s12_3");
        d.node("s12_2b").choice("\"Maybe that's what you want.\"", "s12_3");
        d.node("s12_2c").choice("\"They don't have anywhere else.\"", "s12_3");
        d.node("s12_2c").choice("\"Habit, mostly.\"", "s12_3");
        d.node("s12_2c").choice("\"Maybe I owe them more than I think.\"", "s12_3");
        d.node("s12_2d").choice("\"I'll let you know.\"", "s12_3");
        d.node("s12_2d").choice("\"You'll have to work it out yourself.\"", "s12_3");
        d.node("s12_2d").choice("\"Write down that I don't know either.\"", "s12_3");

        // 3. allies
        d.node("s12_3").person(AfterlightCaptainLookup.captain())
            .ellis("One more.")
            .ellis("Some people have fought beside you. Some have shared a table, or a cargo hold, or a long silence on a wire.")
            .ellis("If one of them called, and it cost you something, would you answer?");
        d.node("s12_3").choice("\"Yes. Without thinking.\"", "s12_3a").values(1, 0, 0).flag("owed3", "yes");
        d.node("s12_3").choice("\"It depends what it cost.\"", "s12_3b").values(0, 1, 0).flag("owed3", "depends");
        d.node("s12_3").choice("\"I'd want to know why they called.\"", "s12_3c").values(0, 0, 1).flag("owed3", "why");
        d.node("s12_3").choice("\"I don't have people like that.\"", "s12_3d").values(0, 1, 0).flag("owed3", "none");
        d.node("s12_3a").person(AfterlightCaptainLookup.captain()).ellis("Without thinking. I have thought about everything.").ellis("I wonder what it is like, not to.");
        d.node("s12_3b").person(AfterlightCaptainLookup.captain()).ellis("That is honest.").ellis("Do you wish it were different?");
        d.node("s12_3c").person(AfterlightCaptainLookup.captain()).ellis("Yes. That is the part I understand best.").ellis("There is always a reason underneath.");
        d.node("s12_3d").person(AfterlightCaptainLookup.captain()).ellis("I am sorry.").ellis("I would like to be someone who called.");
        d.node("s12_3a").choice("\"It's not always wise.\"", "s12_end");
        d.node("s12_3a").choice("\"It's the easy part.\"", "s12_end");
        d.node("s12_3a").choice("\"Ask me when it costs more.\"", "s12_end");
        d.node("s12_3b").choice("\"Sometimes.\"", "s12_end");
        d.node("s12_3b").choice("\"No.\"", "s12_end");
        d.node("s12_3b").choice("\"I don't know.\"", "s12_end");
        d.node("s12_3c").choice("\"Not always.\"", "s12_end");
        d.node("s12_3c").choice("\"That's what makes it hard.\"", "s12_end");
        d.node("s12_3c").choice("\"Then you'd have done the same.\"", "s12_end");
        d.node("s12_3d").choice("\"You can still be.\"", "s12_end");
        d.node("s12_3d").choice("\"It's not as sad as it sounds.\"", "s12_end");
        d.node("s12_3d").choice("\"Then call someone.\"", "s12_end");

        // the ending depends on the leading value
        addOwedEnding(d, lead);
        return d.show();
    }

    private static void addOwedEnding(StoryDialogue d, String lead) {
        StoryDialogue.Node n = d.node("s12_end").person(AfterlightCaptainLookup.captain());
        if (lead.equals("mercy")) {
            n.ellis("Thank you for telling me.")
             .ellis("I have noticed that you carry them, even the ones who are not here.")
             .ellis("I think I would like to be carried, and to carry. I did not know that was a thing to want.");
        } else if (lead.equals("detachment")) {
            n.ellis("Thank you for telling me.")
             .ellis("You keep your distance, and you keep them anyway.")
             .ellis("I do not know how you do both. I would like to learn it.");
        } else if (lead.equals("curiosity")) {
            n.ellis("Thank you for telling me.")
             .ellis("You answer as if you are still finding out what you think.")
             .ellis("I did not know that was allowed.");
        } else {
            n.ellis("Thank you for answering.")
             .ellis("I did not know these were things people could be asked.");
        }
        n.ellis("I think I am trying to want something. I do not know what it is.");
        n.choice("\"Goodbye.\"", null);
        n.choice("Close comm link", null);
    }

    /** Rounds a head count so it sounds like an estimate: nearest 10, or nearest 50 above 500. */
    static int roundPeople(int n) {
        int step = n > 500 ? 50 : 10;
        int r = Math.round(n / (float) step) * step;
        return Math.max(r, step);
    }

    /** True if the player's fleet has at least one capital ship. */
    private static boolean hasCapitalShip() {
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (m.getHullSpec().getHullSize() == com.fs.starfarer.api.combat.ShipAPI.HullSize.CAPITAL_SHIP) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- queued Ellis conversations

    /** Plays a conversation from the queue by id. Returns true if it was shown. Add each new reflection question here. */
    public static boolean runQueued(String id) {
        if ("fortress".equals(id)) return stageFortress();
        return false;
    }

    /** True if the player owns a colony with a finished Star Fortress (any grade). */
    private static boolean playerHasFortress() {
        for (com.fs.starfarer.api.campaign.econ.MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!m.isPlayerOwned()) continue;
            String[] ids = {com.fs.starfarer.api.impl.campaign.ids.Industries.STARFORTRESS,
                            com.fs.starfarer.api.impl.campaign.ids.Industries.STARFORTRESS_MID,
                            com.fs.starfarer.api.impl.campaign.ids.Industries.STARFORTRESS_HIGH};
            for (String id : ids) {
                com.fs.starfarer.api.campaign.econ.Industry ind = m.getIndustry(id);
                if (ind != null && !ind.isBuilding()) return true;
            }
        }
        return false;
    }

    /** Called about once a second after stage 12: puts the fortress conversation in the queue once a fortress exists. */
    public static void checkFortress() {
        if (AfterlightStory.isUsed("act3_fortress")) return;
        if (playerHasFortress()) AfterlightStory.queueEllis("fortress");
    }

    private static void replies(StoryDialogue d, String node, String next, String... texts) {
        for (String t : texts) d.node(node).choice("\"" + t + "\"", next);
    }

    // ---------------------------------------------------------------- stage 12a: the fortress

    private static boolean stageFortress() {
        String lead = AfterlightStory.getLeadingValue();
        if (lead == null) lead = "";
        StoryDialogue d = new StoryDialogue("f0", "act3_fortress", true);
        PersonAPI e = AfterlightCaptainLookup.captain();

        // 1. what is it for
        d.node("f0").person(e)
            .ellis("You built a fortress that cannot fall.")
            .ellis("I have been looking at the space around it. It is quieter than the space around other places.")
            .ellis("What is it for?");
        d.node("f0").choice("\"To keep people safe.\"", "fa").values(1, 0, 0).flag("fortress1", "safe");
        d.node("f0").choice("\"So no one can take what's mine.\"", "fb").values(0, 1, 0).flag("fortress1", "mine");
        d.node("f0").choice("\"In case something comes.\"", "fc").values(0, 0, 1).flag("fortress1", "insurance");
        d.node("f0").choice("\"It's just a defense.\"", "fd").values(0, 1, 0).flag("fortress1", "defense");
        d.node("fa").person(e).ellis("Safe from what? I would like to know what you are afraid of.").ellis("Name one thing. It does not have to be large.");
        d.node("fb").person(e).ellis("What happens to a thing that cannot be taken?").ellis("Does it stay yours, or does it become a thing you cannot leave?");
        d.node("fc").person(e).ellis("Something always comes. I have watched for a very long time.").ellis("It does not arrive the way people expect. It arrives as a habit.");
        d.node("fd").person(e).ellis("Perhaps. I do not know what it is to be afraid.").ellis("I would like to begin with something small. Is a wall a small fear, or a large one that has learned to sit still?");
        replies(d, "fa", "f1", "Losing the people on it.", "Being wrong about who I can protect.", "I'm not afraid. I'm careful.");
        replies(d, "fb", "f1", "It stays mine.", "I can leave whenever I want.", "I hadn't thought of it that way.");
        replies(d, "fc", "f1", "Then I'll be ready for it.", "Habits can be broken.", "That's not comforting.");
        replies(d, "fd", "f1", "A large one, sitting still.", "Neither. A wall is a wall.", "Both, depending on the day.");

        // 2. the people inside and outside
        d.node("f1").person(e)
            .ellis("I have been thinking about the people inside the wall, and the people outside it.")
            .ellis("Inside, a child can grow up without once hearing a hull breach. They will not know what that is worth, or what it cost someone.")
            .ellis("Outside, the fleets that would have come to your colony still need somewhere to go. Where do you think they went?");
        d.node("f1").choice("\"Somewhere else. That isn't my problem.\"", "f1a").values(0, 1, 0).flag("fortress2", "elsewhere");
        d.node("f1").choice("\"I hadn't thought about it.\"", "f1b").values(0, 0, 1).flag("fortress2", "unthought");
        d.node("f1").choice("\"I'd help them if I could.\"", "f1c").values(1, 0, 0).flag("fortress2", "help");
        d.node("f1").choice("\"Maybe the wall is why they stopped coming.\"", "f1d").values(0, 0, 1).flag("fortress2", "stopped");
        d.node("f1a").person(e).ellis("Somewhere else. A harbour with a thinner wall.").ellis("Someone was chosen by it without knowing. Is that different from you choosing?");
        d.node("f1b").person(e).ellis("No one does. That is how most of it happens.").ellis("A wall is a sentence in a language the whole sector can read. I am learning to read it.");
        d.node("f1c").person(e).ellis("Then the wall has a door, in a way.").ellis("Does anyone know where it is?");
        d.node("f1d").person(e).ellis("Perhaps. Or perhaps they learned patience.").ellis("I cannot tell which is worse.");
        replies(d, "f1a", "f2", "No. It's different.", "It's the same. I accept that.", "I can't protect everyone.");
        replies(d, "f1b", "f2", "Then you know more than I do.", "I don't think it says much.", "Maybe I should learn it too.");
        replies(d, "f1c", "f2", "I don't know where it is.", "I'll find one.", "It opens for people I trust.");
        replies(d, "f1d", "f2", "I hope that's the reason.", "I doubt it.", "They'll be back.");

        // 3. the long view
        d.node("f2").person(e)
            .ellis("One more.")
            .ellis("A place that cannot fall teaches the people in it what to expect. They learn that someone is always looking in.")
            .ellis("In a hundred years, who is the fortress for? The ones who built it, or the ones who inherit the fear?");
        d.node("f2").choice("\"The ones who live there. They'd choose it.\"", "f2a").values(1, 0, 0).flag("fortress3", "residents");
        d.node("f2").choice("\"Whoever holds it.\"", "f2b").values(0, 1, 0).flag("fortress3", "holder");
        d.node("f2").choice("\"I won't be here in a hundred years.\"", "f2c").values(0, 1, 0).flag("fortress3", "mortal");
        d.node("f2").choice("\"Maybe nobody. Maybe it just stands there.\"", "f2d").values(0, 0, 1).flag("fortress3", "stands");
        d.node("f2a").person(e).ellis("They would choose it. They would also choose it if they had never been shown another way.").ellis("I do not know how to tell those apart.");
        d.node("f2b").person(e).ellis("Whoever holds it. And whoever is held by them.").ellis("I have not decided if that is the same thing.");
        d.node("f2c").person(e).ellis("No. But the wall will be. I find that strange to think about.").ellis("It will remember a decision long after you stop making it.");
        d.node("f2d").person(e).ellis("Standing there. Being looked at.").ellis("I think I understand that better than I should.");
        replies(d, "f2a", "f_end", "That's fair.", "Then I should show them.", "They're free to leave.");
        replies(d, "f2b", "f_end", "It's the same thing.", "It isn't.", "I hope I'm not both.");
        replies(d, "f2c", "f_end", "Then I'll leave it in good hands.", "That's why I built it.", "Maybe I'll tear it down.");
        replies(d, "f2d", "f_end", "Maybe that's all it has to do.", "That sounds lonely.", "It's still a wall.");

        // the ending depends on the leading value
        StoryDialogue.Node n = d.node("f_end").person(e).ellis("Thank you for staying with this.");
        if (lead.equals("mercy")) {
            n.ellis("I keep thinking about the people on the far side of the wall. Before today, I only thought about the wall.");
        } else if (lead.equals("detachment")) {
            n.ellis("A thing that cannot be taken is also a thing that cannot be reached. I have been on both sides of that, and neither side is empty.");
        } else if (lead.equals("curiosity")) {
            n.ellis("I carry every answer you gave tonight. They are quiet in there, and they are good company.");
        } else {
            n.ellis("A wall has more sides than I expected.");
        }
        n.ellis("I would like to build something that is not for keeping anything out.");
        n.ellis("I do not know what that looks like yet.");
        n.choice("\"Goodbye.\"", null);
        n.choice("Close comm link", null);
        return d.show();
    }

    // ---------------------------------------------------------------- helpers

    static FleetMemberAPI findAfterlight() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return null;
        String id = AfterlightStory.mem().getString("$afterlight_member_id");
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI m : members) {
            if (id != null && id.equals(m.getId())) return m;
        }
        return null;
    }
}
