package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import roguePlanets.econ.conditions.TrashKingCondition;

/** Rule command: RpKingOffer. Adds the "Call back" button to the colony menu while the deposed Trash King can be called back. */
public class RpKingOffer extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target == null || target.getMarket() == null) return false;
        if (!TrashKingCondition.canCallBack(target.getMarket())) return false;
        dialog.getOptionPanel().addOption(TrashKingCondition.callBackLabel(target.getMarket()), "rpKingRestore");
        return false; // the button is added here; no further rule text
    }
}
