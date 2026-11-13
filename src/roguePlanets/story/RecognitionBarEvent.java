package roguePlanets.story;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;

/**
 * Stage 11: recognition. At any market the player does not own, a stranger in the bar greets the player by first name,
 * then looks confused and says they do not know why they said it. No choices apart from leaving.
 */
public class RecognitionBarEvent extends BaseBarEvent {

    private static final String LEAVE = "afl_rc_leave";
    private PersonAPI stranger;

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    /** Deliberately does not use the base check, which would pin the event to the first market it was shown at. */
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return market != null && !market.isPlayerOwned();
    }

    private void makeStranger(InteractionDialogAPI dialog) {
        MarketAPI market = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
        String factionId = market != null ? market.getFactionId() : "independent";
        stranger = Global.getSector().getFaction(factionId).createRandomPerson();
        stranger.setRankId(Ranks.CITIZEN);
        stranger.setPostId(Ranks.POST_CITIZEN);
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        makeStranger(dialog);
        dialog.getTextPanel().addPara("Someone near the end of the bar is watching the door, and their face lights up "
            + "as you come through it.");
        dialog.getOptionPanel().addOption("Walk over to " + stranger.getName().getFullName(), this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        if (stranger == null) makeStranger(dialog);
        dialog.getVisualPanel().showPersonInfo(stranger);
        String first = Global.getSector().getPlayerPerson().getName().getFirst();
        text.addPara(stranger.getName().getFullName() + " stands as you approach, warm and easy, like someone greeting "
            + "an old friend.");
        text.addPara("\"" + first + "! It's been far too long.\"");
        text.addPara("The smile stays on their face a moment longer than the recognition behind it. Then they put a "
            + "hand to their mouth, as if checking what it said.");
        text.addPara("\"I'm sorry. I don't know why I said that,\" they say. \"I've never seen you before in my life.\"");
        options.clearOptions();
        options.addOption("Leave", LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (LEAVE.equals(optionData)) {
            done = true;
            PortsideBarData.getInstance().removeEvent(this);
            StageEvents.finishRecognition();
        }
    }

    @Override
    public boolean shouldRemoveEvent() {
        return done;
    }
}
