package roguePlanets;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;

/**
 * The eerie extras of the Violet Dwarf system: derelicts, a hidden dark mass and the memory keys the
 * VioletSystemScript uses to track clues. Everything is placed once, when the system is generated.
 */
public final class VioletSystemContent {

    private VioletSystemContent() {}

    public static final Color VIOLET_TEXT = new Color(190, 130, 255);

    // Sector memory keys (never rename: they are stored in saves).
    public static final String KEY_SYSTEM_ID = "$violet_system_id";
    public static final String KEY_STATION_ID = "$violet_station_id";
    public static final String KEY_SHIP_ID = "$violet_ship_id";
    public static final String KEY_MASS_ID = "$violet_mass_id";
    public static final String KEY_PLANET_ID = "$violet_planet_id";
    public static final String KEY_CLUE_STATION = "$violet_clue_station";
    public static final String KEY_CLUE_SHIP = "$violet_clue_ship";
    public static final String KEY_CLUE_MASS = "$violet_clue_mass";
    public static final String KEY_CLUE_PLANET = "$violet_clue_planet";
    public static final String KEY_VAULT_SPAWNED = "$violet_vault_spawned";
    public static final String KEY_ENTRY_COUNT = "$violet_entry_count";
    public static final String PHANTOM_FLAG = "$violetPhantom";
    public static final String KEY_PHANTOM_SPAWNED = "$violet_phantom_spawned";
    public static final String KEY_DISTRESS_SPAWNED = "$violet_distress_spawned";
    public static final String KEY_GHOST_TIME = "$violet_ghost_timestamp";

    public static void addFeatures(StarSystemAPI system, PlanetAPI star, PlanetAPI watchedPlanet,
                                   float ringInner, float ringTotal, Random rand) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(KEY_SYSTEM_ID, system.getId());
        mem.set(KEY_PLANET_ID, watchedPlanet.getId());

        // 1. The silent habitat: intact, powered, empty.
        SectorEntityToken station = BaseThemeGenerator.addSalvageEntity(system, Entities.ORBITAL_HABITAT, Factions.NEUTRAL);
        station.setName("Silent Habitat");
        station.setCustomDescriptionId("violet_silent_habitat");
        float r1 = freeOrbit(system, star, star.getRadius() + 3500f, 300f);
        station.setCircularOrbit(star, rand.nextFloat() * 360f, r1, r1 / 12f);
        mem.set(KEY_STATION_ID, station.getId());

        // 2. A survey ship that was already here.
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(system, Entities.DERELICT_SURVEY_SHIP, Factions.NEUTRAL);
        ship.setName("Survey Ship \"Counting\"");
        ship.setCustomDescriptionId("violet_derelict_ship");
        float r2 = freeOrbit(system, star, r1 + 1400f, 300f);
        ship.setCircularOrbit(star, rand.nextFloat() * 360f, r2, r2 / 14f);
        mem.set(KEY_SHIP_ID, ship.getId());

        // 3. A dark mass hiding in the rings: barely visible to sensors until you are close.
        SectorEntityToken mass = BaseThemeGenerator.addSalvageEntity(system, Entities.EQUIPMENT_CACHE_SMALL, Factions.NEUTRAL);
        mass.setName("Unregistered Mass");
        mass.setCustomDescriptionId("violet_dark_mass");
        float rm = ringInner + ringTotal / 2f;
        mass.setCircularOrbit(star, rand.nextFloat() * 360f, rm, rm / 17f);
        mem.set(KEY_MASS_ID, mass.getId());
    }

    /** Spawns the hidden payoff once all clues have been found. */
    public static SectorEntityToken spawnVault(StarSystemAPI system, Random rand) {
        PlanetAPI star = system.getStar();
        SectorEntityToken vault = BaseThemeGenerator.addSalvageEntity(system, Entities.LARGE_CACHE, Factions.NEUTRAL);
        vault.setName("The Convergence Cache");
        vault.setCustomDescriptionId("violet_convergence_cache");
        vault.addDropValue("basic", 40000);
        // in the heart of the system, hugging the star
        float r = star.getRadius() + 450f;
        vault.setCircularOrbit(star, rand.nextFloat() * 360f, r, r / 18f);
        vault.setDiscoverable(true);

        // The real prize: one unique, pristine ship drifting beside the cache.
        ShipVariantAPI wolf = Global.getSettings().getVariant("wolf_CS").clone();
        PerShipData psd = new PerShipData(wolf, ShipCondition.PRISTINE, "Afterlight", Factions.INDEPENDENT, 0f);
        psd.nameAlwaysKnown = true;
        psd.addDmods = false;
        psd.pruneWeapons = false;
        SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL,
                new DerelictShipData(psd, false));
        wreck.getMemoryWithoutUpdate().set("$violetAfterlight", true);
        wreck.setDiscoverable(true);
        wreck.setCircularOrbit(star, vault.getCircularOrbitAngle() + 5f, r, r / 18f);
        return vault;
    }

    /** Inner radius of a free orbit band of the given width, at or beyond startRadius. */
    public static float freeOrbit(StarSystemAPI system, PlanetAPI star, float startRadius, float width) {
        List<Float> used = new ArrayList<Float>();
        for (SectorEntityToken e : system.getAllEntities()) {
            if (e == star || e.getOrbitFocus() != star) continue;
            float r = e.getCircularOrbitRadius();
            if (r > 0f) used.add(r);
        }
        float radius = startRadius;
        boolean clear = false;
        int guard = 0;
        while (!clear && guard++ < 200) {
            clear = true;
            for (float r : used) {
                if (r > radius - 450f && r < radius + width + 450f) {
                    radius = r + 500f;
                    clear = false;
                    break;
                }
            }
        }
        return radius;
    }
}
