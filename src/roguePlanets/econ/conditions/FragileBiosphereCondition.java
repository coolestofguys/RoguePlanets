package roguePlanets.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class FragileBiosphereCondition extends BaseMarketConditionPlugin {

    public static final String PATROL_HQ = "patrolhq";
    public static final String MILITARY_BASE = "militarybase";
    public static final String HIGH_COMMAND = "highcommand";

    private float messageCooldown = 0f;

    public static boolean isIndustryAllowed(Industry ind) {
        if (ind == null) return true;

        if (!ind.isIndustry()) {
            return true;
        }

        return Industries.FARMING.equals(ind.getId()) || "farming".equals(ind.getId());
    }

    private void enforceAllowedIndustries() {
        if (market == null) return;

        // The normal case, nothing forbidden, costs only this quick loop: the list is built only when needed
        List<Industry> toRemove = null;
        for (Industry ind : market.getIndustries()) {
            if (!isIndustryAllowed(ind)) {
                if (toRemove == null) toRemove = new ArrayList<Industry>();
                toRemove.add(ind);
            }
        }

        if (toRemove == null) return;

        boolean shouldNotify = messageCooldown <= 0f;

        for (Industry ind : toRemove) {
            float cost = ind.getBuildCost();
            String id = ind.getId();
            String name = ind.getCurrentName();

            market.removeIndustry(id, null, false);

            if (MILITARY_BASE.equals(id) || HIGH_COMMAND.equals(id)) {
                if (!market.hasIndustry(PATROL_HQ)) {
                    market.addIndustry(PATROL_HQ);
                }
            }

            if (market.isPlayerOwned()) {
                // the refund always goes through; the cooldown only limits how often the message shows
                if (Global.getSector().getPlayerFleet() != null) {
                    Global.getSector().getPlayerFleet().getCargo().getCredits().add(cost);
                }
                if (shouldNotify) {
                    Global.getSector().getCampaignUI().addMessage(
                        "Construction halted on " + market.getName() + " [" + name +
                        "]: Only Farming is permitted under Fragile Biosphere zoning. Structures upgraded into industries are reverted, and credits are refunded.",
                        Misc.getNegativeHighlightColor()
                    );
                    messageCooldown = 1.5f;
                }
            }
        }
    }

    @Override
    public void apply(String id) {
        if (market != null) {
            market.getHazard().modifyFlat(id, -0.10f, "Fragile Biosphere");
            enforceAllowedIndustries();
        }
    }

    @Override
    public void unapply(String id) {
        if (market != null) {
            market.getHazard().unmodify(id);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (messageCooldown > 0f) {
            messageCooldown -= amount;
        }
        if (market != null) {
            enforceAllowedIndustries();
        }
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Fragile Biosphere");
        tooltip.addPara(
            "The world's hyper-delicate planetary ecosystem strictly prohibits commercial and heavy industrial zoning. " +
            "Farming is the only permitted industry. Any non-agricultural industry constructed, or any civilian or military " +
            "structure upgraded into an industry (such as Patrol HQ to Military Base), will be dismantled and refunded.",
            10f
        );
        tooltip.addPara("-10%% hazard rating.", 10f, Misc.getPositiveHighlightColor(), "-10%");
        tooltip.addPara(
            "Strict zoning: %s is the only industry permitted on this world. All other industries and structure-to-industry upgrades are prohibited.",
            10f,
            Misc.getHighlightColor(),
            "Farming"
        );
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}