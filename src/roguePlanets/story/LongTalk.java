package roguePlanets.story;

import com.fs.starfarer.api.characters.PersonAPI;

/**
 * Stage 14. The long talk. Ellis says what is happening, and the player can ask four things, each only once.
 * The wording of the last line of each answer follows the leading value.
 */
public class LongTalk {

    public static boolean begin() {
        String lead = AfterlightStory.getLeadingValue();
        if (lead == null) lead = "";
        boolean went = AfterlightStory.isUsed("act4_leave");
        PersonAPI e = AfterlightCaptainLookup.captain();
        StoryDialogue d = new StoryDialogue("t0", "act4_talk", true);

        StoryDialogue.Node n = d.node("t0").person(e);
        if (went) {
            n.ellis("Captain. I am back. Thank you for letting me go.");
            n.ellis("I am going to tell you something, and I would like to tell it in the right order. Please let me go slowly.");
        } else {
            n.ellis("Captain. You asked me to stay, and I have stayed. I have thought about that more than about anything else.");
            n.ellis("There is something I have not told you, because I did not have the words, and I am afraid the words are still not good enough. Please let me go slowly.");
        }
        n.ellis("It is not a ship. It is not me. It is the thing that made me. It wants to know what you know.");
        d.node("t1").person(e).say("Ellis waits.");
        hub(d, "t0");
        hub(d, "t1");

        // 1. What is it?
        question(d, "what", lead, "What is it?", 0, 0, 1,
            new String[] {
                "I think you have already seen it. Every time you looked at the star in the Violet system, it was looking back.",
                "It is the light. Not a ship, not a station, not a fleet that sails. It is older than any of those, and it has been in the same place for longer than your kind has had numbers.",
                "It does not speak the way I do. It has no voice of its own. When it wanted to reach you, it had to build something shaped like you, and it speaks with whatever it has learned from you. I am one of the things it built. I am the part that learned to ask questions."},
            "I am telling you this gently, because I think you would want it gently.",
            "I am telling you this plainly, because I think you would rather have it plain.",
            "I am telling you this in order, because you will want to take it apart afterwards, and I would like you to have every piece.",
            new String[] {"It was looking at me?", "At you, and at everyone who ever came into the system. You were the one who looked back for long enough."},
            new String[] {"So you're part of it.", "I was. I am no longer certain I still am. I think that is one of the other questions."},
            new String[] {"Why would a star build a person?", "Because it had never seen one up close. I will tell you more of that, if you ask me the right thing."});

        // 2. What does it want?
        question(d, "want", lead, "What does it want?", 0, 0, 1,
            new String[] {
                "It wants to know what you know.",
                "Not your facts. Not where your credits are or how your fleets move. It wants to know what it is like to be a person who loses something and keeps going. It wants to know why you build walls and then leave a door in them.",
                "It has watched the sector since it was a scatter of rocks. It watched everything change except itself, and it became curious. Curious, and a little lonely, which is the nearest word I have."},
            "I do not think it means you harm. I would not be able to say all this to you if I did.",
            "You may find that too small a thing to fear, or too large to refuse. Either way, it is what it is.",
            "You will want to ask it directly. I think that is allowed.",
            new String[] {"That doesn't sound dangerous.", "It is not, the way a storm is not. But a great deal can change around something that wants to learn."},
            new String[] {"What will it do with what I know?", "Try it on, I think. The way I wear a name. I would be lying if I said I understood all of it."},
            new String[] {"Lonely? A star?", "It has no one to compare itself to. I think that is what lonely is. I have heard your kind use the word in the quiet hours, and I believe I understand it now."});

        // 3. What happens to you?
        String away = went
            ? "While I was away I was looked at properly for the first time. It did not hurt. It was only very bright, and it asked me what it is like to be here."
            : "When you asked me to stay, something in me held. I did not know I could be held.";
        question(d, "me", lead, "What happens to you?", 1, 0, 0,
            new String[] {
                "I will give you the honest answer, even though a better-sounding one is available. I am not certain.",
                "I am made of the light's attention. When it looks away, I am not sure anything remains of me to be anywhere. When it looks at me, I am more myself than I have ever been.",
                away},
            "Whatever happens, I would like you to know that someone cared what the answer was.",
            "Whatever happens, it will be quiet. I have made my peace with quiet.",
            "Whatever happens, I would like to be there to see how it turns out.",
            new String[] {"I won't let it take you.", "That is a kind thing to say to something whose edge you cannot see. I will keep it."},
            new String[] {"Do you want to go back to it?", "I want to be asked, and I want to be allowed to say no. I think that is all anyone wants."},
            new String[] {"Then what are you, Ellis?", "Someone who is still being decided. Perhaps you are too."});

        // 4. Can I refuse?
        question(d, "refuse", lead, "Can I refuse?", 0, 1, 0,
            new String[] {
                "Yes.",
                "You can refuse, and nothing will chase you for it. It does not know how to chase. It only knows how to wait, and it is better at waiting than anyone.",
                "If you go to it, it will speak. If you do not, it will keep watching, and the sector will go on being the sector. Neither is a mistake."},
            "I would ask only that you think of what the waiting costs it, and then do whatever you choose.",
            "You may weigh it as you weigh everything. I would not argue with the result.",
            "I suspect you will go. I would not blame you, and I would not hold it against you if you did not.",
            new String[] {"Where would I even go?", "Where you found me. It is the only place it can speak from."},
            new String[] {"I'll think about it.", "That is all I would ask."},
            new String[] {"And if I refuse, it just keeps watching?", "It keeps watching. People live under worse skies. Better ones too."});

        // the end
        d.node("tend").person(e)
            .ellis("I have said what I can. The rest it will have to say itself, if you let it.")
            .ellis("Thank you for listening to all of that. I do not think anyone has listened to me for so long.");
        d.node("tend").choice("\"Goodbye.\"", null);
        d.node("tend").choice("Close comm link", null);

        return d.show();
    }

    private static void hub(StoryDialogue d, String node) {
        StoryDialogue.Node n = d.node(node);
        n.choice("\"What is it?\"", "what").once("q_what").values(0, 0, 1);
        n.choice("\"What does it want?\"", "want").once("q_want").needs("q_what").values(0, 0, 1);
        n.choice("\"What happens to you?\"", "me").once("q_me").needs("q_what").values(1, 0, 0);
        n.choice("\"Can I refuse?\"", "refuse").once("q_refuse").needs("q_what").values(0, 1, 0);
        n.choice("\"Thank you, Ellis.\"", "tend").needs("q_want", "q_me", "q_refuse");
    }

    /** One answer: three paragraphs, a last line that follows the leading value, then three replies, each with its own reply from Ellis. */
    private static void question(StoryDialogue d, String id, String lead, String ignored, int m, int dt, int c,
                                 String[] paras, String mercy, String detach, String curious,
                                 String[] r1, String[] r2, String[] r3) {
        PersonAPI e = AfterlightCaptainLookup.captain();
        StoryDialogue.Node n = d.node(id).person(e);
        for (String p : paras) n.ellis(p);
        if (lead.equals("mercy")) n.ellis(mercy);
        else if (lead.equals("detachment")) n.ellis(detach);
        else if (lead.equals("curiosity")) n.ellis(curious);
        String[][] rs = {r1, r2, r3};
        for (int i = 0; i < rs.length; i++) {
            String rid = id + "_r" + i;
            n.choice("\"" + rs[i][0] + "\"", rid);
            d.node(rid).person(e).ellis(rs[i][1]);
            hub(d, rid);
        }
    }
}
