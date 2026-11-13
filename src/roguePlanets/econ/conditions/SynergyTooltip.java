package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Shared layout for conditions that depend on industries working together. Every synergy condition builds its
 * tooltip from these pieces, so they all read the same way:
 *
 *   Title
 *   Description paragraph
 *   Section heading
 *   Colonized:    status line, effect lines, "Prerequisite Status:" list of industries
 *   Uncolonized:  "Potential: UNCOLONIZED" and a one-paragraph summary of the synergy
 */
public final class SynergyTooltip {

    private SynergyTooltip() {
    }

    public static boolean isColonized(MarketAPI market) {
        return market != null && !market.isPlanetConditionMarketOnly() && market.getSize() > 0;
    }

    /** True if the colony has this industry and it is currently working (not disrupted). */
    public static boolean isFunctional(MarketAPI market, String industryId) {
        if (!isColonized(market)) return false;
        Industry industry = market.getIndustry(industryId);
        return industry != null && industry.isFunctional();
    }

    public static void heading(TooltipMakerAPI tooltip, String text) {
        tooltip.addSectionHeading(text, Alignment.MID, 10f);
    }

    /** "Label: VALUE" in green when good and red when not. */
    public static void status(TooltipMakerAPI tooltip, String label, boolean good, String value) {
        tooltip.addPara(label + ": %s", 10f,
            good ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), value);
    }

    /** A gray explanatory paragraph. */
    public static void note(TooltipMakerAPI tooltip, String text) {
        tooltip.addPara(text, Misc.getGrayColor(), 5f);
    }

    public static void prerequisiteHeader(TooltipMakerAPI tooltip) {
        tooltip.addPara("Prerequisite Status:", 8f);
    }

    /** " - Industry name: OPERATIONAL" in green, or "REQUIRED" in red. */
    public static void prerequisite(TooltipMakerAPI tooltip, String name, boolean operational) {
        tooltip.addPara(" - " + name + ": %s", 3f,
            operational ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
            operational ? "OPERATIONAL" : "REQUIRED");
    }

    /** What an uncolonized world shows: a potential line and a summary of what colonizing it would do. */
    public static void uncolonized(TooltipMakerAPI tooltip, String potentialLabel, String summary) {
        tooltip.addPara(potentialLabel + ": %s", 10f, Misc.getHighlightColor(), "UNCOLONIZED");
        note(tooltip, summary);
    }
}
