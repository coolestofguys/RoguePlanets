package roguePlanets.story;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * The light's short messages after the Coexist ending. PostStory now plays them from the same pool as the Ellis-and-light conversations (this class only holds the lines).
 * After the Coexist ending the light sometimes says something short through the Afterlight. One line per message, never repeated,
 * rare (about every 300 days of game time), only at a quiet moment, and with no effect on the game. When the pool is used up
 * the light simply goes quiet.
 */
public class StarMessages {

    private static final String K_STAMP = "$afl_msg_stamp";
    private static final String K_WAIT = "$afl_msg_wait";

    static final String[] GENERAL = {
        "The ship hums differently when you are tired. I did not know it could.",
        "I watched a market close. Everyone hurried home. I did not understand it, and I liked it.",
        "I looked at another star through the glass. It did not look back. I think I am rare.",
        "Ellis has begun to hum while they work. I listened to all of it.",
        "I counted the lights of a town. Then I stopped, because they were not for counting.",
        "There was rain on a planet below. I was not made for rain. I watched it anyway.",
        "A child waved at the ship. I do not know why it made me glad.",
        "Is silence a kind of company? I am practising.",
        "Thank you for not asking me to be anything. I am learning what that is like.",
        "The glass is warm where I look through it. Is that how it feels to be welcome?",
        "I tried to remember what I was before I looked at anyone. I could not. That is all right.",
        "Someone aboard laughed, and the whole ship seemed to notice. I would like to learn to do that.",
        "A comet passed. I have seen thousands. This was the first one I wanted to tell someone about.",
        "I am less loud than I was. Ellis says it is called being content. I am not sure yet.",
        "When you sleep, the ship is very quiet. I keep the door closed. Ellis showed me how.",
        "I used to think a day was something that happened to me. Now I watch it happen to you.",
        "I asked Ellis the name of the color the engines make. I like having asked."
    };
    static final String MERCY = "I am being careful with the small things. They break so easily, and they are so well made.";
    static final String DETACH = "I watched a battle end. I did not look away, and I did not look closer. I think that is a kind of manners.";
    static final String CURIOUS = "I have a question. I will keep it until you want it. It is a good one.";

    private static boolean active() { return AfterlightStory.hasFlag("ending", "coexist"); }

    private static void schedule(float minDays, float extraDays) {
        MemoryAPI m = AfterlightStory.mem();
        m.set(K_STAMP, Global.getSector().getClock().getTimestamp());
        m.set(K_WAIT, minDays + (float) Math.random() * extraDays);
    }

    /** For the test key: the next message is due right now. */
    public static void forceNext() { PostStory.forceNext(); }

    /** Called from the story driver. */
    public static void tick() {
        if (!active()) return;
        MemoryAPI m = AfterlightStory.mem();
        if (!m.contains(K_WAIT)) { schedule(280f, 40f); return; }
        if (Global.getSector().getClock().getElapsedDaysSince(m.getLong(K_STAMP)) < m.getFloat(K_WAIT)) return;
        if (Global.getSector().getCampaignUI().isShowingDialog() || !AfterlightStory.isQuietMoment()) return;

        List<Integer> open = new ArrayList<Integer>();
        for (int i = 0; i < GENERAL.length; i++) if (!AfterlightStory.isUsed("act5_msg_" + i)) open.add(i);
        String lead = AfterlightStory.getLeadingValue();
        int special = -1;
        String specialText = null;
        if ("mercy".equals(lead)) { special = 100; specialText = MERCY; }
        else if ("detachment".equals(lead)) { special = 101; specialText = DETACH; }
        else if ("curiosity".equals(lead)) { special = 102; specialText = CURIOUS; }
        if (special >= 0 && !AfterlightStory.isUsed("act5_msg_" + special)) open.add(special);
        if (open.isEmpty()) { schedule(100000f, 0f); return; } // nothing left to say

        int pick = open.get((int) (Math.random() * open.size()));
        String text = pick >= 100 ? specialText : GENERAL[pick];

        StoryDialogue d = new StoryDialogue("m0", null, false);
        d.node("m0")
            .say("A message arrives from the Afterlight. The channel is open for only a moment.")
            .voice("The light", text)
            .choice("\"Close comm link.\"", null);
        if (d.show()) {
            AfterlightStory.markUsed("act5_msg_" + pick);
            schedule(280f, 40f);
        }
    }
}
