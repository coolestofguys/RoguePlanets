package roguePlanets;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

/**
 * Compatibility with Starship Legends (optional; does nothing when it is not installed).
 *
 * Starship Legends lowers a captain's loyalty to a ship each time that ship is disabled, and at its three lowest
 * levels it caps the ship's maximum CR (-15%, -30%, -60%) and speeds up CR decay. Ellis Hale can never be moved off
 * the Afterlight, so that spiral could never be undone. Here his loyalty to the Afterlight is never allowed to fall
 * below Indifferent (no penalty, no bonus). It can still rise as normal.
 *
 * Uses Starship Legends' own public RepRecord.getOrCreate / getLoyalty / setLoyalty (the same calls its SetLoyalty
 * console command uses), looked up at runtime so Rogue Planets builds and runs without it.
 */
public final class StarshipLegendsCompat {

    private static final Logger log = Global.getLogger(StarshipLegendsCompat.class);
    private static final String MOD_ID = "sun_starship_legends";
    /** Starship Legends' loyalty scale runs -4 (Mutinous) .. +4 (Inspired); 0 is Indifferent. */
    private static final int FLOOR = 0;
    /** Ordinal of LoyaltyLevel.INDIFFERENT in Starship Legends' enum (MUTINOUS=0 ... INDIFFERENT=4). */
    private static final int FLOOR_ORDINAL = 4;

    private static boolean checked;
    private static boolean available;
    private static boolean warned;
    private static MethodHandle getOrCreate;
    private static MethodHandle getLoyalty;
    private static MethodHandle setLoyalty;

    private StarshipLegendsCompat() {}

    private static boolean init() {
        if (checked) return available;
        checked = true;
        try {
            if (!Global.getSettings().getModManager().isModEnabled(MOD_ID)) return false;
            Class<?> rep = Class.forName("starship_legends.RepRecord", true, Global.getSettings().getScriptClassLoader());
            Class<?> level = Class.forName("starship_legends.LoyaltyLevel", true, Global.getSettings().getScriptClassLoader());
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            getOrCreate = l.findStatic(rep, "getOrCreate", MethodType.methodType(rep, FleetMemberAPI.class));
            getLoyalty = l.findVirtual(rep, "getLoyalty", MethodType.methodType(level, PersonAPI.class));
            setLoyalty = l.findVirtual(rep, "setLoyalty", MethodType.methodType(void.class, PersonAPI.class, int.class));
            available = true;
            log.info("Rogue Planets: Starship Legends found; Ellis Hale's loyalty to the Afterlight will not fall below Indifferent.");
        } catch (Throwable t) {
            log.warn("Rogue Planets: Starship Legends is enabled but its loyalty hooks could not be found: " + t);
        }
        return available;
    }

    /** Keeps the captain's Starship Legends loyalty to this ship at Indifferent or better. */
    public static void keepLoyal(FleetMemberAPI ship, PersonAPI captain) {
        if (ship == null || captain == null || captain.isDefault()) return;
        if (!init()) return;
        try {
            Object record = getOrCreate.invoke(ship);
            if (record == null) return;
            Object level = getLoyalty.invoke(record, captain);
            if (level instanceof Enum && ((Enum<?>) level).ordinal() < FLOOR_ORDINAL) {
                setLoyalty.invoke(record, captain, FLOOR);
                log.info("Rogue Planets: restored " + captain.getNameString() + "'s loyalty to the "
                        + ship.getShipName() + " from " + ((Enum<?>) level).name() + " to INDIFFERENT (Starship Legends).");
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                log.warn("Rogue Planets: could not adjust Starship Legends loyalty: " + t);
            }
        }
    }
}
