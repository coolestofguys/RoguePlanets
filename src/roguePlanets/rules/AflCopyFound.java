package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import roguePlanets.story.CopyWreck;

/** Rule command: AflCopyFound. The player has opened the copy's wreck: the hunt ends and the story moves to stage 8. */
public class AflCopyFound extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (CopyWreck.isActive()) CopyWreck.found();
        return true;
    }
}
