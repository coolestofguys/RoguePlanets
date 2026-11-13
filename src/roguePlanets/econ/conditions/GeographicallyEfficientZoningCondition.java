package roguePlanets.econ.conditions;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class GeographicallyEfficientZoningCondition extends BaseMarketConditionPlugin {

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null || amount <= 0f) return;

        // Construction runs a second time each frame, so the progress bar itself moves twice as fast
        // and everything finishes in half the standard time (no early completion needed).
        List<Industry> industries = new ArrayList<Industry>(market.getIndustries());

        for (Industry ind : industries) {
            if (ind == null || ind.getSpec() == null) continue;

            // Never accelerate colony population / size growth
            String indId = ind.getId();
            if (Industries.POPULATION.equals(indId) || "population".equals(indId)) {
                continue;
            }

            try {
                if (ind.isBuilding() || ind.isUpgrading()) {
                    ind.advance(amount);
                }
            } catch (Throwable t) {
                // Ignore transient errors from modded industries mid-construction
            }
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Geographically Efficient Zoning");
        tooltip.addPara(
            "Vast stretches of uninterrupted, uniform bedrock plains and natural basalt shelf plateaus provide exceptionally stable foundational strata across this world. Devoid of fractured seismic fault lines, subterranean sink voids, or disruptive magnetic ground currents, civil engineering crews and autonomous fabrication complexes can grade terrain, sink structural pylons, and assemble massive colonial complexes with unparalleled logistical efficiency.",
            10f
        );

        tooltip.addSectionHeading("Colonial Engineering Advantages", Alignment.MID, 10f);

        tooltip.addPara("Structure and industry construction speed: %s", 10f,
            Misc.getPositiveHighlightColor(),
            "+100% (Builds and upgrades in half the standard time)"
        );

    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}