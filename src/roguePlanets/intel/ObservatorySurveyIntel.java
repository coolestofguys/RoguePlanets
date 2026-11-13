package roguePlanets.intel;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The Cold Sky Observatory's monthly survey report. It stays in the intel log for 30 days, until the next survey, and
 * clicking it takes the map to the system where the planet was surveyed.
 */
public class ObservatorySurveyIntel extends BaseIntelPlugin {

    public static final float LIFETIME_DAYS = 30f;

    private final PlanetAPI planet;
    private final String colonyName;
    private final String planetName;
    private final String typeName;
    private final String systemName;
    private final String surveyClass;
    private final String dataName;
    private final long creationTimestamp;

    public ObservatorySurveyIntel(PlanetAPI planet, String colonyName, String typeName, String systemName,
                                  String surveyClass, String dataName) {
        this.planet = planet;
        this.colonyName = colonyName;
        this.planetName = planet.getName();
        this.typeName = typeName;
        this.systemName = systemName;
        this.surveyClass = surveyClass;
        this.dataName = dataName;
        this.creationTimestamp = Global.getSector().getClock().getTimestamp();
    }

    private boolean isExpired() {
        return Global.getSector().getClock().getElapsedDaysSince(creationTimestamp) >= LIFETIME_DAYS;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (isExpired() && !isEnded()) {
            endImmediately();
            Global.getSector().getIntelManager().removeIntel(this);
        }
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
        return "Observatory Survey: " + planetName;
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/cold_sky_observatory.png";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_EXPLORATION);
        return tags;
    }

    /** The system where the planet was surveyed, so the map centres on it. */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (planet != null && planet.getStarSystem() != null && planet.getStarSystem().getHyperspaceAnchor() != null) {
            return planet.getStarSystem().getHyperspaceAnchor();
        }
        return planet;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getTitleColor(mode);
        info.addPara(getName(), tc, 0f);
        info.addPara("- %s, %s, %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), systemName, typeName, "survey " + surveyClass.toLowerCase());
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        info.addPara("The %s observatory has fully surveyed %s, a %s in the %s star system.", 10f, h,
            colonyName, planetName, typeName, systemName);
        info.addPara("Survey class: %s", 5f, h, surveyClass);
        info.addPara("A %s item has been delivered to %s's local storage.", 5f, h, dataName, colonyName);
        info.addPara("The planet's conditions are now visible. Select this entry on the map to see where it is. "
            + "This report is kept for 30 days, until the next survey.", Misc.getGrayColor(), 10f);
    }
}
