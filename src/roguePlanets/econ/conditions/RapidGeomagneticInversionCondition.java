package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class RapidGeomagneticInversionCondition extends BaseMarketConditionPlugin {

    public static final float HAZARD_SURGE = 1.0f;     // +100% Hazard during Inversion Surge
    public static final float HAZARD_CALM = -1.0f;     // -100% Hazard during Stabilized Alignment

    private int lastEvaluatedMonth = -1;

    private boolean isInversionSurgePhase() {
        if (Global.getSector() == null || Global.getSector().getClock() == null) return false;
        // Alternates cleanly every month: odd months = surge, even months = calm
        int month = Global.getSector().getClock().getMonth();
        return (month % 2 != 0);
    }

    private void updateHazardModifier(String id) {
        if (market == null) return;

        // Work out the hazard from everything else first, so the calm month can never push it below zero
        market.getHazard().unmodify(id);
        float others = market.getHazard().getModifiedValue();

        boolean isSurge = isInversionSurgePhase();
        float modValue = isSurge ? HAZARD_SURGE : -Math.min(-HAZARD_CALM, Math.max(0f, others));
        String desc = isSurge
            ? "Rapid Geomagnetic Inversion (Mid-Inversion Ionizing Storms)"
            : "Rapid Geomagnetic Inversion (Field Alignment Calm)";

        if (modValue != 0f) {
            market.getHazard().modifyFlat(id, modValue, desc);
        }
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        updateHazardModifier(id);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getHazard().unmodify(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;

        if (Global.getSector() != null && Global.getSector().getClock() != null) {
            int currentMonth = Global.getSector().getClock().getMonth();
            if (currentMonth != lastEvaluatedMonth) {
                lastEvaluatedMonth = currentMonth;
                updateHazardModifier(getModId());
            }
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Rapid Geomagnetic Inversion");

        tooltip.addPara(
            "A hyper-dense core drives rapid, monthly geomagnetic pole reversals across this world. Mid-inversion, the failing magnetosphere exposes surface infrastructure to devastating electrical surges and ionizing storms. When the field locks into alignment, the enhanced shielding creates an eerie, pristine calm that neutralizes environmental hazards.",
            10f
        );

        tooltip.addSectionHeading("Planetary Cycle", Alignment.MID, 10f);

        boolean isSurge = isInversionSurgePhase();
        if (isSurge) {
            tooltip.addPara("Current Phase: %s", 10f,
                Misc.getNegativeHighlightColor(),
                "Mid-Inversion Surge (+100% Hazard Rating)"
            );
            tooltip.addPara("Next Month: %s", 5f,
                Misc.getPositiveHighlightColor(),
                "Locked Field Alignment (up to -100% Hazard Rating, never below 0%)"
            );
        } else {
            tooltip.addPara("Current Phase: %s", 10f,
                Misc.getPositiveHighlightColor(),
                "Locked Field Alignment (up to -100% Hazard Rating, never below 0%)"
            );
            tooltip.addPara("Next Month: %s", 5f,
                Misc.getNegativeHighlightColor(),
                "Mid-Inversion Surge (+100% Hazard Rating)"
            );
        }

        tooltip.addPara(
            "The cycle shifts at the start of each standard Sector month as convective molten currents inside the planetary dynamo destabilize and reform.",
            Misc.getGrayColor(),
            10f
        );
    }
}
