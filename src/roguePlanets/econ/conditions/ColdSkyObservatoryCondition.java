package roguePlanets.econ.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.plugins.SurveyPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import roguePlanets.intel.ObservatorySurveyIntel;

/**
 * Found on ice giants. A Waystation and an Orbital Station (including Battle Stations and Star Fortresses) in orbit
 * together make a telescope platform, a moon of their own, in the cold and the giant's shadow. When both are working on a
 * colony you own, the observatory fully surveys random unsurveyed planets in the whole sector at the end of every month:
 * one with an Orbital Station, two with a Battle Station, three with a Star Fortress.
 */
public class ColdSkyObservatoryCondition extends BaseMarketConditionPlugin implements EconomyTickListener {

    private static final Random random = new Random();

    private static final String[] STATIONS = {
        Industries.ORBITALSTATION, Industries.ORBITALSTATION_MID, Industries.ORBITALSTATION_HIGH,
        Industries.BATTLESTATION, Industries.BATTLESTATION_MID, Industries.BATTLESTATION_HIGH,
        Industries.STARFORTRESS, Industries.STARFORTRESS_MID, Industries.STARFORTRESS_HIGH
    };

    private static final String[] BATTLE_STATIONS = {
        Industries.BATTLESTATION, Industries.BATTLESTATION_MID, Industries.BATTLESTATION_HIGH
    };
    private static final String[] FORTRESSES = {
        Industries.STARFORTRESS, Industries.STARFORTRESS_MID, Industries.STARFORTRESS_HIGH
    };

    private boolean colonized() {
        return SynergyTooltip.isColonized(market);
    }

    public boolean hasWaystation() {
        return SynergyTooltip.isFunctional(market, Industries.WAYSTATION);
    }

    public boolean hasStation() {
        for (String id : STATIONS) {
            if (SynergyTooltip.isFunctional(market, id)) return true;
        }
        return false;
    }

    private boolean hasAny(String[] ids) {
        for (String id : ids) {
            if (SynergyTooltip.isFunctional(market, id)) return true;
        }
        return false;
    }

    /** Planets surveyed each month: 1 with an Orbital Station, 2 with a Battle Station, 3 with a Star Fortress. */
    public int surveysPerMonth() {
        if (hasAny(FORTRESSES)) return 3;
        if (hasAny(BATTLE_STATIONS)) return 2;
        return 1;
    }

    public boolean synergyActive() {
        return colonized() && hasWaystation() && hasStation();
    }

    @Override
    public void apply(String id) {
        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this);
        }
    }

    @Override
    public void unapply(String id) {
        // the listener stays; it retires itself at month end once this condition is gone from the market
    }

    /** True while this plugin is still the live condition on its market. */
    private boolean isLive() {
        return market != null && market.getCondition("cold_sky_observatory") != null
            && market.getCondition("cold_sky_observatory").getPlugin() == this;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    /** Planets anywhere in the sector that are not yet fully surveyed (the hidden Violet Dwarf system is left alone). */
    private List<PlanetAPI> unsurveyed() {
        List<PlanetAPI> list = new ArrayList<PlanetAPI>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getStar() != null && "star_violet".equals(system.getStar().getTypeId())) continue;
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar()) continue;
                MarketAPI m = planet.getMarket();
                if (m == null || m == market) continue;
                if (m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL) continue;
                list.add(planet);
            }
        }
        return list;
    }

    private boolean toldNothingLeft = false;

    private final List<ObservatorySurveyIntel> lastIntels = new ArrayList<ObservatorySurveyIntel>();

    private CargoAPI getTargetCargo() {
        if (market == null) return null;
        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage == null) storage = market.getSubmarket(Submarkets.LOCAL_RESOURCES);
        return storage != null ? storage.getCargo() : null;
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (!isLive()) {
            Global.getSector().getListenerManager().removeListener(this);
            return;
        }
        org.apache.log4j.Logger log = Global.getLogger(ColdSkyObservatoryCondition.class);
        if (!market.isPlayerOwned() || !market.isInEconomy()) return;
        if (!synergyActive()) return;

        List<PlanetAPI> list = unsurveyed();
        if (list.isEmpty()) {
            // said once, not every month
            if (!toldNothingLeft) {
                toldNothingLeft = true;
                Global.getSector().getCampaignUI().addMessage("The Cold Sky Observatory at " + market.getName()
                    + " has nothing left to survey: every planet in the sector is already surveyed.", Misc.getHighlightColor());
            }
            return;
        }
        toldNothingLeft = false;
        int count = Math.min(surveysPerMonth(), list.size());
        log.info("Cold Sky Observatory at " + market.getName() + ": surveying " + count + " of " + list.size() + " unsurveyed planets.");

        // last month's reports make way for this month's
        for (ObservatorySurveyIntel old : lastIntels) {
            old.endImmediately();
            Global.getSector().getIntelManager().removeIntel(old);
        }
        lastIntels.clear();

        for (int n = 0; n < count; n++) {
            PlanetAPI target = list.remove(random.nextInt(list.size()));
            MarketAPI targetMarket = target.getMarket();

            // The survey itself, without the game's own message: the observatory sends its own report.
            Misc.setFullySurveyed(targetMarket, null, false);

            // The survey data item, the same kind a survey by the player would give, delivered to local storage.
            String dataId = "survey_data_1";
            try {
                SurveyPlugin plugin = (SurveyPlugin) Global.getSettings().getNewPluginInstance("surveyPlugin");
                plugin.init(Global.getSector().getPlayerFleet(), target);
                dataId = plugin.getSurveyDataType(target);
            } catch (Throwable t) {
                // fall back to the lowest class
            }
            String dataName = Global.getSettings().getCommoditySpec(dataId).getName();
            CargoAPI storage = getTargetCargo();
            if (storage != null) storage.addCommodity(dataId, 1f);

            String surveyClass = Misc.getPlanetSurveyClass(target);
            String typeName = target.getTypeNameWithWorld();
            if (typeName != null) typeName = typeName.toLowerCase();
            String systemName = target.getStarSystem() != null ? target.getStarSystem().getName() : "unknown";

            // one report per planet, so each can be selected to see where it is
            ObservatorySurveyIntel intel = new ObservatorySurveyIntel(target, market.getName(), typeName, systemName, surveyClass, dataName);
            Global.getSector().getIntelManager().addIntel(intel);
            lastIntels.add(intel);
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Cold Sky Observatory");
        tooltip.addPara(
            "Most ice giants are only cold. This one is different: its magnetic field is unusually quiet, its bulk casts a deep, steady shadow, "
            + "and the cold at that distance keeps infrared instruments almost perfectly still. A Waystation to relay the data and an Orbital Station to carry the optics, built together in orbit here, "
            + "would make a platform with a view of the whole sector that no other ice giant could give.",
            10f);

        SynergyTooltip.heading(tooltip, "Sector Survey");

        if (colonized()) {
            boolean way = hasWaystation();
            boolean stn = hasStation();
            boolean on = way && stn;
            if (on) {
                int n = surveysPerMonth();
                tooltip.addPara("Survey: %s", 5f, Misc.getPositiveHighlightColor(), n + " random unsurveyed planet" + (n > 1 ? "s" : "") + " each month");
                tooltip.addPara("Survey data for each planet is delivered to local storage, with a separate report in the intel log for 30 days.", 3f);
            } else {
                tooltip.addPara("Synergy: %s", 5f, Misc.getNegativeHighlightColor(), "INACTIVE");
            }
            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Waystation", way);
            SynergyTooltip.prerequisite(tooltip, "Orbital Station, Battle Station or Star Fortress", stn);
            SynergyTooltip.note(tooltip,
                "While both are working, the observatory fully surveys random planets anywhere in the sector at the end of each month, as long as any are left unsurveyed: "
                + "one with an Orbital Station, two with a Battle Station, three with a Star Fortress. "
                + "Together the two stations act as the observatory platform.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "With a Waystation and an Orbital Station, Battle Station or Star Fortress both working, a colony here fully surveys random unsurveyed planets anywhere in the sector every month: one with an Orbital Station, two with a Battle Station, three with a Star Fortress.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
