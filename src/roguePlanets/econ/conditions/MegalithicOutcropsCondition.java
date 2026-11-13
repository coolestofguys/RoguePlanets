package roguePlanets.econ.conditions;

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

public class MegalithicOutcropsCondition extends BaseMarketConditionPlugin {

    public static final float UPKEEP_MULT = 0f;          // Upkeep reduced to 0 credits (-100%)
    public static final float DEMAND_REDUCTION = -2f;    // 2 fewer units of demand for Drugs and Heavy Machinery

    /** Catches a mine built since the last refresh; the economy also re-applies the condition on every market refresh. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private void applyBonuses(String id) {
        if (market == null) return;

        Industry mining = market.getIndustry(Industries.MINING);
        if (mining != null && mining.isFunctional()) {
            // 1. Reduce mining upkeep to 0 credits
            if (mining.getUpkeep() != null) {
                mining.getUpkeep().modifyMult(id, UPKEEP_MULT, "Megalithic Outcrops");
            }

            // 2. Reduce recreational drugs demand by 2 units
            MutableCommodityQuantity drugs = mining.getDemand(Commodities.DRUGS);
            if (drugs != null && drugs.getQuantity() != null) {
                drugs.getQuantity().modifyFlat(id, DEMAND_REDUCTION, "Megalithic Outcrops");
            }

            // 3. Reduce heavy machinery demand by 2 units
            MutableCommodityQuantity machinery = mining.getDemand(Commodities.HEAVY_MACHINERY);
            if (machinery != null && machinery.getQuantity() != null) {
                machinery.getQuantity().modifyFlat(id, DEMAND_REDUCTION, "Megalithic Outcrops");
            }
        }
    }

    private void unapplyBonuses(String id) {
        if (market == null) return;

        Industry mining = market.getIndustry(Industries.MINING);
        if (mining != null) {
            if (mining.getUpkeep() != null) {
                mining.getUpkeep().unmodify(id);
            }

            MutableCommodityQuantity drugs = mining.getDemand(Commodities.DRUGS);
            if (drugs != null && drugs.getQuantity() != null) {
                drugs.getQuantity().unmodify(id);
            }

            MutableCommodityQuantity machinery = mining.getDemand(Commodities.HEAVY_MACHINERY);
            if (machinery != null && machinery.getQuantity() != null) {
                machinery.getQuantity().unmodify(id);
            }
        }
    }

    @Override
    public void apply(String id) {
        applyBonuses(id);
    }

    @Override
    public void unapply(String id) {
        unapplyBonuses(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            applyBonuses(getModId());
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Megalithic Outcrops");
        tooltip.addPara(
            "Colossal monolithic mineral protrusions and exposed crystalline ridges jut miles high through the planetary crust. Rather than requiring extensive subterranean shaft excavation, deep core extraction, or hazardous tectonic tunneling, vast seams of rich raw metallics and concentrated ore sit openly exposed across surface plains, accessible to surface strip-crawlers with unprecedented ease.",
            10f
        );

        tooltip.addSectionHeading("Surface Extraction Advantages", Alignment.MID, 10f);

        tooltip.addPara("Mining industry upkeep: %s", 10f,
            Misc.getPositiveHighlightColor(),
            "-100%"
        );

        tooltip.addPara("Mining Heavy Machinery demand: %s", 5f,
            Misc.getPositiveHighlightColor(),
            "" + (int) DEMAND_REDUCTION
        );

        tooltip.addPara("Mining Recreational Drugs demand: %s", 5f,
            Misc.getPositiveHighlightColor(),
            "" + (int) DEMAND_REDUCTION
        );
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}