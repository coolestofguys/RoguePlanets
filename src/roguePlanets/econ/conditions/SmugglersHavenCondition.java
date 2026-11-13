package roguePlanets.econ.conditions;

import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class SmugglersHavenCondition extends BaseMarketConditionPlugin {

    public static final float TARIFF_MULT = 0.25f; // 75% reduction (player pays only 25% tariff)

    /** Re-checks once a day; the economy also re-applies the condition whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private boolean isColonized() {
        return market != null && !market.isPlanetConditionMarketOnly() && market.getSize() > 0;
    }

    private boolean isCommerceActive() {
        if (!isColonized()) return false;
        Industry commerce = market.getIndustry(Industries.COMMERCE);
        return commerce != null && commerce.isFunctional();
    }

    private boolean isHeavyIndustryActive() {
        if (!isColonized()) return false;
        Industry hi = market.getIndustry(Industries.HEAVYINDUSTRY);
        if (hi != null && hi.isFunctional()) return true;
        Industry ow = market.getIndustry(Industries.ORBITALWORKS);
        return ow != null && ow.isFunctional();
    }

    public boolean isSynergyActive() {
        return isColonized() && isCommerceActive() && isHeavyIndustryActive();
    }

    private float calculatePenaltiesToNullify() {
        if (market == null) return 0f;
        float totalPenalty = 0f;

        // Inspect active flat stability modifiers for Commerce and Free Port penalties
        Map<String, MutableStat.StatMod> flatMods = market.getStability().getFlatMods();
        if (flatMods != null) {
            for (Map.Entry<String, MutableStat.StatMod> entry : flatMods.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
                String desc = entry.getValue().getDesc() != null ? entry.getValue().getDesc().toLowerCase() : "";
                float val = entry.getValue().getValue();

                if (val < 0) {
                    if (key.contains("free_port") || desc.contains("free port") || key.contains("freeport")
                            || key.contains("commerce") || desc.contains("commerce")) {
                        totalPenalty += Math.abs(val);
                    }
                }
            }
        }

        // Fallback: If Free Port is active but hasn't applied a named flat mod yet, offset vanilla's 3-point penalty
        if (market.isFreePort() && totalPenalty == 0f) {
            totalPenalty = 3f;
        }

        return totalPenalty;
    }

    private void applyModifiers(String id) {
        if (market == null) return;

        if (isSynergyActive()) {
            // 75% Tariff reduction on the market (multiplies baseline 30% tariff down to 7.5%)
            market.getTariff().modifyMult(
                id,
                TARIFF_MULT,
                "Smugglers' Haven (75% Tariff Reduction)"
            );

            // Nullify Free Port & Commerce stability penalties by granting an exact offset
            float penaltyOffset = calculatePenaltiesToNullify();
            if (penaltyOffset > 0f) {
                market.getStability().modifyFlat(
                    id,
                    penaltyOffset,
                    "Smugglers' Haven (Black Market Order)"
                );
            } else {
                market.getStability().unmodifyFlat(id);
            }
        } else {
            market.getTariff().unmodifyMult(id);
            market.getStability().unmodifyFlat(id);
        }
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        applyModifiers(id);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getTariff().unmodifyMult(id);
        market.getStability().unmodifyFlat(id);
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
        tooltip.addTitle("Smugglers' Haven");
        tooltip.addPara(
            "Tucked away beneath the shifting dunes and scorched canyons of this world lies a sprawling, lawless trading hub where outlaws, scavengers, and rogue traders broker unsanctioned deals. When paired with high-volume manufacturing and an open commercial bazaar, the settlement thrives as an autonomous freebooter haven where black-market cartels self-regulate, preventing the lawlessness of open commerce and free ports from destabilizing local authority.",
            10f
        );

        boolean colonized = isColonized();
        boolean active = isSynergyActive();
        boolean commerce = isCommerceActive();
        boolean heavyIndustry = isHeavyIndustryActive();

        tooltip.addSectionHeading("Black-Market Haven Status", Alignment.MID, 10f);

        if (colonized) {
            if (active) {
                float nullified = calculatePenaltiesToNullify();
                tooltip.addPara("Smugglers' Hub: %s", 10f,
                    Misc.getPositiveHighlightColor(),
                    "OPERATIONAL (Commerce & Heavy Industry Synchronized)"
                );
                tooltip.addPara("Player Tariffs on Commerce: %s", 5f,
                    Misc.getPositiveHighlightColor(),
                    "-75% (Reduced to 7.5% effective tariff)"
                );
                tooltip.addPara("Free Port & Commerce Stability Penalties: %s", 5f,
                    Misc.getPositiveHighlightColor(),
                    nullified > 0 ? "NULLIFIED (+" + (int)nullified + " Stability Compensation)" : "NULLIFIED (No Active Penalties)"
                );
            } else {
                tooltip.addPara("Smugglers' Hub: %s", 10f,
                    Misc.getNegativeHighlightColor(),
                    "INACTIVE (Requires Commerce & Heavy Industry / Orbital Works)"
                );
                tooltip.addPara(
                    "Establishing both functional Commerce and Heavy Industry or Orbital Works activates the outpost's underworld trade networks, cutting local commerce tariffs by 75% and completely nullifying stability penalties from Free Port status and Commerce.",
                    Misc.getGrayColor(),
                    5f
                );
            }

            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Commerce Industry", commerce);
            SynergyTooltip.prerequisite(tooltip, "Heavy Industry or Orbital Works", heavyIndustry);
        } else {
            SynergyTooltip.uncolonized(tooltip, "Outlaw Frontier Potential",
                "When colonized and developed with both Commerce and Heavy Industry or Orbital Works, this lawless trade post slashes player tariffs by 75% and cancels all stability penalties associated with Commerce and Free Port operations.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
