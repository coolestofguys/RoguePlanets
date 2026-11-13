package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import roguePlanets.story.VisitorsEvent;

/**
 * Rule command: AflVisitorsTalk. Runs when the player's fleet meets the Visitors. It closes the game's own fleet
 * encounter screen and asks the story to open the Visitors' conversation in its place.
 */
public class AflVisitorsTalk extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        VisitorsEvent.requestTalk();
        dialog.dismiss();
        return true;
    }
}
