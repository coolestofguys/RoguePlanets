package roguePlanets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import roguePlanets.econ.conditions.VolatileAdditiveReactorCondition;

/**
 * Watches every Volatile Additive Reactor world once a second: grants the +2 burn while Refining and Fuel
 * Production are both working, and triggers the meltdown if either is ever disrupted or shut down.
 */
public class AdditiveReactorScript implements EveryFrameScript {

    private static final Logger log = Global.getLogger(AdditiveReactorScript.class);

    public static final String CONDITION_ID = "volatile_additive_reactor";
    private static final String BURN_ID = "roguePlanets_additive_burn";
    private static final String KEY_WAS_ACTIVE = "$additive_wasActive";

    private float interval = 0f;
    /** The burn bonus is saved with each fleet, but the list of boosted fleets is not: strip it from everyone once after a load. */
    private boolean cleaned = false;
    private Set<CampaignFleetAPI> boosted = new HashSet<CampaignFleetAPI>();

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        if (!cleaned) {
            cleaned = true;
            for (LocationAPI loc : Global.getSector().getAllLocations()) {
                for (CampaignFleetAPI f : loc.getFleets()) {
                    f.getStats().getFleetwideMaxBurnMod().unmodifyFlat(BURN_ID);
                }
            }
        }
        interval += amount;
        if (interval < 1f) return;
        interval = 0f;

        // Which factions get the bonus in which locations (same id everywhere, so copies never stack).
        Map<LocationAPI, Set<String>> boostedFactions = new HashMap<LocationAPI, Set<String>>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.hasCondition(CONDITION_ID)) continue;
            if (market.isPlanetConditionMarketOnly() || market.getSize() <= 0) continue;

            boolean active = update(market);
            if (!active) continue;
            LocationAPI loc = market.getContainingLocation();
            if (loc == null) continue;
            Set<String> set = boostedFactions.get(loc);
            if (set == null) {
                set = new HashSet<String>();
                boostedFactions.put(loc, set);
            }
            set.add(market.getFactionId());
        }

        Set<CampaignFleetAPI> target = new HashSet<CampaignFleetAPI>();
        for (Map.Entry<LocationAPI, Set<String>> e : boostedFactions.entrySet()) {
            for (CampaignFleetAPI f : e.getKey().getFleets()) {
                if (!e.getValue().contains(f.getFaction().getId())) continue;
                target.add(f);
                f.getStats().getFleetwideMaxBurnMod().modifyFlat(BURN_ID,
                        VolatileAdditiveReactorCondition.BURN_BONUS, "Volatile additive reactor");
            }
        }
        for (CampaignFleetAPI f : boosted) {
            if (!target.contains(f)) f.getStats().getFleetwideMaxBurnMod().unmodifyFlat(BURN_ID);
        }
        boosted = target;
    }

    /** Updates one reactor world. Returns true while the burn bonus should be on. */
    private boolean update(MarketAPI market) {
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        Industry refining = market.getIndustry(Industries.REFINING);
        Industry fuel = market.getIndustry(Industries.FUELPROD);
        if (mem.getBoolean(VolatileAdditiveReactorCondition.KEY_MELTDOWN)) {
            // Meltdown ends once nothing on the planet is disrupted any more.
            boolean anyDisrupted = false;
            for (Industry ind : market.getIndustries()) {
                if (ind.isDisrupted()) {
                    anyDisrupted = true;
                    break;
                }
            }
            if (!anyDisrupted) {
                mem.set(VolatileAdditiveReactorCondition.KEY_MELTDOWN, false);
                market.reapplyConditions();
            }
            return false;
        }

        boolean both = refining != null && fuel != null && refining.isFunctional() && fuel.isFunctional();
        // A running reactor that loses either industry melts down, whether the industry was disrupted or shut down
        if (mem.getBoolean(KEY_WAS_ACTIVE) && !both) {
            meltdown(market, mem);
            return false;
        }
        mem.set(KEY_WAS_ACTIVE, both);
        return both;
    }

    private void meltdown(MarketAPI market, MemoryAPI mem) {
        for (Industry ind : market.getIndustries()) {
            ind.setDisrupted(VolatileAdditiveReactorCondition.MELTDOWN_DAYS, true);
        }
        mem.set(VolatileAdditiveReactorCondition.KEY_MELTDOWN, true);
        mem.set(KEY_WAS_ACTIVE, false);
        market.reapplyConditions();
        log.info("Rogue Planets: additive reactor meltdown on " + market.getName());
        if (market.isPlayerOwned()) {
            Global.getSector().getCampaignUI().addMessage(
                    "REACTOR MELTDOWN on " + market.getName() + ": the additive reactor has failed. Every industry on the "
                    + "planet is disrupted for 180 days and hazard is up by 100%.",
                    Misc.getNegativeHighlightColor());
        }
    }
}
