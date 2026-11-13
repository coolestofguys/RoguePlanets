package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class MagmaticAntimatterForgeCondition extends BaseMarketConditionPlugin {

    public static final String PROCESSED_TAG = "$magmatic_forge_processed";
    public static final String PLAYER_FLEET_TAG = "$magmatic_forge_player_ship";

    private final IntervalUtil trackerInterval = new IntervalUtil(0.25f, 0.25f);

    /** How many ships were in storage the last time it was looked through; storage is only searched again when this changes. */
    private transient int lastSeenCount = -1;

    /** Mods the forge can add when a ship has too few of its own fitted mods to harden. Each one is also checked against the hull. */
    private static final String[] PRIMARY_SMOD_CANDIDATES = {
        "reinforcedhull",
        "heavyarmor",
        "hardenedshieldemitter",
        "extendedshieldemitter",
        "fluxdistributor",
        "fluxbreakers",
        "targetingunit",
        "auxiliarythrusters",
        "armoredweapons",
        "unstable_injector",
        "expanded_deck_crew",
        "solar_shielding",
        "blast_doors",
        "insulatedengine",
        "eccm",
        "missleracks"
    };

    private static final java.util.Set<String> NEEDS_SHIELDS = new java.util.HashSet<String>(
        java.util.Arrays.asList("hardenedshieldemitter", "extendedshieldemitter"));

    private boolean isColonized() {
        return market != null && !market.isPlanetConditionMarketOnly() && market.getSize() > 0;
    }

    private boolean isHeavyIndustryActive() {
        if (!isColonized()) return false;
        Industry ow = market.getIndustry(Industries.ORBITALWORKS);
        if (ow != null && ow.isFunctional()) return true;
        Industry hi = market.getIndustry(Industries.HEAVYINDUSTRY);
        return hi != null && hi.isFunctional();
    }

    private boolean isFuelProductionActive() {
        if (!isColonized()) return false;
        Industry fp = market.getIndustry(Industries.FUELPROD);
        return fp != null && fp.isFunctional();
    }

    /** Both industries are working. The forge also needs to be where the player's production is delivered. */
    public boolean isForgeOperational() {
        return isHeavyIndustryActive() && isFuelProductionActive();
    }

    private boolean isTargetGatheringPoint() {
        if (market == null || Global.getSector() == null || Global.getSector().getPlayerFaction() == null) return false;
        FactionProductionAPI prod = Global.getSector().getPlayerFaction().getProduction();
        if (prod == null) return false;
        return prod.getGatheringPoint() == market;
    }

    private boolean isPlayerInteractingWithMarket() {
        if (market == null || Global.getSector() == null) return false;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null || market.getPrimaryEntity() == null) return false;

        boolean sameLocation = playerFleet.getContainingLocation() == market.getContainingLocation();
        boolean nearStation = Misc.getDistance(playerFleet.getLocation(), market.getPrimaryEntity().getLocation()) < 250f;
        boolean dialogOpen = Global.getSector().getCampaignUI() != null && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;

        return sameLocation && nearStation && dialogOpen;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        MarketConditionAPI mc = market.getCondition(id);
        if (mc != null) {
            mc.setSurveyed(true);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null || Global.getSector() == null) return;

        trackerInterval.advance(amount);
        if (!trackerInterval.intervalElapsed()) return;

        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage == null || storage.getCargo() == null || storage.getCargo().getMothballedShips() == null) return;

        boolean playerInteracting = isPlayerInteractingWithMarket();
        int count = storage.getCargo().getMothballedShips().getNumMembers();

        // Nothing new has turned up and nobody is moving ships about: nothing to do.
        if (count == lastSeenCount && !playerInteracting) return;
        lastSeenCount = count;
        if (count == 0) return;

        List<FleetMemberAPI> storageShips = storage.getCargo().getMothballedShips().getMembersListCopy();
        if (storageShips == null || storageShips.isEmpty()) return;

        // Ships the player moves in and out while docked here are never "newly produced", and neither are
        // ships that turn up while the forge is cold or this is not where production is delivered.
        if (!isForgeOperational() || !isTargetGatheringPoint() || playerInteracting) {
            for (FleetMemberAPI member : storageShips) {
                if (member == null || member.getVariant() == null) continue;
                if (!member.getVariant().hasTag(PROCESSED_TAG)) {
                    member.getVariant().addTag(PROCESSED_TAG);
                }
            }
            return;
        }

        int enhancedCount = 0;
        Random rand = new Random();

        for (FleetMemberAPI member : storageShips) {
            if (member == null || member.getVariant() == null) continue;
            if (member.getVariant().hasTag(PROCESSED_TAG)) continue;

            // Clone stock variant so multiple produced hulls of the same type never bleed into each other
            ShipVariantAPI variant = member.getVariant().clone();
            variant.setSource(VariantSource.REFIT);
            member.setVariant(variant, false, false);

            enhanceShipAtForge(variant, rand);
            variant.addTag(PROCESSED_TAG);
            enhancedCount++;
        }

        if (enhancedCount > 0 && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage(
                "Magmatic Antimatter Forge tempered " + enhancedCount + " newly constructed faction production hull(s) on " + market.getName() + "!",
                Misc.getPositiveHighlightColor()
            );
        }
    }

    private void enhanceShipAtForge(ShipVariantAPI variant, Random rand) {
        stripDMods(variant);

        // Cumulative threshold odds:
        //  1% chance for at least 4 upgrades (roll < 0.01)
        // 11% chance for at least 3 upgrades (roll < 0.11)
        // 33% chance for at least 2 upgrades (roll < 0.33)
        // 60% chance for at least 1 upgrade  (roll < 0.60)
        // 40% chance for 0 upgrades          (roll >= 0.60)
        float roll = rand.nextFloat();
        int targetCount = 0;
        if (roll < 0.01f) {
            targetCount = 4;
        } else if (roll < 0.11f) {
            targetCount = 3;
        } else if (roll < 0.33f) {
            targetCount = 2;
        } else if (roll < 0.60f) {
            targetCount = 1;
        }

        if (targetCount <= 0) return;

        applySModsToVariant(variant, targetCount, rand);
    }

    private void stripDMods(ShipVariantAPI variant) {
        if (variant == null) return;

        List<String> currentMods = new ArrayList<String>(variant.getHullMods());
        for (String modId : currentMods) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
            if (spec != null && (spec.hasTag(Tags.HULLMOD_DMOD) || spec.getId().startsWith("dmod_"))) {
                variant.removePermaMod(modId);
                variant.removeMod(modId);
            }
        }
    }

    private static boolean isChassisBuiltIn(ShipVariantAPI variant, String modId) {
        return variant.getHullSpec() != null
            && variant.getHullSpec().getBuiltInMods() != null
            && variant.getHullSpec().getBuiltInMods().contains(modId);
    }

    /** True if the game would let this mod be built into the ship in the first place. */
    private static boolean canBeBuiltIn(HullModSpecAPI spec) {
        if (spec == null) return false;
        if (spec.isHidden() || spec.isHiddenEverywhere()) return false;
        if (spec.hasTag(Tags.HULLMOD_DMOD)) return false;
        if (spec.hasTag(Tags.HULLMOD_NO_BUILD_IN) || spec.hasTag(Tags.HULLMOD_SPECIAL)) return false;
        return true;
    }

    /** True if a mod the ship does not have yet fits this hull. Only used for the forge's own picks. */
    private static boolean fitsHull(HullModSpecAPI spec, ShipVariantAPI variant) {
        String modId = spec.getId();
        ShipHullSpecAPI hull = variant.getHullSpec();
        if (hull == null) return false;

        ShieldType shield = hull.getShieldType();
        boolean hasShield = shield == ShieldType.FRONT || shield == ShieldType.OMNI;
        boolean phase = shield == ShieldType.PHASE || hull.getHints().contains(ShipTypeHints.PHASE);
        boolean civilian = hull.getHints().contains(ShipTypeHints.CIVILIAN) || variant.hasHullMod("civgrade");
        boolean automated = variant.hasHullMod("automated");

        if ((spec.hasTag(Tags.HULLMOD_REQ_SHIELDS) || NEEDS_SHIELDS.contains(modId)) && !hasShield) return false;
        if (spec.hasTag(Tags.HULLMOD_NOT_CIV) && civilian) return false;
        if (spec.hasTag(Tags.HULLMOD_CIV_ONLY) && !civilian) return false;
        if (spec.hasTag(Tags.HULLMOD_NOT_PHASE) && phase) return false;
        if (spec.hasTag(Tags.HULLMOD_NOT_AUTO) && automated) return false;

        if ("expanded_deck_crew".equals(modId) && hull.getFighterBays() <= 0) return false;
        if ("targetingunit".equals(modId) && variant.hasHullMod("dedicated_targeting_core")) return false;
        return true;
    }

    /** Builds the mod into the ship for good; returns false (leaving the ship as it was) if the game would not take it. */
    private static boolean tryBuildIn(ShipVariantAPI variant, String modId, boolean asSMod) {
        boolean wasFitted = variant.getHullMods().contains(modId);
        try {
            if (wasFitted) variant.removeMod(modId);
            variant.addPermaMod(modId, asSMod);
            if (variant.hasHullMod(modId)) return true;
        } catch (Throwable ignored) {
            // fall through and put things back
        }
        try {
            variant.removePermaMod(modId);
            if (wasFitted) variant.addMod(modId);
        } catch (Throwable ignored) {
            // nothing more to do
        }
        return false;
    }

    private void applySModsToVariant(ShipVariantAPI variant, int targetCount, Random rand) {
        if (variant == null || targetCount <= 0) return;

        int applied = 0;

        // 1. The ship's own fitted mods come first: they are hardened into the hull (first 2 as true S-mods, the rest as free built-ins).
        List<String> fitted = new ArrayList<String>();
        for (String modId : new ArrayList<String>(variant.getHullMods())) {
            if (isChassisBuiltIn(variant, modId)) continue;
            if (variant.getPermaMods().contains(modId)) continue;
            if (canBeBuiltIn(Global.getSettings().getHullModSpec(modId))) fitted.add(modId);
        }
        Collections.shuffle(fitted, rand);

        for (String modId : fitted) {
            if (applied >= targetCount) break;
            if (tryBuildIn(variant, modId, applied < 2)) applied++;
        }
        if (applied >= targetCount) return;

        // 2. Not enough fitted mods: draw from the forge's own list, keeping only mods that suit this hull.
        List<String> pool = new ArrayList<String>();
        for (String modId : PRIMARY_SMOD_CANDIDATES) {
            if (variant.hasHullMod(modId) || isChassisBuiltIn(variant, modId)) continue;
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
            if (canBeBuiltIn(spec) && fitsHull(spec, variant)) pool.add(modId);
        }
        Collections.shuffle(pool, rand);

        for (String modId : pool) {
            if (applied >= targetCount) break;
            if (tryBuildIn(variant, modId, applied < 2)) applied++;
        }
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Magmatic Antimatter Forge");
        tooltip.addPara(
            "Anchored directly into active mantle convection currents and abyssal magma chambers, this scorching Hadean world harnesses extreme thermal and pressure differentials capable of pulverizing conventional metallurgy. When coupled with an active fuel production facility to inject supercritical antimatter streams directly into the foundries, industrial shipyards can forge hyper-dense alloy plating and relativistic structural lattices with unmatched structural integrity.",
            10f
        );

        SynergyTooltip.heading(tooltip, "Forge Operational Status");

        if (isColonized()) {
            boolean operational = isForgeOperational() && isTargetGatheringPoint();

            if (operational) {
                SynergyTooltip.status(tooltip, "Forge Synchronization", true,
                    "ONLINE (Heavy Industry & Fuel Production Synchronized)");
                tooltip.addPara(
                    "All faction custom production hulls ordered by the player and delivered to personal storage on this planet are guaranteed to be manufactured with %s.",
                    5f,
                    Misc.getPositiveHighlightColor(),
                    "ZERO D-MODS"
                );
                tooltip.addPara("Supercritical Alloy Hardening probabilities per delivered hull:", 5f);
                tooltip.addPara(" - %s chance for at least %s enhanced mods (2 S-mods + 2 built-in)", 3f, Misc.getHighlightColor(), "1%", "4");
                tooltip.addPara(" - %s chance for at least %s enhanced mods (2 S-mods + 1 built-in)", 3f, Misc.getHighlightColor(), "11%", "3");
                tooltip.addPara(" - %s chance for at least %s built-in S-mods", 3f, Misc.getHighlightColor(), "33%", "2");
                tooltip.addPara(" - %s chance for at least %s built-in S-mod", 3f, Misc.getHighlightColor(), "60%", "1");
                tooltip.addPara(" - %s chance for zero upgrades", 3f, Misc.getGrayColor(), "40%");
            } else {
                SynergyTooltip.status(tooltip, "Forge Synchronization", false,
                    isForgeOperational()
                        ? "OFFLINE (This colony is not the production delivery point)"
                        : "OFFLINE (Requires Heavy Industry / Orbital Works & Fuel Production)");
                SynergyTooltip.note(tooltip,
                    "The magmatic forge requires active coupling between colonial heavy foundries and antimatter fuel injection channels to temper alloy lattices, and only tempers hulls from the player's custom production that are delivered to this colony.");
            }

            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Heavy Industry or Orbital Works", isHeavyIndustryActive());
            SynergyTooltip.prerequisite(tooltip, "Fuel Production", isFuelProductionActive());
            SynergyTooltip.prerequisite(tooltip, "Set as the production delivery point", isTargetGatheringPoint());
        } else {
            SynergyTooltip.uncolonized(tooltip, "Forge Potential",
                "When colonized with both Heavy Industry or Orbital Works and Fuel Production, all faction custom production hulls delivered to storage on this world are forged with zero D-mods and a chance of bonus S-mods. If either industry is missing or disrupted, the forge stays cold.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
