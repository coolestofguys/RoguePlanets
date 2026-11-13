package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class HollowedCalderaGarrisonsCondition extends BaseMarketConditionPlugin {

    public static final float DEFENSE_MULT = 1.5f;
    public static final float DEFENSE_PENALTY_MULT = 0.5f;

    /** Re-checks once a day; the economy also re-applies the condition whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    private boolean isColonized() {
        return market != null && !market.isPlanetConditionMarketOnly() && market.getSize() > 0;
    }

    private boolean isMiningActive() {
        if (!isColonized()) return false;
        Industry mining = market.getIndustry(Industries.MINING);
        return mining != null && mining.isFunctional();
    }

    private boolean isMilitaryBaseActive() {
        if (!isColonized()) return false;
        Industry mb = market.getIndustry(Industries.MILITARYBASE);
        if (mb != null && mb.isFunctional()) return true;
        Industry hc = market.getIndustry(Industries.HIGHCOMMAND);
        return hc != null && hc.isFunctional();
    }

    public boolean isSynergyActive() {
        return isMiningActive() && isMilitaryBaseActive();
    }

    private void applyModifiers(String id) {
        if (market == null) return;

        if (isColonized()) {
            if (isSynergyActive()) {
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(
                    id,
                    DEFENSE_MULT,
                    "Excavated Subterranean Bastions"
                );
            } else {
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(
                    id,
                    DEFENSE_PENALTY_MULT,
                    "Unsecured Subterranean Infiltration Shafts"
                );
            }
        } else {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
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
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Hollowed Caldera Garrisons");
        tooltip.addPara(
            "The crust of this world is riddled with dormant volcanic complexes, cavernous collapsed calderas, and sprawling subterranean networks of drained magma chambers and basaltic lava tubes. When fully surveyed, mined, and fortified by military coordination, these abyssal vaults grant impenetrable subterranean bunkers. However, without active mining maintenance and specialized garrison forces securing the labyrinthine underground breaches, these empty volcanic shafts serve as unmonitored infiltration conduits that attackers can easily exploit to bypass surface perimeters.",
            10f
        );

        boolean colonized = isColonized();
        boolean active = isSynergyActive();
        boolean mining = isMiningActive();
        boolean military = isMilitaryBaseActive();

        tooltip.addSectionHeading("Subterranean Fortification Status", Alignment.MID, 10f);

        if (colonized) {
            if (active) {
                tooltip.addPara("Excavated Garrison Bastions: %s", 10f,
                    Misc.getPositiveHighlightColor(),
                    "OPERATIONAL (Mining & Garrison Synchronized)"
                );
                tooltip.addPara(
                    "Colony ground defense strength is multiplied by %s.",
                    5f,
                    Misc.getPositiveHighlightColor(),
                    "x" + DEFENSE_MULT
                );
            } else {
                tooltip.addPara("Excavated Garrison Bastions: %s", 10f,
                    Misc.getNegativeHighlightColor(),
                    "COMPROMISED (Defensive Perimeter Breached)"
                );
                tooltip.addPara(
                    "Because one or both required industries are missing or disrupted, subterranean access tunnels remain unsecured. Colony ground defense strength is multiplied by %s.",
                    5f,
                    Misc.getNegativeHighlightColor(),
                    "x" + DEFENSE_PENALTY_MULT
                );
            }

            SynergyTooltip.prerequisiteHeader(tooltip);
            SynergyTooltip.prerequisite(tooltip, "Mining Industry", mining);
            SynergyTooltip.prerequisite(tooltip, "Military Base or High Command", military);
        } else {
            SynergyTooltip.uncolonized(tooltip, "Excavation Potential",
                "When colonized with both Mining and a Military Base or High Command, colony ground defense strength is multiplied by x" + DEFENSE_MULT
                + ". If either industry is missing or disrupted, ground defenses are penalized to x" + DEFENSE_PENALTY_MULT + ".");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
