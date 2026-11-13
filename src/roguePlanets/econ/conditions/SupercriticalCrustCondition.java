package roguePlanets.econ.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Unstable crust. Every month on a player colony, a crustquake disrupts one random working structure for 30 days and
 * uncovers ore, rare ore and luxury goods, delivered to local storage and scaling with colony size. Structures that cannot be
 * disrupted (some modded ones report this) are never picked.
 */
public class SupercriticalCrustCondition extends BaseMarketConditionPlugin implements EconomyTickListener {

    public static final float HAZARD_PENALTY = 0.50f;
    public static final float DEFENSE_MULT = 0.50f;
    public static final float DISRUPTION_DAYS = 30f;
    public static final float ORE_PER_SIZE = 100f;
    public static final float RARE_ORE_PER_SIZE = 50f;
    public static final float LUXURY_PER_SIZE = 25f;
    /** Each quake rolls a yield between 0% and 200% of the base amount (average 100%). */
    public static final float YIELD_MIN = 0f;
    public static final float YIELD_MAX = 2f;

    private static final Random random = new Random();

    @Override
    public void apply(String id) {
        if (market == null) return;
        market.getHazard().modifyFlat(id, HAZARD_PENALTY, "Supercritical Crust");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, DEFENSE_MULT, "Supercritical Crust");

        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this);
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) {
            market.getHazard().unmodify(id);
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    /** Working structures that a quake can hit. */
    private List<Industry> candidates() {
        List<Industry> list = new ArrayList<Industry>();
        for (Industry ind : market.getIndustries()) {
            if (ind == null || ind.isHidden()) continue;
            if (Industries.POPULATION.equals(ind.getId())) continue;
            if (!ind.canBeDisrupted() || ind.isDisrupted() || !ind.isFunctional()) continue;
            list.add(ind);
        }
        return list;
    }

    @Override
    public void reportEconomyMonthEnd() {
        // The listener stays registered while the condition exists; it retires itself once the condition is gone
        if (market == null || market.getCondition("supercritical_crust") == null
                || market.getCondition("supercritical_crust").getPlugin() != this) {
            Global.getSector().getListenerManager().removeListener(this);
            return;
        }
        if (!market.isPlayerOwned() || !market.isInEconomy()) return;
        int size = market.getSize();
        if (size <= 0) return;

        String hit = null;
        List<Industry> list = candidates();
        if (!list.isEmpty()) {
            Industry pick = list.get(random.nextInt(list.size()));
            pick.setDisrupted(DISRUPTION_DAYS);
            hit = pick.getCurrentName();
        }

        float yield = YIELD_MIN + random.nextFloat() * (YIELD_MAX - YIELD_MIN);
        int ore = Math.round(size * ORE_PER_SIZE * yield);
        int rare = Math.round(size * RARE_ORE_PER_SIZE * yield);
        int lux = Math.round(size * LUXURY_PER_SIZE * yield);
        CargoAPI storage = getTargetCargo();
        if (storage != null) {
            if (ore > 0) storage.addCommodity(Commodities.ORE, ore);
            if (rare > 0) storage.addCommodity(Commodities.RARE_ORE, rare);
            if (lux > 0) storage.addCommodity(Commodities.LUXURY_GOODS, lux);
        }

        String text = "A crustquake shook " + market.getName() + ". "
            + (hit != null ? hit + " is disrupted for " + (int) DISRUPTION_DAYS + " days. " : "No structure was damaged. ")
            + (storage == null ? "" : (ore + rare + lux > 0 ? ore + " ore, " + rare + " rare ore and " + lux + " luxury goods were uncovered and sent to storage." : "The quake uncovered nothing of value."));
        Global.getSector().getCampaignUI().addMessage(text.trim(), Misc.getHighlightColor());
    }

    private CargoAPI getTargetCargo() {
        if (market == null) return null;
        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage == null) storage = market.getSubmarket(Submarkets.LOCAL_RESOURCES);
        return storage != null ? storage.getCargo() : null;
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Supercritical Crust");
        tooltip.addPara(
            "The upper crust is trapped in a supercritical state, a sea of pressurized fluid held under a skin of fractured rock. "
            + "Plates shift without warning and the ground breaks open in crustquakes that throw deep ore, rare ore and crystalline luxuries to the surface. "
            + "Colonists collect what the quakes bring up, but nothing built here stays whole for long, and fortification is almost impossible.",
            10f);

        tooltip.addPara("+50%% hazard rating.", 10f, Misc.getNegativeHighlightColor(), "+50%");
        tooltip.addPara("Ground defense strength multiplied by %s.", 5f, Misc.getNegativeHighlightColor(), "0.5x");

        tooltip.addSectionHeading("Crustquakes", Alignment.MID, 10f);
        tooltip.addPara("Each month on a colony you own, one random working structure is %s for %s days. "
            + "Structures that cannot be disrupted are never chosen.", 10f, Misc.getNegativeHighlightColor(),
            "disrupted", "" + (int) DISRUPTION_DAYS);
        tooltip.addPara("The same quake delivers ore, rare ore and luxury goods to local storage. The base amount per colony size is %s, %s and %s, "
            + "and each quake brings up anywhere from %s to %s of that.", 5f,
            Misc.getPositiveHighlightColor(), (int) ORE_PER_SIZE + " ore", (int) RARE_ORE_PER_SIZE + " rare ore",
            (int) LUXURY_PER_SIZE + " luxury goods", "0%", "200%");

        if (market != null && market.getSize() > 0) {
            int sz = market.getSize();
            tooltip.addPara("At this colony size a quake yields up to %s ore, %s rare ore and %s luxury goods.", 8f,
                Misc.getHighlightColor(), "" + (int) (sz * ORE_PER_SIZE * YIELD_MAX), "" + (int) (sz * RARE_ORE_PER_SIZE * YIELD_MAX),
                "" + (int) (sz * LUXURY_PER_SIZE * YIELD_MAX));
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
