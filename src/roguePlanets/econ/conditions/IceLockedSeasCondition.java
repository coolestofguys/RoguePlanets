package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Ice-Locked Seas: given to every Snowball world. Works like vanilla's Water-covered Surface
 * (it is a food resource deposit, so Aquaculture can be built) but at -1 food production.
 * The -1 and the food link are registered in RoguePlanetsModPlugin.onApplicationLoad().
 */
public class IceLockedSeasCondition extends ResourceDepositsCondition {

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    /** Whole tooltip written here, like the mod's other conditions (the CSV has no description text). */
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(condition.getName(), market.getTextColorForFactionOrPlanet());
        createTooltipAfterDescription(tooltip, expanded);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        // Not calling super: vanilla's text would credit Farming instead of Aquaculture.
        tooltip.addPara("Liquid oceans still lie beneath the planet-wide ice sheet. Aquaculture is possible, "
                + "but every harvest has to be drilled and pumped up through kilometres of ice.", 10f);
        tooltip.addPara("%s food production (Aquaculture)", 10f, Misc.getHighlightColor(), "-1");
    }
}
