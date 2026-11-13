package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

/** Rule command: AflCopyMark <name>. Sets $violetCopy_<name> = true on the wreck being talked to, so it is remembered. */
public class AflCopyMark extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target == null) return false;
        String what = params.get(0).getString(memoryMap);
        if ("fade".equals(what)) {
            com.fs.starfarer.api.util.Misc.fadeAndExpire(target, 1f); // the wreck is gone once the player has left it
            return true;
        }
        target.getMemoryWithoutUpdate().set("$violetCopy_" + what, true);
        return true;
    }
}
