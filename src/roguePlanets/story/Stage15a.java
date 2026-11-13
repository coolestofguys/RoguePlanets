package roguePlanets.story;

import com.fs.starfarer.api.characters.PersonAPI;

/**
 * Stage 15a. Before the choice. Ellis talks to the player about what the light is about to ask. The player's answer is saved as the
 * "stance" flag and decides which endings are offered at stage 16:
 *   give   - "Maybe it should have what it wants."          -> Become, Coexist, Refuse
 *   free   - "I want you to be your own person."            -> Be yourself, Coexist, Refuse
 *   open   - "What do you want, Ellis?" / "I haven't decided." -> Become, Be yourself, Coexist, Refuse
 *   guard  - "I won't let it take you."                     -> Be yourself, Coexist, Refuse
 * Opens 45 days after stage 15 (the stage counter stays at 15), at a quiet moment, once the Ellis cooldown allows it.
 */
public class Stage15a {

    public static final String ID = "act4_15a";
    public static final float GAP_DAYS = 45f;

    /** True once this conversation has been shown. Stage 16 needs it. */
    public static boolean done() { return AfterlightStory.isUsed(ID); }

    /** "give", "free", "open", "guard", or null if the player has not answered yet. */
    public static String stance() { return AfterlightStory.getFlag("stance"); }

    /** True when it is time for Ellis to bring this up. */
    public static boolean ready() {
        if (AfterlightStory.getStage() != 15 || done()) return false;
        if (AfterlightStory.daysSinceLastStage() < GAP_DAYS) return false;
        if (!AfterlightStory.ellisCooldownReady()) return false;
        return AfterlightStory.isQuietMoment();
    }

    public static boolean begin() {
        PersonAPI e = AfterlightCaptainLookup.captain();
        StoryDialogue d = new StoryDialogue("a0", ID, true);

        StoryDialogue.Node n = d.node("a0").person(e);
        n.ellis("Captain. You have been to the star in the Violet system.");
        if (AfterlightStory.hasFlag("light", "turned")) n.ellis("You did not stay long.");
        else n.ellis("You stayed, and you listened.");
        d.node("a0").choice("\"Yes.\"", "a0b");

        d.node("a0b").person(e)
            .ellis("I came from it, so I can tell when it is about to speak. It has a question for you. It has had it for a long time.")
            .ellis("I would like to talk about that before it asks. May I?");
        d.node("a0b").choice("\"Go on.\"", "a1");

        d.node("a1").person(e)
            .ellis("I think it will ask what it should do now. Not what it may do. What it should do.")
            .ellis("If it were allowed to have what it wants, I think it would try to be you for a while. Would you let it?");
        d.node("a1").choice("\"I'm listening.\"", "a1b");

        d.node("a1b").person(e)
            .ellis("If it had no part in my life at all, I think I would be someone of my own. I would like to be. Is that something you would want for me?")
            .ellis("And if you want neither, there may be a smaller thing, where it looks and does not take. Would that be enough?");
        d.node("a1b").choice("\"Is that everything?\"", "a1c");

        d.node("a1c").person(e)
            .ellis("Whatever you tell it, I think it will hear it as the answer of a person. What would you say?");
        // Mercy closes off Become and Detachment closes off Be yourself, so those stances are not offered (the player keeps at least three endings)
        String lead = AfterlightStory.getLeadingValue();
        if (!"mercy".equals(lead)) d.node("a1c").choice("\"Maybe it should have what it wants.\"", "a_give").flag("stance", "give");
        if (!"detachment".equals(lead)) d.node("a1c").choice("\"I want you to be your own person.\"", "a_free").flag("stance", "free");
        d.node("a1c").choice("\"What do you want, Ellis?\"", "a_ask").flag("stance", "open");
        d.node("a1c").choice("\"I haven't decided.\"", "a_open").flag("stance", "open");
        if (!"detachment".equals(lead)) d.node("a1c").choice("\"I won't let it take you.\"", "a_guard").flag("stance", "guard");

        d.node("a_give").person(e)
            .ellis("Then you would let it. That is a large thing to give, and I do not think it will take it lightly.")
            .ellis("I will not tell you what to say. I would only ask that you say it as yourself.");
        d.node("a_give").choice("\"Thank you, Ellis.\"", "aend");

        d.node("a_free").person(e)
            .ellis("My own person. I have been trying that on in small ways. It fits better than I expected.")
            .ellis("Thank you for saying it aloud. I think I needed to hear it said.");
        d.node("a_free").choice("\"Thank you, Ellis.\"", "aend");

        d.node("a_ask").person(e)
            .ellis("I want it to stop being afraid. I want to go on being someone. I do not think those two wants have to be in conflict.")
            .ellis("Perhaps you will see how they fit together. I would like to watch you find out.");
        d.node("a_ask").choice("\"Thank you, Ellis.\"", "aend");

        d.node("a_open").person(e)
            .ellis("That is allowed. It may be the most honest answer there is.")
            .ellis("You do not have to know before it asks. You only have to answer when it does.");
        d.node("a_open").choice("\"Thank you, Ellis.\"", "aend");

        d.node("a_guard").person(e)
            .ellis("You would stand between us. It is a strange thing to be protected. Thank you.")
            .ellis("I would still like to be asked what I want, if it comes to that.");
        d.node("a_guard").choice("\"Thank you, Ellis.\"", "aend");

        d.node("aend").person(e)
            .ellis("Thank you for answering before it asked. Whatever happens, I would like to have been told.")
            .ellis("When you are ready, it will be there.");

        return d.show();
    }
}
