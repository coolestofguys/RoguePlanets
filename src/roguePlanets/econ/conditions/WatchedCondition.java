package roguePlanets.econ.conditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/** Atmospheric flavor condition for the retrograde world in the Violet Dwarf system. */
public class WatchedCondition extends BaseMarketConditionPlugin {

    public static final float HAZARD_BONUS = 0.25f;

    @Override
    public void apply(String id) {
        if (market != null) {
            market.getHazard().modifyFlat(id, HAZARD_BONUS, "Watched");
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) {
            market.getHazard().unmodifyFlat(id);
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Watched");
        tooltip.addPara(
            "This world circles its star against the flow of every other body in the system, and no survey has ever explained why. Settlers report the same dream, and the same feeling when they wake: that something stood very near, and stopped when they opened their eyes.",
            10f);
        tooltip.addPara("Hazard rating: %s", 10f, Misc.getNegativeHighlightColor(), "+25%");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
