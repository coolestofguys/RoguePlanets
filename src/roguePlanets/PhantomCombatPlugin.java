package roguePlanets;

import java.util.List;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;

/**
 * Registered in data/config/settings.json. When the enemy is the phantom pirate armada, this keeps its
 * ships from ever deploying, so the battle ends in victory without a shot being fired.
 */
public class PhantomCombatPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private boolean phantom = false;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        try {
            CampaignFleetAPI other = engine.getContext() != null ? engine.getContext().getOtherFleet() : null;
            phantom = other != null && other.getMemoryWithoutUpdate().getBoolean(VioletSystemContent.PHANTOM_FLAG);
        } catch (Exception e) {
            phantom = false;
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!phantom || engine == null) return;

        CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.ENEMY);
        if (fm == null) return;

        for (FleetMemberAPI member : fm.getReservesCopy()) {
            fm.removeFromReserves(member);
        }
        for (FleetMemberAPI member : fm.getDeployedCopy()) {
            ShipAPI ship = fm.getShipFor(member);
            if (ship != null) {
                fm.removeDeployed(ship, false);
                engine.removeEntity(ship);
            }
        }
    }
}
