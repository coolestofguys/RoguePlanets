package roguePlanets;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Runs while the game is loaded and drives the Violet Dwarf system's strangeness: entry message,
 * sensor interference, clue tracking, the hidden cache and the follower that vanishes when approached.
 */
public class VioletSystemScript implements EveryFrameScript {

    private static final Logger log = Global.getLogger(VioletSystemScript.class);

    private static final String MOD_ID = "roguePlanets_violet_light";
    private static final float SENSOR_MULT = 0.6f;
    private static final float CLUE_RANGE = 650f;
    private static final float GHOST_VANISH_RANGE = 350f;
    private static final float GHOST_COOLDOWN_DAYS = 25f;

    private static final String[] ENTRY_LINES = {
        "The light here is the wrong color. Your instruments agree on this, and on nothing else.",
        "You have been in this system before. The logs say you have not.",
        "Every shadow on your bridge points toward the star.",
        "The glow does not flicker. Stars flicker.",
        "Somewhere in the system, something noted your arrival."
    };

    private CampaignFleetAPI ghost;
    private CampaignFleetAPI remnantGhost;
    private float remnantTimer = -1f;
    private boolean inside = false;
    private float interval = 0f;
    private float ghostTimer = -1f;   // seconds until the next follower appears
    private float distressTimer = -1f; // seconds until the distress call appears
    private float doubleTimer = -1f;   // seconds until the doppelganger patrol appears
    private float phantomTimer = -1f;  // seconds until the pirate armada appears
    private final Random rand = new Random();

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String systemId = mem.getString(VioletSystemContent.KEY_SYSTEM_ID);
        if (systemId == null) return;

        boolean here = player.getContainingLocation() != null
                && systemId.equals(player.getContainingLocation().getId());

        if (!here) {
            if (inside) {
                inside = false;
                player.getStats().getSensorRangeMod().unmodifyMult(MOD_ID);
                dismissGhost();
                if (remnantGhost != null) { remnantGhost.despawn(); remnantGhost = null; }
            }
            return;
        }

        StarSystemAPI system = (StarSystemAPI) player.getContainingLocation();

        if (!inside) {
            inside = true;
            int count = mem.getInt(VioletSystemContent.KEY_ENTRY_COUNT);
            mem.set(VioletSystemContent.KEY_ENTRY_COUNT, count + 1);
            Global.getSector().getCampaignUI().addMessage(
                    ENTRY_LINES[count % ENTRY_LINES.length], VioletSystemContent.VIOLET_TEXT);
        }

        player.getStats().getSensorRangeMod().modifyMult(MOD_ID, SENSOR_MULT, "Violet light interference");

        interval += amount;
        if (interval < 0.5f) return;
        interval = 0f;

        checkClue(player, mem, VioletSystemContent.KEY_STATION_ID, VioletSystemContent.KEY_CLUE_STATION,
                "The habitat's lights are on. Nothing aboard answers your hails.");
        checkClue(player, mem, VioletSystemContent.KEY_SHIP_ID, VioletSystemContent.KEY_CLUE_SHIP,
                "The survey ship's transponder repeats one phrase, over and over: \"the light came first\".");
        checkClue(player, mem, VioletSystemContent.KEY_MASS_ID, VioletSystemContent.KEY_CLUE_MASS,
                "A mass resolves out of the ring dust. It was not on your sensors a moment ago.");
        checkClue(player, mem, VioletSystemContent.KEY_PLANET_ID, VioletSystemContent.KEY_CLUE_PLANET,
                "The outer world turns against the flow of the system. Its dark side faces you.");

        maybeSpawnVault(system, mem);
        checkVaultRange(player, mem);
        updateGhost(system, player, mem);
        updateRemnantGhost(system, player, mem);
        updateDistress(system, player, mem);
        updatePhantom(system, player, mem);
        updateDouble(system, player, mem);
    }

    private void checkClue(CampaignFleetAPI player, MemoryAPI mem, String idKey, String clueKey, String message) {
        if (mem.getBoolean(clueKey)) return;
        String id = mem.getString(idKey);
        if (id == null) return;
        SectorEntityToken entity = player.getContainingLocation().getEntityById(id);
        if (entity == null) return;
        if (Misc.getDistance(player, entity) <= CLUE_RANGE) {
            mem.set(clueKey, true);
            Global.getSector().getCampaignUI().addMessage(message, VioletSystemContent.VIOLET_TEXT);
        }
    }

    private void checkVaultRange(CampaignFleetAPI player, MemoryAPI mem) {
        if (!mem.getBoolean(VioletSystemContent.KEY_VAULT_SPAWNED) || mem.getBoolean("$violet_vault_range_msg")) return;
        String id = mem.getString("$violet_vault_id");
        if (id == null) return;
        SectorEntityToken vault = player.getContainingLocation().getEntityById(id);
        if (vault == null) return;
        if (Misc.getDistance(player, vault) <= 900f) {
            mem.set("$violet_vault_range_msg", true);
            Global.getSector().getCampaignUI().addMessage(
                    "You are within range of the light.", VioletSystemContent.VIOLET_TEXT);
        }
    }

    private void maybeSpawnVault(StarSystemAPI system, MemoryAPI mem) {
        if (mem.getBoolean(VioletSystemContent.KEY_VAULT_SPAWNED)) return;
        if (!mem.getBoolean(VioletSystemContent.KEY_CLUE_STATION)
                || !mem.getBoolean(VioletSystemContent.KEY_CLUE_SHIP)
                || !mem.getBoolean(VioletSystemContent.KEY_CLUE_MASS)
                || !mem.getBoolean(VioletSystemContent.KEY_CLUE_PLANET)) return;

        mem.set(VioletSystemContent.KEY_VAULT_SPAWNED, true);
        SectorEntityToken vault = VioletSystemContent.spawnVault(system, new Random(system.getId().hashCode()));
        mem.set("$violet_vault_id", vault.getId());
        Global.getSector().getCampaignUI().addMessage(
                "Four fragments of one signal fall into place. Something new has appeared at the heart of the system, hard against the star.",
                VioletSystemContent.VIOLET_TEXT);
    }

    private void updateGhost(StarSystemAPI system, CampaignFleetAPI player, MemoryAPI mem) {
        if (ghost != null) {
            if (!ghost.isAlive() || ghost.getContainingLocation() != system) {
                ghost = null;
                return;
            }
            if (Misc.getDistance(player, ghost) < GHOST_VANISH_RANGE) {
                Global.getSector().getCampaignUI().addMessage(
                        "The contact that has been trailing you is gone. There is no wake.",
                        VioletSystemContent.VIOLET_TEXT);
                dismissGhost();
                mem.set(VioletSystemContent.KEY_GHOST_TIME, Global.getSector().getClock().getTimestamp());
            }
            return;
        }

        if (mem.contains(VioletSystemContent.KEY_GHOST_TIME)) {
            long last = mem.getLong(VioletSystemContent.KEY_GHOST_TIME);
            if (Global.getSector().getClock().getElapsedDaysSince(last) < GHOST_COOLDOWN_DAYS) return;
        }
        if (ghostTimer < 0f) ghostTimer = 40f + rand.nextFloat() * 80f;
        ghostTimer -= 0.5f;
        if (ghostTimer > 0f) return;
        ghostTimer = -1f;

        ghost = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Unlisted Contact", true);
        ghost.getFleetData().addFleetMember("hound_Hull");
        ghost.getFleetData().addFleetMember("hound_Hull");
        ghost.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        ghost.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        ghost.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        ghost.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        makeSpecial(ghost);
        ghost.setTransponderOn(false);
        ghost.getStats().getDetectedRangeMod().modifyMult("roguePlanets_ghost", 3f, "Violet light");
        ghost.getFleetData().setSyncNeeded();
        ghost.getFleetData().syncIfNeeded();
        log.info("Violet Dwarf: spawning follower, members=" + ghost.getFleetData().getNumMembers());

        float angle = rand.nextFloat() * 360f;
        float dist = 3500f;
        system.addEntity(ghost);
        ghost.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        ghost.addAssignment(FleetAssignment.FOLLOW, player, 100000f, "shadowing you");
    }

    private static final String KEY_REMNANT_GHOST_TIME = "$violet_remnant_ghost_timestamp";

    /** A Remnant-faction contact that shadows the player and is gone the moment it is approached. */
    private void updateRemnantGhost(StarSystemAPI system, CampaignFleetAPI player, MemoryAPI mem) {
        if (remnantGhost != null) {
            if (!remnantGhost.isAlive() || remnantGhost.getContainingLocation() != system) {
                remnantGhost = null;
                return;
            }
            if (Misc.getDistance(player, remnantGhost) < GHOST_VANISH_RANGE) {
                Global.getSector().getCampaignUI().addMessage(
                        "The Remnant contact is gone. Your sensors logged it listening to your own transmissions.",
                        VioletSystemContent.VIOLET_TEXT);
                remnantGhost.despawn();
                remnantGhost = null;
                mem.set(KEY_REMNANT_GHOST_TIME, Global.getSector().getClock().getTimestamp());
            }
            return;
        }

        if (mem.contains(KEY_REMNANT_GHOST_TIME)) {
            long last = mem.getLong(KEY_REMNANT_GHOST_TIME);
            if (Global.getSector().getClock().getElapsedDaysSince(last) < GHOST_COOLDOWN_DAYS) return;
        }
        if (remnantTimer < 0f) remnantTimer = 70f + rand.nextFloat() * 90f;
        remnantTimer -= 0.5f;
        if (remnantTimer > 0f) return;
        remnantTimer = -1f;

        FleetParamsV3 params = new FleetParamsV3(system.getLocation(), Factions.REMNANTS, 1f,
                FleetTypes.PATROL_MEDIUM, 90f, 0f, 0f, 0f, 0f, 0f, 0f);
        CampaignFleetAPI f = FleetFactoryV3.createFleet(params);
        if (f == null) {
            log.error("Violet Dwarf: could not create Remnant ghost fleet.");
            return;
        }
        f.setName("Unlisted Remnant Contact");
        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        fm.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        makeSpecial(f);
        f.setTransponderOn(false);
        f.getStats().getDetectedRangeMod().modifyMult("roguePlanets_remnant_ghost", 3f, "Violet light");

        float angle = rand.nextFloat() * 360f;
        float dist = 3500f;
        system.addEntity(f);
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        f.addAssignment(FleetAssignment.FOLLOW, player, 100000f, "listening");
        remnantGhost = f;
        log.info("Violet Dwarf: Remnant ghost spawned, members=" + f.getFleetData().getNumMembers());
    }

    private static final String DISTRESS_FLAG = "$violetDistress";
    private static final String DISTRESS_PAID = "$violetDistress_paid";

    /** Spawns the stranded freighter once, and removes it the moment its dialogue closes after the transfer. */
    private void updateDistress(StarSystemAPI system, CampaignFleetAPI player, MemoryAPI mem) {
        CampaignFleetAPI distress = null;
        for (CampaignFleetAPI f : system.getFleets()) {
            if (f.getMemoryWithoutUpdate().getBoolean(DISTRESS_FLAG)) {
                distress = f;
                break;
            }
        }

        if (distress != null) {
            boolean dialogOpen = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;
            if (!dialogOpen) updateDistressAmounts(distress, player);
            if (!dialogOpen && distress.getMemoryWithoutUpdate().getBoolean(DISTRESS_PAID)) {
                distress.despawn();
                log.info("Violet Dwarf: distress fleet vanished after transfer.");
                return;
            }
            // Like a vanilla distress call: once they can see you, they come to meet you and hail.
            if (!distress.getMemoryWithoutUpdate().getBoolean("$violetDistress_contacted")
                    && !distress.getMemoryWithoutUpdate().getBoolean("$violetDistress_noHail")) {
                SectorEntityToken.VisibilityLevel level = distress.getVisibilityLevelOfPlayerFleet();
                if (level != SectorEntityToken.VisibilityLevel.NONE
                        && level != SectorEntityToken.VisibilityLevel.SENSOR_CONTACT) {
                    distress.getMemoryWithoutUpdate().set("$violetDistress_contacted", true);
                    distress.clearAssignments();
                    distress.addAssignment(FleetAssignment.INTERCEPT, player, 30f, "approaching your fleet");
                }
            }
            return;
        }

        if (mem.getBoolean(VioletSystemContent.KEY_DISTRESS_SPAWNED)) return;
        if (distressTimer < 0f) distressTimer = 25f + rand.nextFloat() * 25f;
        distressTimer -= 0.5f;
        if (distressTimer > 0f) return;
        distressTimer = -1f;
        mem.set(VioletSystemContent.KEY_DISTRESS_SPAWNED, true);

        CampaignFleetAPI f = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, "Distressed Freighters", true);
        f.getFleetData().addFleetMember("hermes_Hull");
        f.getFleetData().addFleetMember("hermes_Hull");
        f.getFleetData().setSyncNeeded();
        f.getFleetData().syncIfNeeded();
        f.getMemoryWithoutUpdate().set(DISTRESS_FLAG, true);
        f.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        f.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        f.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        makeSpecial(f);
        unlimitedSensors(f);
        f.setTransponderOn(true);
        Misc.setFlagWithReason(f.getMemoryWithoutUpdate(), MemFlags.ENTITY_MISSION_IMPORTANT,
                "distress", true, -1f);

        float angle = rand.nextFloat() * 360f;
        float dist = 3500f;
        system.addEntity(f);
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        f.addAssignment(FleetAssignment.HOLD, null, 1000000f, "adrift, calling for help");

        log.info("Violet Dwarf: distress fleet spawned.");
    }

    /** Keeps the freighters' request at 5% of the player's current supplies and fuel (frozen while a dialogue is open). */
    private void updateDistressAmounts(CampaignFleetAPI distress, CampaignFleetAPI player) {
        if (distress.getMemoryWithoutUpdate().getBoolean(DISTRESS_PAID)) return;
        float haveSup = player.getCargo().getSupplies();
        float haveFuel = player.getCargo().getFuel();
        int sup = Math.min((int) haveSup, Math.max(1, (int) Math.ceil(haveSup * 0.05f)));
        int fuel = Math.min((int) haveFuel, Math.max(1, (int) Math.ceil(haveFuel * 0.05f)));
        MemoryAPI m = distress.getMemoryWithoutUpdate();
        m.set("$violetDistress_supplies", sup);
        m.set("$violetDistress_fuel", fuel);
        m.set("$violetDistress_suppliesDelta", -sup);
        m.set("$violetDistress_fuelDelta", -fuel);
        m.set("$violetDistress_canPay", (sup + fuel) > 0);
    }

    private static final String DOUBLE_FLAG = "$violetDouble";
    private static final String DOUBLE_DONE = "$violetDouble_done";
    private static final String KEY_DOUBLE_SPAWNED = "$violet_double_spawned";

    /** Spawns the doppelganger patrol once: a copy of the player's own fleet, commanded by a copy of the player. */
    private void updateDouble(StarSystemAPI system, CampaignFleetAPI player, MemoryAPI mem) {
        CampaignFleetAPI dbl = null;
        for (CampaignFleetAPI f : system.getFleets()) {
            if (f.getMemoryWithoutUpdate().getBoolean(DOUBLE_FLAG)) {
                dbl = f;
                break;
            }
        }
        if (dbl != null) {
            boolean dialogOpen = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;
            if (dbl.getMemoryWithoutUpdate().getBoolean(DOUBLE_DONE)) {
                if (!dialogOpen) {
                    dbl.despawn();
                    log.info("Violet Dwarf: doppelganger patrol vanished.");
                }
                return;
            }
            if (!dialogOpen) {
                applyPhantomBurn(dbl, player);
                // Same-faction fleets never start a hail on their own, so open the dialogue on contact.
                if (Misc.getDistance(dbl, player) < dbl.getRadius() + player.getRadius() + 120f) {
                    Global.getSector().getCampaignUI().showInteractionDialog(
                            new FleetInteractionDialogPluginImpl(), dbl);
                    return;
                }
                FleetAssignmentDataAPI cur = dbl.getAI() != null ? dbl.getAI().getCurrentAssignment() : null;
                if (cur == null || cur.getAssignment() != FleetAssignment.INTERCEPT || cur.getTarget() != player) {
                    dbl.clearAssignments();
                    dbl.addAssignment(FleetAssignment.INTERCEPT, player, 1000000f, "searching for you");
                }
            }
            return;
        }

        if (mem.getBoolean(KEY_DOUBLE_SPAWNED)) return;
        if (doubleTimer < 0f) doubleTimer = 60f + rand.nextFloat() * 60f;
        doubleTimer -= 0.5f;
        if (doubleTimer > 0f) return;
        doubleTimer = -1f;
        mem.set(KEY_DOUBLE_SPAWNED, true);

        PersonAPI me = Global.getSector().getPlayerPerson();
        String factionId = Global.getSector().getPlayerFaction().getId();
        CampaignFleetAPI f = Global.getFactory().createEmptyFleet(factionId, me.getName().getFullName() + "'s fleet", true);
        f.setNoFactionInName(true);

        PersonAPI cmdr = Global.getFactory().createPerson();
        cmdr.setName(new FullName(me.getName().getFirst(), me.getName().getLast(), me.getGender()));
        cmdr.setGender(me.getGender());
        cmdr.setPortraitSprite(me.getPortraitSprite());
        cmdr.setFaction(factionId);
        cmdr.setRankId(Ranks.SPACE_COMMANDER);
        cmdr.setPostId(Ranks.POST_FLEET_COMMANDER);

        FleetMemberAPI first = null;
        int count = 0;
        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            if (m.isFighterWing() || count >= 30) continue;
            FleetMemberAPI copy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, m.getVariant().clone());
            copy.getRepairTracker().setCR(0.7f);
            f.getFleetData().addFleetMember(copy);
            if (first == null || m.isFlagship()) first = copy;
            count++;
        }
        if (count == 0) {
            f.getFleetData().addFleetMember("hound_Hull");
        }
        f.setCommander(cmdr);
        if (first != null) {
            first.setCaptain(cmdr);
            f.getFleetData().setFlagship(first);
        }
        f.getFleetData().setSyncNeeded();
        f.getFleetData().syncIfNeeded();
        f.getFleetData().sort();

        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(DOUBLE_FLAG, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fm.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true);
        fm.set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
        makeSpecial(f);
        unlimitedSensors(f);
        f.setTransponderOn(true);

        float angle = rand.nextFloat() * 360f;
        float dist = 5000f;
        system.addEntity(f);
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        applyPhantomBurn(f, player);
        f.addAssignment(FleetAssignment.INTERCEPT, player, 1000000f, "searching for you");
        log.info("Violet Dwarf: doppelganger patrol spawned with " + count + " ships.");
    }

    /** Spawns the phantom pirate armada once: a normal-looking pirate fleet that hunts the player. */
    private void updatePhantom(StarSystemAPI system, CampaignFleetAPI player, MemoryAPI mem) {
        if (mem.getBoolean(VioletSystemContent.KEY_PHANTOM_SPAWNED)) {
            for (CampaignFleetAPI f : system.getFleets()) {
                if (f.getMemoryWithoutUpdate().getBoolean(VioletSystemContent.PHANTOM_FLAG)) {
                    applyPhantomBurn(f, player);
                    keepChasing(f, player);
                }
            }
            return;
        }
        if (phantomTimer < 0f) phantomTimer = 90f + rand.nextFloat() * 90f;
        phantomTimer -= 0.5f;
        if (phantomTimer > 0f) return;
        phantomTimer = -1f;
        mem.set(VioletSystemContent.KEY_PHANTOM_SPAWNED, true);

        FleetParamsV3 params = new FleetParamsV3(system.getLocation(), Factions.PIRATES, 1f,
                FleetTypes.PATROL_LARGE, 220f, 0f, 0f, 0f, 0f, 0f, 0f);
        CampaignFleetAPI f = FleetFactoryV3.createFleet(params);
        if (f == null) {
            log.error("Violet Dwarf: could not create phantom pirate fleet.");
            return;
        }
        f.setName("Pirate Armada");
        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(VioletSystemContent.PHANTOM_FLAG, true);
        fm.set(MemFlags.MEMORY_KEY_PIRATE, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fm.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, true);
        fm.set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
        makeSpecial(f);
        unlimitedSensors(f);

        float angle = rand.nextFloat() * 360f;
        float dist = 5000f;
        system.addEntity(f);
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * dist,
                player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * dist);
        f.addAssignment(FleetAssignment.INTERCEPT, player, 1000000f, "hunting you down");
        applyPhantomBurn(f, player);
        log.info("Violet Dwarf: phantom pirate armada spawned with " + f.getFleetData().getNumMembers() + " ships.");
    }

    private int phantomLogTick = 0;

    /** Makes sure the armada keeps hunting the player even if the fleet AI hands it a different job. */
    private void keepChasing(CampaignFleetAPI f, CampaignFleetAPI player) {
        FleetAssignmentDataAPI current = f.getAI() != null ? f.getAI().getCurrentAssignment() : null;
        if (current == null || current.getAssignment() != FleetAssignment.INTERCEPT || current.getTarget() != player) {
            f.clearAssignments();
            f.addAssignment(FleetAssignment.INTERCEPT, player, 1000000f, "hunting you down");
        }
        if (++phantomLogTick % 20 == 0) {
            log.info("Violet Dwarf: armada distance to player " + (int) Misc.getDistance(f, player)
                    + ", burn " + f.getCurrBurnLevel());
        }
    }

    private static final String PHANTOM_BURN_ID = "roguePlanets_phantom_burn";
    private static final float PHANTOM_BURN = 25f;
    private static final float PHANTOM_EMERGENCY_BURN = 30f;
    private static final float PHANTOM_EMERGENCY_RANGE = 3500f;

    /** Forces the armada to travel at burn 25, rising to 30 (an emergency burn) when it is close to you. */
    private void applyPhantomBurn(CampaignFleetAPI f, CampaignFleetAPI player) {
        float target = Misc.getDistance(f, player) < PHANTOM_EMERGENCY_RANGE ? PHANTOM_EMERGENCY_BURN : PHANTOM_BURN;
        float base = f.getFleetData().getMinBurnLevelUnmodified();
        f.getStats().getFleetwideMaxBurnMod().modifyFlat(PHANTOM_BURN_ID, target - base, "Phantom armada");
    }

    /** Special fleets ignore every other fleet and are ignored by every other fleet, but still deal with the player. */
    private static void makeSpecial(CampaignFleetAPI f) {
        MemoryAPI m = f.getMemoryWithoutUpdate();
        m.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        m.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        m.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true);
    }

    /** Effectively unlimited sensor range, so the fleet can always find the player. */
    private static void unlimitedSensors(CampaignFleetAPI f) {
        f.getStats().getSensorRangeMod().modifyFlat("roguePlanets_unlimited_sensors", 1000000f, "Violet light");
    }

    private void dismissGhost() {
        if (ghost != null) {
            ghost.despawn();
            ghost = null;
        }
    }
}
