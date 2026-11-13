package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Helpers for showing the mod's conditions in the in-game codex. The codex draws a condition's normal tooltip with
 * Global.CODEX_TOOLTIP_MODE switched on and no real colony behind it, so each condition shows its "uncolonized" text
 * there: what it is and what it would do. If anything goes wrong, a short line is shown instead of a broken page.
 */
public final class CodexTooltip {

    private CodexTooltip() {
    }

    /** An empty, uncolonized stand-in market for codex pages that have none. */
    public static MarketAPI placeholderMarket() {
        try {
            return Global.getFactory().createMarket("rp_codex_preview", "Codex", 0);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void fallback(TooltipMakerAPI tooltip) {
        tooltip.addPara("Find this condition on a world to see how it works there.", Misc.getGrayColor(), 10f);
    }
}
