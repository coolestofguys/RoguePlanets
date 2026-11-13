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

public class HydrocarbonFractionationCondition extends BaseMarketConditionPlugin {

    public static final float LIGHT_INDUSTRY_BONUS = 2f;
    public static final float HAZARD_REDUCTION = -0.50f;

    /** Re-checks once a day; the economy also re-applies the condition whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private void applyBonuses(String id) {
        if (market == null) return;

        // 1. Hazard reduction: -50% to counteract Dense Atmosphere and harsh giant environments
        market.getHazard().modifyFlat(id, HAZARD_REDUCTION, "Hydrocarbon Fractionation");

        // 2. Dynamic conversion: Transfer all Volatiles output to Organics
        Industry mining = market.getIndustry(Industries.MINING);
        if (mining != null && mining.isFunctional()) {
            MutableCommodityQuantity volSupply = mining.getSupply(Commodities.VOLATILES);
            MutableCommodityQuantity orgSupply = mining.getSupply(Commodities.ORGANICS);

            if (volSupply != null && volSupply.getQuantity() != null && orgSupply != null && orgSupply.getQuantity() != null) {
                // Temporarily unmodify our zeroing mult so we read the full volatile generation pool
                volSupply.getQuantity().unmodify(id);
                float potentialVolatiles = Math.max(0f, volSupply.getQuantity().getModifiedValue());

                // Zero out volatile commodity output (since it is cracked/fractionated)
                volSupply.getQuantity().modifyMult(id, 0f, "Hydrocarbon Fractionation (converted to organics)");

                // Feed the full dynamic volatile value directly into Organics output
                orgSupply.getQuantity().modifyFlat(id, potentialVolatiles, "Hydrocarbon Fractionation (from converted volatiles)");
            }
        }

        // 3. +2 Production across all commodities produced by Light Industry
        Industry lightIndustry = market.getIndustry(Industries.LIGHTINDUSTRY);
        if (lightIndustry != null && lightIndustry.isFunctional()) {
            for (MutableCommodityQuantity supply : lightIndustry.getAllSupply()) {
                if (supply == null || supply.getQuantity() == null) continue;
                // Only boost what Light Industry really produces, never an empty entry
                supply.getQuantity().unmodifyFlat(id);
                if (supply.getQuantity().getModifiedValue() > 0f) {
                    supply.getQuantity().modifyFlat(id, LIGHT_INDUSTRY_BONUS, "Hydrocarbon Fractionation");
                }
            }
        }
    }

    private void unapplyBonuses(String id) {
        if (market == null) return;

        market.getHazard().unmodify(id);

        Industry mining = market.getIndustry(Industries.MINING);
        if (mining != null) {
            MutableCommodityQuantity volSupply = mining.getSupply(Commodities.VOLATILES);
            if (volSupply != null && volSupply.getQuantity() != null) {
                volSupply.getQuantity().unmodify(id);
            }
            MutableCommodityQuantity orgSupply = mining.getSupply(Commodities.ORGANICS);
            if (orgSupply != null && orgSupply.getQuantity() != null) {
                orgSupply.getQuantity().unmodify(id);
            }
        }

        Industry lightIndustry = market.getIndustry(Industries.LIGHTINDUSTRY);
        if (lightIndustry != null) {
            for (MutableCommodityQuantity supply : lightIndustry.getAllSupply()) {
                if (supply != null && supply.getQuantity() != null) {
                    supply.getQuantity().unmodify(id);
                }
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
        tooltip.addTitle("Hydrocarbon Fractionation");
        tooltip.addPara(
            "The upper cloud strata of this hydrocarbon giant are rich in pre-sorted methane, ethane, and complex organic monomers. Automated catalytic cracking and fractionation aerostats harvest an inexhaustible stream of biopolymers and industrial feedstocks directly from the atmospheric soup, dynamically converting all volatile extraction into massive industrial-grade organics output.",
            10f
        );

        tooltip.addSectionHeading("Industrial & Atmospheric Adjustments", Alignment.MID, 10f);

        tooltip.addPara("Hazard rating: %s", 10f,
            Misc.getPositiveHighlightColor(),
            "-50%"
        );

        tooltip.addPara("Dynamic fractionation: %s", 5f,
            Misc.getHighlightColor(),
            "Converts 100% of Mining Volatiles output directly into Organics"
        );

        tooltip.addPara("Light Industry production: %s", 5f,
            Misc.getPositiveHighlightColor(),
            "+" + (int) LIGHT_INDUSTRY_BONUS
        );

        Industry lightInd = market != null ? market.getIndustry(Industries.LIGHTINDUSTRY) : null;
        if (lightInd != null && lightInd.isFunctional()) {
            tooltip.addPara("Light Industry status: %s", 5f,
                Misc.getPositiveHighlightColor(),
                "Active (boosting Light Industry output by +2)"
            );
        } else {
            tooltip.addPara("Light Industry status: %s", 5f,
                Misc.getGrayColor(),
                "Requires functional Light Industry"
            );
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}