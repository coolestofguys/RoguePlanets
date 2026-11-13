package roguePlanets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import roguePlanets.econ.conditions.TrashKingCondition;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class RoguePlanetsModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(RoguePlanetsModPlugin.class);

    // ==========================================
    // 1. ROGUE PLANETS TUNABLES & DATA
    // ==========================================
    private static final String ROGUE_SPAWNED_FLAG = "$roguePlanets_spawnedV3";
    private static final float ONE_IN_N_PLANETS = 30f;
    private static final boolean GUARANTEE_IN_START_SYSTEM = false;
    private static final float RESOURCE_CHANCE = 0.5f;
    private static final float HABITABLE_CHANCE = 0.5f;
    private static final float TECTONIC_CHANCE = 0.5f;
    private static final float MILD_CLIMATE_CHANCE = 0.5f;
    private static final String ROGUE_PLANET_TYPE = "nocturnal";
    private static final float MIN_GAP = 1200f;
    private static final float MAX_GAP = 2500f;

    private static final String COND_DARKNESS = "dark";
    private static final String COND_MILD_CLIMATE = "mild_climate";
    private static final String COND_HABITABLE = "habitable";
    private static final String COND_EXTREME_TECTONICS = "extreme_tectonic_activity";
    private static final String COND_POOR_FARMLAND = "farmland_poor";

    // Each resource has a RESOURCE_CHANCE (50%) chance to appear, then an even pick of tier.
    // Ore and rare ore: -1 to +3 (10% each overall). Organics and volatiles: -1 to +2 (12.5% each overall).
    // Farmland is not rolled here: Habitable Nocturnal worlds always get Poor Farmland, others never do.
    private static final String[][] ROGUE_RESOURCES = {
        {"ore_sparse", "ore_moderate", "ore_abundant", "ore_rich", "ore_ultrarich"},
        {"rare_ore_sparse", "rare_ore_moderate", "rare_ore_abundant", "rare_ore_rich", "rare_ore_ultrarich"},
        {"organics_trace", "organics_common", "organics_abundant", "organics_plentiful"},
        {"volatiles_trace", "volatiles_diffuse", "volatiles_abundant", "volatiles_plentiful"}
    };

    private static final String[] ROGUE_NAMES = {
        "Drifter", "Wanderer", "Orphan", "Exile", "Vagrant", "Outcast", "Stray", "Nomad"
    };

    // ==========================================
    // 2. PERSISTENT SECTOR MEMORY FLAGS
    // ==========================================
    public static final String EDEN_GENERATED_FLAG = "$eden_worlds_generated";
    public static final String HADEAN_GENERATED_FLAG = "$hadean_worlds_generated";
    public static final String CRUST_GENERATED_FLAG = "$supercritical_crust_generated";
    public static final String DEGENERATE_VOLATILES_FLAG = "$degenerate_volatiles_generated";
    public static final String LEYLINES_GENERATED_FLAG = "$geothermal_leylines_generated";
    public static final String DIAMOND_CORE_FLAG = "$diamond_core_generated_v5";
    public static final String AUTOMATED_SHIPYARDS_FLAG = "$automated_shipyards_generated";
    public static final String HYDROCARBON_GIANT_FLAG = "$hydrocarbon_giant_generated_v3";
    public static final String VAULT_WORLD_FLAG = "$forgotten_vault_world_generated";
    public static final String ANOMALY_FLAG = "$antimatter_anomaly_generated";
    public static final String HARVESTING_SYNDICATE_FLAG = "$clandestine_harvesting_syndicate_generated";
    public static final String MEGALITHIC_OUTCROPS_FLAG = "$megalithic_outcrops_generated";
    public static final String ZONING_GENERATED_FLAG = "$geographically_efficient_zoning_generated";
    public static final String RADIOLYTIC_CATALYST_FLAG = "$radiolytic_catalyst_generated";
    public static final String CHRONO_DILATION_FLAG = "$chrono_spatial_dilation_generated";
    public static final String RAPID_GEOMAGNETIC_INVERSION_FLAG = "$rapid_geomagnetic_inversion_generated";
    public static final String HYDROPONIC_MATRIX_FLAG = "$closed_loop_hydroponics_generated";
    public static final String CRYO_SINKS_FLAG = "$supercooled_cryo_sinks_generated";
    public static final String HOLLOWED_CALDERA_FLAG = "$hollowed_caldera_garrisons_generated";
    public static final String VACATION_RESORT_FLAG = "$vacation_paradise_resort_generated";
    public static final String SMUGGLERS_HAVEN_FLAG = "$smugglers_haven_generated";
    public static final String COLD_SKY_FLAG = "$cold_sky_observatory_generated_v3";
    public static final String GROUND_MISSILE_FLAG = "$ground_missile_defense_generated";
    public static final String SIGNAL_TRAP_FLAG = "$resonant_signal_trap_generated";
    public static final String SHIP_GRAVEYARD_FLAG = "$ship_graveyard_generated";
    public static final String PILGRIM_WORLD_FLAG = "$pilgrim_world_generated";
    public static final String TRASH_KING_FLAG = "$trash_king_generated";
    public static final String VIOLET_STARS_FLAG = "$violet_dwarf_system_generated";

    public static final String[] ALL_CUSTOM_CONDITIONS = {
        "fragile_biosphere",
        "supercritical_crust",
        "degenerate_volatiles",
        "geothermal_leylines",
        "diamond_core",
        "automated_shipyards",
        "hydrocarbon_fractionation",
        "forgotten_vault_world",
        "antimatter_anomaly",
        "clandestine_harvesting_syndicate",
        "megalithic_outcrops",
        "geographically_efficient_zoning",
        "radiolytic_catalyst",
        "chrono_spatial_dilation",
        "rapid_geomagnetic_inversion",
        "closed_loop_hydroponics",
        "supercooled_cryo_sinks",
        "magmatic_antimatter_forge",
        "hollowed_caldera_garrisons",
        "vacation_paradise_resort",
        "smugglers_haven",
        "watched",
        "volatile_additive_reactor",
        "viable_slave_labor",
        "cold_sky_observatory",
        "ground_missile_defense",
        "resonant_signal_trap",
        "ship_graveyard",
        "pilgrim_world",
        "trash_king"
    };

    // ==========================================
    // LIFECYCLE HOOKS
    // ==========================================

    @Override
    public void onNewGameAfterProcGen() {
        super.onNewGameAfterProcGen();
        inNewGameProcgen = true;
        try {
            generateAllCustomContent();
        } finally {
            inNewGameProcgen = false;
        }
        syncCustomConditionSurveyFlags();
        registerScripts();
    }

    /** Other mods can change a world's atmosphere after generation, so re-check the reactor worlds once, at game creation. */
    @Override
    public void onNewGameAfterEconomyLoad() {
        super.onNewGameAfterEconomyLoad();
        syncCustomConditionSurveyFlags();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.hasCondition("volatile_additive_reactor")) continue;
            if (!market.hasCondition(Conditions.NO_ATMOSPHERE) || market.hasCondition(Conditions.THIN_ATMOSPHERE)) {
                market.removeCondition("volatile_additive_reactor");
                log.info("Volatile Additive Reactor removed from [" + market.getName() + "]: it is no longer airless.");
            }
        }
    }

    public static final String ICE_LOCKED_SEAS = "ice_locked_seas";

    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();
        // Snowball worlds: Aquaculture can be built on them, using Ice-Locked Seas as the food deposit at -1
        com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.add("snowball");
        com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.COMMODITY.put(ICE_LOCKED_SEAS,
                com.fs.starfarer.api.impl.campaign.ids.Commodities.FOOD);
        com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.MODIFIER.put(ICE_LOCKED_SEAS, -1);
    }

    /** Every Snowball world gets Ice-Locked Seas. Safe to run on every load (only adds where missing). */
    private void addIceLockedSeas() {
        SectorAPI sector = Global.getSector();
        if (sector == null) return;
        int added = 0;
        for (StarSystemAPI system : sector.getStarSystems()) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (!"snowball".equals(planet.getTypeId())) continue;
                MarketAPI market = planet.getMarket();
                if (market == null || market.hasCondition(ICE_LOCKED_SEAS)) continue;
                market.addCondition(ICE_LOCKED_SEAS);
                MarketConditionAPI mc = market.getCondition(ICE_LOCKED_SEAS);
                if (mc != null) mc.setSurveyed(market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL);
                added++;
            }
        }
        if (added > 0) log.info("Ice-Locked Seas added to " + added + " Snowball worlds.");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);
        generateAllCustomContent();
        addIceLockedSeas();
        generateViableSlaveLabor();
        syncCustomConditionSurveyFlags();
        registerScripts();
    }

    private void registerScripts() {
        if (Global.getSector() != null) {
            Global.getSector().unregisterPlugin(BlackoutCampaignPlugin.ID);
            Global.getSector().registerPlugin(new BlackoutCampaignPlugin());
        }
        if (Global.getSector() != null && !Global.getSector().hasScript(VioletSystemScript.class)) {
            Global.getSector().addTransientScript(new VioletSystemScript());
            Global.getSector().addTransientListener(new PhantomListener());
        }
        if (Global.getSector() != null && !Global.getSector().hasScript(ShipGraveyardScript.class)) {
            ShipGraveyardScript graveyard = new ShipGraveyardScript();
            Global.getSector().addTransientScript(graveyard);
            Global.getSector().addTransientListener(graveyard);
        }
        if (Global.getSector() != null && !Global.getSector().hasScript(AdditiveReactorScript.class)) {
            Global.getSector().addTransientScript(new AdditiveReactorScript());
        }
        if (Global.getSector() != null && !Global.getSector().hasScript(AfterlightCaptainScript.class)) {
            AfterlightCaptainScript afterlight = new AfterlightCaptainScript();
            Global.getSector().addTransientScript(afterlight);
            Global.getSector().addTransientListener(afterlight);
        }
        if (Global.getSector() != null && !Global.getSector().hasScript(roguePlanets.story.AfterlightStoryScript.class)) {
            roguePlanets.story.AfterlightStoryScript story = new roguePlanets.story.AfterlightStoryScript();
            Global.getSector().addTransientScript(story);
            Global.getSector().addTransientListener(story);
        }
        if (Global.getSector() != null) {
            Global.getSector().getListenerManager().addListener(new roguePlanets.story.ReflectionListener(), true);
        }
        if (Global.getSector() != null) {
            roguePlanets.story.GlitchJobs.register();
            roguePlanets.story.Strangeness.register();
        }
    }

    private void generateAllCustomContent() {
        generateRoguePlanets();
        generateEdenWorlds();
        generateHadeanWorlds();
        generateSunderedWorlds();     // before the condition scatters (adds Meteor Impacts for Ship Graveyard)
        generateNewPlanetTypes();     // Snowball / Halite / Chthonian / Sub-Brown Dwarf, 1-4 of each
        generateSupercriticalCrust();
        generateDegenerateVolatiles();
        generateGeothermalLeylines();
        generateDiamondCore();
        generateAutomatedShipyards();
        generateHydrocarbonGiants();
        generateForgottenVaultWorlds();
        generateAntimatterAnomaly();
        generateClandestineHarvestingSyndicate();
        generateMegalithicOutcrops();
        generateGeographicallyEfficientZoning();
        generateRadiolyticCatalyst();
        generateChronoSpatialDilation();
        generateRapidGeomagneticInversion();
        generateClosedLoopHydroponics();
        generateSupercooledCryoSinks();
        generateHollowedCalderaGarrisons();
        generateVacationParadiseResort();
        generateSmugglersHaven();
        generateVolatileAdditiveReactor();
        generateColdSkyObservatory();
        generateGroundMissileDefense();
        generateResonantSignalTrap();
        generateShipGraveyard();
        generatePilgrimWorld();
        generateTrashKing();
        generateVioletStars();
    }

    /**
     * Ensures all custom conditions have proper visibility and match the planet's survey state
     * so they render on the system map and Intel Planet List screens.
     */
    private MarketConditionAPI addCustomCondition(MarketAPI market, String conditionId) {
        if (market == null) return null;
        if (!market.hasCondition(conditionId)) {
            market.addCondition(conditionId);
        }
        MarketConditionAPI mc = market.getCondition(conditionId);
        if (mc != null) {
            boolean isFullySurveyed = (market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL);
            mc.setSurveyed(isFullySurveyed);
        }
        return mc;
    }

    /**
     * Sweeps the Sector upon game load to ensure condition survey visibility matches planet survey progress.
     */
    private void syncCustomConditionSurveyFlags() {
        SectorAPI sector = Global.getSector();
        if (sector == null) return;

        for (StarSystemAPI system : sector.getStarSystems()) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar()) continue;
                MarketAPI market = planet.getMarket();
                if (market == null) continue;

                boolean isFullySurveyed = (market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL);

                for (String condId : ALL_CUSTOM_CONDITIONS) {
                    if (market.hasCondition(condId)) {
                        MarketConditionAPI mc = market.getCondition(condId);
                        if (mc != null) {
                            mc.setSurveyed(isFullySurveyed);
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // MODULE: VIOLET DWARF STAR SYSTEM
    // ==========================================

    public static final String VIOLET_STAR_TYPE = "star_violet";
    /** How many Violet Dwarf systems are guaranteed to be generated once per save. */
    private static final int VIOLET_SYSTEM_COUNT = 1;

    private void generateVioletStars() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().getBoolean(VIOLET_STARS_FLAG)) return;
        sector.getMemoryWithoutUpdate().set(VIOLET_STARS_FLAG, true);

        Random rand = new Random(sector.getSeedString().hashCode() ^ 0x71017L);

        for (int i = 0; i < VIOLET_SYSTEM_COUNT; i++) {
            try {
                Vector2f loc = pickEmptyHyperspaceSpot(rand);

                StarSystemGenerator.CustomConstellationParams params =
                        new StarSystemGenerator.CustomConstellationParams(StarAge.OLD);
                params.numStars = 1;
                params.starTypes.add(VIOLET_STAR_TYPE);
                params.systemTypes.add(StarSystemGenerator.StarSystemType.SINGLE);
                params.location = loc;

                StarSystemGenerator gen = new StarSystemGenerator(params);
                Constellation constellation = gen.generate();

                for (StarSystemAPI system : constellation.getSystems()) {
                    // Nexerelin's own flag: AI factions will not send colony expeditions here
                    system.getMemoryWithoutUpdate().set("$nex_do_not_colonize", true);
                    try {
                        addVioletSystemFeatures(system, rand);
                    } catch (Exception e) {
                        log.error("Failed to add rings/belt to Violet Dwarf system", e);
                    }
                    log.info("Rogue Planets: generated Violet Dwarf system [" + system.getName() + "] at "
                            + system.getLocation() + " (star type " + (system.getStar() != null
                            ? system.getStar().getTypeId() : "none") + ")");
                }
            } catch (Exception e) {
                log.error("Failed to generate Violet Dwarf system", e);
            }
        }
    }

    /** Adds a violet ring system around the star and an asteroid belt not far outside it. */
    private void addVioletSystemFeatures(StarSystemAPI system, Random rand) {
        PlanetAPI star = system.getStar();
        if (star == null) return;

        float ringBand = 256f;
        float ringSpacing = 120f;
        int ringCount = 2;
        float ringTotal = ringCount * ringBand + (ringCount - 1) * ringSpacing;
        float ringInner = VioletSystemContent.freeOrbit(system, star, star.getRadius() + 500f, ringTotal);
        java.awt.Color ringColor = new java.awt.Color(190, 130, 255, 255);

        for (int i = 0; i < ringCount; i++) {
            float mid = ringInner + ringBand / 2f + i * (ringBand + ringSpacing);
            float days = mid / (15f + 5f * rand.nextFloat());
            // Only the first band adds the terrain; the rest are visuals layered next to it.
            if (i == 0) {
                system.addRingBand(star, "misc", "rings_dust0", 256f, i, ringColor, ringBand, mid, days,
                        Terrain.RING, "Amethyst Rings");
            } else {
                system.addRingBand(star, "misc", "rings_dust0", 256f, i + 1, ringColor, ringBand, mid, days,
                        Terrain.RING, null);
            }
        }

        float beltWidth = 500f;
        float beltRadius = VioletSystemContent.freeOrbit(system, star, ringInner + ringTotal + 1800f, beltWidth);
        float beltMid = beltRadius + beltWidth / 2f;
        system.addAsteroidBelt(star, 150, beltMid, beltWidth, 200f, 320f, Terrain.ASTEROID_BELT, "Violet Belt");
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 0, new java.awt.Color(200, 170, 255, 255),
                beltWidth, beltMid, 260f);

        // The wrong-way world, and the rest of the system's strangeness.
        PlanetAPI watched = addRoguePlanet(system, rand, 99);
        watched.setName(system.getBaseName() + " Contrary");
        float wr = Misc.getDistance(system.getCenter(), watched);
        watched.setCircularOrbit(system.getCenter(), rand.nextFloat() * 360f, wr, -(wr / 22f)); // retrograde
        addCustomCondition(watched.getMarket(), "watched");
        VioletSystemContent.addFeatures(system, star, watched, ringInner, ringTotal, rand);

        log.info("Rogue Planets: added rings (inner " + (int) ringInner + ") and asteroid belt (" + (int) beltMid
                + ") to [" + system.getName() + "].");
    }

    /**
     * Picks a hyperspace spot in the outer core of the sector: roughly 60-80% of the way from the middle to the edge
     * of the map, measured on an ellipse that matches the sector's real width and height (the map is wider than it
     * is tall), and never near the far fringe. Stray far-away systems added by other mods are ignored when working
     * out the size of the map. It only keeps a small gap so it never lands on top of another star.
     */
    private Vector2f pickEmptyHyperspaceSpot(Random rand) {
        java.util.List<StarSystemAPI> all = Global.getSector().getStarSystems();
        if (all.isEmpty()) return new Vector2f(0f, 0f);

        float sx = 0f, sy = 0f;
        for (StarSystemAPI s : all) { sx += s.getLocation().x; sy += s.getLocation().y; }
        float cx = sx / all.size(), cy = sy / all.size();

        // Half-width and half-height of the map: the 90th percentile spread, so far outliers don't stretch it.
        java.util.List<Float> dxs = new java.util.ArrayList<Float>();
        java.util.List<Float> dys = new java.util.ArrayList<Float>();
        for (StarSystemAPI s : all) {
            dxs.add(Math.abs(s.getLocation().x - cx));
            dys.add(Math.abs(s.getLocation().y - cy));
        }
        java.util.Collections.sort(dxs);
        java.util.Collections.sort(dys);
        int idx = Math.min(all.size() - 1, (int) (all.size() * 0.9f));
        float halfW = Math.max(dxs.get(idx), 4000f);
        float halfH = Math.max(dys.get(idx), 4000f);

        Vector2f best = new Vector2f(cx + halfW * 0.7f, cy);
        float bestGap = -1f;
        for (int attempt = 0; attempt < 400; attempt++) {
            float t = 0.6f + 0.2f * rand.nextFloat();
            float angle = rand.nextFloat() * (float) (Math.PI * 2.0);
            Vector2f cand = new Vector2f(cx + (float) Math.cos(angle) * halfW * t,
                                         cy + (float) Math.sin(angle) * halfH * t);
            float nearest = Float.MAX_VALUE;
            for (StarSystemAPI s : all) {
                nearest = Math.min(nearest, Misc.getDistance(cand, s.getLocation()));
            }
            if (nearest > bestGap) {
                bestGap = nearest;
                best = cand;
            }
            if (nearest >= 1500f) break;
        }
        log.info("Rogue Planets: Violet Dwarf placed at " + best + " (map half-size " + (int) halfW + " x " + (int) halfH
                + ", centre " + (int) cx + "," + (int) cy + ", gap " + (int) bestGap + ").");
        return best;
    }

    // ==========================================
    // MODULE 1: NOCTURNAL / ROGUE PLANETS
    // ==========================================

    private void generateRoguePlanets() {
        SectorAPI sector = Global.getSector();
        if (sector == null) return;

        MemoryAPI mem = sector.getMemoryWithoutUpdate();
        if (mem.getBoolean(ROGUE_SPAWNED_FLAG)) return;
        mem.set(ROGUE_SPAWNED_FLAG, true);

        Random rand = new Random();
        StarSystemAPI startSystem = GUARANTEE_IN_START_SYSTEM ? findStartSystem() : null;
        if (GUARANTEE_IN_START_SYSTEM && startSystem == null) {
            log.warn("Rogue Planets: could not find a starting system with a star; no guaranteed planet added.");
        }

        int eligible = 0;
        int spawned = 0;

        for (StarSystemAPI system : sector.getStarSystems()) {
            boolean isStart = (system == startSystem);

            if (!isStart) {
                if (!isEligibleRogueSystem(system)) continue;
                eligible++;

                int planetCount = countPlanets(system);
                if (planetCount <= 0) continue;

                double chance = 1.0 - Math.pow(1.0 - 1.0 / ONE_IN_N_PLANETS, planetCount);
                if (rand.nextDouble() >= chance) continue;
            }

            try {
                PlanetAPI planet = addRoguePlanet(system, rand, spawned);
                spawned++;
                log.info("Rogue Planets: added [" + planet.getName() + "] in system [" + system.getName() + "]"
                        + (isStart ? " (guaranteed start-system planet)" : ""));
            } catch (Exception e) {
                log.error("Failed to add rogue planet to " + system.getName(), e);
            }
        }

        log.info("Rogue Planets: added " + spawned + " rogue planets (" + eligible + " eligible systems rolled).");
    }

    private StarSystemAPI findStartSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        LocationAPI loc = player.getContainingLocation();
        if (loc instanceof StarSystemAPI && ((StarSystemAPI) loc).getStar() != null) {
            return (StarSystemAPI) loc;
        }

        Vector2f pos = player.getLocationInHyperspace();
        StarSystemAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s.getStar() == null) continue;
            float d = Misc.getDistance(pos, s.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    private boolean isEligibleRogueSystem(StarSystemAPI system) {
        if (system.getStar() == null) return false;
        if (system.hasTag("theme_core_populated") || system.hasTag(Tags.THEME_CORE)) return false;

        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (!market.isPlanetConditionMarketOnly() && market.getSize() > 0) {
                return false;
            }
        }
        return true;
    }

    private int countPlanets(StarSystemAPI system) {
        int count = 0;
        for (PlanetAPI p : system.getPlanets()) {
            if (!p.isStar()) count++;
        }
        return count;
    }

    private PlanetAPI addRoguePlanet(StarSystemAPI system, Random rand, int index) {
        float maxDist = 0f;
        for (PlanetAPI p : system.getPlanets()) {
            if (p.isStar()) continue;
            float d = Misc.getDistance(system.getCenter(), p);
            if (d > maxDist) maxDist = d;
        }
        if (maxDist <= 0f) maxDist = 3000f;

        float orbitRadius = maxDist + MIN_GAP + rand.nextFloat() * (MAX_GAP - MIN_GAP);
        float orbitDays = orbitRadius / (20f + rand.nextFloat() * 5f);
        float radius = 120f + rand.nextFloat() * 50f;

        String name = system.getBaseName() + " " + ROGUE_NAMES[rand.nextInt(ROGUE_NAMES.length)];

        PlanetAPI planet = system.addPlanet(
                "roguePlanets_" + system.getId() + "_" + index,
                system.getCenter(), name, ROGUE_PLANET_TYPE,
                rand.nextFloat() * 360f, radius, orbitRadius, orbitDays);

        MarketAPI market = Global.getFactory().createMarket(
                planet.getId() + "_market", planet.getName(), 0);
        market.setPlanetConditionMarketOnly(true);
        market.setPrimaryEntity(planet);
        market.setFactionId(Factions.NEUTRAL);

        addRogueCondition(market, COND_DARKNESS);                                            // 100%
        if (rand.nextFloat() < TECTONIC_CHANCE) addRogueCondition(market, COND_EXTREME_TECTONICS); // 50%
        if (rand.nextFloat() < MILD_CLIMATE_CHANCE) addRogueCondition(market, COND_MILD_CLIMATE);   // 50%
        if (rand.nextFloat() < HABITABLE_CHANCE) {                                                  // 50%
            addRogueCondition(market, COND_HABITABLE);
            addRogueCondition(market, COND_POOR_FARMLAND); // habitable worlds always have Poor Farmland
        }

        for (String[] tiers : ROGUE_RESOURCES) {
            if (rand.nextFloat() < RESOURCE_CHANCE) {
                addRogueCondition(market, tiers[rand.nextInt(tiers.length)]);
            }
        }

        market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
        planet.setMarket(market);
        return planet;
    }

    private void addRogueCondition(MarketAPI market, String id) {
        try {
            market.addCondition(id);
        } catch (Exception e) {
            log.error("Could not add condition: " + id, e);
        }
    }

    // ==========================================
    // MODULE 2: EDEN WORLDS
    // ==========================================

    private void generateEdenWorlds() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(EDEN_GENERATED_FLAG)) {
            return;
        }

        WeightedRandomPicker<StarSystemAPI> eligibleSystems = new WeightedRandomPicker<StarSystemAPI>();

        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system)) {
                continue;
            }

            PlanetAPI star = system.getStar();
            if (star == null) continue;

            String type = star.getTypeId();
            if (type == null) continue;

            boolean isTerranStar = StarTypes.YELLOW.equals(type) ||
                                  StarTypes.ORANGE.equals(type) ||
                                  StarTypes.ORANGE_GIANT.equals(type) ||
                                  type.contains("yellow") ||
                                  type.contains("orange");

            if (isTerranStar) {
                float weight = 1.0f;
                if (system.getAge() == StarAge.AVERAGE) weight = 2.0f;
                if (system.getAge() == StarAge.YOUNG) weight = 1.5f;
                eligibleSystems.add(system, weight);
            }
        }

        if (eligibleSystems.isEmpty()) return;

        long seed = (long) sector.getSeedString().hashCode() ^ 0xEDA1L;
        Random random = new Random(seed);
        int worldsToSpawn = 1; // Guaranteed 1 spawn, maximum 1 spawn

        for (int i = 0; i < worldsToSpawn; i++) {
            if (eligibleSystems.isEmpty()) break;
            StarSystemAPI selectedSystem = eligibleSystems.pickAndRemove();
            spawnProceduralEdenWorld(selectedSystem, i + 1, random);
        }

        sector.getMemoryWithoutUpdate().set(EDEN_GENERATED_FLAG, true);
    }

    private void spawnProceduralEdenWorld(StarSystemAPI system, int index, Random random) {
        PlanetAPI star = system.getStar();
        if (star == null) return;

        float planetSize = 190f + random.nextFloat() * 50f;
        float orbitRadius = pickEdenOrbitRadius(system, star, planetSize, random);
        float orbitDays = orbitRadius / 15f + random.nextFloat() * 30f;
        float startAngle = random.nextFloat() * 360f;

        String planetId = "eden_procgen_" + system.getId() + "_" + index;
        String planetName = pickEdenName(system);

        PlanetAPI eden = system.addPlanet(
            planetId,
            star,
            planetName,
            "eden_world",
            startAngle,
            planetSize,
            orbitRadius,
            orbitDays
        );

        if (eden != null) {
            MarketAPI market = eden.getMarket();
            boolean createdMarket = false;
            if (market == null) {
                market = Global.getFactory().createMarket(planetId + "_market", planetName, 0);
                market.setPlanetConditionMarketOnly(true);
                market.setPrimaryEntity(eden);
                market.setFactionId(Factions.NEUTRAL);
                createdMarket = true;
            }

            market.addCondition(Conditions.HABITABLE);
            addCustomCondition(market, "fragile_biosphere");
            market.addCondition(Conditions.FARMLAND_BOUNTIFUL);
            market.addCondition(Conditions.ORGANICS_ABUNDANT);
            market.addCondition(Conditions.MILD_CLIMATE);

            if (createdMarket) {
                market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
                eden.setMarket(market);
            }

            log.info("Eden world spawned: [" + planetName + "] in Star System: [" + system.getName() + "]");
            if (Global.getSector().getCampaignUI() != null) {
                Global.getSector().getCampaignUI().addMessage(
                    "Eden world generated in frontier star system: " + system.getName() + " (" + planetName + ")",
                    Misc.getPositiveHighlightColor()
                );
            }
        }
    }

    // Picks an orbit in the usual 3200-5400 band that does not overlap any existing planet's orbit
    private float pickEdenOrbitRadius(StarSystemAPI system, PlanetAPI star, float planetSize, Random random) {
        float outermost = 0f;
        for (PlanetAPI p : system.getPlanets()) {
            if (p == star || p.getOrbitFocus() != star) continue;
            float edge = p.getCircularOrbitRadius() + p.getRadius();
            if (edge > outermost) outermost = edge;
        }

        for (int attempt = 0; attempt < 15; attempt++) {
            float candidate = 3200f + random.nextFloat() * 2200f + attempt * 150f;
            boolean clear = true;
            for (PlanetAPI p : system.getPlanets()) {
                if (p == star || p.getOrbitFocus() != star) continue;
                float gap = Math.abs(p.getCircularOrbitRadius() - candidate);
                if (gap < p.getRadius() + planetSize + 300f) {
                    clear = false;
                    break;
                }
            }
            if (clear) return candidate;
        }

        // Crowded system: place it beyond the outermost planet
        return Math.max(3200f, outermost + planetSize + 500f);
    }

    // "<System> III" first, then II, IV ... VII, skipping names already used in the system
    private String pickEdenName(StarSystemAPI system) {
        int[] order = {3, 2, 4, 5, 6, 7};
        for (int n : order) {
            String candidate = system.getBaseName() + " " + RomanNumeral(n);
            boolean taken = false;
            for (PlanetAPI p : system.getPlanets()) {
                if (candidate.equals(p.getName())) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return candidate;
        }
        return system.getBaseName() + " Eden";
    }

    // ==========================================
    // MODULE 2B: HADEAN WORLDS
    // ==========================================

    // Hadean worlds are about 3x the radius of a normal terran/Eden world (those are roughly 190-240).
    // Change these two numbers to make them bigger or smaller.
    private static final float HADEAN_MIN_RADIUS = 370f;
    private static final float HADEAN_MAX_RADIUS = 500f;

    private void generateHadeanWorlds() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(HADEAN_GENERATED_FLAG)) {
            return;
        }

        WeightedRandomPicker<StarSystemAPI> eligibleSystems = new WeightedRandomPicker<StarSystemAPI>();

        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system)) continue;

            // Starless systems (nebulas) cannot host a world on a proper orbit
            if (system.getStar() == null) continue;

            // Systems that already contain a lava/volcanic world are more likely to be picked
            float weight = 1.0f;
            for (PlanetAPI p : system.getPlanets()) {
                if (p.isStar()) continue;
                String t = p.getTypeId();
                if (t != null && (t.contains("lava") || t.contains("volcanic"))) {
                    weight = 3.0f;
                    break;
                }
            }
            eligibleSystems.add(system, weight);
        }

        if (eligibleSystems.isEmpty()) return;

        long seed = (long) sector.getSeedString().hashCode() ^ 0x4ADEA5L;
        Random random = new Random(seed);
        // Always exactly one Hadean world per sector
        int worldsToSpawn = 1; // Guaranteed 1 spawn, maximum 1 spawn

        for (int i = 0; i < worldsToSpawn; i++) {
            if (eligibleSystems.isEmpty()) break;
            StarSystemAPI selectedSystem = eligibleSystems.pickAndRemove();
            spawnProceduralHadeanWorld(selectedSystem, random);
        }

        sector.getMemoryWithoutUpdate().set(HADEAN_GENERATED_FLAG, true);
    }

    private void spawnProceduralHadeanWorld(StarSystemAPI system, Random random) {
        PlanetAPI star = system.getStar();
        if (star == null) return;

        float planetSize = HADEAN_MIN_RADIUS + random.nextFloat() * (HADEAN_MAX_RADIUS - HADEAN_MIN_RADIUS);
        float orbitRadius = pickHadeanOrbitRadius(system, star, planetSize, random);
        float orbitDays = orbitRadius / 15f + random.nextFloat() * 30f;
        float startAngle = random.nextFloat() * 360f;

        String planetId = "hadean_procgen_" + system.getId();
        String planetName = pickHadeanName(system);

        PlanetAPI hadean = system.addPlanet(
            planetId,
            star,
            planetName,
            "hadean_world",
            startAngle,
            planetSize,
            orbitRadius,
            orbitDays
        );

        if (hadean != null) {
            MarketAPI market = hadean.getMarket();
            boolean createdMarket = false;
            if (market == null) {
                market = Global.getFactory().createMarket(planetId + "_market", planetName, 0);
                market.setPlanetConditionMarketOnly(true);
                market.setPrimaryEntity(hadean);
                market.setFactionId(Factions.NEUTRAL);
                createdMarket = true;
            }

            // Forced hazards
            market.addCondition(Conditions.TOXIC_ATMOSPHERE);
            market.addCondition(Conditions.VERY_HOT); // shown in-game as "Extreme Heat"
            market.addCondition(Conditions.EXTREME_TECTONIC_ACTIVITY);
            market.addCondition(Conditions.DARK);
            market.addCondition(Conditions.IRRADIATED);
            market.addCondition(Conditions.METEOR_IMPACTS);
            market.addCondition(Conditions.HIGH_GRAVITY);
            market.addCondition(Conditions.EXTREME_WEATHER);

            // Unique Hadean Wonder Condition
            addCustomCondition(market, "magmatic_antimatter_forge");

            // Random resource quality, sparse through ultrarich
            String[] oreLevels = {
                Conditions.ORE_SPARSE, Conditions.ORE_MODERATE, Conditions.ORE_ABUNDANT,
                Conditions.ORE_RICH, Conditions.ORE_ULTRARICH
            };
            String[] rareOreLevels = {
                Conditions.RARE_ORE_SPARSE, Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_ABUNDANT,
                Conditions.RARE_ORE_RICH, Conditions.RARE_ORE_ULTRARICH
            };
            market.addCondition(oreLevels[random.nextInt(oreLevels.length)]);
            market.addCondition(rareOreLevels[random.nextInt(rareOreLevels.length)]);

            if (createdMarket) {
                market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
                hadean.setMarket(market);
            }

            log.info("Hadean world spawned: [" + planetName + "] in Star System: [" + system.getName() + "]");
            if (Global.getSector().getCampaignUI() != null) {
                Global.getSector().getCampaignUI().addMessage(
                    "Hadean world generated in frontier star system: " + system.getName() + " (" + planetName + ")",
                    Misc.getNegativeHighlightColor()
                );
            }
        }
    }

    // Picks an orbit close to the star that does not overlap any existing planet's orbit
    private float pickHadeanOrbitRadius(StarSystemAPI system, PlanetAPI star, float planetSize, Random random) {
        float minOrbit = star.getRadius() + planetSize + 900f;
        float outermost = minOrbit;

        for (PlanetAPI p : system.getPlanets()) {
            if (p == star || p.getOrbitFocus() != star) continue;
            float edge = p.getCircularOrbitRadius() + p.getRadius();
            if (edge > outermost) outermost = edge;
        }

        for (int attempt = 0; attempt < 15; attempt++) {
            float candidate = minOrbit + random.nextFloat() * 1800f + attempt * 150f;
            boolean clear = true;
            for (PlanetAPI p : system.getPlanets()) {
                if (p == star || p.getOrbitFocus() != star) continue;
                float gap = Math.abs(p.getCircularOrbitRadius() - candidate);
                if (gap < p.getRadius() + planetSize + 300f) {
                    clear = false;
                    break;
                }
            }
            if (clear) return candidate;
        }

        // Crowded system: place it beyond the outermost planet
        return outermost + planetSize + 500f;
    }

    // "<System> II" ... "<System> VII", skipping names already used in the system
    private String pickHadeanName(StarSystemAPI system) {
        for (int n = 2; n <= 7; n++) {
            String candidate = system.getBaseName() + " " + RomanNumeral(n);
            boolean taken = false;
            for (PlanetAPI p : system.getPlanets()) {
                if (candidate.equals(p.getName())) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return candidate;
        }
        return system.getBaseName() + " Hadean";
    }


    // ==========================================
    // MODULES 3-7 AND 10-21: CONDITIONS SCATTERED ACROSS WORLDS
    // ==========================================
    // Each of these follows the same recipe: once per save, walk every frontier world, ask a rule how likely the
    // world is to get the condition (0 = not eligible), roll the dice and add it. To add another condition like
    // this, write one small rule below and call scatterCondition() from generateAllCustomContent().

    private interface SpawnRule {
        /** Chance (0 to 1) that this world gets the condition, or 0 if it is not eligible. lowerType is "" when type is null. */
        float chance(StarSystemAPI system, PlanetAPI planet, MarketAPI market, String type, String lowerType);
    }

    private void scatterCondition(String conditionId, String label, String flag, long seedXor,
                                  boolean unclaimedOnly, SpawnRule rule, Consumer<MarketAPI> beforeAdd) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(flag)) {
            return;
        }

        Random random = new Random((long) sector.getSeedString().hashCode() ^ seedXor);
        int count = 0;

        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system)) continue;

            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar()) continue;

                MarketAPI market = planet.getMarket();
                if (market == null) continue;
                if (unclaimedOnly && !isUnclaimed(market)) continue;

                String type = planet.getTypeId();
                float chance = rule.chance(system, planet, market, type, type == null ? "" : type.toLowerCase());
                if (chance <= 0f || random.nextFloat() >= chance) continue;
                if (market.hasCondition(conditionId)) continue;

                if (beforeAdd != null) beforeAdd.accept(market);
                addCustomCondition(market, conditionId);
                count++;
                log.info(label + " added to [" + planet.getName() + "] in [" + system.getName() + "]");
            }
        }

        sector.getMemoryWithoutUpdate().set(flag, true);
        log.info("=== " + label + " generation complete. Placed on " + count + " worlds. ===");
    }

    /** True for worlds nobody has colonized or claimed (condition-only markets or neutral-faction markets). */
    private boolean isUnclaimed(MarketAPI market) {
        if (!market.isPlanetConditionMarketOnly() && market.getSize() > 0) return false;
        return market.getFaction() == null || market.getFaction().isNeutralFaction();
    }

    private boolean hasAnyCondition(MarketAPI market, String... ids) {
        for (String id : ids) {
            if (market.hasCondition(id)) return true;
        }
        return false;
    }

    private boolean isGiantWorld(PlanetAPI planet, String type) {
        return planet.isGasGiant() || (type != null && type.contains("giant"));
    }

    public static final String SLAVE_LABOR_FLAG = "$viable_slave_labor_generated";

    private static boolean isDecivilized(MarketAPI m) {
        return m != null && (m.hasCondition("decivilized_subpop") || m.hasCondition("decivilized"));
    }

    /** Every market with Decivilized or Decivilized Subpopulation, wherever it is (any system, other mods' systems, stations). */
    private java.util.List<MarketAPI> findDecivilizedMarkets() {
        SectorAPI sector = Global.getSector();
        java.util.LinkedHashSet<MarketAPI> found = new java.util.LinkedHashSet<MarketAPI>();
        for (StarSystemAPI system : sector.getStarSystems()) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar()) continue;
                MarketAPI market = planet.getMarket();
                if (isDecivilized(market)) found.add(market);
            }
        }
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (isDecivilized(market)) found.add(market);
        }
        return new java.util.ArrayList<MarketAPI>(found);
    }

    /**
     * Runs once per save, after every mod has set up its worlds. About half of all Decivilized and Decivilized Subpopulation worlds
     * (anywhere in the sector) get Viable Slave Labor Population. If fewer than two worlds end up with it, ruins worlds
     * in the outer systems make up the numbers.
     */
    private void generateViableSlaveLabor() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(SLAVE_LABOR_FLAG)) return;

        java.util.Random random = new java.util.Random((long) sector.getSeedString().hashCode() ^ 0x51A7EL);

        java.util.List<MarketAPI> decivilized = findDecivilizedMarkets();
        java.util.Collections.shuffle(decivilized, random);
        int count = 0;
        for (MarketAPI m : decivilized) if (m.hasCondition("viable_slave_labor")) count++;
        // 3/8 of decivilized worlds (three quarters of the original half), fractional part rounded randomly
        float exact = decivilized.size() * 0.375f;
        int target = (int) exact + (random.nextFloat() < exact - (int) exact ? 1 : 0);
        for (MarketAPI m : decivilized) {
            if (count >= target) break;
            if (m.hasCondition("viable_slave_labor")) continue;
            addCustomCondition(m, "viable_slave_labor");
            count++;
            log.info("Viable Slave Labor Population added to [" + m.getName() + "]");
        }

        if (count < 2) {
            java.util.List<PlanetAPI> ruinsFallback = new java.util.ArrayList<PlanetAPI>();
            for (StarSystemAPI system : sector.getStarSystems()) {
                if (isCoreSystem(system)) continue;
                for (PlanetAPI planet : system.getPlanets()) {
                    if (planet.isStar()) continue;
                    MarketAPI market = planet.getMarket();
                    if (market == null || market.hasCondition("viable_slave_labor")) continue;
                    if (isUnclaimed(market) && hasAnyCondition(market, Conditions.RUINS_SCATTERED, Conditions.RUINS_WIDESPREAD,
                            Conditions.RUINS_EXTENSIVE, Conditions.RUINS_VAST)) {
                        ruinsFallback.add(planet);
                    }
                }
            }
            while (count < 2 && !ruinsFallback.isEmpty()) {
                PlanetAPI pick = ruinsFallback.remove(random.nextInt(ruinsFallback.size()));
                addCustomCondition(pick.getMarket(), "viable_slave_labor");
                count++;
                log.info("Viable Slave Labor Population added (ruins fallback) to [" + pick.getName() + "]");
            }
        }

        sector.getMemoryWithoutUpdate().set(SLAVE_LABOR_FLAG, true);
        log.info("=== Viable Slave Labor Population generation complete. " + count + " worlds have it (of "
            + decivilized.size() + " decivilized worlds). ===");
    }

    public static final String ADDITIVE_REACTOR_FLAG = "$volatile_additive_reactor_generated";

    private void generateVolatileAdditiveReactor() {
        scatterCondition("volatile_additive_reactor", "Volatile Additive Reactor", ADDITIVE_REACTOR_FLAG, 0xADD17L, false,
            (system, planet, market, type, lower) -> {
                if (isGiantWorld(planet, type)) return 0f;
                return (market.hasCondition(Conditions.NO_ATMOSPHERE) && !market.hasCondition(Conditions.THIN_ATMOSPHERE)) ? 0.02f : 0f;
            }, null);
    }

    private void generateSupercriticalCrust() {
        scatterCondition("supercritical_crust", "Supercritical Crust", CRUST_GENERATED_FLAG, 0x5C05L, false,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                // Only tectonic worlds, and never Hadean or Nocturnal worlds.
                if ("hadean_world".equals(type) || "nocturnal".equals(type)) return 0f;
                boolean tectonic = market.hasCondition(Conditions.TECTONIC_ACTIVITY)
                    || market.hasCondition(Conditions.EXTREME_TECTONIC_ACTIVITY);
                return tectonic ? 0.05f : 0f;
            }, null);
    }

    /** Dense-atmosphere worlds that are not giants: the air traps signals until a Waystation lets them out as a pulse. */
    /** Worlds already pelted by meteors, which pull the wrecks of nearby battles down onto themselves. */
    /** The Trash King exists on exactly one world in the whole sector: a habitable or thin-atmosphere world, or any world with ruins. */
    private void generateTrashKing() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(TRASH_KING_FLAG)) return;

        Random random = new Random((long) sector.getSeedString().hashCode() ^ 0x5C4A9E1L);

        List<MarketAPI> candidates = new ArrayList<MarketAPI>();
        List<Float> weights = new ArrayList<Float>();
        float totalWeight = 0f;

        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system) || isSentinelSystem(system)) continue;
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar() || planet.isGasGiant()) continue;
                MarketAPI market = planet.getMarket();
                if (market == null || isSentinelPlanet(planet, market)) continue;
                if (market.hasCondition(TrashKingCondition.CONDITION_ID)) continue;
                if (isGiantWorld(planet, planet.getTypeId())) continue;

                boolean habitableOrThin = market.hasCondition(Conditions.HABITABLE) || market.hasCondition(Conditions.THIN_ATMOSPHERE);
                boolean ruins = market.hasCondition(Conditions.RUINS_SCATTERED) || market.hasCondition(Conditions.RUINS_WIDESPREAD)
                    || market.hasCondition(Conditions.RUINS_EXTENSIVE) || market.hasCondition(Conditions.RUINS_VAST);
                if (!habitableOrThin && !ruins) continue;
                if (!isUnclaimed(market)) continue;

                candidates.add(market);
                weights.add(1f);
                totalWeight += 1f;
            }
        }

        if (!candidates.isEmpty()) {
            float roll = random.nextFloat() * totalWeight;
            MarketAPI chosen = candidates.get(candidates.size() - 1);
            for (int i = 0; i < candidates.size(); i++) {
                roll -= weights.get(i);
                if (roll <= 0f) {
                    chosen = candidates.get(i);
                    break;
                }
            }
            addCustomCondition(chosen, TrashKingCondition.CONDITION_ID);
            log.info("Trash King added to [" + chosen.getPrimaryEntity().getName() + "] (one of " + candidates.size() + " candidate worlds).");
        } else {
            log.info("Trash King: no habitable or thin-atmosphere world was available.");
        }

        sector.getMemoryWithoutUpdate().set(TRASH_KING_FLAG, true);
    }

    private void generatePilgrimWorld() {
        scatterCondition("pilgrim_world", "Pilgrim World", PILGRIM_WORLD_FLAG, 0x91A7C3L, true,
            (system, planet, market, type, lower) -> {
                if (isSentinelSystem(system) || isSentinelPlanet(planet, market)) return 0f;
                if (planet.isStar() || planet.isGasGiant() || isGiantWorld(planet, type)) return 0f;

                // an old holy site: worlds with ruins, or worlds people could always live on
                boolean ruins = market.hasCondition(Conditions.RUINS_SCATTERED) || market.hasCondition(Conditions.RUINS_WIDESPREAD)
                    || market.hasCondition(Conditions.RUINS_EXTENSIVE) || market.hasCondition(Conditions.RUINS_VAST);
                if (ruins) return 0.02f;
                if (market.hasCondition(Conditions.HABITABLE)) return 0.02f;
                return 0f;
            }, null);
    }

    private void generateShipGraveyard() {
        scatterCondition("ship_graveyard", "Ship Graveyard", SHIP_GRAVEYARD_FLAG, 0x6A4E9A1L, true,
            (system, planet, market, type, lower) -> {
                if (planet.isStar() || planet.isGasGiant() || isGiantWorld(planet, type)) return 0f;
                if ("hadean_world".equals(type)) return 0f; // never on the Hadean world
                if (SUNDERED_TYPE.equals(type)) return 0.50f; // half of all Sundered worlds
                return market.hasCondition(Conditions.METEOR_IMPACTS) ? 0.10f : 0f;
            }, null);
    }

    private void generateResonantSignalTrap() {
        scatterCondition("resonant_signal_trap", "Resonant Signal Trap", SIGNAL_TRAP_FLAG, 0x516A17L, true,
            (system, planet, market, type, lower) -> {
                if (planet.isStar() || isGiantWorld(planet, type)) return 0f;
                return market.hasCondition(Conditions.DENSE_ATMOSPHERE) ? 0.15f : 0f;
            }, null);
    }

    /** Low-gravity worlds, where missiles leave the ground with almost no push needed. */
    private void generateGroundMissileDefense() {
        scatterCondition("ground_missile_defense", "Ground-to-Space Missile Defense", GROUND_MISSILE_FLAG, 0x61D4155L, true,
            (system, planet, market, type, lower) -> {
                if (planet.isStar()) return 0f;
                return market.hasCondition(Conditions.LOW_GRAVITY) ? 0.05f : 0f;
            }, null);
    }

    /** True for ice giants however the type is spelled (type id, spec type or display name). */
    private boolean isIceGiant(PlanetAPI planet) {
        if (planet == null || planet.isStar()) return false;
        String[] names = {planet.getTypeId(), planet.getSpec() == null ? null : planet.getSpec().getPlanetType(),
                          planet.getSpec() == null ? null : planet.getSpec().getName()};
        for (String n : names) {
            if (n == null) continue;
            String l = n.toLowerCase().replace(' ', '_');
            if (l.contains("ice_giant")) return true;
        }
        return false;
    }

    private void generateColdSkyObservatory() {
        final int[] seen = {0};
        scatterCondition("cold_sky_observatory", "Cold Sky Observatory", COLD_SKY_FLAG, 0xC01D5L, true,
            (system, planet, market, type, lower) -> {
                if (!isIceGiant(planet)) return 0f;
                seen[0]++;
                return 0.20f;
            }, null);
        log.info("Cold Sky Observatory: " + seen[0] + " eligible ice giants were rolled.");
    }

    private void generateDegenerateVolatiles() {
        scatterCondition("degenerate_volatiles", "Degenerate Volatiles", DEGENERATE_VOLATILES_FLAG, 0xD8A3L, false,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                boolean isGasGiant = type.contains("gas_giant");
                boolean hasHighGravity = market.hasCondition(Conditions.HIGH_GRAVITY);
                boolean isOtherGiant = type.contains("ice_giant") || (type.contains("giant") && !isGasGiant);
                return ((isGasGiant && hasHighGravity) || isOtherGiant) ? 0.10f : 0f;
            }, null);
    }

    private void generateGeothermalLeylines() {
        scatterCondition("geothermal_leylines", "Geothermal Leylines", LEYLINES_GENERATED_FLAG, 0x7E14L, false,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                boolean excluded = type.contains("giant") || "volcanic".equals(type)
                    || "lava".equals(type) || "lava_minor".equals(type);
                return excluded ? 0f : 0.03f;
            }, null);
    }

    private void generateDiamondCore() {
        scatterCondition("diamond_core", "Diamond Core", DIAMOND_CORE_FLAG, 0x4D1A0L, true,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                boolean isGiant = planet.isGasGiant() || isGiantWorld(planet, type) || market.hasCondition("hydrocarbon_fractionation");
                return (!isGiant && market.hasCondition(Conditions.HIGH_GRAVITY)) ? 0.10f : 0f;
            }, null);
    }

    private void generateAutomatedShipyards() {
        scatterCondition("automated_shipyards", "Automated Shipyards", AUTOMATED_SHIPYARDS_FLAG, 0x91CA3L, true,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                boolean hasRuins = hasAnyCondition(market, Conditions.RUINS_SCATTERED, Conditions.RUINS_WIDESPREAD,
                    Conditions.RUINS_EXTENSIVE, Conditions.RUINS_VAST);
                return hasRuins ? 0.05f : 0f;
            }, null);
    }

    private void generateAntimatterAnomaly() {
        scatterCondition("antimatter_anomaly", "Antimatter Anomaly", ANOMALY_FLAG, 0x9F4C2L, true,
            (system, planet, market, type, lower) -> {
                if (market.hasCondition(Conditions.HABITABLE) || isGiantWorld(planet, type)) return 0f;
                return 0.02f;
            }, null);
    }

    private void generateClandestineHarvestingSyndicate() {
        scatterCondition("clandestine_harvesting_syndicate", "Clandestine Harvesting Syndicate", HARVESTING_SYNDICATE_FLAG, 0x7E3A1L, true,
            (system, planet, market, type, lower) -> {
                boolean eligible = market.hasCondition(Conditions.HABITABLE) || market.hasCondition(Conditions.THIN_ATMOSPHERE);
                return eligible ? 0.03f : 0f;
            }, null);
    }

    private void generateMegalithicOutcrops() {
        scatterCondition("megalithic_outcrops", "Megalithic Outcrops", MEGALITHIC_OUTCROPS_FLAG, 0x6E4A9L, true,
            (system, planet, market, type, lower) -> {
                boolean highTierOre = hasAnyCondition(market, Conditions.ORE_RICH, Conditions.ORE_ULTRARICH,
                    Conditions.RARE_ORE_RICH, Conditions.RARE_ORE_ULTRARICH);
                return highTierOre ? 0.03f : 0f;
            }, null);
    }

    private void generateGeographicallyEfficientZoning() {
        scatterCondition("geographically_efficient_zoning", "Geographically Efficient Zoning", ZONING_GENERATED_FLAG, 0x3E1F8L, true,
            (system, planet, market, type, lower) -> {
                if (isGiantWorld(planet, type)) return 0f;
                if (hasAnyCondition(market, Conditions.TECTONIC_ACTIVITY, Conditions.EXTREME_TECTONIC_ACTIVITY)) return 0f;
                return 0.03f;
            }, null);
    }

    private void generateRadiolyticCatalyst() {
        scatterCondition("radiolytic_catalyst", "Radiolytic Catalyst", RADIOLYTIC_CATALYST_FLAG, 0x9A4C1L, true,
            (system, planet, market, type, lower) -> market.hasCondition(Conditions.IRRADIATED) ? 0.03f : 0f, null);
    }

    private void generateChronoSpatialDilation() {
        // 0.5% chance on any unclaimed non-star world
        scatterCondition("chrono_spatial_dilation", "Chrono-Spatial Dilation", CHRONO_DILATION_FLAG, 0x3F89A1L, true,
            (system, planet, market, type, lower) -> 0.005f, null);
    }

    private void generateRapidGeomagneticInversion() {
        scatterCondition("rapid_geomagnetic_inversion", "Rapid Geomagnetic Inversion", RAPID_GEOMAGNETIC_INVERSION_FLAG, 0x4B2C8FL, true,
            (system, planet, market, type, lower) -> isGiantWorld(planet, type) ? 0f : 0.02f, null);
    }

    private void generateClosedLoopHydroponics() {
        scatterCondition("closed_loop_hydroponics", "Closed-loop Hydroponic Matrix", HYDROPONIC_MATRIX_FLAG, 0x8D3F1AL, true,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                return (lower.contains("barren") || lower.contains("azure")) ? 0.03f : 0f;
            }, null);
    }

    private void generateSupercooledCryoSinks() {
        scatterCondition("supercooled_cryo_sinks", "Supercooled Cryo-Sinks", CRYO_SINKS_FLAG, 0x3A9F4BL, true,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                if (planet.isGasGiant() || lower.contains("giant")) return 0f;
                // Frozen, cryovolcanic, ice, tundra and Snowball worlds: 10% chance
                boolean isCryoPlanet = lower.contains("cryovolcanic") || lower.contains("frozen")
                    || lower.contains("ice") || lower.contains("tundra") || lower.contains("snowball");
                return isCryoPlanet ? 0.10f : 0f;
            }, null);
    }

    private void generateHollowedCalderaGarrisons() {
        scatterCondition("hollowed_caldera_garrisons", "Hollowed Caldera Garrisons", HOLLOWED_CALDERA_FLAG, 0xCA1DE8AL, true,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;

                // Never gas/ice giants or Hadean worlds
                if (planet.isGasGiant() || lower.contains("giant") || lower.contains("gas") || lower.contains("hadean")) return 0f;

                // Never habitable worlds
                if (market.hasCondition(Conditions.HABITABLE) || marketHasConditionContaining(market, "habitable")) return 0f;

                // Never acid or alkali worlds
                if (lower.contains("acid") || lower.contains("alkali")
                    || marketHasConditionContaining(market, "acid") || marketHasConditionContaining(market, "alkali")) return 0f;

                // Volcanic/lava or extreme heat worlds: 6.67%. Hot worlds: 3.33%. (Tectonic activity is allowed.)
                // (Two thirds of the original 10% / 5%.)
                boolean isVolcanic = lower.contains("volcanic") || lower.contains("lava");
                if (isVolcanic || market.hasCondition(Conditions.VERY_HOT)) return 0.0667f;
                if (market.hasCondition(Conditions.HOT)) return 0.0333f;
                return 0f;
            }, null);
    }

    private void generateVacationParadiseResort() {
        scatterCondition("vacation_paradise_resort", "Vacation Paradise & Resort", VACATION_RESORT_FLAG, 0x5E0C9AL, true,
            (system, planet, market, type, lower) -> {
                // Never the Sentinel storyline system or its guaranteed world
                if (isSentinelSystem(system) || isSentinelPlanet(planet, market)) return 0f;
                if (type == null) return 0f;

                // Never gas/ice giants, and never terran, jungle or similar worlds
                if (planet.isGasGiant() || lower.contains("giant") || lower.contains("gas")) return 0f;
                if (lower.contains("terran") || lower.contains("jungle") || lower.contains("forest")
                    || lower.contains("gaia") || lower.contains("eden") || lower.contains("earth")) return 0f;

                boolean isWaterWorld = market.hasCondition(Conditions.WATER_SURFACE) || market.hasCondition("water")
                    || marketHasConditionContaining(market, "water_surface")
                    || lower.contains("water") || lower.contains("ocean") || lower.contains("atoll")
                    || lower.contains("island") || lower.contains("aquatic") || lower.contains("pelagic");
                boolean isTundra = lower.contains("tundra");
                boolean isLifeless = lower.contains("lifeless") || lower.contains("barren");

                // Water worlds and tundra: 12.5%. Lifeless/barren: 5%.
                float chance;
                if (isWaterWorld || isTundra) chance = 0.125f;
                else if (isLifeless) chance = 0.05f;
                else return 0f;

                // Tundra and lifeless worlds must have farmland (water worlds are exempt)
                if (!isWaterWorld && !marketHasConditionContaining(market, "farmland") && !marketHasConditionContaining(market, "farming")) {
                    return 0f;
                }
                return chance;
            },
            market -> {
                // A resort replaces any decivilized population
                market.removeCondition(Conditions.DECIVILIZED);
                market.removeCondition(Conditions.DECIVILIZED_SUBPOP);
            });
    }

    private void generateSmugglersHaven() {
        scatterCondition("smugglers_haven", "Smugglers' Haven", SMUGGLERS_HAVEN_FLAG, 0x5A4D01L, true,
            (system, planet, market, type, lower) -> {
                if (type == null) return 0f;
                if (planet.isGasGiant() || lower.contains("giant") || lower.contains("gas")) return 0f;

                // Desert, arid, dust, windswept and bombarded lifeless worlds (not plain barren or barren bombarded)
                boolean isDesertOrArid = lower.contains("desert") || lower.contains("arid");
                boolean isDustOrWindswept = lower.contains("dust") || lower.contains("windswept");
                boolean isBombardedLifeless = lower.contains("bombarded") && !lower.contains("barren");
                return (isDesertOrArid || isDustOrWindswept || isBombardedLifeless) ? 0.10f : 0f;
            }, null);
    }

    private boolean marketHasConditionContaining(MarketAPI market, String text) {
        for (MarketConditionAPI mc : market.getConditions()) {
            if (mc.getId() != null && mc.getId().toLowerCase().contains(text)) return true;
        }
        return false;
    }

    private boolean isSentinelSystem(StarSystemAPI system) {
        return containsSentinel(system.getName()) || containsSentinel(system.getBaseName())
            || containsSentinel(system.getId()) || system.hasTag("theme_sentinel");
    }

    private boolean isSentinelPlanet(PlanetAPI planet, MarketAPI market) {
        return containsSentinel(planet.getName()) || containsSentinel(planet.getId())
            || planet.hasTag("sentinel")
            || (market.getMemoryWithoutUpdate() != null && market.getMemoryWithoutUpdate().contains("$sentinel"));
    }

    private boolean containsSentinel(String text) {
        return text != null && text.toLowerCase().contains("sentinel");
    }


    // ==========================================
    // MODULE 8: HYDROCARBON GIANTS
    // ==========================================

    private void generateHydrocarbonGiants() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(HYDROCARBON_GIANT_FLAG)) {
            return;
        }

        WeightedRandomPicker<PlanetAPI> gasGiantCandidates = new WeightedRandomPicker<PlanetAPI>();

        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system)) {
                continue;
            }

            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar()) continue;

                String type = planet.getTypeId();
                if (type == null) continue;

                MarketAPI market = planet.getMarket();
                if (market == null) continue;

                if (!market.isPlanetConditionMarketOnly() && market.getSize() > 0) continue;
                if (market.getFaction() != null && !market.getFaction().isNeutralFaction()) continue;

                boolean isGasGiant = "gas_giant".equals(type) ||
                                     type.contains("gas_giant") ||
                                     "ice_giant".equals(type) ||
                                     type.contains("giant");

                if (isGasGiant) {
                    gasGiantCandidates.add(planet, 1.0f);
                }
            }
        }

        log.info("Hydrocarbon Giant: " + gasGiantCandidates.getItems().size() + " candidate gas giants found");
        if (gasGiantCandidates.isEmpty()) return;

        long seed = (long) sector.getSeedString().hashCode() ^ 0xC4120L;
        Random random = new Random(seed);
        int worldsToSpawn = 1; // Guaranteed 1 spawn, maximum 1 spawn
        int count = 0;

        for (int i = 0; i < worldsToSpawn; i++) {
            if (gasGiantCandidates.isEmpty()) break;
            PlanetAPI giant = gasGiantCandidates.pickAndRemove();
            StarSystemAPI system = giant.getStarSystem();
            if (system == null) continue;

            MarketAPI market = giant.getMarket();
            if (market == null) continue;

            String name = giant.getName();
            String id = giant.getId();
            float radius = giant.getRadius();

            // Change the planet's type in place, the same way Unknown Skies does it
            giant.changeType("hydrocarbon_giant", random);
            giant.applySpecChanges();
            giant.setCustomDescriptionId("hydrocarbon_giant");
            giant.addTag(Tags.GAS_GIANT);
            if ("hydrocarbon_giant".equals(giant.getTypeId())) {
                log.info("Hydrocarbon giant [" + name + "] type change OK");
            } else {
                log.warn("Hydrocarbon giant [" + name + "] type change FAILED, type is still: " + giant.getTypeId());
            }

            // Find the outermost existing moon so the companion goes outside it
            int moonCount = 0;
            float maxMoonDist = radius + 250f;
            for (PlanetAPI p : system.getPlanets()) {
                if (p != giant && p.getOrbitFocus() == giant) {
                    moonCount++;
                    float d = p.getCircularOrbitRadius();
                    if (d > maxMoonDist) maxMoonDist = d;
                }
            }

            // Hydrocarbon Fractionation
            if (!market.hasCondition("hydrocarbon_fractionation")) {
                addCustomCondition(market, "hydrocarbon_fractionation");
            }

            // Make sure the giant has volatiles
            boolean hasVolatiles = market.hasCondition(Conditions.VOLATILES_TRACE) ||
                                   market.hasCondition(Conditions.VOLATILES_DIFFUSE) ||
                                   market.hasCondition(Conditions.VOLATILES_ABUNDANT) ||
                                   market.hasCondition(Conditions.VOLATILES_PLENTIFUL);
            if (!hasVolatiles) {
                float vRoll = random.nextFloat();
                if (vRoll < 0.20f) market.addCondition(Conditions.VOLATILES_TRACE);
                else if (vRoll < 0.60f) market.addCondition(Conditions.VOLATILES_DIFFUSE);
                else if (vRoll < 0.90f) market.addCondition(Conditions.VOLATILES_ABUNDANT);
                else market.addCondition(Conditions.VOLATILES_PLENTIFUL);
            }
            market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);

            // Companion satellite
            String extraMoonType = pickThematicSatelliteType(system, random);
            if (!planetTypeExists(extraMoonType)) {
                log.warn("Unknown satellite type '" + extraMoonType + "', falling back to barren");
                extraMoonType = "barren";
            }
            float extraMoonRadius = 45f + random.nextFloat() * 40f;
            float extraMoonDist = maxMoonDist + 280f + random.nextFloat() * 320f;
            float extraMoonDays = extraMoonDist / (15f + random.nextFloat() * 5f);
            int satelliteIndex = moonCount + 1;
            String extraMoonName = name + " " + RomanNumeral(satelliteIndex);
            String extraMoonId = id + "_companion_" + satelliteIndex;

            PlanetAPI extraMoon = system.addPlanet(
                extraMoonId, giant, extraMoonName, extraMoonType,
                random.nextFloat() * 360f, extraMoonRadius, extraMoonDist, extraMoonDays
            );
            if (extraMoon != null) {
                MarketAPI extraMoonMarket = Global.getFactory().createMarket(extraMoonId + "_market", extraMoonName, 0);
                extraMoonMarket.setPlanetConditionMarketOnly(true);
                extraMoonMarket.setPrimaryEntity(extraMoon);
                extraMoonMarket.setFactionId(Factions.NEUTRAL);
                extraMoonMarket.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
                addProceduralConditionsToSatellite(extraMoonMarket, extraMoonType, random);
                extraMoon.setMarket(extraMoonMarket);
            }

            count++;
            log.info("Hydrocarbon Giant spawned: [" + name + "] in system [" + system.getName() + "]");
            if (Global.getSector().getCampaignUI() != null) {
                Global.getSector().getCampaignUI().addMessage(
                    "Hydrocarbon Giant surveyed in frontier star system: " + system.getName() + " (" + name + ")",
                    Misc.getHighlightColor()
                );
            }
        }
        
        sector.getMemoryWithoutUpdate().set(HYDROCARBON_GIANT_FLAG, true);
        log.info("=== Hydrocarbon Giant generation complete. Placed on " + count + " gas giants. ===");
    }

    // ==========================================
    // MODULE 9: FORGOTTEN VAULT-WORLDS
    // ==========================================

    private void generateForgottenVaultWorlds() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(VAULT_WORLD_FLAG)) {
            return;
        }

        WeightedRandomPicker<MarketAPI> eligibleRuins = new WeightedRandomPicker<MarketAPI>();

        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system)) {
                continue;
            }

            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar()) continue;

                MarketAPI market = planet.getMarket();
                if (market == null) continue;

                if (!market.isPlanetConditionMarketOnly() && market.getSize() > 0) continue;
                if (market.getFaction() != null && !market.getFaction().isNeutralFaction()) continue;

                boolean hasExtensive = market.hasCondition(Conditions.RUINS_EXTENSIVE) || market.hasCondition("ruins_extensive");
                boolean hasVast = market.hasCondition(Conditions.RUINS_VAST) || market.hasCondition("ruins_vast");

                if (hasVast) {
                    eligibleRuins.add(market, 2.5f);
                } else if (hasExtensive) {
                    eligibleRuins.add(market, 1.0f);
                }
            }
        }

        if (eligibleRuins.isEmpty()) return;

        long seed = (long) sector.getSeedString().hashCode() ^ 0x7A01L;
        Random random = new Random(seed);

        float roll = random.nextFloat();
        int targetSpawns;
        if (roll < 0.15f) {
            targetSpawns = 0;
        } else if (roll < 0.60f) {
            targetSpawns = 1;
        } else if (roll < 0.90f) {
            targetSpawns = 2;
        } else {
            targetSpawns = 3;
        }

        int count = 0;
        for (int i = 0; i < targetSpawns; i++) {
            if (eligibleRuins.isEmpty()) break;
            MarketAPI targetMarket = eligibleRuins.pickAndRemove();

            if (!targetMarket.hasCondition("forgotten_vault_world")) {
                addCustomCondition(targetMarket, "forgotten_vault_world");
                count++;

                PlanetAPI planet = targetMarket.getPlanetEntity();
                String pName = planet != null ? planet.getName() : targetMarket.getName();
                String sName = planet != null && planet.getStarSystem() != null ? planet.getStarSystem().getName() : "Unknown";

                log.info("Forgotten Vault-World added to [" + pName + "] in [" + sName + "]");
                if (Global.getSector().getCampaignUI() != null) {
                    Global.getSector().getCampaignUI().addMessage(
                        "Ancient subterranean vault matrix detected on frontier world: " + sName + " (" + pName + ")",
                        Misc.getHighlightColor()
                    );
                }
            }
        }

        sector.getMemoryWithoutUpdate().set(VAULT_WORLD_FLAG, true);
        log.info("=== Forgotten Vault-World generation complete. Placed on " + count + " extensive/vast ruin worlds. ===");
    }

    // ==========================================
    // SUNDERED WORLDS: rare variant of barren worlds
    // ==========================================

    public static final String SUNDERED_GENERATED_FLAG = "$sundered_worlds_generated";
    public static final String SUNDERED_TYPE = "sundered";
    private static final float SUNDERED_CHANCE = 0.04f;   // per eligible barren world
    private static final int SUNDERED_MAX = 5;            // per sector

    private static final String[] SUNDERED_STRIP = {
        Conditions.LOW_GRAVITY, Conditions.HIGH_GRAVITY,
        Conditions.NO_ATMOSPHERE, Conditions.THIN_ATMOSPHERE, Conditions.DENSE_ATMOSPHERE, Conditions.TOXIC_ATMOSPHERE,
        Conditions.TECTONIC_ACTIVITY, Conditions.EXTREME_TECTONIC_ACTIVITY,
        Conditions.ORE_SPARSE, Conditions.ORE_MODERATE, Conditions.ORE_ABUNDANT, Conditions.ORE_RICH, Conditions.ORE_ULTRARICH,
        Conditions.RARE_ORE_SPARSE, Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_ABUNDANT, Conditions.RARE_ORE_RICH, Conditions.RARE_ORE_ULTRARICH,
    };

    /**
     * Turns a few unclaimed barren worlds into Sundered worlds: the planet is retextured and shrunk,
     * gets broken-crust conditions (including Meteor Impacts), and is surrounded by tumbling crust
     * fragments, a debris ring and a salvageable debris field.
     * Runs before the condition scatters so Ship Graveyard can use the Meteor Impacts it adds.
     */
    private void generateSunderedWorlds() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(SUNDERED_GENERATED_FLAG)) return;
        // Separate dice: one only for the 4% rolls, one for building each world (type change, fragments,
        // debris), so building a Sundered world can never affect the odds of the next roll.
        long seed = (long) sector.getSeedString().hashCode();
        Random rollRandom = new Random(seed ^ 0x5D3E7EDL);
        Random buildRandom = new Random(seed ^ 0x2B1D5E7L);

        int eligible = 0, made = 0;
        for (StarSystemAPI system : sector.getStarSystems()) {
            if (made >= SUNDERED_MAX) break;
            if (isCoreSystem(system)) continue;

            for (PlanetAPI planet : new ArrayList<PlanetAPI>(system.getPlanets())) {
                if (made >= SUNDERED_MAX) break;
                if (!isSunderCandidate(system, planet)) continue;
                eligible++;
                if (rollRandom.nextFloat() >= SUNDERED_CHANCE) continue;
                try {
                    sunderPlanet(system, planet, buildRandom);
                    made++;
                    log.info("Sundered world created: [" + planet.getName() + "] in [" + system.getName() + "] on roll #" + eligible);
                } catch (Exception e) {
                    log.error("Could not create Sundered world at " + planet.getName(), e);
                }
            }
        }
        sector.getMemoryWithoutUpdate().set(SUNDERED_GENERATED_FLAG, true);
        log.info("=== Sundered world generation complete. " + made + " created after " + eligible + " rolls"
                + (made >= SUNDERED_MAX ? " (stopped at the cap of " + SUNDERED_MAX + ")" : " (every eligible barren world rolled)") + ". ===");
    }

    private boolean isSunderCandidate(StarSystemAPI system, PlanetAPI planet) {
        if (planet.isStar() || planet.isGasGiant()) return false;
        String type = planet.getTypeId();
        if (type == null) return false;
        String lower = type.toLowerCase();
        if (!lower.contains("barren") || lower.contains("desert")) return false;

        MarketAPI market = planet.getMarket();
        if (market == null || !isUnclaimed(market)) return false;

        // Must orbit a star directly (not a moon), with nothing orbiting it
        SectorEntityToken focus = planet.getOrbitFocus();
        if (!(focus instanceof PlanetAPI) || !((PlanetAPI) focus).isStar()) return false;
        for (SectorEntityToken e : system.getAllEntities()) {
            if (e != planet && e.getOrbitFocus() == planet) return false;
        }

        // Room for the fragment cloud: keep clear of neighbouring planets around the same star
        float myOrbit = Misc.getDistance(planet.getLocation(), focus.getLocation());
        float cloud = planet.getRadius() + 220f;
        for (PlanetAPI other : system.getPlanets()) {
            if (other == planet || other.isStar() || other.getOrbitFocus() != focus) continue;
            float otherOrbit = Misc.getDistance(other.getLocation(), focus.getLocation());
            if (Math.abs(otherOrbit - myOrbit) < cloud + other.getRadius() + 100f) return false;
        }
        return true;
    }

    private void sunderPlanet(StarSystemAPI system, PlanetAPI planet, Random random) {
        String oldType = planet.getTypeId();
        planet.changeType(SUNDERED_TYPE, random);
        if (!SUNDERED_TYPE.equals(planet.getTypeId())) {
            // Fallback: force the type and the look directly
            log.warn("Sundered: changeType did not take on [" + planet.getName() + "] (still " + planet.getTypeId() + "), forcing it");
            planet.setTypeId(SUNDERED_TYPE);
            com.fs.starfarer.api.campaign.PlanetSpecAPI spec = planet.getSpec();
            spec.setTexture("graphics/planets/sundered_planet.jpg");
            spec.setGlowTexture("graphics/planets/sundered_planet_glow.png");
            spec.setGlowColor(java.awt.Color.WHITE);
            spec.setUseReverseLightForGlow(false);
            spec.setAtmosphereThickness(0f);
            spec.setAtmosphereThicknessMin(0f);
            spec.setCloudTexture(null);
            spec.setPlanetColor(java.awt.Color.WHITE);
            spec.setIconColor(new java.awt.Color(200, 120, 90, 255));
        }
        planet.setRadius(Math.max(60f, planet.getRadius() * 0.8f));   // it lost a lot of mass
        planet.applySpecChanges();
        planet.setCustomDescriptionId(SUNDERED_TYPE);
        log.info("Sundered: [" + planet.getName() + "] type " + oldType + " -> " + planet.getTypeId()
                + (SUNDERED_TYPE.equals(planet.getTypeId()) ? " (OK)" : " (FAILED)"));
        float pr = planet.getRadius();

        // Conditions: broken, airless, shaking, bombarded by its own debris, rich in exposed ore
        MarketAPI market = planet.getMarket();
        for (String id : SUNDERED_STRIP) {
            if (market.hasCondition(id)) market.removeCondition(id);
        }
        WeightedRandomPicker<String> ore = new WeightedRandomPicker<String>(random);
        ore.add(Conditions.ORE_MODERATE, 25f); ore.add(Conditions.ORE_ABUNDANT, 40f);
        ore.add(Conditions.ORE_RICH, 25f); ore.add(Conditions.ORE_ULTRARICH, 10f);
        WeightedRandomPicker<String> rare = new WeightedRandomPicker<String>(random);
        rare.add(Conditions.RARE_ORE_SPARSE, 20f); rare.add(Conditions.RARE_ORE_MODERATE, 35f);
        rare.add(Conditions.RARE_ORE_ABUNDANT, 30f); rare.add(Conditions.RARE_ORE_RICH, 15f);
        String[] add = { Conditions.LOW_GRAVITY, Conditions.NO_ATMOSPHERE, Conditions.EXTREME_TECTONIC_ACTIVITY,
                         Conditions.METEOR_IMPACTS, ore.pick(), rare.pick() };
        for (String id : add) {
            if (!market.hasCondition(id)) market.addCondition(id);
            MarketConditionAPI mc = market.getCondition(id);
            if (mc != null) mc.setSurveyed(market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL);
        }

        // Tumbling crust fragments
        int count = 5 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            float size = 22f + random.nextFloat() * 26f;
            float spin = (2f + random.nextFloat() * 6f) * (random.nextBoolean() ? 1f : -1f);
            int sprite = random.nextInt(SunderedFragmentPlugin.SPRITES.length);
            SectorEntityToken frag = system.addCustomEntity(null, null, "rp_sundered_fragment", Factions.NEUTRAL,
                    new SunderedFragmentPlugin.FragmentParams(sprite, size, spin));
            float orbitR = pr + 45f + random.nextFloat() * 130f;
            float angle = (360f / count) * i + random.nextFloat() * 30f;
            float days = 20f + orbitR / 12f + random.nextFloat() * 10f;
            frag.setCircularOrbit(planet, angle, orbitR, days);
            frag.setFacing(random.nextFloat() * 360f);
        }

        // Debris ring and belt
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 1, new java.awt.Color(210, 170, 140, 200), 256f, pr + 110f, 30f);
        system.addAsteroidBelt(planet, 40, pr + 110f, 120f, 18f, 32f);

        // Salvageable debris field around the wreck
        com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams params =
            new com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams(
                pr + 260f, -1f, 10000000f, 0f);
        params.source = com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource.GEN;
        SectorEntityToken debris = Misc.addDebrisField(system, params, random);
        debris.setCircularOrbit(planet, 0f, 0f, 100f);
    }

    // ==========================================
    // NEW PLANET TYPES: Snowball, Halite, Chthonian, Sub-Brown Dwarf
    // ==========================================
    // Their random-generation weight is 0 in planet_gen_data.csv (Unknown Skies and other planet mods
    // dilute those weights too much). Instead, every new sector rolls how many of each type it gets
    // (0-5, same spread as Sundered worlds) and converts that many fitting worlds, on top of whatever
    // other mods add. Only runs while a new game is being generated, so existing saves are left alone.

    public static final String NEW_TYPES_GENERATED_FLAG = "$rp_new_planet_types_generated";
    /** Chance (in %) of a sector getting 0, 1, 2, 3, 4 or 5 of each new type. Matches Sundered worlds. */
    private static final float[] NEW_TYPE_COUNT_WEIGHTS = { 6f, 17f, 25f, 23f, 16f, 15f };
    private transient boolean inNewGameProcgen = false;

    private void generateNewPlanetTypes() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getMemoryWithoutUpdate().contains(NEW_TYPES_GENERATED_FLAG)) return;
        sector.getMemoryWithoutUpdate().set(NEW_TYPES_GENERATED_FLAG, true);
        if (!inNewGameProcgen) {
            log.info("New planet types: existing save, no worlds converted (only new games get them).");
            return;
        }
        // Separate dice: one only for how many of each type, one for choosing and building the worlds
        long seed = (long) sector.getSeedString().hashCode();
        Random countRandom = new Random(seed ^ 0x5A17C0DEL);
        Random buildRandom = new Random(seed ^ 0x3C0FFEEL);

        convertWorlds(sector, rollCount(countRandom), buildRandom, "snowball", "Snowball");
        convertWorlds(sector, rollCount(countRandom), buildRandom, "halite", "Halite");
        convertWorlds(sector, rollCount(countRandom), buildRandom, "chthonian", "Chthonian");
        convertWorlds(sector, rollCount(countRandom), buildRandom, "subbrown_dwarf", "Sub-Brown Dwarf");
    }

    private int rollCount(Random random) {
        WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<Integer>(random);
        for (int i = 0; i < NEW_TYPE_COUNT_WEIGHTS.length; i++) picker.add(i, NEW_TYPE_COUNT_WEIGHTS[i]);
        return picker.pick();
    }

    private void convertWorlds(SectorAPI sector, int target, Random random, String newType, String label) {
        List<PlanetAPI> candidates = new ArrayList<PlanetAPI>();
        for (StarSystemAPI system : sector.getStarSystems()) {
            if (isCoreSystem(system)) continue;
            for (PlanetAPI planet : system.getPlanets()) {
                if (isNewTypeCandidate(system, planet, newType)) candidates.add(planet);
            }
        }
        java.util.Collections.shuffle(candidates, random);
        int made = 0;
        for (PlanetAPI planet : candidates) {
            if (made >= target) break;
            // An earlier conversion in the same system may have grown a world next to this one
            if (!isNewTypeCandidate((StarSystemAPI) planet.getContainingLocation(), planet, newType)) continue;
            try {
                if (convertPlanet(planet, newType, random)) made++;
            } catch (Exception e) {
                log.error(label + ": could not convert " + planet.getName(), e);
            }
        }
        log.info("=== " + label + ": " + made + " of " + target + " wanted, from " + candidates.size() + " candidates. ===");
    }

    private boolean isNewTypeCandidate(StarSystemAPI system, PlanetAPI planet, String newType) {
        if (system == null || planet == null || planet.isStar()) return false;
        String type = planet.getTypeId();
        if (type == null || newType.equals(type)) return false;
        if (planet.getCustomDescriptionId() != null) return false;   // another mod's (or our own) special world
        MarketAPI market = planet.getMarket();
        if (market == null || !isUnclaimed(market)) return false;
        String lower = type.toLowerCase();

        if ("snowball".equals(newType)) {
            return !planet.isGasGiant() && (lower.startsWith("frozen") || lower.equals("us_icea") || lower.equals("us_iceb"));
        }
        if ("halite".equals(newType)) {
            if (planet.isGasGiant()) return false;
            // Worlds that already have farmland stay as they are
            if (hasAnyCondition(market, Conditions.FARMLAND_POOR, Conditions.FARMLAND_ADEQUATE,
                    Conditions.FARMLAND_RICH, Conditions.FARMLAND_BOUNTIFUL)) return false;
            return lower.contains("desert") || lower.equals("us_arid") || lower.equals("us_lifelessarid");
        }
        if ("subbrown_dwarf".equals(newType)) {
            return planet.isGasGiant() && (lower.equals("gas_giant") || lower.startsWith("us_gas_giant"));
        }
        if ("chthonian".equals(newType)) {
            if (planet.isGasGiant()) return false;
            boolean lava = lower.startsWith("lava") || lower.equals("us_lava") || lower.equals("us_volcanic");
            boolean rocky = (lower.contains("barren") && !lower.contains("desert")) || lower.equals("rocky_metallic")
                    || lower.equals("us_burnt");
            if (!lava && !rocky) return false;
            if (!market.hasCondition(Conditions.HOT) && !market.hasCondition(Conditions.VERY_HOT)) return false;
            // It will grow to gas-giant-core size, so it needs clear space: orbits a star, nothing orbits it
            return hasRoomToGrow(system, planet, 290f);
        }
        return false;
    }

    /** Orbits a star directly, nothing orbits it, and neighbouring orbits stay clear at the given radius. */
    private boolean hasRoomToGrow(StarSystemAPI system, PlanetAPI planet, float newRadius) {
        SectorEntityToken focus = planet.getOrbitFocus();
        if (!(focus instanceof PlanetAPI) || !((PlanetAPI) focus).isStar()) return false;
        for (SectorEntityToken e : system.getAllEntities()) {
            if (e != planet && e.getOrbitFocus() == planet) return false;
        }
        float myOrbit = Misc.getDistance(planet.getLocation(), focus.getLocation());
        if (myOrbit - focus.getRadius() - newRadius < 300f) return false;
        for (PlanetAPI other : system.getPlanets()) {
            if (other == planet || other.isStar() || other.getOrbitFocus() != focus) continue;
            float otherOrbit = Misc.getDistance(other.getLocation(), focus.getLocation());
            if (Math.abs(otherOrbit - myOrbit) < newRadius + other.getRadius() + 200f) return false;
        }
        return true;
    }

    private boolean convertPlanet(PlanetAPI planet, String newType, Random random) {
        String oldType = planet.getTypeId();
        float oldRadius = planet.getRadius();
        planet.changeType(newType, random);
        if (!newType.equals(planet.getTypeId())) {
            log.warn("New planet types: changeType to " + newType + " did not take on [" + planet.getName() + "], skipped");
            return false;
        }
        MarketAPI market = planet.getMarket();

        // Halite keeps every condition the world had (worlds with farmland are never picked, see isNewTypeCandidate)
        if ("chthonian".equals(newType)) {
            planet.setRadius(250f + random.nextFloat() * 40f);
            removeConditions(market, Conditions.LOW_GRAVITY, Conditions.HIGH_GRAVITY,
                    Conditions.NO_ATMOSPHERE, Conditions.THIN_ATMOSPHERE, Conditions.DENSE_ATMOSPHERE,
                    Conditions.TOXIC_ATMOSPHERE, Conditions.HABITABLE, Conditions.MILD_CLIMATE, Conditions.WATER_SURFACE,
                    Conditions.FARMLAND_POOR, Conditions.FARMLAND_ADEQUATE, Conditions.FARMLAND_RICH, Conditions.FARMLAND_BOUNTIFUL,
                    Conditions.ORGANICS_TRACE, Conditions.ORGANICS_COMMON, Conditions.ORGANICS_ABUNDANT, Conditions.ORGANICS_PLENTIFUL,
                    Conditions.VOLATILES_TRACE, Conditions.VOLATILES_DIFFUSE, Conditions.VOLATILES_ABUNDANT, Conditions.VOLATILES_PLENTIFUL,
                    Conditions.ORE_SPARSE, Conditions.ORE_MODERATE, Conditions.ORE_ABUNDANT, Conditions.ORE_RICH, Conditions.ORE_ULTRARICH,
                    Conditions.RARE_ORE_SPARSE, Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_ABUNDANT, Conditions.RARE_ORE_RICH,
                    Conditions.RARE_ORE_ULTRARICH);
            WeightedRandomPicker<String> ore = new WeightedRandomPicker<String>(random);
            ore.add(Conditions.ORE_MODERATE, 30f); ore.add(Conditions.ORE_ABUNDANT, 31f);
            ore.add(Conditions.ORE_RICH, 22f); ore.add(Conditions.ORE_ULTRARICH, 17f);
            WeightedRandomPicker<String> rare = new WeightedRandomPicker<String>(random);
            rare.add(Conditions.RARE_ORE_MODERATE, 30f); rare.add(Conditions.RARE_ORE_ABUNDANT, 31f);
            rare.add(Conditions.RARE_ORE_RICH, 22f); rare.add(Conditions.RARE_ORE_ULTRARICH, 17f);
            addConditions(market, Conditions.HIGH_GRAVITY, Conditions.NO_ATMOSPHERE, ore.pick(), rare.pick());
        } else if ("subbrown_dwarf".equals(newType)) {
            // Grow toward its usual size only when nothing orbits close enough to end up inside it
            float want = 380f + random.nextFloat() * 70f;
            if (want > planet.getRadius() && hasRoomToGrow((StarSystemAPI) planet.getContainingLocation(), planet, want)) {
                planet.setRadius(want);
            }
        }
        // Snowball keeps its frozen-world conditions; Ice-Locked Seas is added on load.

        planet.applySpecChanges();
        planet.setCustomDescriptionId(newType);
        log.info("New planet type: [" + planet.getName() + "] in [" + planet.getContainingLocation().getName() + "] "
                + oldType + " -> " + newType + " (radius " + (int) oldRadius + " -> " + (int) planet.getRadius() + ")");
        return true;
    }

    private void removeConditions(MarketAPI market, String... ids) {
        for (String id : ids) {
            if (market.hasCondition(id)) market.removeCondition(id);
        }
    }

    private void addConditions(MarketAPI market, String... ids) {
        for (String id : ids) {
            if (!market.hasCondition(id)) market.addCondition(id);
            MarketConditionAPI mc = market.getCondition(id);
            if (mc != null) mc.setSurveyed(market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL);
        }
    }

    private boolean isCoreSystem(StarSystemAPI system) {
        if (!system.isProcgen()) return true;

        if (system.hasTag(Tags.THEME_CORE) ||
            system.hasTag(Tags.THEME_CORE_POPULATED) ||
            system.hasTag(Tags.THEME_CORE_UNPOPULATED) ||
            system.hasTag("theme_core") ||
            system.hasTag("theme_core_populated")) {
            return true;
        }

        for (MarketAPI m : Misc.getMarketsInLocation(system)) {
            if (m.getSize() > 0 && !m.isPlanetConditionMarketOnly()) {
                return true;
            }
            if (m.getFaction() != null && !m.getFaction().isNeutralFaction()) {
                return true;
            }
        }

        return false;
    }

    private boolean planetTypeExists(String typeId) {
        for (PlanetSpecAPI spec : Global.getSettings().getAllPlanetSpecs()) {
            if (spec.getPlanetType().equals(typeId)) return true;
        }
        return false;
    }
    
    private String pickThematicSatelliteType(StarSystemAPI system, Random random) {
        PlanetAPI star = system != null ? system.getStar() : null;
        String starType = star != null && star.getTypeId() != null ? star.getTypeId().toLowerCase() : "";

        // 1. Extreme Stellar Remnants: Black Holes, Neutron Stars, Pulsars
        if (starType.contains("black_hole") || starType.contains("neutron") || starType.contains("pulsar")) {
            String[] remnantPool = {
                "barren", "barren-cupric", "barren_castiron", "rocky_unstable", "rocky_metallic", "frozen", "rocky_ice"
            };
            return remnantPool[random.nextInt(remnantPool.length)];
        }

        // 2. High Radiation / Hyper-Energetic Stars: Blue Giants, Blue Supergiants, White Dwarfs
        if (starType.contains("blue") || starType.contains("white")) {
            String[] hotPool = {
                "volcanic", "lava", "lava_minor", "barren", "barren_castiron", "rocky_metallic", "rocky_unstable", "barren-cupric"
            };
            return hotPool[random.nextInt(hotPool.length)];
        }

        // 3. Cool / Low-Luminosity Stars: Red Dwarfs, Brown Dwarfs
        if (starType.contains("red_dwarf") || starType.contains("brown")) {
            String[] coolPool = {
                "frozen", "frozen1", "frozen2", "frozen3", "rocky_ice", "cryovolcanic", "barren", "barren-cupric", "tundra"
            };
            return coolPool[random.nextInt(coolPool.length)];
        }

        // 4. Dying Giants: Red Giants, Red Supergiants
        if (starType.contains("red_giant") || starType.contains("red_supergiant")) {
            String[] redGiantPool = {
                "barren", "barren-cupric", "barren_desert", "desert", "arid", "rocky_unstable", "volcanic", "frozen"
            };
            return redGiantPool[random.nextInt(redGiantPool.length)];
        }

        // 5. Habitable / Main-Sequence Solar Types: Yellow, Orange, Terran Stars
        if (starType.contains("yellow") || starType.contains("orange")) {
            String[] solarPool = {
                "terran", "terran-eccentric", "jungle", "water", "arid", "desert", "tundra",
                "barren", "barren-cupric", "frozen", "rocky_ice"
            };
            return solarPool[random.nextInt(solarPool.length)];
        }

        // 6. Generic Fallback
        String[] fallbackPool = {
            "barren", "barren-cupric", "rocky_ice", "frozen", "tundra", "desert", "arid"
        };
        return fallbackPool[random.nextInt(fallbackPool.length)];
    }

    private void addProceduralConditionsToSatellite(MarketAPI market, String planetType, Random random) {
        if (market == null || planetType == null) return;
        String lower = planetType.toLowerCase();

        if (lower.contains("barren") || lower.contains("rocky_metallic") || lower.contains("rocky_unstable")) {
            market.addCondition(Conditions.NO_ATMOSPHERE);
            float oRoll = random.nextFloat();
            if (oRoll < 0.35f) market.addCondition(Conditions.ORE_SPARSE);
            else if (oRoll < 0.70f) market.addCondition(Conditions.ORE_MODERATE);
            else market.addCondition(Conditions.ORE_ABUNDANT);

            if (random.nextFloat() < 0.50f) {
                market.addCondition(random.nextFloat() < 0.50f ? Conditions.RARE_ORE_SPARSE : Conditions.RARE_ORE_MODERATE);
            }
            if (random.nextFloat() < 0.40f) {
                market.addCondition(random.nextFloat() < 0.50f ? Conditions.COLD : Conditions.HOT);
            }
        } else if (lower.contains("frozen") || lower.contains("ice") || lower.contains("rocky_ice")) {
            market.addCondition(random.nextFloat() < 0.60f ? Conditions.THIN_ATMOSPHERE : Conditions.NO_ATMOSPHERE);
            market.addCondition(random.nextFloat() < 0.60f ? Conditions.COLD : Conditions.VERY_COLD);
            float vRoll = random.nextFloat();
            if (vRoll < 0.40f) market.addCondition(Conditions.VOLATILES_DIFFUSE);
            else if (vRoll < 0.80f) market.addCondition(Conditions.VOLATILES_ABUNDANT);
            else market.addCondition(Conditions.VOLATILES_PLENTIFUL);

            if (random.nextFloat() < 0.40f) market.addCondition(Conditions.ORE_SPARSE);
        } else if (lower.contains("cryovolcanic")) {
            market.addCondition(Conditions.THIN_ATMOSPHERE);
            market.addCondition(Conditions.VERY_COLD);
            market.addCondition(Conditions.EXTREME_TECTONIC_ACTIVITY);
            market.addCondition(random.nextFloat() < 0.50f ? Conditions.VOLATILES_ABUNDANT : Conditions.VOLATILES_PLENTIFUL);
            market.addCondition(Conditions.RARE_ORE_MODERATE);
        } else if (lower.contains("volcanic") || lower.contains("lava")) {
            market.addCondition(Conditions.DENSE_ATMOSPHERE);
            market.addCondition(Conditions.VERY_HOT);
            market.addCondition(Conditions.EXTREME_TECTONIC_ACTIVITY);
            market.addCondition(random.nextFloat() < 0.50f ? Conditions.ORE_RICH : Conditions.ORE_ULTRARICH);
            market.addCondition(Conditions.RARE_ORE_ABUNDANT);
        } else if (lower.contains("desert") || lower.contains("arid")) {
            market.addCondition(Conditions.HABITABLE);
            market.addCondition(Conditions.HOT);
            market.addCondition(Conditions.FARMLAND_POOR);
            market.addCondition(Conditions.ORE_MODERATE);
            if (random.nextFloat() < 0.50f) market.addCondition(Conditions.ORGANICS_TRACE);
        } else if (lower.contains("tundra")) {
            market.addCondition(Conditions.HABITABLE);
            market.addCondition(Conditions.COLD);
            market.addCondition(Conditions.FARMLAND_POOR);
            market.addCondition(Conditions.ORE_MODERATE);
            market.addCondition(Conditions.ORGANICS_COMMON);
        } else {
            // Terran, Jungle, Water, Habitable
            market.addCondition(Conditions.HABITABLE);
            market.addCondition(Conditions.MILD_CLIMATE);
            market.addCondition(random.nextFloat() < 0.50f ? Conditions.FARMLAND_RICH : Conditions.FARMLAND_BOUNTIFUL);
            market.addCondition(Conditions.ORGANICS_ABUNDANT);
        }
    }

    private String RomanNumeral(int num) {
        switch (num) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            default: return "VII";
        }
    }
}
