package roguePlanets;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.OptionId;

import java.awt.Color;
import java.util.Map;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * Watches for dialogues with the phantom pirate armada. Everything stays vanilla until the battle is over;
 * then the fleet is gone and the dialogue says so.
 */
public class PhantomListener extends BaseCampaignEventListener {

    public PhantomListener() {
        super(false);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog.getInteractionTarget();
        if (!(target instanceof CampaignFleetAPI)) return;
        if (!target.getMemoryWithoutUpdate().getBoolean(VioletSystemContent.PHANTOM_FLAG)) return;
        if (dialog.getPlugin() instanceof Wrapper || dialog.getPlugin() instanceof Aftermath) return;
        dialog.setPlugin(new Wrapper(dialog.getPlugin(), (CampaignFleetAPI) target));
        stripEscapes(dialog);
    }

    /** Removes the story-point escape and emergency-repair options: this fleet cannot be cleanly escaped. */
    static void stripEscapes(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getOptionPanel() == null) return;
        OptionId[] banned = { OptionId.CLEAN_DISENGAGE, OptionId.EMERGENCY_REPAIRS };
        for (OptionId id : banned) {
            if (dialog.getOptionPanel().hasOption(id)) {
                dialog.getOptionPanel().removeOption(id);
            }
        }
    }

    /** Delegates everything to the vanilla encounter plugin, except what happens after the battle. */
    public static class Wrapper implements InteractionDialogPlugin {
        private final InteractionDialogPlugin delegate;
        private final CampaignFleetAPI fleet;

        public Wrapper(InteractionDialogPlugin delegate, CampaignFleetAPI fleet) {
            this.delegate = delegate;
            this.fleet = fleet;
        }

        @Override public void init(InteractionDialogAPI dialog) { delegate.init(dialog); }
        @Override public void optionSelected(String optionText, Object optionData) {
            delegate.optionSelected(optionText, optionData);
            stripEscapes(com.fs.starfarer.api.Global.getSector().getCampaignUI().getCurrentInteractionDialog());
        }
        @Override public void optionMousedOver(String optionText, Object optionData) { delegate.optionMousedOver(optionText, optionData); }
        @Override public void advance(float amount) {
            delegate.advance(amount);
            stripEscapes(com.fs.starfarer.api.Global.getSector().getCampaignUI().getCurrentInteractionDialog());
        }
        @Override public Object getContext() { return delegate.getContext(); }
        @Override public Map<String, MemoryAPI> getMemoryMap() { return delegate.getMemoryMap(); }

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult) {
            // Let the game finish its own bookkeeping, then replace what it shows.
            delegate.backFromEngagement(battleResult);
            InteractionDialogAPI dialog = com.fs.starfarer.api.Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            if (dialog == null) return;
            Aftermath after = new Aftermath(fleet);
            dialog.setPlugin(after);
            after.showNothingHere(dialog);
        }
    }

    /** The empty room after the fight. */
    public static class Aftermath implements InteractionDialogPlugin {
        private InteractionDialogAPI dialog;
        private final CampaignFleetAPI fleet;

        public Aftermath(CampaignFleetAPI fleet) { this.fleet = fleet; }

        public void showNothingHere(InteractionDialogAPI dialog) {
            this.dialog = dialog;
            dialog.getTextPanel().clear();
            dialog.getOptionPanel().clearOptions();
            dialog.getTextPanel().addPara(
                    "There is nothing here.", new Color(190, 130, 255));
            dialog.getTextPanel().addPara(
                    "No wreckage. No drive trails. No cargo pods drifting in the dark. The armada that was bearing down on you "
                    + "a moment ago is simply not there, and your logs hold no record that it ever was.");
            dialog.getOptionPanel().addOption("Continue", "vdPhantomLeave");
            dialog.getOptionPanel().setShortcut("vdPhantomLeave", org.lwjgl.input.Keyboard.KEY_ESCAPE, false, false, false, false);
            if (fleet != null) {
                fleet.despawn();
            }
        }

        @Override public void init(InteractionDialogAPI dialog) { this.dialog = dialog; }

        @Override
        public void optionSelected(String optionText, Object optionData) {
            if ("vdPhantomLeave".equals(optionData) && dialog != null) {
                dialog.dismiss();
            }
        }

        @Override public void optionMousedOver(String optionText, Object optionData) {}
        @Override public void advance(float amount) {}
        @Override public void backFromEngagement(EngagementResultAPI battleResult) {}
        @Override public Object getContext() { return null; }
        @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }
    }
}
