package roguePlanets.econ.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Pilgrim World. A holy destination from before the Collapse, whose pilgrims keep the faith only while no machine minds
 * are installed on the colony.
 *
 * For every working industry on the colony the world gives +1 stability and +20% accessibility. If any industry has an
 * AI core installed, or the colony's administrator is an AI core, the same amounts turn into penalties instead.
 */
public class PilgrimWorldCondition extends BaseMarketConditionPlugin {

    public static final String CONDITION_ID = "pilgrim_world";

    public static final float STABILITY_PER_INDUSTRY = 1f;
    public static final float ACCESSIBILITY_PER_INDUSTRY = 0.20f;

    /** Re-checks once a day; the economy also re-applies the condition whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    // ---------------------------------------------------------------- what counts

    /** Working industries (not structures such as ports or defenses, and not Population & Infrastructure). */
    private int countIndustries() {
        if (!SynergyTooltip.isColonized(market)) return 0;
        int n = 0;
        for (Industry industry : market.getIndustries()) {
            if (industry == null || !industry.isIndustry() || !industry.isFunctional()) continue;
            if (Industries.POPULATION.equals(industry.getId())) continue;
            n++;
        }
        return n;
    }

    /** Names of everything on the colony that has a machine mind: industries with a core installed, and an AI administrator. */
    private List<String> coreSources() {
        List<String> out = new ArrayList<String>();
        if (!SynergyTooltip.isColonized(market)) return out;
        for (Industry industry : market.getIndustries()) {
            if (industry != null && industry.getAICoreId() != null) out.add(industry.getCurrentName());
        }
        if (market.getAdmin() != null && market.getAdmin().isAICore()) out.add("AI core administrator");
        return out;
    }

    // ---------------------------------------------------------------- the effect

    private void applyModifiers(String id) {
        if (market == null) return;

        int n = countIndustries();
        if (n <= 0) {
            clearModifiers(id);
            return;
        }

        boolean broken = !coreSources().isEmpty();
        float sign = broken ? -1f : 1f;
        String label = broken ? "Pilgrim World (sanctity broken by AI cores)" : "Pilgrim World (sanctified)";

        market.getStability().modifyFlat(id, sign * n * STABILITY_PER_INDUSTRY, label);
        market.getAccessibilityMod().modifyFlat(id, sign * n * ACCESSIBILITY_PER_INDUSTRY, label);
    }

    private void clearModifiers(String id) {
        market.getStability().unmodify(id);
        market.getAccessibilityMod().unmodify(id);
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

    // ---------------------------------------------------------------- tooltip

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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Pilgrim World");
        tooltip.addPara(
            "Long before the Collapse, pilgrims crossed the sector to walk this world's processional roads and pray at its shrines. "
            + "The faithful still come, and they bring order and traffic wherever the work is done by human hands. "
            + "Where machine minds are installed, they turn away.",
            10f);

        SynergyTooltip.heading(tooltip, "Sanctity");

        if (SynergyTooltip.isColonized(market)) {
            int n = countIndustries();
            List<String> cores = coreSources();
            boolean broken = !cores.isEmpty();

            SynergyTooltip.status(tooltip, "Sanctity", !broken,
                broken ? "BROKEN (machine minds installed)" : "INTACT (no AI cores or AI administrator)");

            tooltip.addPara("Working industries: %s", 5f, Misc.getHighlightColor(), String.valueOf(n));

            int stability = Math.round(n * STABILITY_PER_INDUSTRY);
            int access = Math.round(n * ACCESSIBILITY_PER_INDUSTRY * 100f);
            if (broken) {
                tooltip.addPara("Stability: %s    Accessibility: %s", 3f, Misc.getNegativeHighlightColor(),
                    "-" + stability, "-" + access + "%");
                tooltip.addPara("Driving the pilgrims away:", 5f);
                for (String source : cores) {
                    tooltip.addPara(" - %s", 3f, Misc.getNegativeHighlightColor(), source);
                }
            } else {
                tooltip.addPara("Stability: %s    Accessibility: %s", 3f, Misc.getPositiveHighlightColor(),
                    "+" + stability, "+" + access + "%");
            }

            SynergyTooltip.note(tooltip,
                "Each working industry gives +1 stability and +20% accessibility. Installing an AI core in any industry, "
                + "or appointing an AI core administrator, turns every one of those bonuses into the same-sized penalty.");
        } else {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "A colony here earns +1 stability and +20% accessibility for every working industry, as long as no AI cores are installed and the administrator is not an AI core. "
                + "If any are, the same amounts become penalties.");
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
