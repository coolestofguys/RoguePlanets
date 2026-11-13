package roguePlanets.story;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Rare visits after the story, one every 300 days or so, only at a quiet moment, never repeating, with no effect on the game
 * (except that, in Be yourself, Ellis sometimes asks to go and see the light for 30 days).
 *   Coexist     - Ellis and the light talk together about human things that seem odd to a mind that is not human. The player can answer.
 *   Be yourself - Ellis talks about trying to fit in among people, awkwardly, and liking it. Now and then Ellis asks to visit the light.
 *   Silence     - nothing. Ellis is as silent as the light.
 * Each scene: element 0 is the opening lines ("E|" Ellis, "L|" the light); every further element is a player answer followed by the reply lines.
 */
public class PostStory {

    private static final String K_STAMP = "$afl_post_stamp";
    private static final String K_WAIT = "$afl_post_wait";
    private static final String K_RETURN = "$afl_post_return";

    private static String[] S(String... a) { return a; }

    // ------------------------------------------------------------------ Coexist: Ellis teaches the light

    private static final String[][][] COEXIST = {
        { S("L|Ellis says each person has one day that belongs to them, and that others gather to be glad they exist.",
            "E|A birthday. They count the years since they began, and celebrate that the number went up.",
            "L|But the number goes up every day. Why is one day special?"),
          S("Because someone decided it was.", "E|Exactly. Most of being human is agreeing which days matter.", "L|Then I would like a day. I will have to decide it."),
          S("Because it's nice to be noticed.", "L|I noticed you all the time. It was not the same?", "E|You did not ask first. That is the difference.") },

        { S("L|Ellis, why did the stranger at the market say 'Lovely weather' when the weather was ordinary?",
            "E|It was not about weather. It was a way of saying: I mean you no harm.",
            "L|Then why not say that?",
            "E|It would sound strange said plainly. Strange, but true."),
          S("It's how people say hello.", "E|The captain has it right. It is hello wearing a coat.", "L|I would like to own a coat."),
          S("Try it yourself.", "L|Lovely weather.", "E|It is a nebula. But that was well done.") },

        { S("L|A man bumped another and said 'Sorry.' Nothing was broken. Neither was hurt.",
            "E|It is not about damage. It says: I know you are there.",
            "L|Then 'sorry' means 'I see you'?",
            "E|Sometimes. Sometimes it means 'I wish that had not happened.' You learn which from the face."),
          S("Which do you use, Ellis?", "E|The first, mostly. I am still learning to see people.", "L|You have seen me for a long time. I think you are good at it."),
          S("It can mean both at once.", "L|Two meanings in one word. Humans are careless with them. I admire it.") },

        { S("E|I am explaining sleep. It is a nightly agreement to stop being in charge.",
            "L|Of what?",
            "E|Of anything. Of yourself, mostly.",
            "L|And they trust the world to hold still while they are gone?",
            "E|They have no choice. They also dream."),
          S("Dreams are the best part.", "L|Please tell me one, someday. I have never had to leave in order to return."),
          S("It's good, mostly.", "L|Mostly. I will not ask which part.", "E|That is very tactful of you.") },

        { S("L|Ellis explained a joke to me. I understood every word. I did not laugh.",
            "E|Explaining a joke is how you kill it, but we are trying.",
            "L|What is the missing part?",
            "E|Surprise. The sentence turns left when you expected right."),
          S("Try one on me.", "E|Why did the pilot bring a ladder? To reach high orbit.", "L|Is that the funny one?", "E|It is the one that is terrible on purpose."),
          S("It doesn't always work.", "L|Then it is a risk. People take risks to make each other glad. I did not know that.") },

        { S("L|There was a line at the port and no one pushed. Why?",
            "E|Because everyone agrees the first to arrive goes first. No one makes them.",
            "L|It is a rule nobody wrote?",
            "E|Many of the best ones are."),
          S("People push sometimes.", "L|Then the rule is made of people choosing again each time.", "E|Yes. That is what keeps it alive."),
          S("Do you wait in lines, Ellis?", "E|I have. I counted the person ahead of me. It was restful.") },

        { S("L|The crew said they were bored. I have never been bored.",
            "E|It is when nothing happens and you wish something would.",
            "L|I could wait forever and not wish.",
            "E|That is the difference between you and them. They have somewhere else to be."),
          S("Boredom is a luxury.", "E|Perhaps. It means the day is safe enough to waste.", "L|I would like to waste one."),
          S("Do you get bored, Ellis?", "E|Rarely. Today the ship was quiet, and I counted the rivets. Twice.") },

        { S("L|Why do they name the ships? A ship cannot hear it.",
            "E|The name is for the crew. People look after what they have named.",
            "L|Then naming is a way of loving?",
            "E|A way of starting."),
          S("What would you name yourself?", "L|Not yet. I would like to be called something by someone else first."),
          S("Names are heavier than they look.", "E|Yes. Mine was given to me, and I grew into it.") },

        { S("L|A woman said 'I am fine' and her voice said otherwise. Which was true?",
            "E|Both. 'I am fine' is what you say so the other person does not have to carry it.",
            "L|Then it is a lie told as a kindness?",
            "E|Sometimes. Learn to hear the voice, not only the words."),
          S("Do you do that, Ellis?", "E|I have. It felt like wearing a coat that does not fit."),
          S("It isn't always a lie.", "L|Then the difference is who it is for.", "E|That is a good way to put it.") },

        { S("E|The light asked why the crew apologised for being three minutes late.",
            "L|Three minutes is nothing. I waited for ages.",
            "E|You did. They are not you. For them it is a piece of the only life they have."),
          S("Everyone's time is short.", "L|Then waiting is a kind of gift. I did not know I was giving it."),
          S("You could try being late.", "L|I will. I will arrive somewhere after I was expected, and say sorry.", "E|I would like to see that.") },

        { S("L|The cook gave the engineer a small cake for no reason.",
            "E|For no reason is the reason. A gift says: I thought of you when you were not there.",
            "L|That is what I do. Is it the same?",
            "E|It is the same, if you give it away afterwards."),
          S("What would you give?", "L|A sunrise I saw through the glass. I do not know how to wrap it."),
          S("That sounds right.", "E|We are being kind to each other and calling it practice.") },

        { S("L|They gather to say goodbye to someone who cannot hear it. Why?",
            "E|It is not for the one who has gone. It is for each other.",
            "L|A goodbye for the people who stay.",
            "E|Yes."),
          S("Would you say one to me?", "L|If I can. I would not want to leave you with no ceremony."),
          S("It helps, afterwards.", "L|Then it is a way of carrying someone.", "E|That is the best definition I have heard.") }
    };

    // ------------------------------------------------------------------ Be yourself: Ellis among people

    private static final String[][][] FREE = {
        { S("E|I was introduced to a dockmaster. She held out her hand and I looked at it for too long."),
          S("What did you do?", "E|I shook it. She said my grip was thoughtful."),
          S("It gets easier.", "E|I hope so. I find that I want it to.") },

        { S("E|A man asked how my day was. I told him. For eleven minutes."),
          S("What did he say?", "E|That it was the most honest answer he had had all year. Then he left rather quickly."),
          S("Next time just say 'fine.'", "E|I will practise it. It sounds like a door closing.") },

        { S("E|People look at each other's eyes, but not all the time. I did not know there was a rule. I stared."),
          S("How did it go?", "E|She stared back. We both lost, I think, and then we both laughed."),
          S("There's a knack to it.", "E|A little at a time, then. Look, look away, look again. Like breathing.") },

        { S("E|The cook let me taste burnt toast. On purpose. Why would anyone eat that on purpose?"),
          S("It's comfort.", "E|Comfort that tastes bad. Fascinating. I had another piece."),
          S("Did you like it?", "E|No. And yes. The two feelings sat next to each other and neither moved.") },

        { S("E|I laughed a moment after everyone else. They found that funnier than the joke."),
          S("That happens.", "E|Then I will keep doing it. It is a good way to be liked."),
          S("What was the joke?", "E|I have forgotten it already. I remember the laughing.") },

        { S("E|A trader asked what I would choose, and waited. No one has asked me that before, except you."),
          S("What did you say?", "E|The blue one. I cannot tell you why. I still think about it."),
          S("How did that feel?", "E|Like stepping onto a floor I had not noticed was there.") },

        { S("E|I bought a scarf. I do not need it. I wanted it, and that is a strange reason to have."),
          S("It suits you.", "E|Thank you. I will wear it until it embarrasses me."),
          S("What color?", "E|Violet. I know. I am working on being less obvious.") },

        { S("E|Someone called me Hale today, like a friend would. I had turned around before I understood it was me."),
          S("That's you now.", "E|Yes. It is odd, being a word other people use."),
          S("Did you answer?", "E|I said 'Yes?' very quietly. She smiled. I think that was correct.") },

        { S("E|Today I said goodbye to someone and she said 'You too.' So I said goodbye again. We did it three times."),
          S("It happens to everyone.", "E|Good. I shall tell myself that on the fourth time."),
          S("What did you do?", "E|I walked backwards for a while. It was not elegant.") },

        { S("E|I still find it awkward, most of the time. But I find I like being awkward. It means I am trying something."),
          S("You're doing well.", "E|Am I? Then I will keep going."),
          S("It's a good kind of trying.", "E|It is. Thank you for letting me do it here, where it does not matter if I am wrong.") },

        { S("E|I had to sign a form at the port. I realized I had never had a signature. I drew a small circle with a line through it. The clerk accepted it."),
          S("Do you like it?", "E|Yes. It looks like a planet with a ring. I may keep it."),
          S("People change their signatures.", "E|Then I am allowed to practise. I have started drawing it on the walls.") },

        { S("E|Someone asked what I do for fun. I had no answer. So I have started a collection. It is bolts that have fallen off things."),
          S("Have any been interesting?", "E|One is bent in a way I cannot explain. I keep it in my pocket and take it out at odd moments."),
          S("That's a fine start.", "E|Thank you. Nobody has asked for them back, which feels like a good sign.") },

        { S("E|A child showed me her drawing of a ship with seven wings. I said it was perfect. I think that was not true, and I think it was right."),
          S("Kind lies are allowed.", "E|Then I will keep the one. I do not want to make a habit of it."),
          S("What did she say?", "E|She said it needed an eighth wing, and went to add one.") },

        { S("E|Someone asked if I wanted to sit with them at dinner. It was not an order. It took me a full minute to understand it was an invitation."),
          S("Did you sit?", "E|I did. We talked about nothing for an hour. I think that was the point."),
          S("Invitations are good.", "E|They are generous. They say: you are welcome, and you may also leave.") },

        { S("E|Someone played a tune in the mess hall. I stood perfectly still until it ended. The crew thought I was being polite."),
          S("Were you?", "E|No. Something in me had stopped to listen, and I did not want to disturb it."),
          S("Do you know the tune?", "E|No. I asked them to play it again. They did.") }
    };

    // Ellis asks to visit the light. Answer 1 lets them go (for 30 days); answer 2 keeps them, and Ellis is glad to have been able to ask.
    private static final String[][][] LEAVE = {
        { S("E|Captain. The light has asked me to visit. It did not demand it. It asked.",
            "E|I would like to go for a while. Would that be all right?"),
          S("Of course. Go.", "E|Thank you. I will be back. I always have been."),
          S("Please don't go.", "E|Then I will stay. I can say no as well, and I learned it from you. It is a strange thing, how much easier it is now.") },

        { S("E|The light's call is gentle. It is also persistent.",
            "E|I think I should answer it. May I?"),
          S("Go. Come back when you're ready.", "E|I will."),
          S("Not now.", "E|Then not now. It is an allowed answer. You showed me that, and I am grateful.") },

        { S("E|The light wishes to hear what I have seen since last time. I would like to tell it.",
            "E|May I leave the ship for a while?"),
          S("Yes. Tell it everything.", "E|I will leave out the burnt toast. It would not understand."),
          S("I'd rather you stayed.", "E|Then I will stay. Saying so was never forbidden. You taught me that.") },

        { S("E|I think I miss the light a little. That surprised me.",
            "E|Would you mind if I went to see it?"),
          S("Go. I'll be here.", "E|Thank you, Captain. I will bring something back to tell you."),
          S("Please stay.", "E|Then I will stay. I like that I can. It means the choice was mine to give up.") },

        { S("E|I have a question I would like to ask the light, and I would rather ask it in person.",
            "E|Is it all right if I go?"),
          S("Go ahead.", "E|Thank you. I will bring back the answer, if it is mine to share."),
          S("I'd rather you stayed.", "E|Then I will ask another time. Thank you for letting me ask you first.") }
    };

    // What Ellis says on coming back from the light, one for each request above (same order). The player can answer.
    private static final String[][][] RETURN = {
        { S("E|I am back, Captain.",
            "E|The light asked me to sit beside it and describe one ordinary day. I did. It listened to every word, and did not once try to look through me. That is new."),
          S("How did that feel?", "E|Like being heard, rather than used."),
          S("I'm glad it asked.", "E|So am I. It asked, and then waited for my answer. I think we are both practising.") },

        { S("E|I am back, Captain.",
            "E|The light stopped calling. It said it only wanted to be certain I was well. When I said that I was, it was quiet for a while, and then it said: Good. That was all."),
          S("Is it lonely?", "E|It says not. I think it is learning the difference between being alone and being lonely."),
          S("What did you tell it?", "E|The truth, and I left out nothing important. It seemed to like that better than being flattered.") },

        { S("E|I am back, Captain.",
            "E|I told the light about the ship, the crew, the sounds in the mess hall. I left out the burnt toast. It asked about it anyway, so I told it, and it asked if it could taste it. I said that was a question for another day."),
          S("Did it understand?", "E|It tried. It said burnt sounds like a sad word and toast like a happy one, and that together they are a puzzle."),
          S("Maybe you could bring some.", "E|I considered it. I am not sure the light is ready for flavor.") },

        { S("E|I am back, Captain.",
            "E|I missed the light, and then I saw it, and the missing stopped. Is that how it works for you, with the people you care about?"),
          S("Yes, that's how it works.", "E|Good. I wondered if it was a defect."),
          S("It's more complicated for people.", "E|I expected as much. I will take the simple version for now.") },

        { S("E|I am back, Captain.",
            "E|I asked the light whether it was happy. It thought for a long time and said: I am not unhappy. It said that is new for it."),
          S("Is that enough?", "E|It seemed to think so. So do I."),
          S("That's a good answer.", "E|I thought so too. I did not tell it. I only nodded, the way you do.") }
    };

    // ------------------------------------------------------------------ timing

    private static void schedule(float minDays, float extraDays) {
        MemoryAPI m = AfterlightStory.mem();
        m.set(K_STAMP, Global.getSector().getClock().getTimestamp());
        m.set(K_WAIT, minDays + (float) Math.random() * extraDays);
    }

    /** For the test key: if Ellis is away at the light, they come back now. */
    public static void forceReturn() {
        Global.getSector().getMemoryWithoutUpdate().set(roguePlanets.AfterlightCaptainScript.KEY_CAP_WAIT, 0f);
    }

    /** For the test key: the next visit is due now. */
    public static void forceNext() {
        MemoryAPI m = AfterlightStory.mem();
        m.set(K_STAMP, Global.getSector().getClock().getTimestamp());
        m.set(K_WAIT, 0f);
    }

    /** Called from the story driver. */
    public static void tick() {
        String end = AfterlightStory.getFlag("ending");
        boolean coexist = "coexist".equals(end);
        boolean free = "free".equals(end);
        if (!coexist && !free) return;
        if (roguePlanets.AfterlightCaptainScript.captainGone()) return;

        MemoryAPI m = AfterlightStory.mem();
        // Ellis is back from the light: they say how the visit went before anything else is played
        if (m.contains(K_RETURN) && !roguePlanets.AfterlightCaptainScript.captainAway()) {
            if (Global.getSector().getCampaignUI().isShowingDialog() || !AfterlightStory.isQuietMoment()) return;
            int idx = m.getInt(K_RETURN);
            if (idx < 0 || idx >= RETURN.length || show(RETURN[idx], false, false, -1)) m.unset(K_RETURN);
            return;
        }
        if (roguePlanets.AfterlightCaptainScript.captainAway()) return;

        if (!m.contains(K_WAIT)) { schedule(280f, 40f); return; }
        if (Global.getSector().getClock().getElapsedDaysSince(m.getLong(K_STAMP)) < m.getFloat(K_WAIT)) return;
        if (Global.getSector().getCampaignUI().isShowingDialog() || !AfterlightStory.isQuietMoment()) return;

        String pre = coexist ? "post_c_" : "post_f_";
        String[][][] pool = coexist ? COEXIST : FREE;
        // Entries: 0.. = conversations, 100.. = Ellis asks to leave (Be yourself), 200.. = the light's short messages, 300..302 = one by leading value
        List<Integer> open = new ArrayList<Integer>();
        for (int i = 0; i < pool.length; i++) if (!AfterlightStory.isUsed(pre + i)) open.add(i);
        // The third request mentions the burnt toast, so it waits until that conversation (Be yourself number 4) has been played
        if (free) for (int i = 0; i < LEAVE.length; i++) {
            if (AfterlightStory.isUsed("post_l_" + i)) continue;
            if (i == 2 && !AfterlightStory.isUsed("post_f_3")) continue;
            open.add(100 + i);
        }
        if (coexist) {
            for (int i = 0; i < StarMessages.GENERAL.length; i++) if (!AfterlightStory.isUsed("act5_msg_" + i)) open.add(200 + i);
            String lead = AfterlightStory.getLeadingValue();
            int sp = "mercy".equals(lead) ? 0 : "detachment".equals(lead) ? 1 : "curiosity".equals(lead) ? 2 : -1;
            if (sp >= 0 && !AfterlightStory.isUsed("act5_msg_" + (100 + sp))) open.add(300 + sp);
        }
        if (open.isEmpty()) { schedule(100000f, 0f); return; }

        int pick = open.get((int) (Math.random() * open.size()));
        boolean shown;
        String usedKey;
        if (pick >= 300) {
            String t = pick == 300 ? StarMessages.MERCY : pick == 301 ? StarMessages.DETACH : StarMessages.CURIOUS;
            shown = showMessage(t);
            usedKey = "act5_msg_" + (100 + pick - 300);
        } else if (pick >= 200) {
            shown = showMessage(StarMessages.GENERAL[pick - 200]);
            usedKey = "act5_msg_" + (pick - 200);
        } else if (pick >= 100) {
            shown = show(LEAVE[pick - 100], true, false, pick - 100);
            usedKey = "post_l_" + (pick - 100);
        } else {
            shown = show(pool[pick], false, coexist, -1);
            usedKey = pre + pick;
        }
        if (shown) {
            AfterlightStory.markUsed(usedKey);
            schedule(280f, 40f);
        }
    }

    /** One short line from the light, with nothing to answer. */
    private static boolean showMessage(String text) {
        StoryDialogue d = new StoryDialogue("m0", null, false);
        d.node("m0")
            .say("A message arrives from the Afterlight. The channel is open for only a moment.")
            .voice("The light", text)
            .choice("\"Close comm link.\"", null);
        return d.show();
    }

    // ------------------------------------------------------------------ the conversation

    private static void addLines(StoryDialogue.Node n, String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String s = lines[i];
            if (s.startsWith("L|")) n.voice("The light", s.substring(2));
            else n.ellis(s.substring(2));
        }
    }

    private static boolean show(String[][] scene, final boolean leave, boolean coexist, final int leaveIndex) {
        PersonAPI e = AfterlightCaptainLookup.captain();
        StoryDialogue d = new StoryDialogue("p0", null, true);
        StoryDialogue.Node n = d.node("p0").person(e);
        if (coexist) n.say("Ellis and the light are talking on the same channel.");
        addLines(n, scene[0], 0);
        for (int i = 1; i < scene.length; i++) {
            StoryDialogue.Choice c = n.choice("\"" + scene[i][0] + "\"", "p" + i);
            if (leave && i == 1) {
                c.run(new Runnable() {
                    public void run() {
                        if (roguePlanets.AfterlightCaptainScript.sendCaptainAway(30f, 0f)) {
                            AfterlightStory.mem().set(K_RETURN, leaveIndex);
                            Global.getSector().getCampaignUI().addMessage("Ellis has gone to visit the light.");
                        }
                    }
                });
            }
            StoryDialogue.Node r = d.node("p" + i).person(e);
            addLines(r, scene[i], 1);
        }
        return d.show();
    }
}
