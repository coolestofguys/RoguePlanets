package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class ClosedLoopHydroponicsCondition extends BaseMarketConditionPlugin {

    public static final float POP_UPKEEP_MULT = 2.0f; // 2x Population & Infrastructure upkeep

    /** Catches industries built since the last refresh; the economy also re-applies the condition on every market refresh. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private void applyEffects(String id) {
        if (market == null) return;

        // 1. Double the upkeep of Population & Infrastructure
        Industry pop = market.getIndustry(Industries.POPULATION);
        if (pop == null) {
            pop = market.getIndustry("population");
        }
        if (pop != null && pop.getUpkeep() != null) {
            pop.getUpkeep().modifyMult(id, POP_UPKEEP_MULT, "Closed-loop Hydroponic Matrix");
        }

        // 2. Nullify all food demand across the colony
        for (Industry ind : market.getIndustries()) {
            if (ind == null) continue;
            MutableCommodityQuantity foodDemand = ind.getDemand(Commodities.FOOD);
            if (foodDemand != null && foodDemand.getQuantity() != null) {
                foodDemand.getQuantity().modifyMult(id, 0f, "Closed-loop Hydroponic Matrix");
            }
        }
    }

    private void unapplyEffects(String id) {
        if (market == null) return;

        Industry pop = market.getIndustry(Industries.POPULATION);
        if (pop == null) {
            pop = market.getIndustry("population");
        }
        if (pop != null && pop.getUpkeep() != null) {
            pop.getUpkeep().unmodifyMult(id);
        }

        for (Industry ind : market.getIndustries()) {
            if (ind == null) continue;
            MutableCommodityQuantity foodDemand = ind.getDemand(Commodities.FOOD);
            if (foodDemand != null && foodDemand.getQuantity() != null) {
                foodDemand.getQuantity().unmodifyMult(id);
            }
        }
    }

    @Override
    public void apply(String id) {
        applyEffects(id);
    }

    @Override
    public void unapply(String id) {
        unapplyEffects(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            applyEffects(getModId());
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Closed-loop Hydroponic Matrix");
        tooltip.addPara(
            "Equipped with hydroponic arrays, moisture recyclers, and advanced atmospheric scrubbers, a colony on this planet operates on a self-sustaining biological loop that entirely eliminates local food demand.",
            10f
        );

        tooltip.addSectionHeading("Colonial Effects", Alignment.MID, 10f);

        tooltip.addPara("Colony food demand: %s", 10f,
            Misc.getPositiveHighlightColor(),
            "0 (Demand completely eliminated)"
        );

        tooltip.addPara("Population & Infrastructure upkeep: %s", 5f,
            Misc.getNegativeHighlightColor(),
            "×2.0 (Doubled due to maintenance on sealed bio-recyclers)"
        );

        tooltip.addPara(
            "High-density subterranean growth chambers continually convert waste biomass into caloric staples, allowing dense populations to survive on hostile or arid worlds completely insulated from interstellar supply disruption.",
            Misc.getGrayColor(),
            10f
        );
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
