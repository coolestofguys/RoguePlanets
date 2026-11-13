package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class SupercooledCryoSinksCondition extends BaseMarketConditionPlugin {

    public static final float SENSOR_BONUS = 700f;
    public static final float HAZARD_PENALTY = 0.50f;
    public static final float STABILITY_PENALTY = -3f;
    public static final float GRACE_PERIOD_DAYS = 120f;

    public static final String GRACE_TIMER_KEY = "$cryosinks_days_active";

    private float getDaysActive() {
        if (market == null) return 0f;
        return market.getMemoryWithoutUpdate().getFloat(GRACE_TIMER_KEY);
    }

    private void addDaysActive(float days) {
        if (market == null) return;
        float current = getDaysActive();
        market.getMemoryWithoutUpdate().set(GRACE_TIMER_KEY, current + days);
    }

    private boolean isColonized() {
        return market != null && !market.isPlanetConditionMarketOnly() && market.getSize() > 0;
    }

    private boolean isInGracePeriod() {
        if (!isColonized()) return true;
        return getDaysActive() < GRACE_PERIOD_DAYS;
    }

    private boolean isHighStability() {
        if (!isColonized()) return false;
        // Requires stability strictly greater than 5 (i.e. 6 or higher)
        return market.getStabilityValue() > 5;
    }

    /** Re-checks the hazard and stability penalty once a day; the sensor bonus follows the player and is checked every frame. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    /** The sensor bonus: only while stabilized, only on a colony the player owns, and only in that colony's system. */
    private void updateSensorBonus(String id) {
        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        if (playerFleet == null || market == null) return;

        boolean on = isColonized() && market.isPlayerOwned() && isHighStability()
            && playerFleet.getContainingLocation() == market.getContainingLocation();
        if (on) {
            playerFleet.getStats().getSensorRangeMod().modifyFlat(id, SENSOR_BONUS, "Supercooled Cryo-Sinks");
        } else {
            playerFleet.getStats().getSensorRangeMod().unmodifyFlat(id);
        }
    }

    private void removeSensorBonus(String id) {
        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        if (playerFleet != null) {
            playerFleet.getStats().getSensorRangeMod().unmodifyFlat(id);
        }
    }

    /** Hazard and stability penalty: only once the founding grace period is over and stability is 5 or lower. */
    private void updatePenalty(String id) {
        if (market == null) return;
        if (isColonized() && !isHighStability() && !isInGracePeriod()) {
            market.getHazard().modifyFlat(id, HAZARD_PENALTY, "Supercooled Cryo-Sinks (Catastrophic Flash Freeze)");
            market.getStability().modifyFlat(id, STABILITY_PENALTY, "Ruptured Cryogenic Wellheads");
        } else {
            market.getHazard().unmodify(id);
            market.getStability().unmodify(id);
        }
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        updatePenalty(id);
        updateSensorBonus(id);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getHazard().unmodify(id);
        market.getStability().unmodify(id);
        removeSensorBonus(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null || !isColonized()) return;

        String id = getModId();
        updateSensorBonus(id);

        if (Global.getSector() != null && Global.getSector().getClock() != null) {
            float days = Global.getSector().getClock().convertToDays(amount);
            if (days > 0 && getDaysActive() < GRACE_PERIOD_DAYS) {
                addDaysActive(days);
            }
            dailyCheck.advance(days);
            if (dailyCheck.intervalElapsed()) {
                updatePenalty(id);
            }
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Supercooled Cryo-Sinks");
        tooltip.addPara(
            "Vast subterranean reserves of liquid nitrogen and pressurized ammonia geysers permeate the geological crust of this world. When tapped by disciplined colonial engineers, these hyper-cold fluid reservoirs act as near-infinite thermal sinks for high-yield quantum radar arrays, dramatically expanding system-wide sensor coverage. However, maintaining thermodynamic containment requires rigorous technical oversight; should colonial infrastructure falter, runaway cryogenic decompression vents into atmospheric domes, triggering devastating flash freezes.",
            10f
        );

        boolean highStability = isHighStability();
        boolean grace = isInGracePeriod();
        boolean colonized = isColonized();

        tooltip.addSectionHeading("Cryogenic Array Status", Alignment.MID, 10f);

        if (!colonized) {
            tooltip.addPara("Containment Status: %s", 10f,
                Misc.getGrayColor(),
                "UNEXPLOITED (Requires established colony)"
            );
            tooltip.addPara(
                "Subterranean cryo-geysers remain capped. Establishing a colony grants a 120-day founding grace period before thermal stability protocols activate.",
                Misc.getGrayColor(),
                5f
            );
        } else if (highStability) {
            tooltip.addPara("Containment Status: %s", 10f,
                Misc.getPositiveHighlightColor(),
                "STABILIZED (Colony stability above 5)"
            );
            tooltip.addPara("Your fleet's sensor range in this system (colonies you own): %s", 5f,
                Misc.getPositiveHighlightColor(),
                "+" + (int)SENSOR_BONUS + " (Equivalent to a Domain Sensor Array; stacks with stable location arrays)"
            );
        } else if (grace) {
            int remainingDays = Math.max(0, Math.round(GRACE_PERIOD_DAYS - getDaysActive()));
            tooltip.addPara("Containment Status: %s", 10f,
                Misc.getHighlightColor(),
                "FOUNDING GRACE PERIOD (" + remainingDays + " days remaining)"
            );
            tooltip.addPara(
                "Automated emergency isolation bulkheads are actively suppressing cryogenic venting while early colonial infrastructure is established. Sensor arrays remain offline until stability exceeds 5, but disaster protocols are suspended.",
                Misc.getGrayColor(),
                5f
            );
        } else {
            tooltip.addPara("Containment Status: %s", 10f,
                Misc.getNegativeHighlightColor(),
                "CATASTROPHIC FLASH FREEZE (Colony stability 5 or lower)"
            );
            tooltip.addPara("Colony hazard rating: %s", 5f,
                Misc.getNegativeHighlightColor(),
                "+50% (Ruptured cryo-vents and pervasive freezing storms)"
            );
            tooltip.addPara("Colony stability: %s", 5f,
                Misc.getNegativeHighlightColor(),
                "" + (int)STABILITY_PENALTY + " (Widespread infrastructure destruction and thermal shock)"
            );
            tooltip.addPara("System-wide sensor array: %s", 5f,
                Misc.getGrayColor(),
                "OFFLINE (Subterranean radar sinks flooded and frozen)"
            );
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}