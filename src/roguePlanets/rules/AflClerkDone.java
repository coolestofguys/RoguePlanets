package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import roguePlanets.story.StageEvents;

/** Rule command: AflClerkDone. The clerk has spoken: stage 7 is complete. */
public class AflClerkDone extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        StageEvents.finishClerk();
        return true;
    }
}
