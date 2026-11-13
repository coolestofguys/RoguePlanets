package roguePlanets.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddShip;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import roguePlanets.AfterlightCaptainScript;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Rule command: VioletClaimAfterlight. Puts the Afterlight (a stock Wolf with its own permanent captain) straight
 * into the player's fleet and removes the wreck.
 */
public class VioletClaimAfterlight extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        // The original ship if it was lost, otherwise a plain stock Wolf. Either way it gets its captain back.
        FleetMemberAPI member = AfterlightCaptainScript.claim();
        AddShip.addShipGainText(member, dialog.getTextPanel());

        SectorEntityToken target = dialog.getInteractionTarget();
        if (target != null) Misc.fadeAndExpire(target, 2f);
        return true;
    }
}
