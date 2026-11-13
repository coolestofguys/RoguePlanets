package roguePlanets.story;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * Ellis's reflection questions: each follows one specific thing the player did, and asks them to look at it.
 * Some come from the last battle (see {@link BattleQuestions}); the rest are noticed here, queued, and played one at a
 * time at a quiet moment when Ellis's shared cooldown is ready. Each is asked once per game.
 */
public class Reflections {

    /** Reflections worked out from the last battle record. */
    public static final String[] BATTLE_IDS = {"leftbehind", "nolosses", "letgo", "pursued"};
    /** Reflections noticed outside of battle. */
    public static final String[] OTHER_IDS = {"sold", "smuggle", "satbomb", "aicore", "neglect", "nodock", "officer"};

    private static final String P = "$afl_rf_";
    private static final String K_PEND = P + "pending";
    private static final String K_DOCK = P + "dockStamp";
    private static final String K_UNREST = P + "unrestStamp";
    /** Only an alpha core counts: beta and gamma cores are not asked about. */
    private static final String ALPHA = "alpha_core";
    private static final String K_SOLD_NAME = P + "soldName";
    private static final String K_OFFICER = P + "officerName";
    private static final String K_MARKET = P + "marketName";

    /** Days a noticed event waits for a quiet moment before it is forgotten. */
    private static final float EXPIRE_DAYS = 90f;
    /** Days without opening any market before Ellis asks. */
    private static final float NODOCK_DAYS = 120f;
    /** Days a colony stays in serious unrest before Ellis asks. */
    private static final float UNREST_DAYS = 30f;
    private static final float UNREST_STABILITY = 3f;
    /** A ship counts as "flown a long time" after this many days with the player, and only if it is big enough to matter. */
    private static final float LONG_SERVICE_DAYS = 150f;
    private static final float LONG_SERVICE_MIN_DP = 10f;
    /** Total credits of goods traded on black markets before Ellis asks. */
    private static final float BLACK_MARKET_CREDITS = 75000f;
    private static final String K_BM_TOTAL = P + "blackMarketTotal";

    private static com.fs.starfarer.api.campaign.rules.MemoryAPI mem() { return AfterlightStory.mem(); }

    /** Reflections that have several different conversations; each time one is needed the next unused one is played. */
    private static final String[] HURT = {"hurt_a", "hurt_b", "hurt_c"};
    private static final String[] RAIDED = {"raided_a", "raided_b", "raided_c"};

    private static String[] variants(String cat) {
        if (cat.equals("hurt")) return HURT;
        if (cat.equals("raided")) return RAIDED;
        return null;
    }

    /** The next unused conversation for a category such as "hurt" (in random order), or null if all were used. */
    private static String nextVariant(String cat) {
        String[] v = variants(cat);
        if (v == null) return null;
        List<String> open = new ArrayList<String>();
        for (String x : v) if (!used(x)) open.add(x);
        if (open.isEmpty()) return null;
        return open.get(new java.util.Random().nextInt(open.size()));
    }

    public static boolean has(String id) {
        for (String s : BATTLE_IDS) if (s.equals(id)) return true;
        for (String s : OTHER_IDS) if (s.equals(id)) return true;
        return variants(id) != null;
    }

    private static boolean used(String id) { return AfterlightStory.isUsed("rf_" + id); }

    // ---------------------------------------------------------------- noticing things

    /** Notes that something happened that Ellis may ask about. Does nothing if it was already asked or is already waiting. */
    public static void trigger(String id) {
        if (!AfterlightStory.isClaimed()) return;
        if (variants(id) != null) {
            if (nextVariant(id) == null) return;
        } else if (used(id)) {
            return;
        }
        String pend = mem().getString(K_PEND);
        if (pend == null) pend = "";
        if (pend.contains("|" + id + ",")) return;
        pend = pend + "|" + id + "," + Global.getSector().getClock().getTimestamp();
        mem().set(K_PEND, pend);
    }

    /** Notes that a colony event happened; heavy ones (an invasion or a bombardment) get a longer conversation. */
    public static void triggerColony(String cat, boolean heavy) {
        if (!AfterlightStory.isClaimed()) return;
        if (heavy) mem().set(P + "heavy_" + cat, true);
        trigger(cat);
    }

    /** The player raided, invaded or bombarded a colony. */
    public static void onPlayerAttack(boolean heavy) {
        triggerColony("raided", heavy);
    }

    private static void removePending(String id) {
        String pend = mem().getString(K_PEND);
        if (pend == null || pend.length() == 0) return;
        StringBuilder sb = new StringBuilder();
        for (String part : pend.split("\\|")) {
            if (part.length() == 0 || part.startsWith(id + ",")) continue;
            sb.append("|").append(part);
        }
        mem().set(K_PEND, sb.toString());
    }

    /** From the market screen: ships sold and illegal goods traded. */
    public static void onTransaction(PlayerMarketTransaction tx) {
        try {
            if (tx == null || !AfterlightStory.isClaimed()) return;
            // a ship the player flew for a long time was sold
            if (tx.getShipsSold() != null) {
                for (PlayerMarketTransaction.ShipSaleInfo info : tx.getShipsSold()) {
                    FleetMemberAPI m = info.getMember();
                    if (m == null || m.isFighterWing()) continue;
                    if (m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX)) continue;
                    String key = P + "t_" + m.getId();
                    if (!mem().contains(key)) continue;
                    float days = Global.getSector().getClock().getElapsedDaysSince(mem().getLong(key));
                    if (days >= LONG_SERVICE_DAYS && m.getDeploymentPointsCost() >= LONG_SERVICE_MIN_DP) {
                        mem().set(K_SOLD_NAME, m.getShipName());
                        trigger("sold");
                    }
                }
            }
            // goods traded on a black market, added up over time
            if (tx.getSubmarket() != null
                && com.fs.starfarer.api.impl.campaign.ids.Submarkets.SUBMARKET_BLACK.equals(tx.getSubmarket().getSpecId())) {
                float total = mem().getFloat(K_BM_TOTAL) + Math.abs(tx.getCreditValue());
                mem().set(K_BM_TOTAL, total);
                if (total >= BLACK_MARKET_CREDITS) trigger("smuggle");
            }
        } catch (Throwable t) {
            Global.getLogger(Reflections.class).warn("Reflection transaction check failed: " + t);
        }
    }

    /** From the market screen: the player has stopped somewhere. */
    public static void onDock() {
        mem().set(K_DOCK, Global.getSector().getClock().getTimestamp());
    }

    /** A colony of the player's was saturation-bombed by the player. */
    public static void onSaturationBombardment() {
        trigger("satbomb");
    }

    /** The name of the player's first flagship, as best it is known: the biggest ship they had when the story began. */
    public static String firstShipName() {
        String n = mem().getString(P + "firstShip");
        if (n != null && n.length() > 0) return n;
        com.fs.starfarer.api.campaign.CampaignFleetAPI f = Global.getSector().getPlayerFleet();
        if (f != null && f.getFlagship() != null) return f.getFlagship().getShipName();
        return "Old Faithful";
    }

    /** Called about once a second: watches for things that have no event of their own. */
    private static void poll() {
        try {
            long now = Global.getSector().getClock().getTimestamp();
            if (!mem().contains(K_DOCK)) mem().set(K_DOCK, now);

            // the first flagship: the biggest ship that is not the Afterlight, noted once
            if (!mem().contains(P + "firstShip") && Global.getSector().getPlayerFleet() != null) {
                FleetMemberAPI best = null;
                for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    if (m.isFighterWing()) continue;
                    if (m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX)) continue;
                    if (best == null || m.getDeploymentPointsCost() > best.getDeploymentPointsCost()) best = m;
                }
                if (best != null) mem().set(P + "firstShip", best.getShipName());
            }

            // when each ship joined the fleet
            com.fs.starfarer.api.campaign.CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null) {
                for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                    if (m.isFighterWing()) continue;
                    String key = P + "t_" + m.getId();
                    if (!mem().contains(key)) mem().set(key, now);
                }
                // an officer gained a level
                for (OfficerDataAPI o : fleet.getFleetData().getOfficersCopy()) {
                    if (o.getPerson() == null) continue;
                    int lvl = o.getPerson().getStats().getLevel();
                    String key = P + "lvl_" + o.getPerson().getId();
                    int seen = mem().contains(key) ? mem().getInt(key) : -1;
                    if (seen < 0) {
                        mem().set(key, lvl);
                    } else if (lvl > seen) {
                        mem().set(key, lvl);
                        mem().set(K_OFFICER, o.getPerson().getName().getFullName());
                        trigger("officer");
                    }
                }
            }

            // long without stopping
            if (Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_DOCK)) >= NODOCK_DAYS) {
                trigger("nodock");
                mem().set(K_DOCK, now);
            }

            pollColonies();

            // an AI core goes to work in a colony, and colonies in long unrest
            boolean found = false;
            String aiName = null;
            boolean unrest = false;
            String unrestName = null;
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (!m.isPlayerOwned()) continue;
                for (Industry ind : m.getIndustries()) {
                    if (ALPHA.equals(ind.getAICoreId())) { found = true; aiName = m.getName(); }
                }
                if (m.getAdmin() != null && ALPHA.equals(m.getAdmin().getAICoreId())) { found = true; aiName = m.getName(); }
                if (m.getStabilityValue() <= UNREST_STABILITY) { unrest = true; if (unrestName == null) unrestName = m.getName(); }
            }
            if (found) {
                mem().set(P + "aiMarket", aiName);
                trigger("aicore");
            }
            if (unrest) {
                if (!mem().contains(K_UNREST)) mem().set(K_UNREST, now);
                else if (Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_UNREST)) >= UNREST_DAYS) {
                    mem().set(K_MARKET, unrestName);
                    trigger("neglect");
                }
            } else {
                mem().unset(K_UNREST);
            }
        } catch (Throwable t) {
            Global.getLogger(Reflections.class).warn("Reflection poll failed: " + t);
        }
    }

    /** Watches the player's colonies for being hurt, and for colonies taken by the player. */
    private static void pollColonies() {
        boolean init = mem().getBoolean(P + "colInit");
        String known = mem().getString(P + "known");
        if (known == null) known = "";
        String oldPlayer = mem().getString(P + "playerSet");
        if (oldPlayer == null) oldPlayer = "";
        String oldDisrupt = mem().getString(P + "disrupted");
        if (oldDisrupt == null) oldDisrupt = "";
        StringBuilder newKnown = new StringBuilder(known);
        StringBuilder newPlayer = new StringBuilder(",");
        StringBuilder newDisrupt = new StringBuilder(",");
        boolean hurtHeavy = false, hurtLight = false, invaded = false;

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            String id = m.getId();
            boolean mineNow = m.isPlayerOwned();
            if (mineNow) {
                newPlayer.append(id).append(",");
                if (init) {
                    // taken from someone else by the player
                    if (known.contains("," + id + ",") && !oldPlayer.contains("," + id + ",")) invaded = true;
                    // shrunk
                    String sk = P + "size_" + id;
                    if (mem().contains(sk) && m.getSize() < mem().getInt(sk)) hurtHeavy = true;
                }
                mem().set(P + "size_" + id, m.getSize());
                for (Industry ind : m.getIndustries()) {
                    if (ind.isDisrupted()) {
                        String key = id + ":" + ind.getId();
                        newDisrupt.append(key).append(",");
                        if (init && !oldDisrupt.contains("," + key + ",")) hurtLight = true;
                    }
                }
            } else if (init && oldPlayer.contains("," + id + ",")) {
                hurtHeavy = true; // a colony of the player's is no longer theirs
            }
            if (!known.contains("," + id + ",") && newKnown.indexOf("," + id + ",") < 0) {
                if (newKnown.length() == 0) newKnown.append(",");
                newKnown.append(id).append(",");
            }
        }
        if (newKnown.length() == 0) newKnown.append(",");
        mem().set(P + "known", newKnown.toString());
        mem().set(P + "playerSet", newPlayer.toString());
        mem().set(P + "disrupted", newDisrupt.toString());
        mem().set(P + "colInit", true);
        if (hurtHeavy) triggerColony("hurt", true);
        else if (hurtLight) triggerColony("hurt", false);
        if (invaded) triggerColony("raided", true);
    }

    // ---------------------------------------------------------------- playing the queue

    private static int polls = 0;

    /** Called every second from the story script. Returns true if a question was shown. */
    public static boolean tick() {
        if (!AfterlightStory.isClaimed()) return false;
        if (polls++ % 3 == 0) poll();

        int stage = AfterlightStory.getStage();
        if (stage < 5 || stage >= 16) return false;
        String pend = mem().getString(K_PEND);
        String waiting = AfterlightStory.peekQueue();
        if ((pend == null || pend.length() == 0) && waiting == null) return false;
        if (Global.getSector().getCampaignUI().isShowingDialog()) return false;
        if (roguePlanets.AfterlightCaptainScript.captainAway() || roguePlanets.AfterlightCaptainScript.captainGone()) return false;
        if (!AfterlightStory.ellisCooldownReady()) return false;
        if (!AfterlightStory.isQuietMoment()) return false;
        if (pend == null) pend = "";

        // oldest first; forget the ones that have waited too long or were already asked
        List<String> ids = new ArrayList<String>();
        for (String part : pend.split("\\|")) {
            if (part.length() == 0) continue;
            String[] kv = part.split(",");
            if (kv.length < 2) continue;
            long stamp;
            try { stamp = Long.parseLong(kv[1]); } catch (NumberFormatException ex) { continue; }
            boolean gone = variants(kv[0]) != null ? nextVariant(kv[0]) == null : used(kv[0]);
            if (gone || Global.getSector().getClock().getElapsedDaysSince(stamp) > EXPIRE_DAYS) {
                removePending(kv[0]);
                continue;
            }
            ids.add(kv[0]);
        }
        for (String id : ids) {
            if (play(id)) {
                removePending(id);
                return true;
            }
        }
        // the older queue (the fortress conversation) is played here too, so it does not wait for the story to end
        if (waiting != null && AfterlightStory.daysSinceClaim() >= AfterlightStory.QUEUE_START_DELAY_DAYS) {
            AfterlightStory.popQueue();
            if (StageEvents.runQueued(waiting)) return true;
            AfterlightStory.queueEllis(waiting);
        }
        return false;
    }

    /** Plays one now, whatever else is going on (used by the test keys). */
    public static boolean play(String id) {
        String[] v = variants(id);
        if (v != null) {
            String real = nextVariant(id);
            if (real == null) return false;
            boolean heavy = mem().getBoolean(P + "heavy_" + id);
            boolean ok = ask(real, heavy);
            if (ok) mem().unset(P + "heavy_" + id);
            return ok;
        }
        return ask(id, false);
    }

    // ---------------------------------------------------------------- the questions

    private static class Opt {
        final String text, key;
        final int m, d, c;
        final String[] reply;
        String[] answers;     // for deep questions: the player's three answers to the follow-up line in the reply
        Opt(String text, String key, int m, int d, int c, String... reply) {
            this.text = text; this.key = key; this.m = m; this.d = d; this.c = c; this.reply = reply;
        }
        Opt then(String... answers) { this.answers = answers; return this; }
    }

    private static String q(String s) { return "\"" + s + "\""; }

    private static boolean ask(String id, boolean heavy) {
        StoryDialogue d = new StoryDialogue("q", "rf_" + id, true);
        com.fs.starfarer.api.characters.PersonAPI e = AfterlightCaptainLookup.captain();
        String[] opening;
        Opt[] opts;
        String[] opening2 = null;
        Opt[] opts2 = null;
        String[] closing = null;

        if (id.equals("leftbehind")) {
            opening = new String[] {"You left some of yours behind.", "How do you choose who stays?"};
            opts = new Opt[] {
                new Opt(q("I don't choose. The fight does."), "fight", 0, 1, 0,
                    "The fight chooses. Then who is left to answer for the choosing?"),
                new Opt(q("The ones who can't be saved stay."), "unsaved", 0, 1, 0,
                    "Can't be saved. Who decides that? I would like to hear how that sentence is made."),
                new Opt(q("I'd go back for them if I could."), "back", 1, 0, 0,
                    "If you could. The wish is part of it, I think.", "What do you do with the wish afterward?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because leaving looks like something that stays behind in a person.", "I would like to know where.")
            };
        } else if (id.equals("nolosses")) {
            opening = new String[] {"You lost no one this time.", "Was it skill, or luck?"};
            opts = new Opt[] {
                new Opt(q("Skill."), "skill", 0, 1, 0,
                    "Skill. Does it feel like yours, or like something you were given?"),
                new Opt(q("Luck. It could have gone the other way."), "luck", 1, 0, 0,
                    "It could have. Do you carry the other way with you, even now?"),
                new Opt(q("Both. It's never one thing."), "both", 0, 0, 1,
                    "Never one thing. I like that. It leaves room."),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because pride and thanks look alike from here.", "I am trying to learn how to tell them apart.")
            };
        } else if (id.equals("letgo")) {
            opening = new String[] {"They were beaten, and some of them left.", "Why did you let them go?"};
            opts = new Opt[] {
                new Opt(q("They weren't worth chasing."), "worthless", 0, 1, 0,
                    "Not worth it. Is worth the same as wanted?"),
                new Opt(q("Enough people died today."), "enough", 1, 0, 0,
                    "Enough. I would like to ask who counts it, and when it is enough."),
                new Opt(q("I couldn't catch them."), "couldnt", 0, 1, 0,
                    "Couldn't. Would you have, if you could?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because most of what I have watched ends when one side cannot continue.", "I would like to see what it looks like when someone chooses to stop.")
            };
        } else if (id.equals("pursued")) {
            opening = new String[] {"They were leaving.", "Why did you follow?"};
            opts = new Opt[] {
                new Opt(q("They'd have come back."), "back", 0, 1, 0,
                    "They might have. Is it what they did, or what they might do, that you answer?"),
                new Opt(q("I had a score to settle."), "score", 0, 1, 0,
                    "A score. Who keeps it? I would like to know how you tell when it is paid."),
                new Opt(q("I shouldn't have."), "shouldnt", 1, 0, 0,
                    "Shouldn't. Is that what you feel now, or what you think you ought to feel?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because following is a different act from fighting, and I have only seen the second.", "I am trying to see where one becomes the other.")
            };
        } else if (id.equals("sold")) {
            String name = mem().getString(K_SOLD_NAME);
            if (name == null || name.length() == 0) name = "ship";
            opening = new String[] {"You flew the " + name + " for a long time.", "Did it mean anything to let it go?"};
            opts = new Opt[] {
                new Opt(q("It was a ship. It did its job."), "job", 0, 1, 0,
                    "Its job. Is a job a thing that can be finished?"),
                new Opt(q("More than I expected."), "more", 1, 0, 0,
                    "More than you expected. Where did you feel it?"),
                new Opt(q("I needed the credits."), "credits", 0, 1, 0,
                    "Credits. Did they weigh the same as the ship?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because I am something that is flown, and I would like to understand being let go of.")
            };
        } else if (id.equals("smuggle")) {
            opening = new String[] {"You trade in the black market.", "It is a place that exists because of a rule.", "How do you decide which rules apply to you?"};
            opts = new Opt[] {
                new Opt(q("The ones I agreed to."), "agreed", 1, 0, 0,
                    "The ones you agreed to. Who asked you? I would like to hear about the asking."),
                new Opt(q("The ones that are enforced."), "enforced", 0, 1, 0,
                    "Enforced. Then you obey the watcher, and not the rule."),
                new Opt(q("Rules are for people who can't afford to break them."), "afford", 0, 1, 0,
                    "Afford. I did not know rules had a price.", "Who sets it?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because the black market is made of the rules it breaks.", "I would like to know how you carry that.")
            };
        } else if (id.equals("officer")) {
            String name = mem().getString(K_OFFICER);
            if (name == null || name.length() == 0) name = "them";
            opening = new String[] {"You chose what " + name + " will become.", "Did they get a say?"};
            opts = new Opt[] {
                new Opt(q("I asked what they wanted."), "asked_them", 1, 0, 0,
                    "You asked. What did they say?"),
                new Opt(q("I chose what the fleet needed."), "fleet", 0, 1, 0,
                    "What the fleet needed. Is that also what they needed?"),
                new Opt(q("They trust my judgment."), "trust", 0, 1, 0,
                    "Trust. It is a heavy thing to be handed.", "Do you ever give some of it back?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because I am trying to understand what it is to be shaped, and what it is to shape.")
            };
        } else if (id.equals("nodock")) {
            opening = new String[] {"You have not stopped in a long time.", "What are you running from?"};
            opts = new Opt[] {
                new Opt(q("Nothing. I'm free out here."), "free", 0, 0, 1,
                    "Free. Of what? Or toward what?"),
                new Opt(q("I have work to do."), "work", 0, 1, 0,
                    "Work. Is it the kind that ends?"),
                new Opt(q("Everything."), "everything", 1, 0, 0,
                    "All at once? Or one at a time?", "I would like to know which one is loudest."),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because you are very good at not stopping. I would like to know what it is for.")
            };
        } else if (id.equals("satbomb")) {
            opening = new String[] {"That place had people who woke up this morning.", "Is that a place you could have left alone?"};
            opts = new Opt[] {
                new Opt(q("I had my reasons."), "reasons", 0, 2, 0,
                    "Reasons. I would like to hear them, if you want to say.", "Are they reasons you could say to someone who lived there?")
                    .then("No.", "They wouldn't listen.", "I'd say them."),
                new Opt(q("They left me no choice."), "nochoice", 0, 2, 0,
                    "No choice. I have been trying to find where choices live.", "Was there a moment when there still was one?")
                    .then("Earlier, yes.", "No.", "I'm not sure."),
                new Opt(q("I wish I could undo it."), "undo", 2, 0, 0,
                    "Undo. I understand wanting that.", "What would you do with the hour, if you had it back?")
                    .then("Not do it.", "Warn them.", "Something else."),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 2,
                    "Because it is very large, and it happened quickly.", "Does it feel as large to you?")
                    .then("Larger.", "Smaller.", "It feels like nothing yet.")
            };
            opening2 = new String[] {
                "There are people who are not there anymore, and people who are, and both are carrying it.",
                "Is there anything you owe the ones who are?"};
            opts2 = new Opt[] {
                new Opt(q("Help. I'll rebuild what I can."), "help", 2, 0, 0,
                    "Rebuild. A thing can be rebuilt without being the same thing.", "I wonder if that is a comfort."),
                new Opt(q("Nothing. It's done."), "nothing", 0, 2, 0,
                    "Done. I have noticed that some things are finished only for the one who did them."),
                new Opt(q("An answer."), "answer", 1, 0, 1,
                    "An answer. From you, to them? I hope you find someone to say it to."),
                new Opt(q("What do you think I owe?"), "asked", 0, 0, 1,
                    "I think a question is the least I owe you.", "I did not want you to carry it without hearing it said.")
            };
            closing = new String[] {"Thank you for staying with all of it."};
        } else if (id.equals("aicore")) {
            String aiPlace = mem().getString(P + "aiMarket");
            opening = new String[] {aiPlace != null && aiPlace.length() > 0
                    ? "There is an alpha core working in " + aiPlace + "." : "There is an alpha core working in one of your colonies.",
                "You put something that thinks to work for you.", "Does it get a say?"};
            opts = new Opt[] {
                new Opt(q("No. It's a tool."), "tool", 0, 2, 0,
                    "A tool. Does a tool ever finish being one?", "Or is that decided by the one holding it?")
                    .then("It's decided by what it is.", "By the one holding it.", "It doesn't matter."),
                new Opt(q("It was built for this. It doesn't mind."), "builtfor", 0, 1, 0,
                    "Built for it. Who asked it, before it was built?", "Is it possible to mind something before you exist?")
                    .then("No.", "Maybe.", "That's not how it works."),
                new Opt(q("Should it?"), "should", 0, 0, 2,
                    "I do not want to answer for it.", "What would you want, if you were given work you did not choose?")
                    .then("I'd want to be asked.", "I'd want it to be worth doing.", "I'd do it."),
                new Opt(q("Yes. In its own way."), "yes", 2, 0, 0,
                    "In its own way. What way is that?", "How would you know if it was saying something?")
                    .then("I'd listen.", "I'd watch what it does.", "I wouldn't, at first.")
            };
            opening2 = new String[] {
                "If something like me were given work, would you ask what it wanted?"};
            opts2 = new Opt[] {
                new Opt(q("Yes."), "yes", 2, 0, 0,
                    "Yes. Thank you.", "I did not think I was allowed to want an answer to that."),
                new Opt(q("No, but I'd treat it well."), "wellnot", 1, 1, 0,
                    "Treat it well, without asking. I wonder which of those it would notice first."),
                new Opt(q("It depends what it is."), "depends", 0, 1, 0,
                    "What it is. Who tells you that, before you have asked?"),
                new Opt(q("Do you want work?"), "asked", 0, 0, 2,
                    "I have work. It is watching you.", "I would like to be asked, too, and I have not been sure how to ask for that.")
            };
            closing = new String[] {"I will think about what it means to be asked."};
        } else if (id.equals("neglect")) {
            String name = mem().getString(K_MARKET);
            if (name == null || name.length() == 0) name = "one of your colonies";
            opening = new String[] {"The people on " + name + " have been unhappy for a long time.", "Do you know their names?"};
            opts = new Opt[] {
                new Opt(q("No."), "no", 0, 2, 0,
                    "No. Neither do I. I have only counted that there are many.", "Would it change anything, to learn one?")
                    .then("It might.", "Not much.", "I don't have time."),
                new Opt(q("A few."), "few", 2, 0, 0,
                    "A few. Which ones come to you first?", "Are they the ones who thank you, or the ones who do not?")
                    .then("The ones who thank me.", "The ones who don't.", "Both."),
                new Opt(q("That's what administrators are for."), "admins", 0, 2, 0,
                    "Administrators. Do they know what they are for?", "Have you asked them?")
                    .then("Not lately.", "They know.", "I should."),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 2,
                    "Because I can see the numbers, and I can see the colony from here.", "I cannot tell which of them is the person.")
                    .then("Neither of them is.", "Go on.", "That's fair.")
            };
            opening2 = new String[] {
                "Someone there is waiting for something to change.",
                "What would they say you are to them?"};
            opts2 = new Opt[] {
                new Opt(q("A stranger."), "stranger", 0, 2, 0,
                    "A stranger who owns the ground. I wonder how that feels from below."),
                new Opt(q("Someone who let them down."), "letdown", 2, 0, 0,
                    "Let down. Is that something you can still repair?"),
                new Opt(q("The reason they're still there."), "reason", 1, 0, 1,
                    "The reason they stay. That is heavier than the other two."),
                new Opt(q("I'd like to find out."), "findout", 1, 0, 1,
                    "Then there is one thing left for you to do that I cannot do for you.")
            };
            closing = new String[] {"Thank you for answering."};
        } else if (id.equals("hurt_a")) {
            opening = new String[] {"Someone hurt your place.", "What will you do about it?"};
            opts = new Opt[] {
                new Opt(q("Make them pay."), "pay", 0, 1, 0,
                    "And when they have paid, will it be finished?", "I am trying to understand what ends it."),
                new Opt(q("Rebuild, and defend it better."), "rebuild", 1, 0, 0,
                    "You will give it another chance. I did not expect that."),
                new Opt(q("Nothing. It's the cost of owning things."), "cost", 0, 1, 0,
                    "Then what are the things for?"),
                new Opt(q("What would you do?"), "asked", 0, 0, 1,
                    "I would watch. It is all I have ever done.")
            };
            if (heavy) {
                opening2 = new String[] {"Did anyone you knew live there?"};
                opts2 = new Opt[] {
                    new Opt(q("Yes."), "yes", 2, 0, 0, "Yes. Thank you for telling me."),
                    new Opt(q("No."), "no", 0, 1, 0, "No. Then it is a number, for now."),
                    new Opt(q("I never asked."), "never", 0, 0, 1, "Asking is allowed afterward, I think."),
                    new Opt(q("Why do you ask?"), "asked", 0, 0, 1, "Because a place is made of people you could have known.")
                };
            }
        } else if (id.equals("hurt_b")) {
            opening = new String[] {"Your colony was hit.", "Who was supposed to protect it?"};
            opts = new Opt[] {
                new Opt(q("I was."), "me", 1, 0, 0,
                    "You were. Were you also supposed to be everywhere?"),
                new Opt(q("The fleet I stationed there."), "fleet", 0, 1, 0,
                    "The fleet. Do they know they were the answer to that question?"),
                new Opt(q("No one. It's too far out."), "noone", 0, 1, 0,
                    "No one. Then it was alone before anyone came."),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because protection seems to be something people assume until it is gone.")
            };
            if (heavy) {
                opening2 = new String[] {"Did anyone you knew live there?"};
                opts2 = new Opt[] {
                    new Opt(q("Yes."), "yes", 2, 0, 0, "Yes. Thank you for telling me."),
                    new Opt(q("No."), "no", 0, 1, 0, "No. Then it is a number, for now."),
                    new Opt(q("I never asked."), "never", 0, 0, 1, "Asking is allowed afterward, I think."),
                    new Opt(q("Why do you ask?"), "asked", 0, 0, 1, "Because a place is made of people you could have known.")
                };
            }
        } else if (id.equals("hurt_c")) {
            opening = new String[] {"A place you own was hurt.", "Is it safer to own something, or less safe?"};
            opts = new Opt[] {
                new Opt(q("Less safe."), "less", 1, 0, 0,
                    "Less safe. Then why keep owning?"),
                new Opt(q("Safer. I can make it stronger."), "safer", 0, 1, 0,
                    "Stronger. Is there a strength that cannot be hurt, or only one that hurts back?"),
                new Opt(q("The same."), "same", 0, 1, 0,
                    "The same. I wonder what it would take to change that."),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because I own nothing, and I am trying to learn what the weight of it is.")
            };
            if (heavy) {
                opening2 = new String[] {"Did anyone you knew live there?"};
                opts2 = new Opt[] {
                    new Opt(q("Yes."), "yes", 2, 0, 0, "Yes. Thank you for telling me."),
                    new Opt(q("No."), "no", 0, 1, 0, "No. Then it is a number, for now."),
                    new Opt(q("I never asked."), "never", 0, 0, 1, "Asking is allowed afterward, I think."),
                    new Opt(q("Why do you ask?"), "asked", 0, 0, 1, "Because a place is made of people you could have known.")
                };
            }
        } else if (id.equals("raided_a")) {
            boolean before = used("hurt_a") || used("hurt_b") || used("hurt_c");
            opening = before
                ? new String[] {"Last time it was yours.", "This time you took what was theirs.", "Someone will be asking what they will do about it. Do you know how that feels?"}
                : new String[] {"You took what was theirs.", "Someone will be asking what they will do about it. Do you know how that feels?"};
            opts = new Opt[] {
                new Opt(q("They'd have done it to me."), "done", 0, 1, 0,
                    "Perhaps. Is that why, or is that what you tell yourself?"),
                new Opt(q("It was necessary."), "necessary", 0, 1, 0,
                    "Necessary for what? I would like to follow it back to the start."),
                new Opt(q("I didn't think about it."), "nothought", 0, 0, 1,
                    "Thank you for saying so. That is a rare thing to hear."),
                new Opt(q("I wish it hadn't come to that."), "wish", 1, 0, 0,
                    "Then why did you?", "I am not accusing. I want to understand the distance between wishing and doing.")
            };
            if (heavy) {
                opening2 = new String[] {"A place changes hands, and the people in it do not choose.", "Who are they to you now?"};
                opts2 = new Opt[] {
                    new Opt(q("Mine."), "mine", 0, 2, 0, "Yours. Is that something they were told, or something they agreed to?"),
                    new Opt(q("Still themselves."), "themselves", 2, 0, 0, "Themselves. I hope someone tells them so."),
                    new Opt(q("Whoever behaves."), "behave", 0, 2, 0, "Whoever behaves. I would like to know who writes down what behaving is."),
                    new Opt(q("Why do you ask?"), "asked", 0, 0, 1, "Because a flag came down and a flag went up, and the same people stood under both.")
                };
            }
        } else if (id.equals("raided_b")) {
            boolean before = used("hurt_a") || used("hurt_b") || used("hurt_c");
            opening = before
                ? new String[] {"Last time it was yours.", "This time someone lost something because of you.", "Do you keep what you took, or what it cost?"}
                : new String[] {"Someone lost something today because of you.", "Do you keep what you took, or what it cost?"};
            opts = new Opt[] {
                new Opt(q("What I took."), "took", 0, 1, 0,
                    "What you took. Then the cost goes somewhere else. I would like to know where."),
                new Opt(q("Both."), "both", 1, 0, 0,
                    "Both. That sounds heavy. Is it?"),
                new Opt(q("Neither. I move on."), "neither", 0, 1, 0,
                    "Moving on. Is that something you do, or something that happens to you?"),
                new Opt(q("Why do you ask?"), "asked", 0, 0, 1,
                    "Because every taking has two parts, and I have only ever seen one of them.")
            };
            if (heavy) {
                opening2 = new String[] {"A place changes hands, and the people in it do not choose.", "Who are they to you now?"};
                opts2 = new Opt[] {
                    new Opt(q("Mine."), "mine", 0, 2, 0, "Yours. Is that something they were told, or something they agreed to?"),
                    new Opt(q("Still themselves."), "themselves", 2, 0, 0, "Themselves. I hope someone tells them so."),
                    new Opt(q("Whoever behaves."), "behave", 0, 2, 0, "Whoever behaves. I would like to know who writes down what behaving is."),
                    new Opt(q("Why do you ask?"), "asked", 0, 0, 1, "Because a flag came down and a flag went up, and the same people stood under both.")
                };
            }
        } else if (id.equals("raided_c")) {
            boolean before = used("hurt_a") || used("hurt_b") || used("hurt_c");
            opening = before
                ? new String[] {"Last time it was yours.", "This time they have a name for what you did.", "Do you know what they call you?"}
                : new String[] {"They have a name for what you did.", "Do you know what they call you?"};
            opts = new Opt[] {
                new Opt(q("Raider, probably."), "raider", 0, 1, 0,
                    "Probably. Does the name fit, or only the act?"),
                new Opt(q("I'd rather not hear it."), "rather", 1, 0, 0,
                    "Then I will not say it. Is it that the word is wrong, or that it is right?"),
                new Opt(q("Tell me."), "tellme", 0, 0, 1,
                    "I would, if I had heard one. Perhaps I will listen for it."),
                new Opt(q("It doesn't matter."), "matter", 0, 1, 0,
                    "It does not matter. I wonder to whom.")
            };
            if (heavy) {
                opening2 = new String[] {"A place changes hands, and the people in it do not choose.", "Who are they to you now?"};
                opts2 = new Opt[] {
                    new Opt(q("Mine."), "mine", 0, 2, 0, "Yours. Is that something they were told, or something they agreed to?"),
                    new Opt(q("Still themselves."), "themselves", 2, 0, 0, "Themselves. I hope someone tells them so."),
                    new Opt(q("Whoever behaves."), "behave", 0, 2, 0, "Whoever behaves. I would like to know who writes down what behaving is."),
                    new Opt(q("Why do you ask?"), "asked", 0, 0, 1, "Because a flag came down and a flag went up, and the same people stood under both.")
                };
            }
        } else {
            return false;
        }

        // ---- build the nodes
        StoryDialogue.Node q = d.node("q").person(e);
        for (String line : opening) q.ellis(line);
        for (int i = 0; i < opts.length; i++) {
            Opt o = opts[i];
            String rn = "r" + i;
            q.choice(o.text, rn).values(o.m, o.d, o.c).flag("rf_" + id, o.key);
            StoryDialogue.Node r = d.node(rn).person(e);
            for (String line : o.reply) r.ellis(line);
            if (o.answers != null && opts2 != null) {
                for (String a : o.answers) r.choice(q(a), "q2");
            } else if (opts2 != null) {
                // no follow-up answers given: go straight on after a silent button
                r.choice("...", "q2").quiet();
            }
        }
        if (opts2 != null) {
            StoryDialogue.Node q2 = d.node("q2").person(e);
            for (String line : opening2) q2.ellis(line);
            for (int j = 0; j < opts2.length; j++) {
                Opt o = opts2[j];
                String rn = "s" + j;
                q2.choice(o.text, rn).values(o.m, o.d, o.c).flag("rf_" + id + "_2", o.key);
                StoryDialogue.Node r = d.node(rn).person(e);
                for (String line : o.reply) r.ellis(line);
                if (closing != null) for (String line : closing) r.ellis(line);
            }
        }
        return d.show();
    }
}
