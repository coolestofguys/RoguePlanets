package roguePlanets;

import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;
import roguePlanets.econ.conditions.ShipGraveyardCondition;

/**
 * Feeds the Ship Graveyard condition.
 *  - After every fight the player takes part in, each hostile ship that was destroyed or disabled is written into the
 *    ledger of every player colony in that star system that has the condition.
 *  - At the turn of each month, every such colony turns its points into hulls in storage.
 */
public class ShipGraveyardScript extends BaseCampaignEventListener implements EveryFrameScript {

    private static final Logger log = Global.getLogger(ShipGraveyardScript.class);

    private static final String KEY_LAST_MONTH = "$rp_graveyard_lastMonth";

    /** Ships already written into the ledger, shared by every instance, so a ship reported twice (or to two listeners) is only counted once. */
    private static final java.util.Set<String> counted = new java.util.HashSet<String>();

    public ShipGraveyardScript() {
        super(false);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    // ---------------------------------------------------------------- battles

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            if (result == null) return;
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return;
            LocationAPI where = player.getContainingLocation();
            if (where == null) return;

            List<MarketAPI> colonies = graveyardColoniesIn(where);
            if (colonies.isEmpty()) return;

            EngagementResultForFleetAPI enemy = result.getWinnerResult();
            if (enemy == null || enemy.isPlayer()) enemy = result.getLoserResult();
            if (enemy == null || enemy.isPlayer()) return;

            if (counted.size() > 3000) counted.clear();
            int recorded = 0;
            float totalDp = 0f;
            for (List<FleetMemberAPI> group : java.util.Arrays.asList(enemy.getDestroyed(), enemy.getDisabled())) {
                if (group == null) continue;
                for (FleetMemberAPI member : group) {
                    String hullId = usableHullId(member);
                    if (hullId == null) continue;
                    if (!counted.add(member.getId())) continue;
                    float dp = member.getDeploymentPointsCost();
                    for (MarketAPI colony : colonies) {
                        ShipGraveyardCondition.record(colony, hullId, dp);
                    }
                    recorded++;
                    totalDp += dp;
                    log.info("Ship Graveyard: recorded " + hullId + " (" + dp + " DP)");
                }
            }
            if (recorded > 0) {
                log.info("Ship Graveyard: " + recorded + " wrecks, " + totalDp + " DP total, recorded for " + colonies.size() + " colonies.");
            }
        } catch (Throwable t) {
            log.warn("Ship Graveyard could not record a battle: " + t);
        }
    }

    /** The hull id to remember for this ship, or null if it is not the kind of ship that leaves a wreck worth keeping. */
    private static String usableHullId(FleetMemberAPI member) {
        if (member == null || member.isFighterWing() || member.isStation()) return null;
        ShipHullSpecAPI spec = member.getHullSpec();
        if (spec == null) return null;
        if (spec.getHints().contains(ShipTypeHints.STATION) || spec.getHints().contains(ShipTypeHints.MODULE)
            || spec.getHints().contains(ShipTypeHints.UNDER_PARENT)) return null;
        String id = spec.getBaseHullId();
        if (id == null) id = spec.getHullId();
        if (id == null || !Global.getSettings().doesVariantExist(id + "_Hull")) return null;
        return id;
    }

    private static List<MarketAPI> graveyardColoniesIn(LocationAPI where) {
        List<MarketAPI> out = new java.util.ArrayList<MarketAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m == null || !m.isPlayerOwned() || m.getContainingLocation() != where) continue;
            if (m.hasCondition(ShipGraveyardCondition.CONDITION_ID)) out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- the monthly turn

    @Override
    public void advance(float amount) {
        try {
            MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();
            int month = Global.getSector().getClock().getMonth();
            if (!sectorMem.contains(KEY_LAST_MONTH)) {
                sectorMem.set(KEY_LAST_MONTH, month);
                return;
            }
            if (sectorMem.getInt(KEY_LAST_MONTH) == month) return;
            sectorMem.set(KEY_LAST_MONTH, month);

            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (m == null || !m.isPlayerOwned() || !m.hasCondition(ShipGraveyardCondition.CONDITION_ID)) continue;
                ShipGraveyardCondition.processMonth(m);
            }
        } catch (Throwable t) {
            log.warn("Ship Graveyard monthly turn failed: " + t);
        }
    }
}
