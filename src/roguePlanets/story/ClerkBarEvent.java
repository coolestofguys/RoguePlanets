package roguePlanets.story;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;

/**
 * Stage 7: the bartender. At any market the player does not own, the bartender flags the player down and hands over
 * a drink the player never ordered, then claims the player ordered it a moment ago while telling them a story that
 * has not happened. The line used depends on what the player did when the Visitors offered to take the Afterlight.
 * (The class keeps its old name so saves that already contain it still load.)
 */
public class ClerkBarEvent extends BaseBarEvent {

    private static final String WHAT_1 = "afl_bt_what_1";
    private static final String WHAT_2 = "afl_bt_what_2";
    private static final String WHAT_3 = "afl_bt_what_3";
    private static final String NOT_1 = "afl_bt_not_1";
    private static final String NOT_2 = "afl_bt_not_2";
    private static final String NOT_3 = "afl_bt_not_3";
    private static final String EXC_1 = "afl_bt_exc_1";
    private static final String EXC_2 = "afl_bt_exc_2";
    private static final String EXC_3 = "afl_bt_exc_3";
    private static final String LEAVE_1 = "afl_bt_leave_1";
    private static final String LEAVE_2 = "afl_bt_leave_2";
    private static final String LEAVE_3 = "afl_bt_leave_3";

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    /** Deliberately does not use the base check, which would pin the event to the first market it was shown at. */
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return market != null && !market.isPlayerOwned() && AfterlightStory.storyShipHere();
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        dialog.getTextPanel().addPara("The bartender is waving a hand to catch your attention.");
        dialog.getOptionPanel().addOption("Go over to the bartender", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        text.addPara("The bartender flags you down as you cross the room, one hand raised, the other already "
            + "reaching under the counter.");
        options.clearOptions();
        options.addOption("\"What is it?\"", WHAT_1);
        options.addOption("\"Do I know you?\"", WHAT_2);
        options.addOption("\"I'm busy.\"", WHAT_3);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (WHAT_1.equals(optionData) || WHAT_2.equals(optionData) || WHAT_3.equals(optionData)) {
            if (WHAT_2.equals(optionData)) {
                text.addPara("\"You will,\" the bartender says, and sets a glass on the bar in front of you. "
                    + "\"Here's the drink you ordered.\"");
            } else if (WHAT_3.equals(optionData)) {
                text.addPara("\"It won't take long,\" the bartender says, and sets a glass on the bar in front of you. "
                    + "\"Here's the drink you ordered.\"");
            } else {
                text.addPara("\"Here's the drink you ordered,\" the bartender says, and sets a glass on the bar in front of you.");
            }
            options.clearOptions();
            options.addOption("\"I didn't order anything.\"", NOT_1);
            options.addOption("\"I think you have the wrong person.\"", NOT_2);
            options.addOption("\"I haven't said a word to you.\"", NOT_3);

        } else if (NOT_1.equals(optionData) || NOT_2.equals(optionData) || NOT_3.equals(optionData)) {
            boolean gave = AfterlightStory.hasFlag("visitors", "agreed");
            String what = gave ? "gave them away" : "kept them safe";
            if (NOT_2.equals(optionData)) {
                text.addPara("\"No, it was you,\" the bartender says, patient and certain. \"Just a moment ago you told me "
                    + "about how you " + what + ".\"");
            } else if (NOT_3.equals(optionData)) {
                text.addPara("\"You said plenty,\" the bartender says. \"Just a moment ago, when you told me how you "
                    + what + ".\"");
            } else {
                text.addPara("\"Yes, just a moment ago,\" the bartender says, \"when you told me about how you "
                    + what + ".\"");
            }
            options.clearOptions();
            options.addOption("\"Excuse me?\"", EXC_1);
            options.addOption("\"I never told you that.\"", EXC_2);
            options.addOption("\"Who told you that?\"", EXC_3);

        } else if (EXC_1.equals(optionData) || EXC_2.equals(optionData) || EXC_3.equals(optionData)) {
            if (EXC_2.equals(optionData)) {
                text.addPara("The bartender blinks twice. The smile fades to nothing, and for a moment there is no one "
                    + "behind their eyes. Then it all comes back at once, friendly and new.");
                text.addPara("\"Welcome in. Can I get you something?\"");
            } else if (EXC_3.equals(optionData)) {
                text.addPara("The bartender's gaze drifts past you, toward the door, and stays there a moment too long. "
                    + "When it returns, it is warm and attentive, as if you had only just arrived.");
                text.addPara("\"Welcome. What'll it be?\"");
            } else {
                text.addPara("The bartender's face goes slack, just for a moment, like a screen being cleared. Then it "
                    + "returns, warm and attentive, as if you had only just walked in.");
                text.addPara("\"Welcome,\" they say. \"What can I get you?\"");
            }
            AfterlightStory.addValues(0, 0, 1);
            options.clearOptions();
            options.addOption("Leave", LEAVE_1);
            options.addOption("\"Nothing. Thank you.\"", LEAVE_2);
            options.addOption("\"What did I say I wanted?\"", LEAVE_3);

        } else if (LEAVE_2.equals(optionData)) {
            text.addPara("\"Of course,\" the bartender says, and wipes the bar where the glass was. The glass is gone. "
                + "\"Come back any time.\"");
            options.clearOptions();
            options.addOption("Leave", LEAVE_1);

        } else if (LEAVE_3.equals(optionData)) {
            text.addPara("The bartender tilts their head, politely puzzled. \"I'm sorry. I don't think you've said "
                + "anything yet.\"");
            options.clearOptions();
            options.addOption("Leave", LEAVE_1);

        } else if (LEAVE_1.equals(optionData)) {
            finish();
        }
    }

    private void finish() {
        done = true;
        PortsideBarData.getInstance().removeEvent(this);
        StageEvents.finishClerk();
    }

    @Override
    public boolean shouldRemoveEvent() {
        return done;
    }
}
