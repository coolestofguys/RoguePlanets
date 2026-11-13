package roguePlanets.story;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;

import roguePlanets.AfterlightCaptainScript;

/**
 * Stage 13. Ellis asks to be away for about a month. Hidden: he is going home. If the player lets him go, Ellis
 * leaves the ship for 30 to 40 days (the Afterlight stays with the player, with no captain) and comes back by himself. If the player says no, he stays. The stage is done either way.
 */
public class LeaveEvent {

    private static final String K_ACTIVE = "$afl_leaveActive";
    public static final float AWAY_MIN_DAYS = 30f;
    public static final float AWAY_EXTRA_DAYS = 10f;

    private static boolean leaving = false; // set by the "You can go" option, read when the conversation ends

    private static MemoryAPI mem() { return AfterlightStory.mem(); }

    public static boolean isActive() { return mem().getBoolean(K_ACTIVE); }

    /** Opens the conversation. Returns false: the stage counts as done only when Ellis actually leaves. */
    public static boolean begin() {
        if (isActive()) return false;
        leaving = false;
        StoryDialogue d = new StoryDialogue("l0", null, true);
        PersonAPI e = AfterlightCaptainLookup.captain();

        d.node("l0").person(e)
            .ellis("Captain. May I ask you something? It is not about the ship.")
            .ellis("I would like to be away for a while.")
            .ellis("I am asking because I would rather ask than simply be gone.")
            .ellis("Is that something I am allowed to ask?");
        d.node("l1").person(e).say("Ellis waits.");
        hub(d, "l0");
        hub(d, "l1");

        question(d, "where", "Home.",
            "I did not have a word for it until recently. Now I have the word, and it points somewhere.",
            "It is far. Not in distance, I think. In the other way things can be far.",
            new String[] {"I hope you find what you're looking for.", "Thank you. Being hoped for is a new thing to receive. I am keeping it."},
            new String[] {"That's not much of an answer.", "No. It is the only honest one I have. I will try to make it larger when I am back."},
            new String[] {"Is it a place, or a feeling?", "Yes. I think it is both, and the difference was never as large as I believed."});

        question(d, "long", "A month, by your clocks.",
            "By mine it may be a single breath, or longer than I can say. Where I am going, I am told the hours do not keep their order.",
            null,
            new String[] {"A month is a long time to be without you.", "It is. I will carry that with me. It will make the way shorter."},
            new String[] {"I can manage a month.", "I know you can. That is part of why I could ask."},
            new String[] {"Who told you the hours don't keep their order?", "Something that has never had to count them. It was kind about it."});

        question(d, "promise", "I promise.",
            "I have not used that word before. It is heavier than I expected, and warmer.",
            "I will come back to this ship and to you. Hold that for me, if you would. I will hold it too.",
            new String[] {"I'll hold it.", "Then it is held in two places. That is how a thing becomes safe."},
            new String[] {"People break promises all the time.", "They do. I think that is why they matter. I intend to be a poor example of the rule."},
            new String[] {"What if you can't keep it?", "Then I will have meant it anyway. I am told that counts for something, among your kind."});

        question(d, "wrong", "No. Nothing is wrong. Something is being answered.",
            "It is like hearing my name from a long way off, and not yet being certain which direction it comes from. I would like to turn toward it.",
            null,
            new String[] {"Does it frighten you?", "A little. Fear is still new to me, and I find I do not mind it as much as I expected to."},
            new String[] {"Who is calling you?", "Someone who has been very patient. I will tell you more when I can say it properly."},
            new String[] {"Then go and answer it.", "You say that so simply. I will remember how simply you said it."});

        // he goes
        d.node("go").person(e)
            .ellis("Thank you.")
            .ellis("You did not ask me to explain more than I could. That was a gift, and I noticed it.")
            .ellis("Will you keep the ship's lights on while I am gone? Not for the ship. For the sound of it being lived in.");
        d.node("go").choice("\"I will.\"", null);
        d.node("go").choice("\"Take care, Ellis.\"", null);
        d.node("go").choice("Close comm link", null);

        // he stays
        d.node("stay").person(e)
            .ellis("Then I will stay.")
            .ellis("I want you to know that being told no did not feel like a wall. It felt like being wanted somewhere.")
            .ellis("What I heard will keep. It has waited this long, and it is patient.");
        d.node("stay").choice("\"Thank you for understanding.\"", null);
        d.node("stay").choice("\"I need you here more than I can explain.\"", null);
        d.node("stay").choice("\"Tell me if that ever changes.\"", null);

        d.onEnd(new Runnable() {
            public void run() { finishConversation(); }
        });
        mem().set(K_ACTIVE, true);
        if (!d.show()) {
            mem().unset(K_ACTIVE);
        }
        return false;
    }

    private static void finishConversation() {
        mem().unset(K_ACTIVE);
        boolean went = false;
        if (leaving) went = AfterlightCaptainScript.sendCaptainAway(AWAY_MIN_DAYS, AWAY_EXTRA_DAYS);
        leaving = false;
        // Stage 13 is done either way: he goes, or the player asks him to stay.
        AfterlightStory.setStage(13);
        AfterlightStory.markUsed(went ? "act4_leave" : "act4_stay");
    }

    /** The questions and the two ways out, offered at the start and again after every answer. */
    private static void hub(StoryDialogue d, String node) {
        StoryDialogue.Node n = d.node(node);
        n.choice("\"Where are you going?\"", "where").once("q_where").values(0, 0, 1);
        n.choice("\"How long will you be gone?\"", "long").once("q_long");
        n.choice("\"Will you promise to come back?\"", "promise").once("q_promise").values(1, 0, 0);
        n.choice("\"Is something wrong?\"", "wrong").once("q_wrong").values(1, 0, 0);
        n.choice("\"You can go.\"", "go").values(1, 0, 0).run(new Runnable() { public void run() { leaving = true; } });
        n.choice("\"Don't go.\"", "stay");
    }

    /** One answer from Ellis, three things the player can say back, each with its own short reply, then the hub again. */
    private static void question(StoryDialogue d, String id, String l1, String l2, String l3, String[] r1, String[] r2, String[] r3) {
        PersonAPI e = AfterlightCaptainLookup.captain();
        StoryDialogue.Node n = d.node(id).person(e).ellis(l1).ellis(l2);
        if (l3 != null) n.ellis(l3);
        String[][] rs = {r1, r2, r3};
        for (int i = 0; i < rs.length; i++) {
            String rid = id + "_r" + i;
            n.choice("\"" + rs[i][0] + "\"", rid);
            d.node(rid).person(e).ellis(rs[i][1]);
            hub(d, rid);
        }
    }
}
