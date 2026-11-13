package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class ChronoSpatialDilationCondition extends BaseMarketConditionPlugin {

    public static final float ACCESSIBILITY_BONUS = 0.50f;   // +50% Accessibility
    public static final float UPKEEP_MULT = 2.0f;           // 2x Structure Upkeep
    public static final float FLEET_QUALITY_PENALTY = -1.0f; // -100% Ship Quality (guarantees maximum D-mods)

    /** Catches industries built since the last refresh; the economy also re-applies the condition on every market refresh. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    @Override
    public void apply(String id) {
        if (market == null) return;

        // +50% Accessibility bonus
        market.getAccessibilityMod().modifyFlat(id, ACCESSIBILITY_BONUS, "Chrono-Spatial Dilation");

        // -100% Ship Quality (patrols spawn with maximum D-mods)
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(id, FLEET_QUALITY_PENALTY, "Chrono-Spatial Dilation");

        // 2x Industry and Structure Upkeep multiplier
        for (Industry ind : market.getIndustries()) {
            if (ind.getUpkeep() != null) {
                ind.getUpkeep().modifyMult(id, UPKEEP_MULT, "Chrono-Spatial Dilation");
            }
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.getAccessibilityMod().unmodify(id);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodify(id);

        for (Industry ind : market.getIndustries()) {
            if (ind.getUpkeep() != null) {
                ind.getUpkeep().unmodifyMult(id);
            }
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            // Disrupted structures recover twice as fast: each day also takes one extra day off the disruption
            for (Industry ind : market.getIndustries()) {
                if (ind.isDisrupted()) {
                    float left = ind.getDisruptedDays() - dailyCheck.getIntervalDuration();
                    ind.setDisrupted(Math.max(0f, left));
                }
            }

            String id = getModId();
            for (Industry ind : market.getIndustries()) {
                if (ind.getUpkeep() != null) {
                    ind.getUpkeep().modifyMult(id, UPKEEP_MULT, "Chrono-Spatial Dilation");
                }
            }
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Chrono-Spatial Dilation");
        
        tooltip.addPara(
            "A severe localized distortion in the planet's space-time continuum causes subjective surface time to flow at an accelerated rate relative to the surrounding stellar system. Offworld logistics and cargo transfers complete almost instantaneously, dramatically boosting accessibility, and damaged facilities are repaired in half the time. However, physical materials endure decades of structural wear and entropic decay in mere standard months and doubling facility upkeep costs. Furthermore, drydocked and stationed warships suffer extreme relativistic fatigue and sensor desynchronization across the shear boundary, guaranteeing deployed vessels are decrepit.",
            10f
        );

        tooltip.addSectionHeading("Planetary Effects", Alignment.MID, 10f);

        tooltip.addPara("Colony accessibility: %s", 10f,
            Misc.getPositiveHighlightColor(),
            "+50%"
        );

        tooltip.addPara("All industry and structure upkeep costs: %s", 5f,
            Misc.getNegativeHighlightColor(),
            "×2.0 (doubled due to rapid mechanical wear and entropic decay)"
        );

        tooltip.addPara("Disrupted structures recover: %s", 5f,
            Misc.getPositiveHighlightColor(),
            "twice as fast (time runs fast inside the field)"
        );

        tooltip.addPara("Colony ship quality: %s", 5f,
            Misc.getNegativeHighlightColor(),
            "-100% (patrol vessels suffer severe structural defects and pervasive D-mods)"
        );

        tooltip.addPara(
            "Vessels manufactured, drydocked, or stationed within the dilation field endure decades of accelerated operational wear and crystalline fatigue in mere months. Transitioning through the gravitational shear boundary violently desynchronizes targeting matrices and compromises hull integrity, guaranteeing defensive detachments fly decrepit, heavily degraded hulls.",
            Misc.getGrayColor(),
            10f
        );
    }
}