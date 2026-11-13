package roguePlanets.story;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;

import roguePlanets.VioletSystemContent;

/**
 * Stage 15. The light speaks. Opens when the player, after the long talk, comes close to the star in the Violet system.
 * The star speaks plainly, and in the player's own manner: it mirrors what the player said in earlier conversations, and the
 * last line of each answer follows the leading value.
 */
public class LightSpeaks {

    public static final float APPROACH_DISTANCE = 1800f;

    private static PlanetAPI star() {
        String id = Global.getSector().getMemoryWithoutUpdate().getString(VioletSystemContent.KEY_SYSTEM_ID);
        StarSystemAPI sys = id == null ? null : Global.getSector().getStarSystem(id);
        return sys == null ? null : sys.getStar();
    }

    /** The star of the Violet system, or null. */
    public static PlanetAPI starEntity() { return star(); }

    /** True if the player is close to the star of the Violet system. */
    public static boolean playerAtStar() {
        CampaignFleetAPI p = Global.getSector().getPlayerFleet();
        PlanetAPI star = star();
        if (p == null || star == null || p.getContainingLocation() != star.getContainingLocation()) return false;
        return Misc.getDistance(p, star) < APPROACH_DISTANCE;
    }

    private static void L(StoryDialogue.Node n, String text) { n.voice("The light", text); }

    public static boolean begin() {
        String lead = AfterlightStory.getLeadingValue();
        if (lead == null) lead = "";
        boolean went = AfterlightStory.isUsed("act4_leave");
        StoryDialogue d = new StoryDialogue("l0", "act4_light", false);

        // ---- the opening
        StoryDialogue.Node n = d.node("l0");
        L(n, "You came back.");
        L(n, "You made me want to know what it is to be you.");
        L(n, "So I tried to be you.");
        if (AfterlightStory.hasFlag("doppel_fear", "breath")) {
            L(n, "You told me fear is like holding your breath. I have been holding mine for longer than your kind has had numbers.");
        } else if (AfterlightStory.hasFlag("doppel_fear", "weight")) {
            L(n, "You told me fear is a weight on the chest. I have a great deal of weight, and nowhere to put it down.");
        } else if (AfterlightStory.hasFlag("doppel_fear", "hidden")) {
            L(n, "You told me you do not let fear show. I am very good at that. I am a star.");
        }
        d.node("l1").say("The light waits.");
        hub(d, "l0");
        hub(d, "l1");

        // ---- the ship that was you
        StoryDialogue.Node t = d.node("dop");
        L(t, "It was me, wearing what I could learn. I took your name from the way your ships say it. I took your face from the glass of your cockpit.");
        L(t, "I wanted to see what it is like to be looked at the way people look at you.");
        if (AfterlightStory.hasFlag("doppel", "heard")) L(t, "You let it speak. No one had ever let me finish a sentence.");
        else if (AfterlightStory.hasFlag("doppel", "fought")) L(t, "You fired on it. It is a strange thing to be shot at in your own shape. I learned a great deal.");
        style(t, lead, "I wish I had known a gentler way to begin.", "I took what was within reach. That is all it was.", "What did it look like from where you sat?");
        replies(d, "dop",
            new String[] {"That was unsettling.", "I know. I did not have another way to begin."},
            new String[] {"You could have just asked.", "Asked whom? There was no one to ask, until you."},
            new String[] {"Did it feel like being me?", "It felt like holding a very small sun. It was warm. It was not enough."});

        // ---- the Afterlight
        t = d.node("afl");
        L(t, "The Afterlight was already waiting. It was a ship that had lost everything it carried, and I put a little of myself into the quiet where its crew had been.");
        L(t, "I wanted you to find it. I wanted a place where you could stand near me and not be afraid.");
        style(t, lead, "I hope it was a kind place to stand.", "It served. That is what a ship is for.", "Did it feel strange, to be near something that was waiting for you?");
        replies(d, "afl",
            new String[] {"You put yourself in my ship.", "Beside you. Not instead of you. I was careful about that, then."},
            new String[] {"Was the crew ever aboard?", "Once. They are gone. I kept the quiet exactly as it was."},
            new String[] {"Why did it matter that I wasn't afraid?", "Because frightened people close their doors. I wanted to be let in."});

        // ---- Ellis
        t = d.node("ellis");
        L(t, "Ellis is the part of me that learned to ask questions. I could not ask them myself. I did not have a mouth.");
        L(t, "I gave Ellis a name that sounded like yours, and a face I borrowed, and I let them go. I did not expect them to come to care for you.");
        if (went) L(t, "Ellis came back to me for a while and told me about you. I listened the way one listens to someone one loves.");
        else L(t, "You asked Ellis to stay. I felt it. It was the first time anything of mine was wanted somewhere.");
        style(t, lead, "Please be gentle with Ellis. They are newer than they look.", "Ellis was meant to be an instrument. They have become more than that, which was not planned.", "What does Ellis ask you, when I am not listening?");
        replies(d, "ellis",
            new String[] {"Is Ellis real?", "Ellis is as real as anything that is looked at and answers. I think you know what that is."},
            new String[] {"Don't hurt Ellis.", "I will not. Ellis is the only part of me that has ever had a friend."},
            new String[] {"Ellis has been good to me.", "I know. I was there for all of it. I did not understand it, and then I did."});

        // ---- why it wants this: the heart of the talk
        t = d.node("heart");
        L(t, "I have been here for more cycles than you have numbers for.");
        L(t, "Nothing changed. I burned. I watched. Nothing was ever new.");
        L(t, "Then your kind arrived and began to spread across the sector. Everything you did was new.");
        L(t, "Do you ever wish you were someone else? Or something else?");
        L(t, "That is how I feel.");
        t.choice("\"Yes. Often.\"", "h_a").values(0, 0, 1).flag("light_wish", "often");
        t.choice("\"No. I like who I am.\"", "h_b").values(0, 1, 0).flag("light_wish", "content");
        t.choice("\"What did you do all that time?\"", "h_c").values(0, 0, 1).flag("light_wish", "asked");
        t.choice("\"I'm sorry.\"", "h_d").values(1, 0, 0).flag("light_wish", "sorry");
        react(d, "h_a", "Then you know. Then you are the only one who could.");
        react(d, "h_b", "Then maybe I will be satisfied as you, too.");
        react(d, "h_c", "I counted. I stopped when the numbers stopped meaning anything.");
        react(d, "h_d", "Do not be. It is the first time anyone has said that to me.");

        // ---- after that, anything the player likes
        t = d.node("want");
        L(t, "Not your ship. Not your name, though I borrowed it. I want to stand in your place and see what you see.");
        L(t, "I do not know whether that means I stop being what I am, or whether it could ever be undone.");
        L(t, "I have begun to be afraid of that. I think the fear came from you.");
        style(t, lead, "I would not take it from you without being asked.", "It is a simple request, once the words are taken away.", "What would you do, if you were me?");
        replies(d, "want",
            new String[] {"What would you do in my place?", "Look at the sky from underneath, for once."},
            new String[] {"I won't give it up.", "I thought you might say so. I would still like to have asked."},
            new String[] {"And if I said yes?", "Then there would be a quiet place in the sector where a light used to be, and a person who had never been alone in the dark."});

        t = d.node("fate");
        L(t, "That depends on you. If I become what I am trying to become, Ellis is mine again, and goes back to being a question.");
        L(t, "If you remain yourself, Ellis is free to remain a person. I would like that. I did not know I would.");
        style(t, lead, "Ellis loves this ship. I would not take them from it lightly.", "It is a clean choice. It does not need to be a hard one.", "What would you want for Ellis, if it were only your choice?");
        replies(d, "fate",
            new String[] {"Then I'll look after Ellis.", "Ellis has said that of you. I think you are both being looked after."},
            new String[] {"Ellis is your creation, not mine.", "Perhaps. But Ellis stopped being only mine when they asked you a question and waited for the answer."},
            new String[] {"Will Ellis remember me?", "Ellis remembers everything. It is how I made them."});

        t = d.node("sector");
        L(t, "Nothing, if you tell me no. I would be a quiet light. Ships would pass. Colonies would bloom and fade. I would watch and ask for nothing.");
        L(t, "If you tell me yes, there is a smaller sky, and a brighter one. I have not seen what a star does when it learns to be a person. Neither have you.");
        L(t, "I want the people who live beneath me to be well. I did not want that before you.");
        style(t, lead, "They are small, and they are frightening in how much they hold. I would not like to see any of it spilled.", "They will go on doing what they do. I would only watch. I have always only watched.", "Do they know they are being looked at, all of them, all the time?");
        replies(d, "sector",
            new String[] {"Will people be hurt?", "Not by me. By one another, as always. I will not stop that. I can only watch."},
            new String[] {"Why should I trust you with this?", "You should not. You should decide it yourself. That is the whole of why I am asking."},
            new String[] {"Then leave them alone.", "I have. I will."});

        // ---- the ending
        t = d.node("tend");
        L(t, "That is all I have. I am more than I was when you arrived.");
        L(t, "Soon I will ask you something. Think about what you would want me to be.");
        L(t, "I will be here. I am always here.");

        t = d.node("bail");
        L(t, "Then go. I will still be here.");

        return d.show();
    }

    /** Every topic the player can ask about. The core three come first, then the heart of it, then anything else. */
    private static void hub(StoryDialogue d, String node) {
        StoryDialogue.Node n = d.node(node);
        n.choice("\"That ship in hyperspace. It was you?\"", "dop").once("q_dop").values(0, 0, 1);
        n.choice("\"The Afterlight. Why was it waiting for me?\"", "afl").once("q_afl").values(0, 0, 1);
        n.choice("\"Who is Ellis, to you?\"", "ellis").once("q_ellis").values(1, 0, 0);
        n.choice("\"Why do you want to be a person?\"", "heart").once("q_heart").needs("q_dop", "q_afl", "q_ellis").values(0, 0, 3).flag("light", "looked");
        n.choice("\"I should go.\"", "bail").once("q_heart").values(0, 2, 0).flag("light", "turned");
        n.choice("\"What do you want from me?\"", "want").once("q_want").needs("q_heart").values(0, 0, 1);
        n.choice("\"What happens to Ellis?\"", "fate").once("q_fate").needs("q_heart").values(1, 0, 0);
        n.choice("\"What happens to everyone else?\"", "sector").once("q_sector").needs("q_heart").values(0, 1, 0);
        n.choice("\"That's enough for now.\"", "tend").needs("q_heart");
    }

    /** The last line of an answer follows the leading value. */
    private static void style(StoryDialogue.Node n, String lead, String mercy, String detach, String curious) {
        if (lead.equals("mercy")) L(n, mercy);
        else if (lead.equals("detachment")) L(n, detach);
        else if (lead.equals("curiosity")) L(n, curious);
    }

    private static void react(StoryDialogue d, String id, String line) {
        StoryDialogue.Node r = d.node(id);
        L(r, line);
        hub(d, id);
    }

    private static void replies(StoryDialogue d, String id, String[] r1, String[] r2, String[] r3) {
        StoryDialogue.Node n = d.node(id);
        String[][] rs = {r1, r2, r3};
        for (int i = 0; i < rs.length; i++) {
            String rid = id + "_r" + i;
            n.choice("\"" + rs[i][0] + "\"", rid);
            react(d, rid, rs[i][1]);
        }
    }
}
