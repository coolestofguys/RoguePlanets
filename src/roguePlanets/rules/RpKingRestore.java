package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import roguePlanets.econ.conditions.TrashKingCondition;

/** Rule command: RpKingRestore. Calls the deposed Trash King back to his seat on the colony the player is standing on. */
public class RpKingRestore extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target == null || target.getMarket() == null) return false;
        return TrashKingCondition.reinstate(target.getMarket());
    }
}
