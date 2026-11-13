package roguePlanets;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.SharedUnlockData;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.codex.CodexUnlocker;

/**
 * Unlocks codex entries as the player finds things.
 *   Conditions: the game already unlocks them when a planet is surveyed through the normal survey dialog; this adds
 *   docking at a colony, since inhabited worlds cannot be surveyed.
 *   The mod's planet types unlock the first time the player interacts with a world of that type.
 *   (The Violet Dwarf has no codex entry at all.)
 */
public class CodexUnlockListener extends BaseCampaignEventListener {

    public CodexUnlockListener() {
        super(false);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        try {
            CodexUnlocker.makeAwareOfConditionsOn(market);
        } catch (Throwable t) {
            // never let the codex get in the way of docking
        }
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        try {
            SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();
            if (target instanceof PlanetAPI) unlockPlanetType((PlanetAPI) target);
        } catch (Throwable t) {
            // never let the codex get in the way of an interaction
        }
    }

    private static void unlockPlanetType(PlanetAPI planet) {
        if (planet.getSpec() == null || !planet.getSpec().hasTag(Tags.CODEX_UNLOCKABLE)) return;
        SharedUnlockData.get().reportPlayerAwareOfPlanet(planet.getSpec().getPlanetType(), true);
    }
}
