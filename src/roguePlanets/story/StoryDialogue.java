package roguePlanets.story;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A small data-driven conversation: a set of nodes, each with some lines and some options. Choosing an option can add
 * to the hidden values and set a choice flag, then moves to another node. A node with no options ends the
 * conversation with a single "Leave" button. Build one with {@link #node(String)}, then show it with {@link #show()}.
 */
public class StoryDialogue implements InteractionDialogPlugin {

    public static final Color ELLIS_COLOR = new Color(205, 165, 255);
    public static final Color VOICE_COLOR = new Color(255, 200, 150);

    private static final String LEAVE = "__leave";

    public static class Choice {
        String text;
        String next;
        int mercy, detach, curious;
        String flagKey, flagValue;
        Runnable action;
        String onceKey;
        String[] needs;
        boolean quiet, gray;

        /** Nothing is printed for this option when it is chosen (a silent "keep going" button). */
        public Choice quiet() { this.quiet = true; return this; }

        /** The option is printed in faint grey, as an action and not as speech. */
        public Choice gray() { this.gray = true; return this; }

        /** Only shown once every one of these once-keys has been used in this conversation. */
        public Choice needs(String... keys) {
            this.needs = keys;
            return this;
        }

        /** Hidden for the rest of this conversation once it (or any option with the same key) has been chosen. */
        public Choice once(String key) {
            this.onceKey = key;
            return this;
        }

        public Choice values(int mercy, int detach, int curious) {
            this.mercy = mercy; this.detach = detach; this.curious = curious;
            return this;
        }

        /** Something to do when this option is chosen (hand over a ship, and so on). */
        public Choice run(Runnable r) {
            this.action = r;
            return this;
        }

        public Choice flag(String key, String value) {
            this.flagKey = key; this.flagValue = value;
            return this;
        }
    }

    public static class Node {
        final String id;
        final List<String[]> lines = new ArrayList<String[]>(); // {kind, text}
        final List<Choice> choices = new ArrayList<Choice>();
        PersonAPI person;
        com.fs.starfarer.api.campaign.CampaignFleetAPI fleet;
        Runnable onEnter;
        boolean clearFirst;
        PanelMaker panel;
        String panelCaption;

        /** A grey line shown in a strip under the custom panel. */
        public Node panelCaption(String text) { this.panelCaption = text; return this; }

        /** Builds a custom panel (drawn in the picture area) each time the node is shown. */
        public interface PanelMaker {
            com.fs.starfarer.api.campaign.CustomUIPanelPlugin make();
            float width();
            float height();
        }

        public Node panel(PanelMaker m) { this.panel = m; return this; }
        Text dynamic;

        /** A line whose words are worked out each time the node is shown. */
        public interface Text { String get(); }

        /** The text panel is emptied each time this node is shown (for a node the player comes back to again and again). */
        public Node clearFirst() { this.clearFirst = true; return this; }

        /** Adds a grey line made when the node is shown. */
        public Node dynamicSay(Text t) { this.dynamic = t; return this; }

        /** Runs every time this node is shown, before anything is drawn (lets a node change its portrait). */
        public Node onEnter(Runnable r) { this.onEnter = r; return this; }

        Node(String id) { this.id = id; }

        /** A line spoken by Ellis. */
        public Node ellis(String text) { lines.add(new String[] {"ellis", text}); return this; }
        /** A line spoken by an unnamed voice. */
        public Node voice(String text) { lines.add(new String[] {"voice", text}); return this; }
        /** A line spoken by a named voice, shown as  Name: "text"  in the voice colour. */
        public Node voice(String label, String text) {
            lines.add(new String[] {"labeled", label + ": \"" + text + "\""});
            return this;
        }
        /** A line in a given colour, such as the game's own "You're being hailed" line in the faction colour. */
        public Node colored(String text, Color c) {
            lines.add(new String[] {"rgb", text, c.getRed() + "," + c.getGreen() + "," + c.getBlue()});
            return this;
        }
        /** A plain narration line. */
        public Node say(String text) { lines.add(new String[] {"say", text}); return this; }
        /** Shows a portrait while this node is displayed. */
        public Node person(PersonAPI p) { this.person = p; return this; }
        /** Shows the other fleet and the player's fleet side by side (instead of a portrait) while this node is displayed. */
        public Node fleetInfo(com.fs.starfarer.api.campaign.CampaignFleetAPI f) { this.fleet = f; return this; }

        /** Adds an option. Pass the id of the node it leads to, or null to end the conversation. */
        public Choice choice(String text, String next) {
            Choice c = new Choice();
            c.text = text;
            c.next = next;
            choices.add(c);
            return c;
        }
    }

    private final Map<String, Node> nodes = new HashMap<String, Node>();
    private final String startId;
    private final String uniqueId;      // once shown, never shown again (null = not tracked)
    private final boolean isEllis;      // uses the shared Ellis cooldown
    private InteractionDialogAPI dialog;
    private Runnable onEnd;

    /** Something to do when the conversation ends, whichever way it ends. */
    public StoryDialogue onEnd(Runnable r) {
        this.onEnd = r;
        return this;
    }

    private void finish() {
        if (onEnd != null) {
            Runnable r = onEnd;
            onEnd = null;
            r.run();
        }
        dialog.dismiss();
    }

    /** Options stay greyed out for a moment after the box opens, so a stray 1/2/3 keypress cannot pick an answer. */
    public static final float FIRST_LOCK_SECONDS = 2.0f;
    public static final float NEXT_LOCK_SECONDS = 0.8f;
    private float lockTimer = 0f;
    private boolean firstNode = true;
    private final List<Object> lockedData = new ArrayList<Object>();
    private final java.util.Set<String> usedKeys = new java.util.HashSet<String>();

    private boolean hidden(Choice c) {
        if (c.onceKey != null && usedKeys.contains(c.onceKey)) return true;
        if (c.needs != null) for (String k : c.needs) if (!usedKeys.contains(k)) return true;
        return false;
    }

    /** @param startId the first node; @param uniqueId marks the conversation used once shown; @param isEllis starts the cooldown */
    public StoryDialogue(String startId, String uniqueId, boolean isEllis) {
        this.startId = startId;
        this.uniqueId = uniqueId;
        this.isEllis = isEllis;
    }

    public Node node(String id) {
        Node existing = nodes.get(id);
        if (existing != null) return existing;
        Node n = new Node(id);
        nodes.put(id, n);
        return n;
    }

    /** Opens the conversation. Returns false if it could not be shown. */
    public boolean show() {
        boolean ok = Global.getSector().getCampaignUI().showInteractionDialog(this, AfterlightStory.dialogTarget());
        if (ok) {
            if (uniqueId != null) AfterlightStory.markUsed(uniqueId);
            if (isEllis) AfterlightStory.markEllisSpoke();
        }
        return ok;
    }

    // ---------------------------------------------------------------- InteractionDialogPlugin

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setBackgroundDimAmount(0.5f);
        enter(startId);
    }

    private void enter(String id) {
        Node n = nodes.get(id);
        if (n == null) { finish(); return; }
        if (n.onEnter != null) n.onEnter.run();

        if (n.panel != null) {
            com.fs.starfarer.api.ui.CustomPanelAPI cp = dialog.getVisualPanel().showCustomPanel(n.panel.width(), n.panel.height(), n.panel.make());
            if (n.panelCaption != null) {
                try {
                    com.fs.starfarer.api.ui.TooltipMakerAPI cap = cp.createUIElement(n.panel.width() - 20f, 24f, false);
                    cap.addPara(n.panelCaption, Misc.getGrayColor(), 0f);
                    cp.addUIElement(cap).inBL(10f, 4f);
                } catch (Throwable t) {
                    dialog.getTextPanel().addParagraph(n.panelCaption, Misc.getGrayColor());
                }
            }
        } else if (n.fleet != null && n.fleet.isAlive() && Global.getSector().getPlayerFleet() != null) {
            dialog.getVisualPanel().showFleetInfo(n.fleet.getName(), n.fleet, "Your fleet", Global.getSector().getPlayerFleet());
        } else if (n.person != null) dialog.getVisualPanel().showPersonInfo(n.person);

        if (n.clearFirst) dialog.getTextPanel().clear();
        for (String[] line : n.lines) {
            String kind = line[0], text = line[1];
            if (kind.equals("ellis")) {
                dialog.getTextPanel().addParagraph("Ellis: \"" + text + "\"", ELLIS_COLOR);
            } else if (kind.equals("labeled")) {
                dialog.getTextPanel().addParagraph(text, VOICE_COLOR);
            } else if (kind.equals("rgb")) {
                String[] rgb = line[2].split(",");
                dialog.getTextPanel().addParagraph(text, new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2])));
            } else if (kind.equals("voice")) {
                dialog.getTextPanel().addParagraph("\"" + text + "\"", VOICE_COLOR);
            } else {
                dialog.getTextPanel().addParagraph(text, Misc.getGrayColor());
            }
        }

        if (n.dynamic != null) dialog.getTextPanel().addParagraph(n.dynamic.get(), Misc.getGrayColor());

        dialog.getOptionPanel().clearOptions();
        boolean any = false;
        for (Choice c : n.choices) if (!hidden(c)) any = true;
        if (!any) {
            String leaveText = isEllis ? "Close comm link" : "Leave";
            dialog.getOptionPanel().addOption(leaveText, LEAVE);
            dialog.setOptionOnEscape(leaveText, LEAVE);
        } else {
            for (Choice c : n.choices) if (!hidden(c)) dialog.getOptionPanel().addOption(c.text, c);
        }

        // lock every option briefly
        lockedData.clear();
        if (!any) lockedData.add(LEAVE);
        else for (Choice c : n.choices) if (!hidden(c)) lockedData.add(c);
        for (Object data : lockedData) dialog.getOptionPanel().setEnabled(data, false);
        lockTimer = firstNode ? FIRST_LOCK_SECONDS : NEXT_LOCK_SECONDS;
        firstNode = false;
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (lockTimer > 0f) return;
        if (optionData == LEAVE || LEAVE.equals(optionData)) {
            finish();
            return;
        }
        if (!(optionData instanceof Choice)) return;
        Choice c = (Choice) optionData;
        if (c.onceKey != null) usedKeys.add(c.onceKey);

        if (!c.quiet) dialog.getTextPanel().addParagraph(c.text, c.gray ? Misc.getGrayColor() : Misc.getBasePlayerColor());
        AfterlightStory.addValues(c.mercy, c.detach, c.curious);
        if (c.flagKey != null) AfterlightStory.setFlag(c.flagKey, c.flagValue);

        if (c.action != null) c.action.run();

        if (c.next == null) {
            finish();
        } else {
            enter(c.next);
        }
    }

    @Override public void optionMousedOver(String optionText, Object optionData) { }
    @Override
    public void advance(float amount) {
        if (lockTimer <= 0f) return;
        lockTimer -= amount;
        if (lockTimer <= 0f) {
            lockTimer = 0f;
            for (Object data : lockedData) dialog.getOptionPanel().setEnabled(data, true);
            lockedData.clear();
        }
    }
    @Override public void backFromEngagement(EngagementResultAPI battleResult) { }
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
}
