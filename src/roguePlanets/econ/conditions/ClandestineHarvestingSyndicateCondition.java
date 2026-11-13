package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClandestineHarvestingSyndicateCondition extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    private static final org.apache.log4j.Logger log = Global.getLogger(ClandestineHarvestingSyndicateCondition.class);

    public static final float STABILITY_PENALTY = -2f;
    public static final float RESPAWN_DAYS = 200f; // Reverted back to 200 days for release balance
    /** If a fleet could not be built, try again after this many days instead of waiting out the whole timer. */
    public static final float RETRY_DAYS = 10f;
    /** Growth points lost per colony size above 2, for colonies of size 3 and up. */
    public static final float GROWTH_PENALTY_PER_SIZE = 5f;

    public static final String SPAWN_TIMER_KEY = "$clandestine_harvesting_timer";
    public static final String SYNDICATE_FLEET_ID = "$clandestine_harvesting_fleet_id";
    public static final String SPAWN_NOTIFIED_KEY = "$clandestine_harvesting_notified";

    /** Stability and Organs bonuses are re-checked once a day (the economy also re-applies them on every refresh). */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);
    /** The ambush fleet is looked after about twice a second; elapsed time is carried over so the respawn timer stays exact. */
    private final IntervalUtil ambushCheck = new IntervalUtil(0.5f, 0.5f);
    private float pendingAmount = 0f;

    public int getScaleValue() {
        if (market == null) return 1;
        // Scales from 1 at size 3 up to 4 at size 6 (size - 2)
        return Math.max(1, market.getSize() - 2);
    }

    private void applyBonuses(String id) {
        if (market == null) return;

        // 1. -2 Stability
        market.getStability().modifyFlat(id, STABILITY_PENALTY, "Clandestine Harvesting Syndicate");

        int scale = getScaleValue();

        // 2. Scaled supply for Harvested Organs via Spaceport or Megaport
        Industry port = market.getIndustry(Industries.SPACEPORT);
        if (port == null) {
            port = market.getIndustry(Industries.MEGAPORT);
        }

        if (port != null && port.isFunctional()) {
            MutableCommodityQuantity organs = port.getSupply(Commodities.ORGANS);
            if (organs != null && organs.getQuantity() != null) {
                organs.getQuantity().modifyFlat(id, scale, "Clandestine Harvesting Syndicate");
            }
        }
    }

    private void unapplyBonuses(String id) {
        if (market == null) return;

        market.getStability().unmodify(id);

        // Clear Harvested Organs supply modifiers
        for (String indId : new String[]{Industries.SPACEPORT, Industries.MEGAPORT}) {
            Industry port = market.getIndustry(indId);
            if (port != null) {
                MutableCommodityQuantity organs = port.getSupply(Commodities.ORGANS);
                if (organs != null && organs.getQuantity() != null) {
                    organs.getQuantity().unmodify(id);
                }
            }
        }
    }

    /** The cost of the trade: -5 growth points for every colony size above 2, from size 3 up. */
    @Override
    public void modifyIncoming(MarketAPI m, PopulationComposition incoming) {
        if (m == null || incoming == null || m != market || m.getSize() < 3) return;
        incoming.getWeight().modifyFlat("rp_harvest_growth", -GROWTH_PENALTY_PER_SIZE * (m.getSize() - 2),
            "Mysterious disappearances");
    }

    @Override
    public void apply(String id) {
        applyBonuses(id);
        if (market != null) market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply(String id) {
        unapplyBonuses(id);
        if (market != null) market.removeTransientImmigrationModifier(this);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;

        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            applyBonuses(getModId());
        }

        pendingAmount += amount;
        ambushCheck.advance(amount);
        if (ambushCheck.intervalElapsed()) {
            checkAmbushLifecycle(pendingAmount);
            pendingAmount = 0f;
        }
    }

    private void checkAmbushLifecycle(float amount) {
        if (market == null || !market.isPlayerOwned() || !market.isInEconomy()) return;

        SectorEntityToken primary = market.getPrimaryEntity();
        if (primary == null || primary.getStarSystem() == null) return;

        StarSystemAPI system = primary.getStarSystem();

        // Locate existing Interdiction Group fleet in the star system
        CampaignFleetAPI existingFleet = null;
        if (market.getMemoryWithoutUpdate().contains(SYNDICATE_FLEET_ID)) {
            String existingId = market.getMemoryWithoutUpdate().getString(SYNDICATE_FLEET_ID);
            for (CampaignFleetAPI f : system.getFleets()) {
                if (existingId != null && existingId.equals(f.getId()) && f.isAlive()) {
                    existingFleet = f;
                    break;
                }
            }
        }

        if (existingFleet == null && market.getMemoryWithoutUpdate().contains(SYNDICATE_FLEET_ID)) {
            log.info("Interdiction Group for " + market.getName() + " is gone from " + system.getName() + " (destroyed or removed); its respawn timer starts now.");
            market.getMemoryWithoutUpdate().unset(SYNDICATE_FLEET_ID);
        }

        // Active behavior: orbit colony passively, pursue player when spotted, return to orbit when player leaves
        if (existingFleet != null) {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            boolean playerInSystem = playerFleet != null && playerFleet.getContainingLocation() == system;

            if (playerInSystem) {
                float dist = Misc.getDistance(existingFleet.getLocation(), playerFleet.getLocation());
                if (dist < 3200f) {
                    // Player is in detection range -> Intercept and attack
                    if (existingFleet.getCurrentAssignment() == null ||
                        existingFleet.getCurrentAssignment().getTarget() != playerFleet) {
                        existingFleet.clearAssignments();
                        existingFleet.addAssignment(
                            FleetAssignment.INTERCEPT,
                            playerFleet,
                            1000f,
                            "hunting bio-harvesting syndicate"
                        );
                    }

                    // Dynamically set hostile only when actively closing in on the player
                    if (dist < 1800f) {
                        existingFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
                    }
                } else if (dist > 4500f) {
                    // Player has fled or is far away -> De-escalate hostility so patrols ignore it, return to orbit
                    existingFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
                    if (existingFleet.getCurrentAssignment() == null ||
                        existingFleet.getCurrentAssignment().getTarget() != primary) {
                        existingFleet.clearAssignments();
                        existingFleet.addAssignment(
                            FleetAssignment.ORBIT_PASSIVE,
                            primary,
                            1000000f,
                            "standing sentinel over harvesting operations"
                        );
                    }
                }
            } else {
                // Player is out of system -> Maintain neutral sentinel orbit over colony, unsetting hostility
                existingFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
                if (existingFleet.getCurrentAssignment() == null ||
                    existingFleet.getCurrentAssignment().getTarget() != primary) {
                    existingFleet.clearAssignments();
                    existingFleet.addAssignment(
                        FleetAssignment.ORBIT_PASSIVE,
                        primary,
                        1000000f,
                        "standing sentinel over harvesting operations"
                    );
                }
            }
            return;
        }

        // Advance 200-day respawn timer when no active fleet exists
        float days = Global.getSector().getClock().convertToDays(amount);
        float currentTimer = market.getMemoryWithoutUpdate().getFloat(SPAWN_TIMER_KEY);
        currentTimer += days;

        if (currentTimer >= RESPAWN_DAYS) {
            boolean spawned = spawnInterdictionGroup(system, primary);
            // a failed build is retried soon, instead of costing the whole timer
            market.getMemoryWithoutUpdate().set(SPAWN_TIMER_KEY, spawned ? 0f : RESPAWN_DAYS - RETRY_DAYS);
        } else {
            market.getMemoryWithoutUpdate().set(SPAWN_TIMER_KEY, currentTimer);
        }
    }

    /**
     * True if any word of the text matches a blacklist term. Short terms must match a whole word, longer ones can be
     * the start of a word, so "ai" no longer catches names like "Domain" or "Raiders".
     */
    private static boolean matchesBlacklist(String text, String[] terms) {
        if (text == null) return false;
        for (String word : text.toLowerCase().split("[^a-z0-9]+")) {
            if (word.length() == 0) continue;
            for (String term : terms) {
                if (term.length() <= 3 ? word.equals(term) : word.startsWith(term)) return true;
            }
        }
        return false;
    }

    private boolean spawnInterdictionGroup(StarSystemAPI system, SectorEntityToken primary) {
        int colonySize = Math.max(3, market.getSize());
        // Progression: Size 3: 50 pts, Size 4: 100 pts, Size 5: 150 pts, Size 6: 200 pts
        float combatPoints = (colonySize - 2) * 50f;

        // Dynamically find all loaded human factions (including modded factions like UAF, Iron Shell, Mayasura, Roider, etc.)
        // while strictly filtering out non-human, alien, automated, boss, or abyssal factions
        List<String> validHumanFactions = new ArrayList<String>();
        String[] blacklist = new String[]{
            "remnant", "omega", "derelict", "alien", "threat", "abyss",
            "unknown", "sleeper", "spire", "neutral", "player", "automated",
            "creature", "monster", "ai", "drone", "beast", "hive", "infestation",
            "dormant", "horror"
        };

        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f == null) continue;
            boolean blacklisted = matchesBlacklist(f.getId(), blacklist)
                || matchesBlacklist(f.getDisplayNameWithArticle(), blacklist);

            if (!blacklisted && f.getDoctrine() != null && !f.isPlayerFaction()) {
                validHumanFactions.add(f.getId());
            }
        }

        // Safe fallback pool if faction list is unexpectedly restricted
        if (validHumanFactions.isEmpty()) {
            validHumanFactions.add(Factions.HEGEMONY);
            validHumanFactions.add(Factions.PERSEAN);
            validHumanFactions.add(Factions.TRITACHYON);
            validHumanFactions.add(Factions.DIKTAT);
            validHumanFactions.add(Factions.INDEPENDENT);
            validHumanFactions.add(Factions.MERCENARY);
        }

        // Try a few source factions until one actually produces a fleet with ships in it. With many mods installed, some
        // factions have no usable ships and would give an empty fleet, which the game removes at once.
        Random pickRandom = new Random();
        CampaignFleetAPI fleet = null;
        String chosenSourceFaction = null;
        for (int attempt = 0; attempt < 8 && fleet == null; attempt++) {
            chosenSourceFaction = validHumanFactions.get(pickRandom.nextInt(validHumanFactions.size()));

            // Passing 'null' instead of 'market' prevents the fleet from inheriting your fledgling colony's sub-zero local manufacturing penalty.
            FleetParamsV3 params = new FleetParamsV3(
                null, // Decoupled from the colony market's low production quality
                primary.getLocationInHyperspace(),
                chosenSourceFaction,
                1.2f, // pinned quality: pristine military grade, no D-mods
                FleetTypes.PATROL_LARGE,
                combatPoints,
                0f,
                0f,
                0f,
                0.25f,
                0f,
                1.5f // High quality modifier: suppresses D-mods
            );

            params.ignoreMarketFleetSizeMult = true; // the points below are the real fleet size, whatever market the builder picks
            params.averageSMods = 1;          // Base 1 S-mod allocated across ships
            params.officerNumberMult = 1.4f;  // Officer count
            params.officerLevelBonus = 2;     // Veteran officers

            try {
                fleet = FleetFactoryV3.createFleet(params);
            } catch (Throwable t) {
                log.warn("Interdiction Group: building a fleet from " + chosenSourceFaction + " failed: " + t);
                fleet = null;
            }
            if (fleet == null || fleet.getFleetData() == null || fleet.getFleetData().getNumMembers() <= 0) {
                log.warn("Interdiction Group: " + chosenSourceFaction + " produced no usable fleet, trying another faction.");
                fleet = null;
            }
        }
        if (fleet == null) {
            log.warn("Interdiction Group: no faction produced a usable fleet for " + market.getName() + "; retrying in " + (int) RETRY_DAYS + " days.");
            return false;
        }

        // Inflate variants immediately so hullmods and S-mods are instantiated
        fleet.inflateIfNeeded();

        // Refine S-mods to achieve roughly 0.5 average (1 out of every 2 ships keeps its S-mod)
        if (fleet.getFleetData() != null) {
            Random rand = new Random();
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                if (member.getVariant() != null) {
                    // ~50% chance for a ship to retain its S-mod(s); otherwise strip to achieve 0.5 fleet average
                    if (rand.nextFloat() >= 0.5f) {
                        for (String smod : new ArrayList<String>(member.getVariant().getSMods())) {
                            member.getVariant().removePermaMod(smod);
                        }
                    }
                }
            }
        }

        // Re-align spawned fleet to MERCENARY faction
        fleet.setFaction(Factions.MERCENARY, true);
        fleet.setName("Interdiction Group 404");
        fleet.setNoFactionInName(true);

        // Stealth / Go Dark behavior
        fleet.setTransponderOn(false);

        // Immortality & System Lock: Prevents Starsector from despawning or jumping out
        fleet.getMemoryWithoutUpdate().set("$noDespawn", true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        // Patrol Avoidance & Player-Only Pursuit:
        // Do NOT set MEMORY_KEY_MAKE_HOSTILE globally at spawn!
        // Mercenary faction is neutral to Player Faction (0.0 rep), meaning your faction patrols will completely ignore it.
        // FLEET_IGNORES_OTHER_FLEETS and FLEET_IGNORED_BY_OTHER_FLEETS make it ignore other fleets and be ignored by them.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);

        // Spawn into orbit around the colony primary entity
        system.addEntity(fleet);
        fleet.setLocation(primary.getLocation().x + 600f, primary.getLocation().y + 600f);

        // Default behavior: orbit colony passively until player is spotted
        fleet.addAssignment(
            FleetAssignment.ORBIT_PASSIVE,
            primary,
            1000000f,
            "standing sentinel over harvesting operations"
        );

        market.getMemoryWithoutUpdate().set(SYNDICATE_FLEET_ID, fleet.getId());
        log.info("Interdiction Group spawned at " + market.getName() + " in " + system.getName() + ": "
            + fleet.getFleetData().getNumMembers() + " ships from " + chosenSourceFaction + " stock, at "
            + (int) fleet.getLocation().x + "," + (int) fleet.getLocation().y);

        // Display UI notification strictly on the first spawn; suppress on all subsequent respawns
        if (!market.getMemoryWithoutUpdate().getBoolean(SPAWN_NOTIFIED_KEY)) {
            if (Global.getSector().getCampaignUI() != null) {
                Global.getSector().getCampaignUI().addMessage(
                    "Sensors detect a dark mercenary fleet, Interdiction Group 404, taking up sentinel orbit around " + market.getName() + "!",
                    Misc.getNegativeHighlightColor()
                );
            }
            market.getMemoryWithoutUpdate().set(SPAWN_NOTIFIED_KEY, true);
        }
        return true;
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (!Global.CODEX_TOOLTIP_MODE) {
            createTooltipBody(tooltip, expanded);
            return;
        }
        // Codex page: the codex shows the name itself, and there may be no real colony behind it
        MarketAPI real = market;
        try {
            if (market == null) market = CodexTooltip.placeholderMarket();
            createTooltipBody(tooltip, expanded);
        } catch (Throwable t) {
            CodexTooltip.fallback(tooltip);
        } finally {
            market = real;
        }
    }

    private void createTooltipBody(TooltipMakerAPI tooltip, boolean expanded) {
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Clandestine Harvesting Syndicate");
        tooltip.addPara(
            "Operating out of the underbelly of colonial population centers or subterranean medical complexes, a powerful criminal cartel or unregulated corporate conglomerate runs a massive, high-volume biological processing grid. Driven by off-world black-market demand for transplant tissues, neural tissue, and vat-grown clone organs, the facility funnels a steady stream of illegal bio-cargo into smuggling routes while exploiting colonial labor.",
            10f
        );

        tooltip.addSectionHeading("Syndicate Exploitation & Black-Market Trade", Alignment.MID, 10f);

        int scale = getScaleValue();
        tooltip.addPara("Harvested Organs production: %s", 10f,
            Misc.getPositiveHighlightColor(),
            "+" + scale + " (supplied by a working Spaceport or Megaport, scales with colony size)"
        );

        tooltip.addPara("Colony stability: %s", 5f,
            Misc.getNegativeHighlightColor(),
            "" + (int) STABILITY_PENALTY
        );

        if (market != null && market.getSize() >= 3) {
            tooltip.addPara("Colony growth (mysterious disappearances): %s", 5f,
                Misc.getNegativeHighlightColor(),
                "-" + (int) (GROWTH_PENALTY_PER_SIZE * (market.getSize() - 2)) + " (5 for each colony size above 2)"
            );
        } else {
            tooltip.addPara("Colony growth (mysterious disappearances): %s", 5f,
                Misc.getNegativeHighlightColor(),
                "-5 for each colony size above 2, from size 3"
            );
        }

        tooltip.addSectionHeading("Unwanted Attention", Alignment.MID, 10f);

        tooltip.addPara(
            "Whispers through encrypted comms channels suggest that profiting from these illicit bio-operations will inevitably draw dangerous scrutiny from deep space.",
            10f
        );
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}