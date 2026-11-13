package roguePlanets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import roguePlanets.econ.conditions.ResonantSignalTrapCondition;

/**
 * Hands out the blackout-capable encounter screen, but only when it matters: a hostile fleet is stopping the player in a system
 * where a Resonant Signal Trap is working. Everywhere else the game's own encounter screen is used untouched.
 */
public class BlackoutCampaignPlugin extends BaseCampaignPlugin {

    public static final String ID = "roguePlanets_signalBlackout";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        try {
            if (!(interactionTarget instanceof CampaignFleetAPI)) return null;
            CampaignFleetAPI other = (CampaignFleetAPI) interactionTarget;
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null || other.getFaction() == null) return null;
            if (!(player.getContainingLocation() instanceof StarSystemAPI)) return null;
            if (!other.getFaction().isHostileTo(player.getFaction())) return null;
            if (!ResonantSignalTrapCondition.hasActive((StarSystemAPI) player.getContainingLocation())) return null;
            return new PluginPick<InteractionDialogPlugin>(new BlackoutFleetInteractionPlugin(), com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority.MOD_SPECIFIC);
        } catch (Throwable t) {
            return null;
        }
    }
}
