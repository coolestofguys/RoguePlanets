package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class GeothermalLeylinesCondition extends BaseMarketConditionPlugin {

    public static final float HAZARD_INCREASE = 0.50f;
    public static final float UPKEEP_MULT = 0.50f;

    /** Catches industries built since the last refresh; the economy also re-applies the condition on every market refresh. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private void updateIndustryUpkeep(String id) {
        if (market == null) return;

        for (Industry ind : market.getIndustries()) {
            if (ind.getUpkeep() != null) {
                ind.getUpkeep().modifyMult(id, UPKEEP_MULT, "Geothermal Leylines");
            }
        }
    }

    @Override
    public void apply(String id) {
        if (market != null) {
            market.getHazard().modifyFlat(id, HAZARD_INCREASE, "Geothermal Leylines");
            updateIndustryUpkeep(id);
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) {
            market.getHazard().unmodify(id);
            for (Industry ind : market.getIndustries()) {
                if (ind.getUpkeep() != null) {
                    ind.getUpkeep().unmodify(id);
                }
            }
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            updateIndustryUpkeep(getModId());
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Geothermal Leylines");
        tooltip.addPara(
            "Localized crustal fracturing channels hyper-dense thermal energy directly to the surface, providing near-limitless power while increasing tectonic volatility and planetary hazard.",
            10f
        );

        tooltip.addPara("+50%% hazard rating.", 10f, Misc.getNegativeHighlightColor(), "+50%");
        tooltip.addPara("Upkeep cost of all colony structures reduced by %s.", 10f, Misc.getPositiveHighlightColor(), "50%");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}