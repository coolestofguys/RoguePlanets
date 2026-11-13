package roguePlanets.econ.conditions;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class VacationParadiseResortCondition extends BaseMarketConditionPlugin {

    public static final float INCOME_PER_STABILITY = 0.05f; // +5% income per point above 5

    /** Re-checks the resort once a day; the economy also re-applies it whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private boolean isColonized() {
        return SynergyTooltip.isColonized(market);
    }

    private boolean isFoodProductionActive() {
        if (!isColonized()) return false;
        return SynergyTooltip.isFunctional(market, Industries.FARMING)
            || SynergyTooltip.isFunctional(market, Industries.AQUACULTURE);
    }

    private boolean isCommerceActive() {
        if (!isColonized()) return false;
        return SynergyTooltip.isFunctional(market, Industries.COMMERCE);
    }

    public boolean isResortActive() {
        return isColonized() && isFoodProductionActive() && isCommerceActive();
    }

    private int getStabilityValue() {
        if (market == null) return 0;
        return Math.max(0, Math.min(10, Math.round(market.getStability().getModifiedValue())));
    }

    private int getStabilityDiff() {
        return getStabilityValue() - 5;
    }

    private String getRatingReviewText(int stability) {
        if (stability >= 10) return "10/10 - Galactic Five-Star Paradise";
        if (stability >= 8)  return stability + "/10 - Highly Recommended Getaway";
        if (stability >= 6)  return stability + "/10 - Favorable Tourist Destination";
        if (stability == 5)  return "5/10 - Average / Mixed Reviews";
        if (stability >= 3)  return stability + "/10 - Disappointing / Travel Warning";
        return stability + "/10 - Critical Hazard / Avoid At All Costs";
    }

    private Color getRatingColor(int stability) {
        if (stability >= 6) return Misc.getPositiveHighlightColor();
        if (stability == 5) return Misc.getHighlightColor();
        return Misc.getNegativeHighlightColor();
    }

    private void applyModifiers(String id) {
        if (market == null) return;

        if (isResortActive()) {
            // The trade-off for the income bonus: ground defenses are multiplied to 0 whatever the colony builds
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(
                id,
                0f,
                "Vacation Paradise & Resort (Demilitarized Luxury Sanctuary)"
            );

            // Tourism revenue scales with stability, for the colony's whole income
            int diff = getStabilityDiff();
            if (diff != 0) {
                market.getIncomeMult().modifyMult(id, 1f + diff * INCOME_PER_STABILITY, "Resort Reviews");
            } else {
                market.getIncomeMult().unmodify(id);
            }
        } else {
            clearModifiers(id);
        }
    }

    private void clearModifiers(String id) {
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
        market.getIncomeMult().unmodify(id);
        // an older version also moved the hazard rating
        market.getHazard().unmodify(id);
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        applyModifiers(id);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        clearModifiers(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
        if (dailyCheck.intervalElapsed()) {
            applyModifiers(getModId());
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Vacation Paradise & Resort");
        tooltip.addPara(
            "Basking in pristine natural beauty, picturesque coastlines, and exotic local spices, this world can be changed into an elite sector-wide holiday retreat. Sprawling luxury hotels, vibrant commercial promenades, and serene getaways draw wealthy travelers from core and rim worlds alike. Because open civilian resort enclaves dominate the terrain, conventional ground defense operations are completely unviable.",
            10f
        );

        boolean colonized = isColonized();
        boolean active = isResortActive();
        boolean food = isFoodProductionActive();
        boolean commerce = isCommerceActive();

        tooltip.addSectionHeading("Resort Review Board", Alignment.MID, 10f);

        if (colonized) {
            int stability = getStabilityValue();
            int diff = getStabilityDiff();

            tooltip.addPara("Sector Resort Review: %s", 10f,
                getRatingColor(stability),
                getRatingReviewText(stability)
            );

            if (active) {
                tooltip.addPara("Colony ground defenses: %s", 5f,
                    Misc.getNegativeHighlightColor(),
                    "0x (Demilitarized Resort Sanctuary)"
                );
                tooltip.addPara("Tourism Revenue: %s", 5f,
                    diff >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                    (diff >= 0 ? "+" : "") + Math.round(diff * INCOME_PER_STABILITY * 100f) + "%"
                );
            } else {
                tooltip.addPara("Resort Operations: %s", 5f,
                    Misc.getNegativeHighlightColor(),
                    "SUSPENDED (Prerequisites Incomplete)"
                );
                tooltip.addPara(
                    "To open to sector tourism, the colony requires active food production (Farming or Aquaculture) paired with an operational Commerce industry.",
                    Misc.getGrayColor(),
                    5f
                );
            }

            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Farming or Aquaculture", food);
            SynergyTooltip.prerequisite(tooltip, "Commerce Industry", commerce);
        } else {
            SynergyTooltip.uncolonized(tooltip, "Resort Potential",
                "When colonized with active food production (Farming or Aquaculture) and a Commerce hub, this world becomes a demilitarized tourist haven, whose colony income scales with its 1/10 to 10/10 Sector Review Rating based on colony stability.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
