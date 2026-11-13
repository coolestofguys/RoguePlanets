package roguePlanets.rules;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Rule command: AflCopyLoot. Opens a loot screen with what a vanilla supply cache would hold, marks the wreck as
 * looted, and when the screen closes goes on to the dialogue option "vd8After".
 */
public class AflCopyLoot extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, final InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target != null) target.getMemoryWithoutUpdate().set("$violetCopy_looted", true);

        SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(Entities.SUPPLY_CACHE);
        CargoAPI loot = SalvageEntity.generateSalvage(new Random(), 1f, 1f, 1f,
                spec != null ? spec.getDropValue() : null, spec != null ? spec.getDropRandom() : null);

        dialog.getVisualPanel().showLoot("Cargo holds", loot, false, true, true, new CoreInteractionListener() {
            public void coreUIDismissed() {
                dialog.getPlugin().optionSelected(null, "vd8After");
            }
        });
        return true;
    }
}
