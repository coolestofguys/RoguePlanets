package roguePlanets.econ.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Ship Graveyard, found on worlds that are hit by meteors. The world's pull draws the wrecks of battles fought in
 * its system down onto its surface, where they pile up.
 *
 * Every hostile ship destroyed in a battle the player takes part in, inside this system, is written into this
 * colony's ledger. The graveyard points are simply the total DP of the wrecks on file. At the turn of each month, for
 * as long as the oldest wrecks add up to the cost of a hull, the colony takes that whole batch of wrecks, rebuilds
 * one hull from among them (with several D-mods) into the colony's storage, and the rest of the batch is used up.
 *
 *   Base:            500 points per hull, 4 to 5 D-mods
 *   Refining:        1 to 3 D-mods
 *   Heavy Industry:  400 points per hull
 *   Orbital Works:   300 points per hull (replaces Heavy Industry's step, does not stack with it)
 *   Mining:          the hull is picked twice at random and the one with the higher DP is kept
 *
 * The ledger is kept in the colony's memory, so it is saved with the game. The battle tracking and the monthly
 * turn live in ShipGraveyardScript.
 */
public class ShipGraveyardCondition extends BaseMarketConditionPlugin {

    public static final String CONDITION_ID = "ship_graveyard";

    public static final int BASE_COST = 500;
    public static final int HEAVY_COST = 400;
    public static final int ORBITAL_COST = 300;
    public static final int DMODS_MIN = 4;
    public static final int DMODS_MAX = 5;
    public static final int DMODS_REFINED_MIN = 1;
    public static final int DMODS_REFINED_MAX = 3;

    /** Most hulls one colony will produce in a single month, so a huge backlog can't flood a save. */
    public static final int MAX_PER_MONTH = 25;
    /** Most wrecks kept on file; when it is full the smallest wreck is forgotten first (its points go with it). */
    public static final int LEDGER_CAP = 400;

    public static final String KEY_LEDGER = "$rp_graveyard_ledger";

    // ---------------------------------------------------------------- state

    private static MemoryAPI mem(MarketAPI market) {
        return market.getMemoryWithoutUpdate();
    }

    /** The graveyard points: the total DP of every wreck on file. */
    public static float getPoints(MarketAPI market) {
        if (market == null) return 0f;
        Object o = mem(market).get(KEY_LEDGER);
        if (!(o instanceof List)) return 0f;
        float total = 0f;
        for (Object e : (List<?>) o) total += entryDp(String.valueOf(e));
        return total;
    }

    @SuppressWarnings("unchecked")
    private static List<String> ledger(MarketAPI market) {
        Object o = mem(market).get(KEY_LEDGER);
        if (o instanceof List) return (List<String>) o;
        List<String> fresh = new ArrayList<String>();
        mem(market).set(KEY_LEDGER, fresh);
        return fresh;
    }

    public static int getLedgerSize(MarketAPI market) {
        if (market == null) return 0;
        Object o = mem(market).get(KEY_LEDGER);
        return o instanceof List ? ((List<?>) o).size() : 0;
    }

    // ---------------------------------------------------------------- the modules

    public static boolean hasRefining(MarketAPI market) {
        return SynergyTooltip.isFunctional(market, Industries.REFINING);
    }

    public static boolean hasHeavyIndustry(MarketAPI market) {
        return SynergyTooltip.isFunctional(market, Industries.HEAVYINDUSTRY);
    }

    public static boolean hasOrbitalWorks(MarketAPI market) {
        return SynergyTooltip.isFunctional(market, Industries.ORBITALWORKS);
    }

    public static boolean hasMining(MarketAPI market) {
        return SynergyTooltip.isFunctional(market, Industries.MINING);
    }

    public static int costPerHull(MarketAPI market) {
        if (hasOrbitalWorks(market)) return ORBITAL_COST;
        if (hasHeavyIndustry(market)) return HEAVY_COST;
        return BASE_COST;
    }

    public static int dmodsMin(MarketAPI market) {
        return hasRefining(market) ? DMODS_REFINED_MIN : DMODS_MIN;
    }

    public static int dmodsMax(MarketAPI market) {
        return hasRefining(market) ? DMODS_REFINED_MAX : DMODS_MAX;
    }

    // ---------------------------------------------------------------- the ledger

    /** Writes one destroyed hull into the colony's ledger, which adds its DP to the graveyard points. */
    public static void record(MarketAPI market, String hullId, float dp) {
        if (market == null || hullId == null || dp <= 0f) return;
        List<String> list = ledger(market);
        list.add(hullId + "|" + dp);
        while (list.size() > LEDGER_CAP) {
            int smallest = 0;
            for (int i = 1; i < list.size(); i++) {
                if (entryDp(list.get(i)) < entryDp(list.get(smallest))) smallest = i;
            }
            list.remove(smallest);
        }
    }

    private static String entryHull(String entry) {
        int i = entry.indexOf('|');
        return i < 0 ? entry : entry.substring(0, i);
    }

    private static float entryDp(String entry) {
        int i = entry.indexOf('|');
        if (i < 0) return 0f;
        try {
            return Float.parseFloat(entry.substring(i + 1));
        } catch (Throwable t) {
            return 0f;
        }
    }

    /**
     * The monthly turn for one colony. For as long as the oldest wrecks add up to the cost of a hull, that whole batch
     * is used up and one hull from among it is put into storage. Returns how many hulls were put into storage.
     */
    public static int processMonth(MarketAPI market) {
        if (market == null || !market.isPlayerOwned()) return 0;

        List<String> list = ledger(market);
        int cost = costPerHull(market);
        int dmin = dmodsMin(market);
        int dmax = dmodsMax(market);
        boolean favorBig = hasMining(market);
        Random random = new Random();
        CargoAPI storage = Misc.getStorageCargo(market);
        int made = 0;

        while (made < MAX_PER_MONTH && storage != null) {
            // the oldest wrecks, up to the point where they add up to the cost of a hull
            float sum = 0f;
            int count = 0;
            while (count < list.size() && sum < cost) {
                sum += entryDp(list.get(count));
                count++;
            }
            if (sum < cost) break;

            List<String> batch = new ArrayList<String>(list.subList(0, count));
            list.subList(0, count).clear();

            int pick = favorBig ? betterOfTwo(batch, random) : random.nextInt(batch.size());
            FleetMemberAPI member = buildWreck(entryHull(batch.get(pick)), dmin + random.nextInt(dmax - dmin + 1), random);
            if (member == null) continue; // a hull the game can't build: the batch is lost with it

            storage.getMothballedShips().addFleetMember(member);
            made++;
        }

        if (made > 0) {
            try {
                Global.getSector().getCampaignUI().addMessage(
                    "Ship Graveyard on " + market.getName() + ": " + made + (made == 1 ? " hull" : " hulls") + " salvaged into storage.");
            } catch (Throwable t) {
                // the message is only a courtesy
            }
        }
        return made;
    }

    /** Picks twice at random and keeps the wreck with the higher DP. */
    private static int betterOfTwo(List<String> list, Random random) {
        int first = random.nextInt(list.size());
        int second = random.nextInt(list.size());
        return entryDp(list.get(second)) > entryDp(list.get(first)) ? second : first;
    }

    private static FleetMemberAPI buildWreck(String hullId, int dmods, Random random) {
        try {
            String variantId = hullId + "_Hull";
            if (!Global.getSettings().doesVariantExist(variantId)) return null;
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            try {
                DModManager.addDMods(member, true, dmods, random);
            } catch (Throwable t) {
                // a hull that can't take D-mods still comes out as a plain wreck
            }
            try {
                member.setShipName(Global.getSector().getPlayerFaction().pickRandomShipName());
            } catch (Throwable t) {
                // keep the default name
            }
            member.getRepairTracker().setCR(0.5f);
            member.getRepairTracker().setMothballed(true);
            // the Magmatic Antimatter Forge only tempers hulls from the player's own production orders, not salvage
            member.getVariant().addTag(MagmaticAntimatterForgeCondition.PROCESSED_TAG);
            return member;
        } catch (Throwable t) {
            Global.getLogger(ShipGraveyardCondition.class).warn("Ship Graveyard could not build hull " + hullId + ": " + t);
            return null;
        }
    }

    // ---------------------------------------------------------------- tooltip

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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Ship Graveyard");
        tooltip.addPara(
            "This world is struck by meteors, and the same pull draws the wrecks of nearby battles down onto its surface, where they pile up. "
            + "A colony here can pick the pile apart and haul out hulls that are still worth restoring.",
            10f);

        SynergyTooltip.heading(tooltip, "Wreck Recovery");

        if (SynergyTooltip.isColonized(market)) {
            float points = getPoints(market);
            int cost = costPerHull(market);
            tooltip.addPara("Graveyard points: %s", 5f, Misc.getHighlightColor(), String.valueOf(Math.round(points)));
            tooltip.addPara("Cost per hull: %s", 3f, Misc.getHighlightColor(), cost + " points");
            tooltip.addPara("D-mods per hull: %s", 3f, Misc.getHighlightColor(), dmodsMin(market) + " to " + dmodsMax(market));
            tooltip.addPara("Hull choice: %s", 3f, Misc.getHighlightColor(), hasMining(market) ? "best of two picks" : "random wreck");
            tooltip.addPara("Wrecks on file: %s", 3f, Misc.getHighlightColor(), String.valueOf(getLedgerSize(market)));

            tooltip.addPara("Improved by:", 8f);
            module(tooltip, "Refining", hasRefining(market), "fewer D-mods");
            module(tooltip, "Heavy Industry", hasHeavyIndustry(market), "cheaper hulls");
            module(tooltip, "Orbital Works", hasOrbitalWorks(market), "cheaper still");
            module(tooltip, "Mining", hasMining(market), "picks twice, keeps the bigger");

            SynergyTooltip.note(tooltip,
                "Every hostile ship destroyed in a battle you fight in this system adds its deployment points to the pile. "
                + "At the turn of each month, the oldest wrecks that add up to the cost of a hull are broken down together: one hull from among them goes into storage and the rest is used up. "
                + "Refining brings the D-mods down to 1 to 3, Heavy Industry lowers the price to 400 points, and Orbital Works lowers it to 300. "
                + "Mining makes the colony pick twice and keep the wreck with the higher DP. When the pile is full, the smallest wrecks are forgotten first.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "A colony here collects the wrecks of hostile ships destroyed in battles you fight in this system, and at the turn of each month turns them into hulls with D-mods in its storage.");
        }
    }

    private void module(TooltipMakerAPI tooltip, String name, boolean on, String effect) {
        tooltip.addPara(" - " + name + ": %s (" + effect + ")", 3f,
            on ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(), on ? "ACTIVE" : "not installed");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
