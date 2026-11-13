package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class AutomatedShipyardsCondition extends BaseMarketConditionPlugin {

    public static final int STABILITY_PENALTY = 3;
    /** How long a destroyed flotilla takes to rebuild, and how often a living one is reviewed for a refit. */
    public static final float RESPAWN_DELAY_DAYS = 14f;
    /** A living flotilla is only refit when the colony has grown, or its strength would rise by at least this much. */
    public static final float REFIT_STRENGTH_GAIN = 0.25f;

    // Daily heartbeat (advanced in days), used to count the rebuild / review cycle
    private IntervalUtil tracker = new IntervalUtil(1f, 1f);

    /** Combat points from colony size alone. The market's own fleet size multiplier (stability and so on) is applied by the fleet factory on top. */
    public float getBasePoints() {
        if (market == null) return 15f;
        return 15f + market.getSize() * 20f;
    }

    /** What the fleet will actually be built at: the base points times the market's fleet size multiplier. */
    public float getCombatPoints() {
        if (market == null) return 15f;
        float mult = 1f;
        try {
            mult = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        } catch (Throwable ignored) {
            // fall back to no multiplier
        }
        return getBasePoints() * mult;
    }

    private String getFleetFlag() {
        return "$automated_shipyard_" + (market != null ? market.getId() : "");
    }

    private String getFleetIdKey() {
        return "$automated_shipyard_fleet_id_" + (market != null ? market.getId() : "");
    }

    private String getDeadKey() {
        return "$automated_shipyard_dead_" + (market != null ? market.getId() : "");
    }

    /** Days since the flotilla was last built, reviewed, or (once it has been destroyed) lost. */
    private String getCooldownTimerKey() {
        return "$automated_shipyard_cooldown_timer_" + (market != null ? market.getId() : "");
    }

    private List<CampaignFleetAPI> findAllActiveShipyardFleets(LocationAPI loc) {
        List<CampaignFleetAPI> result = new ArrayList<CampaignFleetAPI>();
        if (loc == null || market == null) return result;

        String flag = getFleetFlag();
        String savedId = market.getMemoryWithoutUpdate().getString(getFleetIdKey());

        for (CampaignFleetAPI f : loc.getFleets()) {
            if (f == null || !f.isAlive()) continue;

            boolean matchesFlag = f.getMemoryWithoutUpdate().getBoolean(flag);
            boolean matchesId = savedId != null && savedId.equals(f.getId());

            if (matchesFlag || matchesId) {
                result.add(f);
            }
        }
        return result;
    }

    private void checkDefenseFleet(float days) {
        if (market == null || market.getPrimaryEntity() == null) return;
        SectorEntityToken entity = market.getPrimaryEntity();
        LocationAPI loc = entity.getContainingLocation();
        if (loc == null) return;

        MemoryAPI marketMem = market.getMemoryWithoutUpdate();
        List<CampaignFleetAPI> activeFleets = findAllActiveShipyardFleets(loc);

        // 1. Clean up if market is uncolonized or under size 3
        if (market.isPlanetConditionMarketOnly() || market.getSize() < 3) {
            for (CampaignFleetAPI f : activeFleets) {
                loc.removeEntity(f);
            }
            marketMem.unset(getFleetIdKey());
            marketMem.unset(getCooldownTimerKey());
            marketMem.unset(getDeadKey());
            return;
        }

        // 2. Strict single-fleet enforcement: keep one, purge any duplicates
        CampaignFleetAPI mainFleet = null;
        if (!activeFleets.isEmpty()) {
            mainFleet = activeFleets.get(0);
            for (int i = 1; i < activeFleets.size(); i++) {
                loc.removeEntity(activeFleets.get(i));
            }
        }

        float cycle = marketMem.getFloat(getCooldownTimerKey());

        if (mainFleet != null) {
            // 3. A living flotilla: keep its allegiance with whoever holds the colony, and review it once per cycle
            if (!mainFleet.getFaction().getId().equals(market.getFactionId())) {
                mainFleet.setFaction(market.getFactionId(), true);
            }
            marketMem.set(getFleetIdKey(), mainFleet.getId());
            marketMem.unset(getDeadKey());

            cycle += days;
            if (cycle < RESPAWN_DELAY_DAYS) {
                marketMem.set(getCooldownTimerKey(), cycle);
                return;
            }
            // the review waits until the flotilla is out of combat
            if (mainFleet.getBattle() != null) {
                marketMem.set(getCooldownTimerKey(), cycle);
                return;
            }

            int builtSize = mainFleet.getMemoryWithoutUpdate().getInt("$shipyard_built_size");
            float builtPts = mainFleet.getMemoryWithoutUpdate().getFloat("$shipyard_built_points");
            float targetPts = getCombatPoints();
            boolean needsRefit = market.getSize() > builtSize || targetPts >= builtPts * (1f + REFIT_STRENGTH_GAIN);

            if (!needsRefit) {
                marketMem.set(getCooldownTimerKey(), 0f);
                return;
            }

            // dockside refit: the obsolete flotilla is replaced by the upgraded one
            loc.removeEntity(mainFleet);
            marketMem.unset(getFleetIdKey());
            marketMem.unset(getDeadKey());
            spawnFleet(entity, loc, marketMem);
            return;
        }

        // 4. No living flotilla
        if (marketMem.contains(getFleetIdKey())) {
            // it was destroyed: the rebuild clock starts from the moment it was noticed missing
            if (!marketMem.getBoolean(getDeadKey())) {
                marketMem.set(getDeadKey(), true);
                marketMem.set(getCooldownTimerKey(), 0f);
                return;
            }
            cycle += days;
            if (cycle < RESPAWN_DELAY_DAYS) {
                marketMem.set(getCooldownTimerKey(), cycle);
                return;
            }
        }

        // 5. First flotilla for this colony, or the rebuild cycle is over
        spawnFleet(entity, loc, marketMem);
    }

    private void spawnFleet(SectorEntityToken entity, LocationAPI loc, MemoryAPI marketMem) {
        // The base points come from colony size; the fleet factory then applies the market's own fleet size multiplier.
        float basePts = getBasePoints();

        FleetParamsV3 params = new FleetParamsV3(
            market,
            entity.getLocationInHyperspace(),
            Factions.REMNANTS,
            null,
            FleetTypes.PATROL_MEDIUM,
            basePts,
            0f,
            0f,
            0f,
            0f,
            0f,
            0.10f
        );

        CampaignFleetAPI newFleet = FleetFactoryV3.createFleet(params);
        if (newFleet == null) return;

        newFleet.setFaction(market.getFactionId(), true);
        newFleet.setName("Automated Remnant Flotilla");

        // Identity flags
        newFleet.getMemoryWithoutUpdate().set(getFleetFlag(), true);
        newFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);

        // System lock: keep it from jumping into hyperspace
        newFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        newFleet.getMemoryWithoutUpdate().set("$noJump", true);

        // Record what it was built from, to decide later whether it is worth a refit
        newFleet.getMemoryWithoutUpdate().set("$shipyard_built_size", market.getSize());
        newFleet.getMemoryWithoutUpdate().set("$shipyard_built_points", getCombatPoints());

        loc.addEntity(newFleet);
        newFleet.setLocation(entity.getLocation().x, entity.getLocation().y);

        newFleet.addAssignment(FleetAssignment.DEFEND_LOCATION, entity, 1000000f, "defending automated slipways");

        marketMem.set(getFleetIdKey(), newFleet.getId());
        marketMem.set(getCooldownTimerKey(), 0f);
        marketMem.unset(getDeadKey());
    }

    @Override
    public void apply(String id) {
        if (market != null) {
            market.getStability().modifyFlat(id, -STABILITY_PENALTY, "Automated Shipyards");
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) {
            market.getStability().unmodify(id);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;

        // the interval is counted in days, so this runs about once a day
        tracker.advance(Global.getSector().getClock().convertToDays(amount));
        if (tracker.intervalElapsed()) {
            checkDefenseFleet(tracker.getIntervalDuration());
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Automated Shipyards");
        tooltip.addPara(
            "Deep within the sprawling industrial ruins of this world lies a dormant, self-replicating Remnant fabrication nexus. Though partially degraded, automated sub-routines still pulse through its subterranean slipways, quietly churning out lethal autonomous hulls and specialized ordnance using scavenged planetary slag.",
            10f
        );

        tooltip.addPara("Generates a friendly autonomous %s composed of %s, scaling in strength with colony size and the market's fleet size.",
            10f,
            Misc.getPositiveHighlightColor(),
            "Medium Patrol",
            "Remnant combat hulls"
        );

        tooltip.addPara("Colony stability reduced by %s due to pervasive autonomous signals and erratic AI subroutines.",
            10f,
            Misc.getNegativeHighlightColor(),
            "" + STABILITY_PENALTY
        );

        if (market != null && !market.isPlanetConditionMarketOnly() && market.getSize() >= 3) {
            int pts = Math.round(getCombatPoints());
            MemoryAPI mem = market.getMemoryWithoutUpdate();
            float cycle = mem.getFloat(getCooldownTimerKey());
            int remaining = Math.max(1, Math.round(RESPAWN_DELAY_DAYS - cycle));

            if (mem.getBoolean(getDeadKey())) {
                tooltip.addPara("Fabrication nexus status: %s (reconstructing destroyed flotilla, ~%s days remaining).",
                    10f,
                    Misc.getNegativeHighlightColor(),
                    "Slipways offline",
                    "" + remaining
                );
            } else {
                tooltip.addPara("Current fabrication output: ~%s combat points (Size %s).",
                    10f,
                    Misc.getHighlightColor(),
                    "" + pts, "" + market.getSize()
                );
            }
        } else {
            tooltip.addPara("Fabrication nexus will activate once a colony of size 3 or greater is established.", 10f, Misc.getGrayColor());
        }

        tooltip.addPara("Every " + (int) RESPAWN_DELAY_DAYS + " days the slipways review the active flotilla and refit it if the colony has grown or its fleet size has risen. A destroyed flotilla takes the same " + (int) RESPAWN_DELAY_DAYS + " days to rebuild.", 10f, Misc.getGrayColor());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}