package roguePlanets.intel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class VaultProductionReportIntel extends BaseIntelPlugin {

    public static final float LIFETIME_DAYS = 30f;

    protected MarketAPI market;
    protected CargoAPI bonusCargo;
    protected List<FleetMemberAPI> recoveredShips;
    protected long creationTimestamp;

    public VaultProductionReportIntel(MarketAPI market, CargoAPI bonusCargo, List<FleetMemberAPI> recoveredShips) {
        this.market = market;
        this.bonusCargo = bonusCargo;
        this.recoveredShips = recoveredShips != null ? recoveredShips : new ArrayList<FleetMemberAPI>();
        if (Global.getSector() != null && Global.getSector().getClock() != null) {
            this.creationTimestamp = Global.getSector().getClock().getTimestamp();
        } else {
            this.creationTimestamp = System.currentTimeMillis();
        }
    }

    public boolean isExpired() {
        if (Global.getSector() == null || Global.getSector().getClock() == null) return false;
        return Global.getSector().getClock().getElapsedDaysSince(creationTimestamp) >= LIFETIME_DAYS;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return isExpired() || isEnded();
    }

    @Override
    public boolean isImportant() {
        return false;
    }

    @Override
    public String getName() {
        return "Vault Excavation Report";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/forgotten_vault_world.png";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_PRODUCTION);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (market != null) {
            return market.getPrimaryEntity();
        }
        return null;
    }

    public MarketAPI getMarket() {
        return market;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getTitleColor(mode);
        info.addPara(getName(), tc, 0f);
        if (market != null) {
            info.addPara("- Bonus artifacts recovered on %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), market.getName());
        }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();

        if (market != null && market.getFaction() != null) {
            info.addImage(market.getFaction().getLogo(), width, 64f, 10f);
        }

        info.addPara("Production and archaeological recovery completed during the last month.", 10f);
        if (market != null) {
            info.addPara("- Bonus vault recovery delivered to %s storage.", 3f, Misc.getTextColor(), h, market.getName());
        }

        info.addSectionHeading("Subterranean Vault Breach", Alignment.MID, 10f);
        info.addPara(
            "Excavation teams scouring the deep pre-Collapse repositories bypassed secondary security bulkheads, retrieving two additional caches of technological artifacts and raw materials:",
            10f
        );

        if (bonusCargo != null && !bonusCargo.isEmpty()) {
            info.showCargo(bonusCargo, 10, true, 10f);
        } else {
            info.addPara("No bonus artifacts were recovered this cycle.", g, 10f);
        }

        if (recoveredShips != null && !recoveredShips.isEmpty()) {
            info.addSectionHeading("Intact Vessels Recovered", Alignment.MID, 10f);
            info.addPara(
                "Automated subterranean drydocks yielded preserved intact hulls, now transferred to planetary fleet storage:",
                10f
            );
            for (FleetMemberAPI member : recoveredShips) {
                String shipClass = member.getHullSpec().getHullNameWithDashClass();
                String shipDesignation = member.getHullSpec().getDesignation();
                info.addPara(
                    "- %s (%s)", 3f, Misc.getTextColor(), h,
                    member.getShipName() + ", " + shipClass, shipDesignation
                );
            }
        }
    }
}