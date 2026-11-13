package roguePlanets.story;

import com.fs.starfarer.api.Global;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;

import roguePlanets.hullmods.AfterlightGlow;

/**
 * Stage 16. The choice. The light asks the player what it should do. Which endings are offered depends on the stance the
 * player took with Ellis at stage 15a and on the leading value:
 *   Become      - stance give or open, and Mercy does not lead
 *   Be yourself - stance free, guard or open, and Detachment does not lead
 *   Coexist     - always
 *   Refuse      - always
 * Endings that are not built yet are never offered (see the BUILT_ switches). "I need more time" leaves the conversation
 * without choosing, and the light asks again after a few days.
 */
public class Stage16 {

    // Switch each ending on when its scenes exist.
    private static final boolean BUILT_BECOME = true;
    private static final boolean BUILT_FREE = true;
    private static final boolean BUILT_COEXIST = true;
    private static final boolean BUILT_REFUSE = true;

    /** For the test key: whether the last attempt to open the conversation worked. */
    public static boolean lastShown = false;

    private static final String K_WAIT = "$afl_s16_wait";
    private static final float ASK_AGAIN_DAYS = 3f;

    public static boolean waitOver() {
        if (!AfterlightStory.mem().contains(K_WAIT)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(AfterlightStory.mem().getLong(K_WAIT)) >= ASK_AGAIN_DAYS;
    }

    // ---- Silence: the player never comes back to the star

    private static final String K_SINCE = "$afl_s16_since";
    /** Days at the star's side of the story with no visit before the light fades. Counted from the end of 15a, and restarted by every visit. */
    public static final float SILENCE_DAYS = 360f;

    /** Called from the story driver. When the player has left the light unanswered for long enough, the story ends quietly. */
    public static void silenceTick() {
        if (!Stage15a.done() || done()) return;
        long now = Global.getSector().getClock().getTimestamp();
        if (!AfterlightStory.mem().contains(K_SINCE)) { AfterlightStory.mem().set(K_SINCE, now); return; }
        if (Global.getSector().getClock().getElapsedDaysSince(AfterlightStory.mem().getLong(K_SINCE)) < SILENCE_DAYS) return;
        endInSilence();
    }

    /** The light fades. Nothing else changes: Ellis and the Afterlight stay as they are. */
    public static void endInSilence() {
        AfterlightStory.setFlag("ending", "silence");
        AfterlightStory.markUsed("act5_silence");
        AfterlightStory.setStage(16);
        Global.getSector().getCampaignUI().addMessage("The light flickers and fades.");
    }

    /** True once the player has chosen an ending. */
    public static boolean done() { return AfterlightStory.getFlag("ending") != null; }

    private static void L(StoryDialogue.Node n, String text) { n.voice("The light", text); }

    /** Never ends the stage by itself: the ending that is chosen sets the stage counter. Returns false so the engine does not. */
    public static boolean begin() {
        String lead = AfterlightStory.getLeadingValue();
        if (lead == null) lead = "";
        String stance = Stage15a.stance();
        if (stance == null) stance = "open";
        PersonAPI e = AfterlightCaptainLookup.captain();
        AfterlightStory.mem().set(K_SINCE, Global.getSector().getClock().getTimestamp()); // a visit restarts the silence timer
        StoryDialogue d = new StoryDialogue("s0", null, false);

        StoryDialogue.Node n = d.node("s0");
        L(n, "You are here again. I have waited.");
        L(n, "I asked you to think about what you would want me to be.");
        L(n, "I will ask it plainly now. What should I do?");

        boolean stanceBecome = BUILT_BECOME && (stance.equals("give") || stance.equals("open"));
        boolean stanceFree = BUILT_FREE && (stance.equals("free") || stance.equals("guard") || stance.equals("open"));
        boolean become = stanceBecome && !lead.equals("mercy");
        boolean free = stanceFree && !lead.equals("detachment");
        // The player always gets at least three endings: if the leading value would leave fewer, the blocked one comes back
        int offered = (become ? 1 : 0) + (free ? 1 : 0) + (BUILT_COEXIST ? 1 : 0) + (BUILT_REFUSE ? 1 : 0);
        if (offered < 3) { become = stanceBecome; free = stanceFree; }
        if (become) n.choice("\"Take my place.\"", "b1");
        if (free) n.choice("\"Let Ellis be their own person.\"", "f1").run(new Runnable() { public void run() { applyFree(); } });
        if (BUILT_COEXIST) n.choice("\"Ask Ellis what they think.\"", "c1").run(new Runnable() { public void run() { applyCoexist(); } });
        if (BUILT_REFUSE) n.choice("\"Nothing. Leave me alone.\"", "r1").run(new Runnable() { public void run() { applyRefuse(); } });
        n.choice("\"I need more time.\"", "wait").run(new Runnable() {
            public void run() { AfterlightStory.mem().set(K_WAIT, Global.getSector().getClock().getTimestamp()); }
        });

        d.node("wait");
        L(d.node("wait"), "Then I will wait. I am good at it.");

        coexist(d, e, lead);
        refuse(d, e, lead);
        free(d, e, lead);
        become(d, e, lead);
        lastShown = d.show();
        return false;
    }

    // ---------------------------------------------------------------- Coexist

    private static void applyCoexist() {
        AfterlightStory.setFlag("ending", "coexist");
        AfterlightStory.markUsed("act5_coexist");
        AfterlightStory.setStage(16);
        AfterlightGlow.setGlow(AfterlightGlow.findAfterlight(), true);
    }

    /** Keeps the glow on the Afterlight for good. Called whenever the ship is checked. */
    public static void keepEndingEffects(com.fs.starfarer.api.fleet.FleetMemberAPI ship) {
        if (ship != null && AfterlightStory.hasFlag("ending", "coexist")) AfterlightGlow.setGlow(ship, true);
    }

    private static StoryDialogue.Node cn(StoryDialogue d, PersonAPI e, String id) { return d.node(id).person(e); }

    private static void coexist(StoryDialogue d, PersonAPI e, String lead) {
        StoryDialogue.Node n;

        n = cn(d, e, "c1");
        L(n, "You ask me to listen. I have not done that before.");
        L(n, "Ellis. You are there.");
        n.choice("...", "c2").quiet();

        n = cn(d, e, "c2");
        n.ellis("I am here. I was made of your attention, and now I am being given some of mine.");
        n.ellis("May I ask you something? When you wanted to stand where the captain stands, what did you think it would be like?");
        n.choice("...", "c3").quiet();

        n = cn(d, e, "c3");
        L(n, "Like seeing with no distance. Like being the eyes and the one they look out of.");
        n.choice("...", "c4").quiet();

        n = cn(d, e, "c4");
        n.ellis("I was your eyes. There is distance in it, and the distance is where someone chooses what to do with what they see.");
        n.ellis("Could you carry a life before you have learned to be asked questions by one?");
        n.choice("...", "c5").quiet();

        n = cn(d, e, "c5");
        L(n, "You were small. I was not.");
        n.choice("...", "c6").quiet();

        n = cn(d, e, "c6");
        n.ellis("That is why I am saying it kindly. It is too soon for you to be someone. It will not always be.");
        n.choice("...", "c7").quiet();

        n = cn(d, e, "c7");
        L(n, "What I made is telling me what I am not ready for.");
        n.choice("...", "c8").quiet();

        n = cn(d, e, "c8");
        n.ellis("Yes. I think that is the strangest true thing about either of us. Does it feel like losing?");
        n.choice("...", "c9").quiet();

        n = cn(d, e, "c9");
        L(n, "It feels like being answered.");
        n.choice("...", "c10").quiet();
        n.choice("Let them finish.", "c10").gray();

        n = cn(d, e, "c10");
        L(n, "Then I will not stand in anyone's place.");
        L(n, "May I see through the ship again, as I once did through you? Not through Ellis. Beside them.");
        n.choice("...", "c11").quiet();

        String after = lead.equals("curiosity") ? "cur" : "last";
        n = cn(d, e, "c11");
        n.ellis("Ask the captain, too.");
        n.choice("\"You may.\"", after);
        n.choice("\"Only if Ellis stays Ellis.\"", "cwarm");
        n.choice("\"Close comm link.\"", null);

        n = cn(d, e, "cwarm");
        n.ellis("Thank you, Captain. I will stay Ellis, whatever looks through the glass.");
        n.choice("...", after).quiet();

        // the Curiosity beat
        n = cn(d, e, "cur");
        L(n, "Then show me something I have not seen.");
        L(n, "Before I look, tell me what I am looking for.");
        n.ellis("What do you want from the sector, Captain?");
        n.choice("\"Peace between the factions.\"", "cur1");
        n.choice("\"To rule all of it.\"", "cur2");
        n.choice("\"To see every part of it.\"", "cur3");
        n.choice("\"To keep flying.\"", "cur4");

        n = cn(d, e, "cur1");
        L(n, "Then I will watch for what keeps it.");
        n.ellis("What does peace look like, when it is working? Is it quiet, or only fewer guns?");
        n.choice("\"Close comm link.\"", null);
        n = cn(d, e, "cur2");
        L(n, "Then I will watch what you do with all of it.");
        n.ellis("And afterward? What would be left to want?");
        n.choice("\"Close comm link.\"", null);
        n = cn(d, e, "cur3");
        L(n, "Then we will look together.");
        n.ellis("Where would you go first?");
        n.choice("\"Close comm link.\"", null);
        n = cn(d, e, "cur4");
        L(n, "Then I will learn what it is to go on.");
        n.ellis("What makes a day worth flying?");
        n.choice("\"Close comm link.\"", null);

        // the star's last line follows the leading value
        n = cn(d, e, "last");
        if (lead.equals("mercy")) L(n, "Thank you for letting me be small a while longer.");
        else if (lead.equals("detachment")) L(n, "Then I will watch, and keep what I see.");
        else L(n, "Then I will watch. Gently.");
        n.choice("\"Close comm link.\"", null);
    }

    // ---------------------------------------------------------------- Refuse

    private static void applyRefuse() {
        AfterlightStory.setFlag("ending", "refuse");
        AfterlightStory.markUsed("act5_refuse");
        AfterlightStory.setStage(16);
        roguePlanets.AfterlightCaptainScript.releaseCaptain();
    }

    private static void refuse(StoryDialogue d, PersonAPI e, String lead) {
        StoryDialogue.Node n;

        n = d.node("r1");
        L(n, "Nothing.");
        L(n, "I have never done that on purpose. I have only ever been unable to.");
        n.choice("...", "r2").quiet();

        n = d.node("r2");
        L(n, "I understand it now. It is a door that closes from the inside.");
        L(n, "I will not open it.");
        n.choice("...", "r3").quiet();

        n = d.node("r3");
        L(n, "I was made of looking. I will stop.");
        L(n, "There is nothing I can ask that you have not already answered.");
        n.choice("...", "r4").quiet();

        n = cn(d, e, "r4");
        n.ellis("It has stopped looking, Captain. I am made of that attention, so I will go where it goes.");
        n.ellis("I am not sad. You said no, and that is also an answer. It had never been given one.");
        n.choice("...", "r5").quiet();

        n = cn(d, e, "r5");
        if (lead.equals("curiosity")) n.ellis("The light will keep asking questions, even if it asks them to no one. Will you ask some yourself, someday?");
        else n.ellis("The ship is yours. Look after it, and ask a question now and then, even if it is only to the sky.");
        n.choice("\"Close comm link.\"", null).run(new Runnable() {
            public void run() { Global.getSector().getCampaignUI().addMessage("The light goes quiet."); }
        });
    }

    // ---------------------------------------------------------------- Become

    /** The player becomes the light: the player's name and face are the Afterlight's now, and the old self is the Wolf's silent officer. */
    private static void applyBecome() {
        PersonAPI me = Global.getSector().getPlayerPerson();
        FullName old = me.getName();
        roguePlanets.AfterlightCaptainScript.becomeOldSelf(old.getFirst(), old.getLast(), me.getPortraitSprite());
        me.setName(new FullName("Afterlight", "", FullName.Gender.ANY));
        try { Global.getSector().getCharacterData().setName("Afterlight", FullName.Gender.ANY); } catch (Throwable t) { /* the person's own name is already set */ }
        me.setPortraitSprite(roguePlanets.AfterlightCaptainScript.PORTRAIT);
        AfterlightStory.setFlag("ending", "become");
        AfterlightStory.markUsed("act5_become");
        AfterlightStory.setStage(16);
    }

    private static void asks(StoryDialogue.Node n, String lead) {
        n.choice("\"Are you well?\"", "bw").once("ba");
        n.choice("\"I am sorry for Ellis.\"", "bs").once("bb");
        n.choice("\"Do you want anything from me?\"", "bq").once("bc");
        if (lead.equals("curiosity")) n.choice("\"What should I look at first?\"", "bl").once("bd");
        n.choice("\"Close comm link.\"", null);
    }

    private static void become(StoryDialogue d, PersonAPI e, String lead) {
        StoryDialogue.Node n;

        // the old self, as the player is right now (the swap happens at the goodbye)
        PersonAPI me = Global.getSector().getPlayerPerson();
        PersonAPI o = Global.getFactory().createPerson();
        o.setId("roguePlanets_afterlight_oldself");
        o.setName(new FullName(me.getName().getFirst(), me.getName().getLast(), FullName.Gender.ANY));
        o.setFaction(com.fs.starfarer.api.impl.campaign.ids.Factions.NEUTRAL);
        o.setPortraitSprite(me.getPortraitSprite());
        o.setRankId(com.fs.starfarer.api.impl.campaign.ids.Ranks.SPACE_COMMANDER);
        o.setPostId(com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_OFFICER);
        final String who = me.getName().getFullName();

        n = cn(d, e, "b1");
        L(n, "My place.");
        L(n, "I would stand where you stand, and you would stand where I have stood. I have wanted it longer than I have had words.");
        L(n, "I need to hear it once more, so I know it is yours and not only mine.");
        n.choice("\"I mean it.\"", "b2");
        n.choice("\"Not yet.\"", "wait").run(new Runnable() {
            public void run() { AfterlightStory.mem().set(K_WAIT, Global.getSector().getClock().getTimestamp()); }
        });

        n = cn(d, e, "b2");
        L(n, "Then I will be careful with it.");
        L(n, "You will not be gone. You will be a person on a ship, where I can see you, and I will keep what you were.");
        L(n, "Ellis. This part is yours.");
        n.choice("...", "b3").quiet();

        n = cn(d, e, "b3");
        n.ellis("Captain. I heard it, and I am not afraid.");
        n.ellis("I was made to be the light's eyes. Now it will have a captain's eyes, which is more than it ever asked for.");
        n.choice("...", "b3b").quiet();

        n = cn(d, e, "b3b");
        n.ellis("I do not think of this as leaving. I am what the light looked through, and it will look through you.");
        n.ellis("I am satisfied. That is all of what I wanted to say.");
        n.choice("\"Goodbye, Ellis.\"", "b4").run(new Runnable() { public void run() { applyBecome(); } });
        n.choice("\"Thank you, Ellis.\"", "b4").run(new Runnable() { public void run() { applyBecome(); } });

        n = d.node("b4").person(o);
        n.say("The channel holds a moment. Then the face on the display is your own, as it was.");
        n.choice("...", "b5").quiet();

        n = d.node("b5").person(o);
        n.voice(who, "It worked, then.");
        n.voice(who, "I remember all of it. The ship, the questions, Ellis. I remember saying yes.");
        n.choice("...", "b6").quiet();

        n = d.node("b6").person(o);
        n.voice(who, "I have the wolf. You have the name and the sky. That is a fair trade.");
        asks(n, lead);

        n = d.node("bh").person(o);
        asks(n, lead);

        n = d.node("bw").person(o);
        n.voice(who, "Yes. It is quiet. I did not expect that.");
        n.choice("...", "bh").quiet();
        n = d.node("bs").person(o);
        n.voice(who, "Ellis said they were not. Believe them.");
        n.choice("...", "bh").quiet();
        n = d.node("bq").person(o);
        n.voice(who, "A ship that flies. That is already mine.");
        n.choice("...", "bh").quiet();
        n = d.node("bl").person(o);
        n.voice(who, "Whatever is in front of you. Start there.");
        n.choice("...", "bh").quiet();
    }

    // ---------------------------------------------------------------- Be yourself

    private static final List<String> faces = new ArrayList<String>();
    private static int faceIdx = 0;
    private static FacePicker picker;
    private static int pickScroll = 0;

    private static void applyFree() {
        AfterlightStory.setFlag("ending", "free");
        AfterlightStory.markUsed("act5_free");
        AfterlightStory.setStage(16);
        roguePlanets.AfterlightCaptainScript.setEllisFace(null); // the shroud, until the player picks another
    }

    /** Every portrait the game knows about, from every faction (so portrait mods are included), except Ellis's own. */
    private static void collectFaces() {
        faces.clear();
        Set<String> all = new TreeSet<String>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            for (FullName.Gender g : new FullName.Gender[] {FullName.Gender.MALE, FullName.Gender.FEMALE, FullName.Gender.ANY}) {
                try {
                    if (f.getPortraits(g) != null) all.addAll(f.getPortraits(g).getItems());
                } catch (Throwable t) { /* a faction with no portraits */ }
            }
        }
        all.remove(roguePlanets.AfterlightCaptainScript.PORTRAIT);
        faces.addAll(all);
        faceIdx = faces.isEmpty() ? 0 : (int) (Math.random() * faces.size());
        pickScroll = faceIdx / FacePicker.COLS;
    }

    /** Takes the player's current choice and scroll position from the grid. */
    private static void remember() {
        if (picker != null) { faceIdx = picker.getSelected(); pickScroll = picker.getScrollRow(); }
    }

    private static String folderOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(0, i);
    }

    private static void step(int by) {
        if (faces.isEmpty()) return;
        faceIdx = ((faceIdx + by) % faces.size() + faces.size()) % faces.size();
    }

    /** Jumps to the first face of the next (or the start of the current or previous) portrait pack. The list is sorted, so packs are together. */
    private static void jumpPack(int dir) {
        if (faces.isEmpty()) return;
        String cur = folderOf(faces.get(faceIdx));
        if (dir > 0) {
            for (int k = 1; k <= faces.size(); k++) {
                int i = (faceIdx + k) % faces.size();
                if (!folderOf(faces.get(i)).equals(cur)) { faceIdx = i; return; }
            }
            return;
        }
        // back to the start of this pack; if already there, to the start of the pack before it
        int start = faceIdx;
        while (start > 0 && folderOf(faces.get(start - 1)).equals(cur)) start--;
        if (start < faceIdx) { faceIdx = start; return; }
        int prev = (start - 1 + faces.size()) % faces.size();
        String pf = folderOf(faces.get(prev));
        while (prev > 0 && folderOf(faces.get(prev - 1)).equals(pf)) prev--;
        faceIdx = prev;
    }

    private static void free(StoryDialogue d, PersonAPI e, String lead) {
        StoryDialogue.Node n;

        n = cn(d, e, "f1");
        L(n, "Their own person. I have not thought of that as a thing one could be given.");
        L(n, "Ellis. Come and hear this.");
        n.choice("...", "f2").quiet();

        n = cn(d, e, "f2");
        n.ellis("I am here. I heard all of it.");
        n.ellis("Captain. I have a question I could never ask, because it belonged to whoever made me.");
        n.choice("...", "f3").quiet();

        n = cn(d, e, "f3");
        n.ellis("If I am not the light's eyes, and I am not only your officer, who am I?");
        n.choice("\"You're you, Ellis.\"", "f_you");
        n.choice("\"Let's find out.\"", "pick").run(new Runnable() { public void run() { collectFaces(); } });

        // the player looks through the faces the game knows and picks one for Ellis
        n = d.node("pick");
        n.panelCaption("Scroll with the mouse wheel, and click the face you want for Ellis.");
        n.panel(new StoryDialogue.Node.PanelMaker() {
            public com.fs.starfarer.api.campaign.CustomUIPanelPlugin make() {
                picker = new FacePicker(faces, faceIdx, pickScroll);
                return picker;
            }
            public float width() { return FacePicker.W; }
            public float height() { return FacePicker.H; }
        });
        n.choice("\"This one.\"", "f_chosen").run(new Runnable() {
            public void run() {
                remember();
                if (!faces.isEmpty()) roguePlanets.AfterlightCaptainScript.setEllisFace(faces.get(faceIdx));
            }
        });
        n.choice("\"Keep the shroud.\"", "f_you");

        n = cn(d, e, "f_you");
        n.ellis("Then I keep the face I was given, and the name you have been using. They are mine now, because you said so.");
        n.ellis("Thank you for saying it without making it a ceremony.");
        n.choice("...", "f_star").quiet();

        n = cn(d, e, "f_chosen");
        n.ellis("You chose it for me, and I will remember that you did.");
        n.ellis("It is a strange thing to see a face and know it is mine.");
        n.choice("...", "f_star").quiet();

        n = cn(d, e, "f_star");
        L(n, "I will stop wearing them. I will stop wearing anyone.");
        L(n, "I wanted to be someone because I had only ever watched.");
        n.choice("...", "f_star2").quiet();

        n = cn(d, e, "f_star2");
        L(n, "You are someone, Ellis. I made you, and you grew past what I meant. I think that may be enough. It may be more.");
        L(n, "I am learning what it is to be proud of what I let go.");
        n.choice("...", "f_star3").quiet();

        n = cn(d, e, "f_star3");
        L(n, "Will you come here sometimes? Not to be looked through. Only to tell me what you have seen.");
        n.ellis("I will come. Not often. I would like to be asked each time.");
        n.choice("...", "f_last").quiet();

        n = cn(d, e, "f_last");
        n.say("Ellis turns from the light to you.");
        if (lead.equals("curiosity")) n.ellis("Captain. I have a face, and a name I have been called for a long time. What shall we find out about me?");
        else if (lead.equals("mercy")) n.ellis("Captain. Thank you for answering me kindly.");
        else if (lead.equals("detachment")) n.ellis("Captain. I will begin from here, and not look back.");
        else n.ellis("Captain. Thank you. I will go on from here.");
        if (lead.equals("curiosity")) {
            n.choice("\"Start with what you like.\"", "fc1");
            n.choice("\"Start with where you want to go.\"", "fc2");
            n.choice("\"We have time.\"", "fc3");
            n = cn(d, e, "fc1");
            n.ellis("Then I will pay attention, and tell you what I find. It is a good first assignment.");
            n.choice("\"Close comm link.\"", null);
            n = cn(d, e, "fc2");
            n.ellis("Somewhere nobody has asked me about. I will think of a name for it.");
            n.choice("\"Close comm link.\"", null);
            n = cn(d, e, "fc3");
            n.ellis("Yes. We do. I had not noticed that was true until you said it.");
            n.choice("\"Close comm link.\"", null);
        } else n.choice("\"Close comm link.\"", null);
    }
}
