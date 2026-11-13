package roguePlanets;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;
import roguePlanets.econ.conditions.ResonantSignalTrapCondition;

/**
 * The game's normal fleet-encounter screen with one extra option: when a colony with an active Resonant Signal Trap is in this
 * system, you can release its pulse and slip away. The slip-away itself is the game's own clean disengage, so no battle and no pursuit.
 */
public class BlackoutFleetInteractionPlugin extends FleetInteractionDialogPluginImpl {

    public static final String OPTION = "rp_signal_blackout_escape";

    private boolean forceClean = false;

    private StarSystemAPI system() {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        if (pf != null && pf.getContainingLocation() instanceof StarSystemAPI) return (StarSystemAPI) pf.getContainingLocation();
        return null;
    }

    private boolean eligible() {
        if (otherFleet == null || playerFleet == null || otherFleet.getFaction() == null) return false;
        if (!otherFleet.getFaction().isHostileTo(playerFleet.getFaction())) return false;
        return ResonantSignalTrapCondition.hasActive(system());
    }

    @Override
    protected void updateMainState(boolean withContinueOnItsOwn) {
        super.updateMainState(withContinueOnItsOwn);
        try {
            if (!eligible()) return;
            StarSystemAPI sys = system();
            options.addOption("Activate Signal Blackout to disengage freely", OPTION);
            if (!ResonantSignalTrapCondition.isReady(sys)) {
                options.setEnabled(OPTION, false);
                options.setTooltip(OPTION, "The atmosphere is still charging: " + (int) Math.ceil(ResonantSignalTrapCondition.daysLeft(sys)) + " days left.");
            } else {
                options.setTooltip(OPTION, "Release the stored pulse. Every sensor in the system goes dark, you slip away without a fight, and hostile fleets here are slowed for "
                    + (int) BlackoutPulse.SLOW_DAYS + " days. Recharges in " + (int) ResonantSignalTrapCondition.RECHARGE_DAYS + " days.");
            }
        } catch (Throwable t) {
            Global.getLogger(BlackoutFleetInteractionPlugin.class).warn("Could not add the signal blackout option: " + t);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!OPTION.equals(optionData)) {
            super.optionSelected(optionText, optionData);
            return;
        }
        StarSystemAPI sys = system();
        if (!eligible() || !ResonantSignalTrapCondition.isReady(sys)) return;

        int hit = BlackoutPulse.fire(sys, playerFleet, otherFleet);
        ResonantSignalTrapCondition.startRecharge(sys);
        textPanel.addPara("The stored charge tears out through the Waystation's channel. Every sensor in the system goes dark, and in the noise your fleet slips away.",
            Misc.getPositiveHighlightColor());
        Global.getSector().getCampaignUI().addMessage("Signal blackout: " + hit + " hostile fleets in the " + sys.getName()
            + " system are slowed for " + (int) BlackoutPulse.SLOW_DAYS + " days.", Misc.getHighlightColor());

        forceClean = true;
        try {
            super.optionSelected(optionText, OptionId.CLEAN_DISENGAGE);
        } finally {
            forceClean = false;
        }
    }

    @Override
    protected boolean canDisengageCleanly(CampaignFleetAPI fleet) {
        return forceClean || super.canDisengageCleanly(fleet);
    }
}
