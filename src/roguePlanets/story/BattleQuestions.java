package roguePlanets.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Ellis's questions after a battle, beyond the first crew-count question (stage 5).
 * Each question is asked once per game, only when it fits the battle that just ended, and only when Ellis's
 * 40-day cooldown is ready. Which question fits is worked out from a small record of the last battle.
 */
public class BattleQuestions {

    private static final String P = "$afl_bq_";
    private static final String K_STAMP = P + "stamp";
    private static final String K_USED = P + "used";
    private static final String K_WIN = P + "win";
    private static final String K_RETREAT = P + "retreat";
    private static final String K_OWN_SHIPS = P + "ownShips";
    private static final String K_OWN_CREW = P + "ownCrew";
    private static final String K_OWN_DP = P + "ownDp";
    private static final String K_THEIR_CREW = P + "theirCrew";
    private static final String K_THEIR_DP = P + "theirDp";
    private static final String K_THEIR_ALL = P + "theirAll";
    private static final String K_PLAYER_DP = P + "playerDp";
    private static final String K_CAT = P + "cat";
    private static final String K_STREAK = P + "streak";
    private static final String K_OWN_LEFT = P + "ownLeft";
    private static final String K_THEIR_FLED_DP = P + "theirFledDp";
    private static final String K_PURSUED = P + "pursued";
    private static final String K_ESCAPED_ID = P + "escapedId";
    private static final String K_ESCAPED_STAMP = P + "escapedStamp";

    /** Wins in a row (battles against at least this much enemy) before the streak question can be asked. */
    private static final int STREAK_NEEDED = 12;
    private static final float STREAK_MIN_DP = 20f;
    private static final float MIN_DP = 60f;

    private static com.fs.starfarer.api.campaign.rules.MemoryAPI mem() { return AfterlightStory.mem(); }

    // ---------------------------------------------------------------- recording the battle

    /** Called when an engagement involving the player ends. */
    public static void record(EngagementResultAPI r) {
        try {
            if (r == null) return;
            boolean win = r.didPlayerWin();
            EngagementResultForFleetAPI mine = win ? r.getWinnerResult() : r.getLoserResult();
            EngagementResultForFleetAPI theirs = win ? r.getLoserResult() : r.getWinnerResult();
            if (mine == null || theirs == null || !mine.isPlayer()) return;

            int ownShips = 0, ownCrew = 0;
            float ownDp = 0f;
            for (FleetMemberAPI m : mine.getDestroyed()) {
                if (m.isFighterWing()) continue;
                if (m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX)) continue;
                ownShips++;
                ownCrew += (int) m.getMinCrew();
                ownDp += m.getDeploymentPointsCost();
            }
            boolean retreat = !win && mine.getRetreated() != null && !mine.getRetreated().isEmpty();
            int ownLeft = ownShips;
            for (FleetMemberAPI m : mine.getDisabled()) { if (!m.isFighterWing()) ownLeft++; }

            int theirCrew = 0;
            float theirDp = 0f, theirAll = 0f;
            for (FleetMemberAPI m : theirs.getDestroyed()) { theirDp += m.getDeploymentPointsCost(); theirAll += m.getDeploymentPointsCost(); theirCrew += (int) m.getMinCrew(); }
            for (FleetMemberAPI m : theirs.getDisabled()) { theirDp += m.getDeploymentPointsCost(); theirAll += m.getDeploymentPointsCost(); theirCrew += (int) m.getMinCrew(); }
            float theirFledDp = 0f;
            for (FleetMemberAPI m : theirs.getRetreated()) { theirAll += m.getDeploymentPointsCost(); if (!m.isFighterWing()) theirFledDp += m.getDeploymentPointsCost(); }
            for (FleetMemberAPI m : theirs.getReserves()) theirAll += m.getDeploymentPointsCost();

            float playerDp = ownDp;
            if (Global.getSector().getPlayerFleet() != null) {
                for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    if (!m.isFighterWing()) playerDp += m.getDeploymentPointsCost();
                }
            }

            String cat = "other";
            if (theirs.getFleet() != null && theirs.getFleet().getFaction() != null) {
                String f = theirs.getFleet().getFaction().getId();
                if ("pirates".equals(f)) cat = "pirates";
                else if ("luddic_path".equals(f)) cat = "path";
                else if ("remnant".equals(f)) cat = "remnant";
                else if (f != null && !f.equals("derelict") && !f.equals("neutral") && !f.equals("player")) cat = "faction";
            }

            // was this the fleet that got away from an earlier battle? then the player has gone after it
            boolean pursued = false;
            String fleetId = theirs.getFleet() == null ? null : theirs.getFleet().getId();
            if (win && fleetId != null && fleetId.equals(mem().getString(K_ESCAPED_ID))
                && Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_ESCAPED_STAMP)) <= 5f) {
                pursued = true;
                mem().unset(K_ESCAPED_ID);
            } else if (win && fleetId != null && theirFledDp >= 30f) {
                mem().set(K_ESCAPED_ID, fleetId);
                mem().set(K_ESCAPED_STAMP, Global.getSector().getClock().getTimestamp());
            }

            long now = Global.getSector().getClock().getTimestamp();
            boolean merge = mem().contains(K_STAMP) && !mem().getBoolean(K_USED)
                && Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_STAMP)) <= AfterlightStory.BATTLE_MERGE_DAYS;
            if (merge) {
                ownShips += mem().getInt(K_OWN_SHIPS);
                ownCrew += mem().getInt(K_OWN_CREW);
                ownDp += mem().getFloat(K_OWN_DP);
                theirCrew += mem().getInt(K_THEIR_CREW);
                theirDp += mem().getFloat(K_THEIR_DP);
                theirAll += mem().getFloat(K_THEIR_ALL);
                retreat = retreat || mem().getBoolean(K_RETREAT);
                ownLeft += mem().getInt(K_OWN_LEFT);
                theirFledDp += mem().getFloat(K_THEIR_FLED_DP);
                pursued = pursued || mem().getBoolean(K_PURSUED);
            } else {
                // a new battle: the streak counts battles, not rounds
                int streak = mem().getInt(K_STREAK);
                if (win && theirDp >= STREAK_MIN_DP) streak++;
                else if (!win) streak = 0;
                mem().set(K_STREAK, streak);
            }
            mem().set(K_STAMP, now);
            mem().set(K_USED, false);
            mem().set(K_WIN, win);
            mem().set(K_RETREAT, retreat);
            mem().set(K_OWN_SHIPS, ownShips);
            mem().set(K_OWN_CREW, ownCrew);
            mem().set(K_OWN_DP, ownDp);
            mem().set(K_THEIR_CREW, theirCrew);
            mem().set(K_THEIR_DP, theirDp);
            mem().set(K_THEIR_ALL, theirAll);
            mem().set(K_PLAYER_DP, playerDp);
            mem().set(K_CAT, cat);
            mem().set(K_OWN_LEFT, ownLeft);
            mem().set(K_THEIR_FLED_DP, theirFledDp);
            mem().set(K_PURSUED, pursued);
        } catch (Throwable t) {
            Global.getLogger(BattleQuestions.class).warn("Could not record battle for questions: " + t);
        }
    }

    private static boolean fresh() {
        if (!mem().contains(K_STAMP) || mem().getBoolean(K_USED)) return false;
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_STAMP)) <= AfterlightStory.BATTLE_WINDOW_DAYS;
    }

    private static final String[] ALL = {"pirates", "path", "faction", "remnant", "own", "both", "easy", "hard", "retreat", "streak"};

    // ---------------------------------------------------------------- choosing a question

    private static boolean open(String id) {
        return !AfterlightStory.isUsed((Reflections.has(id) ? "rf_" : "bt_") + id);
    }

    private static List<String> eligible() {
        List<String> list = new ArrayList<String>();
        boolean win = mem().getBoolean(K_WIN);
        boolean retreat = mem().getBoolean(K_RETREAT);
        float theirDp = mem().getFloat(K_THEIR_DP);
        float all = mem().getFloat(K_THEIR_ALL);
        float mine = mem().getFloat(K_PLAYER_DP);
        int ownCrew = mem().getInt(K_OWN_CREW);
        int theirCrew = mem().getInt(K_THEIR_CREW);
        String cat = mem().getString(K_CAT);
        if (cat == null) cat = "other";

        if (retreat) {
            if (open("retreat")) list.add("retreat");
            if (mem().getInt(K_OWN_LEFT) >= 1 && open("leftbehind")) list.add("leftbehind");
            return list;
        }
        if (!win || theirDp < MIN_DP) return list;

        if (cat.equals("pirates") && open("pirates")) list.add("pirates");
        if (cat.equals("path") && open("path")) list.add("path");
        if (cat.equals("faction") && open("faction")) list.add("faction");
        if (cat.equals("remnant") && open("remnant")) list.add("remnant");
        if (ownCrew >= 20 && open("own")) list.add("own");
        if (ownCrew >= 20 && theirCrew >= 50 && open("both")) list.add("both");
        if (mine > 0f && all * 4f <= mine && open("easy")) list.add("easy");
        if (mine > 0f && all >= mine * 1.5f && open("hard")) list.add("hard");
        if (mem().getInt(K_STREAK) >= STREAK_NEEDED && open("streak")) list.add("streak");
        if (mem().getInt(K_OWN_LEFT) == 0 && theirDp >= 100f && open("nolosses")) list.add("nolosses");
        if (mem().getBoolean(K_PURSUED)) {
            if (open("pursued")) list.add("pursued");
        } else if (mem().getFloat(K_THEIR_FLED_DP) >= 30f && open("letgo")) {
            list.add("letgo");
        }
        return list;
    }

    /** Called every second from the story script. Returns true if a question was shown. */
    public static boolean tick() {
        int stage = AfterlightStory.getStage();
        if (stage < 5 || stage >= 16) return false;
        if (!fresh()) return false;
        if (Global.getSector().getCampaignUI().isShowingDialog()) return false;
        if (roguePlanets.AfterlightCaptainScript.captainAway() || roguePlanets.AfterlightCaptainScript.captainGone()) return false;
        if (!AfterlightStory.ellisCooldownReady()) return false;
        if (!AfterlightStory.isQuietMoment(600f)) return false;

        List<String> list = eligible();
        if (list.isEmpty()) return false;
        String id = list.get(new Random().nextInt(list.size()));
        return play(id);
    }

    /** Plays one question now, whatever the record says (used by the test keys). */
    public static boolean play(String id) {
        boolean ok = ask(id);
        if (ok) mem().set(K_USED, true);
        return ok;
    }

    // ---------------------------------------------------------------- the questions

    private static class Opt {
        final String text, key;
        final int m, d, c;
        final String[] reply;
        Opt(String text, String key, int m, int d, int c, String... reply) {
            this.text = text; this.key = key; this.m = m; this.d = d; this.c = c; this.reply = reply;
        }
    }

    private static String people(int n) {
        if (n <= 30) return String.valueOf(n);
        return "about " + String.format("%,d", StageEvents.roundPeople(n));
    }

    private static String cap(String s) { return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static boolean ask(String id) {
        if (Reflections.has(id)) return Reflections.play(id);
        StoryDialogue d = new StoryDialogue("q", "bt_" + id, true);
        com.fs.starfarer.api.characters.PersonAPI e = AfterlightCaptainLookup.captain();
        String[] opening;
        Opt[] opts;

        if (id.equals("pirates")) {
            opening = new String[] {"They were pirates.", "Is this one different, because they were pirates?"};
            opts = new Opt[] {
                new Opt("\"They chose that life.\"", "chose", 0, 1, 0,
                    "Chose it. Some of them, I think. Some were handed it.", "How would you tell the two apart?"),
                new Opt("\"It's different. They'd have killed us.\"", "killed", 0, 1, 0,
                    "They might have. I keep wondering what a person looks like the moment before they decide.", "Have you ever watched for it?"),
                new Opt("\"No. People are people.\"", "people", 1, 0, 0,
                    "People are people. I like how that sounds.", "I am still learning what it holds."),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because the word pirates seems to make the number smaller.", "I would like to know whether it should.")
            };
        } else if (id.equals("path")) {
            opening = new String[] {"Those ships flew the Path's sign.", "They seemed very sure of something.", "What is it like to be that sure?"};
            opts = new Opt[] {
                new Opt("\"Certainty like that is dangerous.\"", "danger", 0, 1, 0,
                    "Dangerous. To the one who holds it, or to the one standing near?"),
                new Opt("\"A belief isn't a crime. A weapon is.\"", "belief", 1, 0, 0,
                    "A belief and a hand are different things. I will keep that one."),
                new Opt("\"I envy them a little.\"", "envy", 0, 0, 1,
                    "Do you? I would like to hear which part.", "I find I am curious about it too."),
                new Opt("\"Nobody is that certain.\"", "nobody", 0, 1, 0,
                    "Perhaps not for long. Perhaps everyone is, about one thing.", "What is yours?")
            };
        } else if (id.equals("faction")) {
            opening = new String[] {"They had a flag.", "Does the flag matter?"};
            opts = new Opt[] {
                new Opt("\"It tells me who to shoot.\"", "shoot", 0, 1, 0,
                    "It tells you who to shoot. Does it tell you when to stop?"),
                new Opt("\"Not in the end. They're crews.\"", "crews", 1, 0, 0,
                    "Crews. Yes. A flag is the only part of a ship that cannot be hurt.", "I wonder who it is for."),
                new Opt("\"It matters to them.\"", "them", 0, 1, 0,
                    "It matters to them. I would like to understand being held by a flag."),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because a flag is a very small thing to be so large.", "I am trying to see how it is done.")
            };
        } else if (id.equals("remnant")) {
            opening = new String[] {"That was zero people.", "I counted, the way I always count. There was nothing aboard to count.", "Does that change what it was?"};
            opts = new Opt[] {
                new Opt("\"They're machines. It's easier.\"", "easier", 0, 1, 0,
                    "Easier. I wonder what has been doing the hard part until now."),
                new Opt("\"They were still trying to kill us.\"", "tried", 0, 1, 0,
                    "They were. They kept going, long after whoever sent them. I find that harder to look at than a number."),
                new Opt("\"Somebody built them. Somebody lost them.\"", "built", 1, 0, 0,
                    "Somebody did. Somebody who is not here to be asked.", "Thank you for saying it."),
                new Opt("\"Is it different for you?\"", "asked", 0, 0, 1,
                    "A little. A number of zero is quiet.", "I am not sure yet whether I like how quiet it is.")
            };
        } else if (id.equals("own")) {
            int ownCrew = mem().getInt(K_OWN_CREW);
            opening = new String[] {"You lost " + people(ownCrew) + " of your own.", "Was the fight worth it?"};
            opts = new Opt[] {
                new Opt("\"Every one of them chose to be here.\"", "chose", 0, 1, 0,
                    "Chose. Did they choose the day, or only the work?"),
                new Opt("\"No. I should have found another way.\"", "another", 1, 0, 0,
                    "Another way. Did you see one at the time, or only afterward?"),
                new Opt("\"It was worth it.\"", "worth", 0, 1, 0,
                    "Worth it. To whom? I would like to hear how you add that up."),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because the number is yours, and I would like to know how it is held.")
            };
        } else if (id.equals("both")) {
            int ownCrew = mem().getInt(K_OWN_CREW);
            int theirCrew = mem().getInt(K_THEIR_CREW);
            opening = new String[] {cap(people(ownCrew)) + " of yours. " + cap(people(theirCrew)) + " of theirs.",
                "Is there a number that would have made it not worth it?"};
            opts = new Opt[] {
                new Opt("\"No. A fight is a fight.\"", "fight", 0, 1, 0,
                    "Then the number does not move anything. I wonder what does."),
                new Opt("\"Any number is too many.\"", "toomany", 1, 0, 0,
                    "Any number. Then why does the fight still happen?", "I am asking sincerely."),
                new Opt("\"Fewer of mine. That's what I count.\"", "mine", 0, 1, 0,
                    "Yours first. That seems natural.", "Where does the counting of theirs go, then?"),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because both numbers are real, and they only sit beside each other in me.", "I would like to see them sit beside each other in you.")
            };
        } else if (id.equals("easy")) {
            opening = new String[] {"They could not have won.", "Why did you fight them?"};
            opts = new Opt[] {
                new Opt("\"They came at me.\"", "came", 0, 1, 0,
                    "They came at you. And did you have to answer?"),
                new Opt("\"Because I could.\"", "could", 0, 1, 0,
                    "Because you could. I find that sentence very quiet.", "What else is true because you can?"),
                new Opt("\"They gave me no way out.\"", "noway", 1, 0, 0,
                    "No way out. For you, or for them?"),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because I am trying to learn what a choice looks like when the outcome is already known.")
            };
        } else if (id.equals("hard")) {
            opening = new String[] {"They were stronger than you.", "You could have run.", "Why did you stay?"};
            opts = new Opt[] {
                new Opt("\"Running would have cost more.\"", "cost", 0, 1, 0,
                    "More than this. I wonder what the sum looked like."),
                new Opt("\"I wouldn't leave my people to it.\"", "people", 1, 0, 0,
                    "Your people. Do they know that is why?"),
                new Opt("\"I wanted to see if I could win.\"", "see", 0, 0, 1,
                    "To see. I understand wanting to find out.", "Did it tell you what you hoped?"),
                new Opt("\"There was nowhere to run.\"", "nowhere", 0, 1, 0,
                    "Nowhere. Then it was not a choice, only a direction.", "Does that make it easier?")
            };
        } else if (id.equals("retreat")) {
            opening = new String[] {"You left.", "How do you know when to leave?"};
            opts = new Opt[] {
                new Opt("\"When the cost outweighs the prize.\"", "cost", 0, 1, 0,
                    "The cost and the prize. Is there a part of the cost that is not a number?"),
                new Opt("\"When I'm afraid for my crew.\"", "afraid", 1, 0, 0,
                    "Afraid. I did not think of that as a way of knowing.", "Thank you."),
                new Opt("\"I don't, until it's late.\"", "late", 0, 0, 1,
                    "Until it is late. Then it is a feeling that arrives after the thing it was for."),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because leaving looks like the hardest part, and I have only watched it from outside.", "I would like to understand how it is done well.")
            };
        } else if (id.equals("streak")) {
            opening = new String[] {"You have won many times in a row.", "Does winning change what you want?"};
            opts = new Opt[] {
                new Opt("\"It makes me want more.\"", "more", 0, 1, 0,
                    "More. And is there a point where more is simply the same?"),
                new Opt("\"It makes me tired.\"", "tired", 1, 0, 0,
                    "Tired. I had not thought winning could be heavy.", "What would you set down first?"),
                new Opt("\"I don't notice.\"", "notice", 0, 1, 0,
                    "You do not notice. I wonder who does."),
                new Opt("\"Why do you ask?\"", "asked", 0, 0, 1,
                    "Because I have not won or lost anything yet, and I would like to know what it leaves behind.")
            };
        } else {
            return false;
        }

        StoryDialogue.Node q = d.node("q").person(e);
        for (String line : opening) q.ellis(line);
        for (int i = 0; i < opts.length; i++) {
            Opt o = opts[i];
            String next = "r" + i;
            q.choice(o.text, next).values(o.m, o.d, o.c).flag("bq_" + id, o.key);
            StoryDialogue.Node r = d.node(next).person(e);
            for (String line : o.reply) r.ellis(line);
        }
        return d.show();
    }
}
