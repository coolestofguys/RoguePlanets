package roguePlanets.econ.conditions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.ListMap;
import com.fs.starfarer.api.util.Misc;

import roguePlanets.intel.VaultProductionReportIntel;

public class ForgottenVaultWorldCondition extends BaseMarketConditionPlugin {

    public static final float UPKEEP_MULT = 10.0f;          // Upkeep x10
    public static final float STABILITY_PENALTY = -3f;
    public static final float ACCESSIBILITY_PENALTY = -0.50f;
    public static final float HAZARD_PENALTY = 0.50f;       // +50% hazard rating
    public static final float SHIP_HULL_CHANCE = 0.50f;     // Two 50% rolls

    public static final String VAULT_MONTHS_KEY = "$vault_excavation_months";
    public static final String VAULT_GRAND_PRIZE_GIVEN = "$vault_grand_prize_given";

    private static final String KEY_LAST_MONTH = "$vault_lastMonth";

    private transient IntervalUtil tracker = new IntervalUtil(1.0f, 1.0f);

    private int getCompletedMonths() {
        if (market == null) return 0;
        return market.getMemoryWithoutUpdate().getInt(VAULT_MONTHS_KEY);
    }

    private void incrementCompletedMonths() {
        if (market == null) return;
        int current = getCompletedMonths();
        market.getMemoryWithoutUpdate().set(VAULT_MONTHS_KEY, current + 1);
    }

    private int getMaxVaultMonths() {
        if (market == null) return 18;
        boolean isVast = market.hasCondition(Conditions.RUINS_VAST) || market.hasCondition("ruins_vast");
        return isVast ? 30 : 18;
    }

    private boolean isVaultExhausted() {
        return getCompletedMonths() >= getMaxVaultMonths();
    }

    private boolean isGrandPrizeDelivered() {
        if (market == null) return false;
        return market.getMemoryWithoutUpdate().getBoolean(VAULT_GRAND_PRIZE_GIVEN);
    }

    private void setGrandPrizeDelivered(boolean delivered) {
        if (market == null) return;
        market.getMemoryWithoutUpdate().set(VAULT_GRAND_PRIZE_GIVEN, delivered);
    }

    private void updateModifiers(String id) {
        if (market == null) return;

        Industry techMining = market.getIndustry(Industries.TECHMINING);
        boolean isActive = techMining != null && techMining.isFunctional() && !isVaultExhausted();

        if (isActive) {
            market.getStability().modifyFlat(id, STABILITY_PENALTY, "Forgotten Vault-World (Active Excavation)");
            market.getAccessibilityMod().modifyFlat(id, ACCESSIBILITY_PENALTY, "Forgotten Vault-World (Hazard Quarantines)");
            market.getHazard().modifyFlat(id, HAZARD_PENALTY, "Anomaly Radiation & Destabilized Vaults");
            techMining.getUpkeep().modifyMult(id, UPKEEP_MULT, "Vault Extraction");
        } else {
            market.getStability().unmodify(id);
            market.getAccessibilityMod().unmodify(id);
            market.getHazard().unmodify(id);
            if (techMining != null) {
                techMining.getUpkeep().unmodifyMult(id);
            }
        }
    }

    @Override
    public void apply(String id) {
        if (market != null) {
            updateModifiers(id);
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) {
            market.getStability().unmodify(id);
            market.getAccessibilityMod().unmodify(id);
            market.getHazard().unmodify(id);
            Industry techMining = market.getIndustry(Industries.TECHMINING);
            if (techMining != null) {
                techMining.getUpkeep().unmodifyMult(id);
            }
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null) return;

        if (tracker == null) {
            tracker = new IntervalUtil(1.0f, 1.0f);
        }

        // once a day is plenty: refresh the modifiers and look for the turn of the month
        tracker.advance(Global.getSector().getClock().convertToDays(amount));
        if (!tracker.intervalElapsed()) return;

        updateModifiers(getModId());
        try {
            checkMonthTurn();
        } catch (Throwable t) {
            Global.getLogger(ForgottenVaultWorldCondition.class).warn("Forgotten Vault monthly turn failed: " + t);
        }
    }

    // ---------------------------------------------------------------- the monthly turn

    /** At each turn of the month, while Tech Mining works and the vault still has something in it, the vault pays out. */
    private void checkMonthTurn() {
        int month = Global.getSector().getClock().getMonth();
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        if (!mem.contains(KEY_LAST_MONTH)) {
            mem.set(KEY_LAST_MONTH, month);
            return;
        }
        if (mem.getInt(KEY_LAST_MONTH) == month) return;
        mem.set(KEY_LAST_MONTH, month);

        // the vault pays only a colony the player owns: an AI faction's excavation neither uses up the vault nor delivers anything
        if (!market.isPlayerOwned()) return;

        Industry techMining = market.getIndustry(Industries.TECHMINING);
        if (techMining == null || !techMining.isFunctional()) return;

        CargoAPI storage = Misc.getStorageCargo(market);
        if (storage == null) return;

        if (isVaultExhausted()) {
            // an exhausted vault still owes its grand prize, if it was never handed over
            if (!isGrandPrizeDelivered()) {
                FleetMemberAPI flagship = generateVaultGrandPrizeCapital(new Random(), getHullPool());
                if (flagship != null) {
                    storage.getMothballedShips().addFleetMember(flagship);
                    announceGrandPrize(flagship);
                }
                // with no capital hull to give there is nothing to retry every month
                setGrandPrizeDelivered(true);
            }
            updateModifiers(getModId());
            return;
        }

        incrementCompletedMonths();
        int completedMonths = getCompletedMonths();
        int maxMonths = getMaxVaultMonths();
        boolean isFinalMonth = completedMonths >= maxMonths;
        float progress = (float) completedMonths / (float) maxMonths;

        float decayMult = Math.max(0.20f, 1.0f - (progress * 0.85f));
        float currentShipChance = SHIP_HULL_CHANCE * Math.max(0f, 1.0f - (progress * 1.15f));

        Random random = new Random();
        boolean isVast = market.hasCondition(Conditions.RUINS_VAST) || market.hasCondition("ruins_vast");

        // the lists of what the vault can hold are only built now that something is actually coming out
        Map<String, List<String>> hullPool = getHullPool();
        List<String> legalWeapons = getWeaponList();
        List<String> legalPackages = getPackageList();

        CargoAPI bonusCargo = Global.getFactory().createCargo(true);
        List<FleetMemberAPI> recoveredShips = new ArrayList<FleetMemberAPI>();

        CargoAPI roll1 = generateDistinctVaultRoll(random, isVast, decayMult, legalWeapons, hullPool, legalPackages);
        CargoAPI roll2 = generateDistinctVaultRoll(random, isVast, decayMult, legalWeapons, hullPool, legalPackages);
        if (roll1 != null && !roll1.isEmpty()) bonusCargo.addAll(roll1);
        if (roll2 != null && !roll2.isEmpty()) bonusCargo.addAll(roll2);

        for (int i = 0; i < 2; i++) {
            FleetMemberAPI ship = rollIntactHull(random, hullPool, currentShipChance);
            if (ship != null) {
                storage.getMothballedShips().addFleetMember(ship);
                recoveredShips.add(ship);
            }
        }

        FleetMemberAPI flagship = null;
        if (isFinalMonth && !isGrandPrizeDelivered()) {
            flagship = generateVaultGrandPrizeCapital(random, hullPool);
            if (flagship != null) {
                storage.getMothballedShips().addFleetMember(flagship);
                recoveredShips.add(flagship);
            }
            setGrandPrizeDelivered(true);
        }

        if (bonusCargo.isEmpty() && recoveredShips.isEmpty()) {
            if (isFinalMonth) updateModifiers(getModId());
            return;
        }

        storage.addAll(bonusCargo);

        if (market.isPlayerOwned()) {
            IntelManagerAPI intelManager = Global.getSector().getIntelManager();
            for (IntelInfoPlugin old : new ArrayList<IntelInfoPlugin>(intelManager.getIntel(VaultProductionReportIntel.class))) {
                if (old instanceof VaultProductionReportIntel && ((VaultProductionReportIntel) old).getMarket() == market) {
                    intelManager.removeIntel(old);
                }
            }
            intelManager.addIntel(new VaultProductionReportIntel(market, bonusCargo, recoveredShips));

            if (isFinalMonth) {
                if (flagship != null) {
                    announceGrandPrize(flagship);
                } else {
                    Global.getSector().getCampaignUI().addMessage(
                        "The main chamber of the ancient vault complex on " + market.getName() + " has been breached and recovery is now complete!",
                        Misc.getHighlightColor());
                }
            } else {
                String msg = "Excavation teams on " + market.getName() + " breached an ancient vault sector: bonus salvage secured in local storage!";
                if (recoveredShips.size() == 1) {
                    msg += " (Intact vessel recovered: " + recoveredShips.get(0).getHullSpec().getHullName() + ")";
                } else if (recoveredShips.size() >= 2) {
                    msg += " (Intact vessels recovered: " + recoveredShips.get(0).getHullSpec().getHullName() + ", " + recoveredShips.get(1).getHullSpec().getHullName() + ")";
                }
                Global.getSector().getCampaignUI().addMessage(msg, Misc.getPositiveHighlightColor());
            }
        }

        if (isFinalMonth) updateModifiers(getModId());
    }

    private void announceGrandPrize(FleetMemberAPI flagship) {
        if (!market.isPlayerOwned()) return;
        Global.getSector().getCampaignUI().addMessage(
            "The main chamber of the ancient vault complex on " + market.getName() + " has been breached and recovery is now complete! "
            + "Pristine capital hull recovered: " + flagship.getHullSpec().getHullName() + "-class (" + flagship.getShipName()
            + ") with 2 permanent S-mods delivered to local storage on " + market.getName() + "!",
            Misc.getHighlightColor());
    }

    // ---------------------------------------------------------------- what the vault can hold

    private boolean isCivilizedFaction(FactionAPI faction) {
        if (faction == null) return false;
        if (faction.isPlayerFaction()) return false;

        String id = faction.getId().toLowerCase();
        if (Factions.NEUTRAL.equals(id)) return false;
        if (Factions.REMNANTS.equals(id) || id.contains("remnant")) return false;
        if (Factions.DERELICT.equals(id) || id.contains("derelict")) return false;
        if (Factions.OMEGA.equals(id) || id.contains("omega")) return false;
        if (id.contains("shroud") || id.contains("abyss") || id.contains("sleeper") || id.contains("unknown") || id.contains("boss")) {
            return false;
        }

        return faction.isShowInIntelTab();
    }

    private static boolean looksForbidden(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("shroud") || lower.contains("abyss") || lower.contains("omega") || lower.contains("remnant")
            || lower.contains("threat") || lower.contains("dweller") || lower.contains("sleeper") || lower.contains("boss");
    }

    private boolean isLegalHull(ShipHullSpecAPI spec) {
        if (spec == null) return false;
        if (!spec.isBaseHull() || spec.isDefaultDHull() || spec.isDHull()) return false;
        if (spec.getHullSize() == HullSize.FIGHTER || spec.getHullSize() == HullSize.DEFAULT) return false;

        Set<ShipTypeHints> hints = spec.getHints();
        if (hints.contains(ShipTypeHints.STATION) || hints.contains(ShipTypeHints.MODULE)
            || hints.contains(ShipTypeHints.UNBOARDABLE) || hints.contains(ShipTypeHints.UNDER_PARENT)
            || hints.contains(ShipTypeHints.HIDE_IN_CODEX)) return false;

        if (looksForbidden(spec.getHullId()) || looksForbidden(spec.getManufacturer())) return false;

        if (spec.hasTag(Tags.RESTRICTED) || spec.hasTag(Tags.AUTOMATED_RECOVERABLE) || spec.hasTag(Tags.NO_DROP)
            || spec.hasTag(Tags.OMEGA) || spec.hasTag(Tags.THREAT) || spec.hasTag(Tags.DWELLER)) return false;
        if (spec.hasTag("no_drop") || spec.hasTag("no_bp_drop") || spec.hasTag("special_only")
            || spec.hasTag("remnant") || spec.hasTag("omega") || spec.hasTag("derelict") || spec.hasTag("auto_rec")
            || spec.hasTag("threat") || spec.hasTag("dweller")) return false;

        return true;
    }

    /**
     * Every hull the vault can hold, with the ready-made loadouts (the ones the autofit menu offers) it can come in.
     * Hulls known to a civilised faction are always in. Hulls added by mods that no faction knows are in too, as long
     * as nothing marks them as a boss ship, an Omega ship or something the player can never obtain; a hull that only
     * a non-civilised faction (remnants, derelicts and so on) knows is left out.
     */
    // The three lists are the same all session, so they are built the first time a vault pays out and kept after that
    private static Map<String, List<String>> cachedHullPool;
    private static List<String> cachedWeapons;
    private static List<String> cachedPackages;

    private Map<String, List<String>> getHullPool() {
        if (cachedHullPool == null) cachedHullPool = buildHullPool();
        return cachedHullPool;
    }

    private List<String> getWeaponList() {
        if (cachedWeapons == null) cachedWeapons = buildWeaponList();
        return cachedWeapons;
    }

    private List<String> getPackageList() {
        if (cachedPackages == null) cachedPackages = buildPackageList();
        return cachedPackages;
    }

    private Map<String, List<String>> buildHullPool() {
        Set<String> civilized = new HashSet<String>();
        Set<String> shunned = new HashSet<String>();
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (isCivilizedFaction(faction)) civilized.addAll(faction.getKnownShips());
            else shunned.addAll(faction.getKnownShips());
        }

        Map<String, List<String>> pool = new LinkedHashMap<String, List<String>>();
        ListMap<String> variantsByHull = Global.getSettings().getHullIdToVariantListMap();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            String hullId = spec.getHullId();
            if (!isLegalHull(spec)) continue;
            if (!civilized.contains(hullId) && shunned.contains(hullId)) continue;

            List<String> variants = new ArrayList<String>();
            List<String> all = variantsByHull.getList(hullId);
            if (all != null) {
                for (String variantId : all) {
                    if (variantId == null || variantId.endsWith("_Hull")) continue;
                    ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
                    if (variant != null && variant.isGoalVariant()) {
                        variants.add(variantId);
                    }
                }
            }
            if (!variants.isEmpty()) pool.put(hullId, variants);
        }
        return pool;
    }

    private List<String> buildWeaponList() {
        Set<String> weapons = new HashSet<String>();
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (isCivilizedFaction(faction)) weapons.addAll(faction.getKnownWeapons());
        }
        return new ArrayList<String>(weapons);
    }

    private List<String> buildPackageList() {
        List<String> packages = new ArrayList<String>();
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            String id = spec.getId();
            if (id == null || looksForbidden(id)) continue;
            String lower = id.toLowerCase();
            if (lower.endsWith("_package") || lower.contains("blueprint_package") || spec.hasTag("package") || spec.hasTag("blueprint_package")) {
                packages.add(id);
            }
        }
        return packages;
    }

    private CargoAPI generateDistinctVaultRoll(Random random, boolean isVast, float decayMult, List<String> legalWeapons,
                                               Map<String, List<String>> hullPool, List<String> legalPackages) {
        CargoAPI drop = Global.getFactory().createCargo(true);

        int metals = Math.round((60 + random.nextInt(isVast ? 120 : 60)) * decayMult);
        int supplies = Math.round((20 + random.nextInt(isVast ? 40 : 25)) * decayMult);
        int fuel = Math.round((20 + random.nextInt(isVast ? 40 : 25)) * decayMult);
        int machinery = Math.round((2 + random.nextInt(isVast ? 6 : 4)) * decayMult);

        if (metals > 0) drop.addCommodity(Commodities.METALS, metals);
        if (supplies > 0) drop.addCommodity(Commodities.SUPPLIES, supplies);
        if (fuel > 0) drop.addCommodity(Commodities.FUEL, fuel);
        if (machinery > 0) drop.addCommodity(Commodities.HEAVY_MACHINERY, machinery);

        if ((isVast || random.nextFloat() < 0.60f) && decayMult > 0.20f) {
            int rare = Math.round((10 + random.nextInt(isVast ? 35 : 20)) * decayMult);
            if (rare > 0) drop.addCommodity(Commodities.RARE_METALS, rare);
        }

        if (!legalWeapons.isEmpty() && decayMult > 0.25f) {
            int weaponCount = 1 + random.nextInt(isVast ? 3 : 2);
            for (int i = 0; i < weaponCount; i++) {
                String weaponId = legalWeapons.get(random.nextInt(legalWeapons.size()));
                drop.addWeapons(weaponId, 1);
            }
        }

        float bpChance = (isVast ? 0.70f : 0.50f) * decayMult;
        if (random.nextFloat() < bpChance) {
            if (!legalPackages.isEmpty() && random.nextFloat() < 0.30f) {
                String pkg = legalPackages.get(random.nextInt(legalPackages.size()));
                drop.addSpecial(new SpecialItemData(pkg, null), 1);
            } else if (!hullPool.isEmpty()) {
                List<String> ids = new ArrayList<String>(hullPool.keySet());
                drop.addSpecial(new SpecialItemData(Items.SHIP_BP, ids.get(random.nextInt(ids.size()))), 1);
            }
        }

        if (decayMult > 0.35f) {
            float coreRoll = random.nextFloat();
            if (isVast) {
                if (coreRoll < 0.15f) drop.addCommodity(Commodities.ALPHA_CORE, 1);
                else if (coreRoll < 0.45f) drop.addCommodity(Commodities.BETA_CORE, 1);
                else if (coreRoll < 0.85f) drop.addCommodity(Commodities.GAMMA_CORE, 1);
            } else {
                if (coreRoll < 0.05f) drop.addCommodity(Commodities.ALPHA_CORE, 1);
                else if (coreRoll < 0.25f) drop.addCommodity(Commodities.BETA_CORE, 1);
                else if (coreRoll < 0.60f) drop.addCommodity(Commodities.GAMMA_CORE, 1);
            }
        }

        return drop;
    }

    // ---------------------------------------------------------------- ships

    /** A brand new ship in one of the hull's ready-made loadouts: no D-mods, full CR, wrapped up in storage. */
    private FleetMemberAPI buildNewShip(Map<String, List<String>> hullPool, String hullId, Random random) {
        List<String> variants = hullPool.get(hullId);
        if (variants == null || variants.isEmpty()) return null;
        try {
            String variantId = variants.get(random.nextInt(variants.size()));
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            if (member == null) return null;
            FactionAPI indie = Global.getSector().getFaction(Factions.INDEPENDENT);
            if (indie != null) member.setShipName(indie.pickRandomShipName());
            return member;
        } catch (Throwable t) {
            Global.getLogger(ForgottenVaultWorldCondition.class).warn("Forgotten Vault could not build hull " + hullId + ": " + t);
            return null;
        }
    }

    private void finishShip(FleetMemberAPI member) {
        // the Magmatic Antimatter Forge only tempers hulls from the player's own production orders, not salvage
        try {
            member.getVariant().addTag(MagmaticAntimatterForgeCondition.PROCESSED_TAG);
        } catch (Throwable ignored) {
            // only a courtesy
        }
        try {
            member.updateStats();
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
            member.getRepairTracker().setMothballed(true);
        } catch (Throwable ignored) {
            // the ship is still a fine ship
        }
    }

    private FleetMemberAPI rollIntactHull(Random random, Map<String, List<String>> hullPool, float chance) {
        if (chance <= 0f || random.nextFloat() >= chance || hullPool.isEmpty()) return null;
        List<String> ids = new ArrayList<String>(hullPool.keySet());
        FleetMemberAPI ship = buildNewShip(hullPool, ids.get(random.nextInt(ids.size())), random);
        if (ship != null) finishShip(ship);
        return ship;
    }

    /** A pristine, fully fitted capital with 2 built-in S-mods. */
    private FleetMemberAPI generateVaultGrandPrizeCapital(Random random, Map<String, List<String>> hullPool) {
        List<String> capitals = new ArrayList<String>();
        for (String hullId : hullPool.keySet()) {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec != null && spec.getHullSize() == HullSize.CAPITAL_SHIP) capitals.add(hullId);
        }
        if (capitals.isEmpty()) return null;

        String hullId = capitals.get(random.nextInt(capitals.size()));
        FleetMemberAPI capital = buildNewShip(hullPool, hullId, random);
        if (capital == null) return null;

        ShipVariantAPI variant = capital.getVariant();
        ShipHullSpecAPI hullSpec = capital.getHullSpec();

        List<String> candidates = new ArrayList<String>();
        candidates.add("heavyarmor");
        candidates.add("reinforcedhull");
        candidates.add("fluxdistributor");
        candidates.add("fluxbreakers");
        candidates.add("armoredweapons");
        candidates.add("solar_shielding");
        candidates.add("insulatedengine");
        candidates.add("efficiency_overhaul");
        candidates.add("missleracks");
        candidates.add("advancedoptics");
        candidates.add("eccm");
        candidates.add("turretgyros");
        if (!variant.hasHullMod("dedicated_targeting_core")) candidates.add("targetingunit");

        ShieldType shield = hullSpec.getShieldType();
        if (shield == ShieldType.FRONT || shield == ShieldType.OMNI) {
            candidates.add("hardenedshieldemitter");
            candidates.add("extendedshieldemitter");
        }
        if (shield == ShieldType.OMNI) candidates.add("frontshield");
        if (hullSpec.getFighterBays() > 0) candidates.add("expanded_deck_crew");

        List<String> valid = new ArrayList<String>();
        for (String modId : candidates) {
            if (Global.getSettings().getHullModSpec(modId) != null && !variant.hasHullMod(modId)) valid.add(modId);
        }

        int added = 0;
        while (added < 2 && !valid.isEmpty()) {
            String chosen = valid.remove(random.nextInt(valid.size()));
            try {
                variant.addPermaMod(chosen, true);
                added++;
            } catch (Throwable ignored) {
                // try another
            }
        }

        finishShip(capital);
        return capital;
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Forgotten Vault-World");
        tooltip.addPara(
            "Deep beneath the surface crust of this dead world lies a titanic, sealed repository predating the Collapse: a legendary hoard of pristine hulls, hyper-advanced matrices, and forbidden schematics untouched for centuries. Ripping open its automated seals and scouring its sprawling subterranean databanks yields miraculous technological finds, but the process awakens unpredictable security constructs, erratic anomaly radiation, and superstitious panic among colonial work crews.",
            10f
        );

        boolean exhausted = isVaultExhausted();
        Industry techMining = market != null ? market.getIndustry(Industries.TECHMINING) : null;
        boolean isActive = techMining != null && techMining.isFunctional() && !exhausted;

        tooltip.addSectionHeading("Excavation Modifiers", Alignment.MID, 10f);

        if (!exhausted) {
            tooltip.addPara("Bonus vault caches each month: %s", 10f,
                Misc.getPositiveHighlightColor(),
                "2"
            );

            tooltip.addPara("Tech Mining upkeep: %s", 5f,
                Misc.getNegativeHighlightColor(),
                "x" + (int)UPKEEP_MULT
            );

            if (isActive) {
                tooltip.addPara("Active Vault Excavation Status: %s", 10f,
                    Misc.getHighlightColor(),
                    "ACTIVE"
                );
                tooltip.addPara("Colony hazard rating: %s", 5f,
                    Misc.getNegativeHighlightColor(),
                    "+50%"
                );
                tooltip.addPara("Colony stability: %s", 5f,
                    Misc.getNegativeHighlightColor(),
                    "" + (int)STABILITY_PENALTY
                );
                tooltip.addPara("Colony accessibility: %s", 5f,
                    Misc.getNegativeHighlightColor(),
                    "" + (int)(ACCESSIBILITY_PENALTY * 100f) + "%"
                );
            } else {
                tooltip.addPara("Active Vault Excavation Status: %s", 10f,
                    Misc.getGrayColor(),
                    "INACTIVE (Requires functional Tech Mining industry)"
                );
                tooltip.addPara("When Tech Mining is built and active, colony hazard increases by %s, stability suffers %s, and accessibility %s.", 5f,
                    Misc.getNegativeHighlightColor(),
                    "+50%",
                    "" + (int)STABILITY_PENALTY,
                    "" + (int)(ACCESSIBILITY_PENALTY * 100f) + "%"
                );
            }
        } else {
            tooltip.addPara("Vault Sector Status: %s", 10f,
                Misc.getGrayColor(),
                "EXHAUSTED"
            );
            tooltip.addPara(
                "Prolonged tech mining operations have thoroughly scoured and cleared the deep sealed vaults. With all major repositories stripped down to scattered debris, the vault yield bonuses and hazardous expeditionary penalties have permanently ceased.",
                5f
            );
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
