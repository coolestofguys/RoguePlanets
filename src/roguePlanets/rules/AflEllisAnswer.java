package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import roguePlanets.story.AfterlightStory;

/** Rule command: AflEllisAnswer <flag> <mercy> <detach> <curious>. Records an answer to Ellis and starts his cooldown. */
public class AflEllisAnswer extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.size() < 4) return false;
        AfterlightStory.setFlag("copy", params.get(0).getString(memoryMap));
        AfterlightStory.addValues((int) params.get(1).getFloat(memoryMap), (int) params.get(2).getFloat(memoryMap),
                (int) params.get(3).getFloat(memoryMap));
        AfterlightStory.markEllisSpoke();
        return true;
    }
}
