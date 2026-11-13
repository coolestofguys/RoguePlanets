package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class AntimatterAnomalyCondition extends BaseMarketConditionPlugin implements EconomyTickListener {

    /** Fuel delivered to local storage each month, per colony size step above 2 (size 3 = 100, 4 = 200, 5 = 300, 6+ = 400). */
    public static final float FUEL_PER_STEP = 100f;
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 6;

    /** Monthly fuel delivered to storage for a colony of the given size. */
    public static float fuelForSize(int size) {
        if (size < MIN_SIZE) return 0f;
        return (Math.min(size, MAX_SIZE) - (MIN_SIZE - 1)) * FUEL_PER_STEP;
    }

    @Override
    public void apply(String id) {
        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this);
        }
    }

    @Override
    public void unapply(String id) {
        // the listener stays registered; it retires itself once the condition is gone from its market
    }

    /** True while this plugin is still the live condition on its market. */
    private boolean isLive() {
        return market != null && market.getCondition("antimatter_anomaly") != null
            && market.getCondition("antimatter_anomaly").getPlugin() == this;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (!isLive()) {
            Global.getSector().getListenerManager().removeListener(this);
            return;
        }
        if (!market.isPlayerOwned() || !market.isInEconomy()) return;

        float fuelAmount = fuelForSize(market.getSize());
        if (fuelAmount <= 0f) return;

        CargoAPI storage = getTargetCargo();
        if (storage == null) return;
        storage.addCommodity(Commodities.FUEL, fuelAmount);
    }

    private CargoAPI getTargetCargo() {
        if (market == null) return null;
        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage == null) {
            storage = market.getSubmarket("storage");
        }
        if (storage == null) {
            storage = market.getSubmarket(Submarkets.LOCAL_RESOURCES);
        }
        return storage != null ? storage.getCargo() : null;
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Antimatter Anomaly");
        tooltip.addPara(
            "A pocket of primordial quantum asymmetry lies close beneath the surface of this world, where exotic particle reactions continuously release trace antimatter. The flux is easy to harvest and ideal for topping up fuel tanks, but it cannot be refined or contained in quantity, so it will never support the production of antimatter fuel canisters for trade.",
            10f
        );

        tooltip.addSectionHeading("Refueling Flux", Alignment.MID, 10f);

        tooltip.addPara("Fuel delivered to local storage each month:", 10f);
        tooltip.addPara("Colony size 3: %s", 3f, Misc.getPositiveHighlightColor(), "+100");
        tooltip.addPara("Colony size 4: %s", 2f, Misc.getPositiveHighlightColor(), "+200");
        tooltip.addPara("Colony size 5: %s", 2f, Misc.getPositiveHighlightColor(), "+300");
        tooltip.addPara("Colony size 6 or larger: %s", 2f, Misc.getPositiveHighlightColor(), "+400");

        if (market != null && market.getSize() > 0) {
            float now = fuelForSize(market.getSize());
            tooltip.addPara("Current monthly delivery to storage: %s", 8f,
                Misc.getHighlightColor(),
                (now > 0f ? (int) now + " fuel" : "none (the colony is too small)")
            );
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}