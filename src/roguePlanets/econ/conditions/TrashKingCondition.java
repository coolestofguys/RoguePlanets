package roguePlanets.econ.conditions;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Trash King. A hermit who has lived alone on a wrecked world for longer than anyone remembers, and whom everyone who
 * arrives ends up respecting. He is the colony's administrator from the day it is founded.
 *
 * Every 90 to 180 days he announces a new decree, picked at random, each with a good side and a bad side (some mild,
 * some wild). He is never in the player's own list of administrators.
 *
 * If the player puts one of their own administrators in his place, he steps aside. The colony then suffers a growing
 * "Deposed King" penalty over the next 30 days. Landing at the colony in that time offers an option to call him back;
 * after 30 days he is gone for good and the full penalty stays.
 */
public class TrashKingCondition extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    public static final String CONDITION_ID = "trash_king";

    public static final float GRACE_DAYS = 30f;
    public static final float DEPOSED_STABILITY = -4f;
    public static final float DEPOSED_ACCESSIBILITY = -0.25f;
    public static final int DECREE_MIN_DAYS = 90;
    public static final int DECREE_MAX_DAYS = 180;

    private static final String K_KING = "$rp_king_person";
    private static final String K_STATE = "$rp_king_state";
    private static final String K_GRACE = "$rp_king_graceLeft";
    private static final String K_DECREE = "$rp_king_decree";
    private static final String K_DECREE_LEFT = "$rp_king_decreeLeft";

    private static final String REIGNING = "reigning";
    private static final String GRACE = "grace";
    private static final String EXILED = "exiled";

    /** Re-checked once a day; the economy also re-applies the condition whenever the market refreshes. */
    private final IntervalUtil dailyCheck = new IntervalUtil(1f, 1f);

    // ---------------------------------------------------------------- decrees

    /** One of the king's decrees. Zero or one means "no effect" for each number. */
    public static class Decree {
        final String id, name, quote;
        final boolean wild;
        final float stability, accessibility, hazard, incomePercent, groundMult, upkeepMult, growth;

        Decree(String id, String name, String quote, boolean wild, float stability, float accessibility, float hazard,
               float incomePercent, float groundMult, float upkeepMult, float growth) {
            this.id = id;
            this.name = name;
            this.quote = quote;
            this.wild = wild;
            this.stability = stability;
            this.accessibility = accessibility;
            this.hazard = hazard;
            this.incomePercent = incomePercent;
            this.groundMult = groundMult;
            this.upkeepMult = upkeepMult;
            this.growth = growth;
        }
    }

    //                                                  id            name                         quote                                                          wild   stab  acc    haz    inc   gnd   upk   grow
    public static final Decree[] DECREES = {
        new Decree("hats",      "Hats Are Mandatory",       "\"A bare head is a rude head. Hats, all of you.\"",              false,  2f,  0f,    0f,   -10f, 1f,   1f,   0f),
        new Decree("ale",       "Free Ale on Fridays",      "\"Every Friday is a Friday if I say so.\"",                      false,  2f,  0f,    0f,     0f, 1f,   1.15f, 0f),
        new Decree("dark",      "No Strangers After Dark",  "\"If you don't live here, you're a stranger. Go home.\"",        false,  0f, -0.15f, 0f,     0f, 1.5f, 1f,   0f),
        new Decree("cleanup",   "The Great Cleanup",        "\"Pick it up, put it in a pile, and don't tell me which pile.\"", false, -1f,  0f,   -0.15f,  0f, 1f,   1f,   0f),
        new Decree("gates",     "Open Gates, Free Trade",   "\"Anyone who can carry something is welcome here.\"",            false,  0f,  0.20f, 0f,     0f, 0.75f, 1f,  0f),
        new Decree("parade",    "Royal Parade",             "\"I'll be passing by your window at noon. Wave.\"",              false, -1f,  0f,    0f,     0f, 1f,   1f,   5f),
        new Decree("nap",       "The King Is Napping",      "\"Not today. Do your own thinking.\"",                           false,  0f,  0f,    0f,     0f, 1f,   1f,   0f),
        new Decree("debts",     "All Debts Are Forgiven",   "\"Nobody owes anybody anything. Start again.\"",                 true,   4f,  0f,    0f,   -30f, 1f,   1f,   0f),
        new Decree("looting",   "Festival of Looting",      "\"For one season, whatever you can carry is yours.\"",           true,  -4f,  0f,    0f,    35f, 1f,   1f,   0f),
        new Decree("silence",   "Silence in the Streets",   "\"I'm thinking. Everybody whisper.\"",                           true,   5f, -0.20f, 0f,     0f, 1f,   1f,  -10f),
        new Decree("rust",      "War on Rust",              "\"The rust has been warned. Scrub.\"",                           true,   0f,  0f,   -0.30f,  0f, 1f,   1.40f, 0f),
        new Decree("tax",       "Tax the Poor",             "\"They have nothing, so they won't miss it.\"",                 true,  -3f,  0f,    0f,     1f, 1f,   1f,   0f),
        new Decree("soldiers",  "Everyone Is a Soldier Today", "\"Pick up something heavy and look angry.\"",                 true,  -3f,  0f,    0f,   -15f, 2.5f, 1f,   0f)
    };

    private static Decree decreeById(String id) {
        if (id == null) return null;
        for (Decree d : DECREES) if (d.id.equals(id)) return d;
        return null;
    }

    // ---------------------------------------------------------------- state

    private boolean colonized() {
        return SynergyTooltip.isColonized(market);
    }

    private static MemoryAPI mem(MarketAPI m) {
        return m.getMemoryWithoutUpdate();
    }

    private static String stateOf(MarketAPI m) {
        return m == null ? null : mem(m).getString(K_STATE);
    }

    private static PersonAPI kingOf(MarketAPI m) {
        if (m == null) return null;
        Object o = mem(m).get(K_KING);
        return o instanceof PersonAPI ? (PersonAPI) o : null;
    }

    /** True while the king rules the colony. Other conditions can use this. */
    public static boolean isReigning(MarketAPI m) {
        return m != null && m.hasCondition(CONDITION_ID) && REIGNING.equals(stateOf(m));
    }

    private static String titleOf(PersonAPI king) {
        return "Trash King";
    }

    private boolean isKingAdmin(PersonAPI king) {
        return king != null && market.getAdmin() != null && king.getId().equals(market.getAdmin().getId());
    }

    private Decree currentDecree() {
        return decreeById(mem(market).getString(K_DECREE));
    }

    private void announce(String text, Color color) {
        if (market == null || !market.isPlayerOwned()) return;
        try {
            Global.getSector().getCampaignUI().addMessage(text, color);
        } catch (Throwable ignored) {
            // the message is only a courtesy
        }
    }

    // ---------------------------------------------------------------- the king

    private PersonAPI createKing() {
        Random random = new Random();
        FactionAPI independent = Global.getSector().getFaction(Factions.INDEPENDENT);
        PersonAPI p = independent.createRandomPerson(random);
        String[] male = {"Tolliver", "Odo", "Bram", "Hobart", "Ludo", "Garrick"};
        String[] female = {"Magda", "Hesper", "Ysolde", "Brunhild", "Tansy", "Odalys"};
        String[] last = {"Grist", "Slagg", "Rusk", "Dross", "Clatter", "Heap", "Spall", "Tinker"};
        String[] firsts = p.getGender() == Gender.FEMALE ? female : male;
        p.getName().setFirst(firsts[random.nextInt(firsts.length)]);
        p.getName().setLast(last[random.nextInt(last.length)]);
        p.setPostId(Ranks.POST_ADMINISTRATOR);
        p.setRankId(Ranks.CITIZEN);
        return p;
    }

    /** Puts the king in the colony's administrator slot and frees anyone of the player's who was holding it. */
    private void seat(PersonAPI king) {
        try {
            for (AdminData data : Global.getSector().getCharacterData().getAdmins()) {
                if (data.getMarket() == market) data.setMarket(null);
            }
        } catch (Throwable ignored) {
            // nothing of the player's to free
        }
        market.setAdmin(king);
    }

    private void setEntityFlag(boolean grace, PersonAPI king) {
        if (market.getPrimaryEntity() == null) return;
        MemoryAPI em = market.getPrimaryEntity().getMemoryWithoutUpdate();
        if (grace && king != null) {
            em.set("$rpKingGrace", true);
            em.set("$rpKingName", king.getNameString());
            em.set("$rpKingTitle", titleOf(king));
        } else {
            em.unset("$rpKingGrace");
        }
    }

    /** A returning king picks up the decree he left, with the days it had left: calling him back cannot reroll it. */
    private void resumeDecree() {
        if (mem(market).getString(K_DECREE) == null || mem(market).getFloat(K_DECREE_LEFT) <= 0f) rollDecree();
    }

    private void rollDecree() {
        Decree old = currentDecree();
        Random random = new Random();
        Decree pick;
        do {
            pick = DECREES[random.nextInt(DECREES.length)];
        } while (pick == old && DECREES.length > 1);
        mem(market).set(K_DECREE, pick.id);
        mem(market).set(K_DECREE_LEFT, (float) (DECREE_MIN_DAYS + random.nextInt(DECREE_MAX_DAYS - DECREE_MIN_DAYS + 1)));

        PersonAPI king = kingOf(market);
        if (king != null) {
            announce(titleOf(king) + " " + king.getNameString() + " of " + market.getName() + " decrees: " + pick.name + ". " + pick.quote,
                pick.wild ? Misc.getHighlightColor() : Misc.getTextColor());
        }
    }

    /** Called when the player takes the deposed king up on his return. */
    /** True while the deposed king can still be called back on this market. */
    public static boolean canCallBack(MarketAPI m) {
        if (m == null || m.getMemoryWithoutUpdate() == null) return false;
        if (!m.hasCondition(CONDITION_ID)) return false;
        return GRACE.equals(m.getMemoryWithoutUpdate().getString(K_STATE)) && kingOf(m) != null;
    }

    /** "Trash King Name" for the menu button. */
    public static String callBackLabel(MarketAPI m) {
        PersonAPI k = kingOf(m);
        if (k == null) return "Call back the Trash King";
        return "Call back " + titleOf(k) + " " + k.getNameString();
    }

    public static boolean reinstate(MarketAPI m) {
        if (m == null) return false;
        MarketConditionAPI cond = m.getCondition(CONDITION_ID);
        if (cond == null || !(cond.getPlugin() instanceof TrashKingCondition)) return false;
        return ((TrashKingCondition) cond.getPlugin()).doReinstate();
    }

    private boolean doReinstate() {
        PersonAPI king = kingOf(market);
        if (king == null || !GRACE.equals(stateOf(market))) return false;
        seat(king);
        mem(market).set(K_STATE, REIGNING);
        mem(market).unset(K_GRACE);
        setEntityFlag(false, null);
        resumeDecree();
        applyModifiers(getModId());
        announce(titleOf(king) + " " + king.getNameString() + " is back on the seat on " + market.getName() + ". The colony is calm again.",
            Misc.getPositiveHighlightColor());
        return true;
    }

    // ---------------------------------------------------------------- the daily turn

    /**
     * Runs every frame (it is only a few memory lookups): puts the king in place the first time the colony exists, and
     * notices the moment a player administrator takes his seat, so the "call back" option is there as soon as the player lands.
     */
    private void watch() {
        if (!colonized()) return;

        MemoryAPI m = mem(market);
        String state = m.getString(K_STATE);
        PersonAPI king = kingOf(market);

        if (state == null || king == null) {
            king = createKing();
            m.set(K_KING, king);
            seat(king);
            m.set(K_STATE, REIGNING);
            resumeDecree();
            applyModifiers(getModId());
            Global.getLogger(TrashKingCondition.class).info("Trash King: " + king.getNameString() + " takes the seat on " + market.getName());
            return;
        }

        if (REIGNING.equals(state) && market.isPlayerOwned() && !isKingAdmin(king)) {
            m.set(K_STATE, GRACE);
            m.set(K_GRACE, GRACE_DAYS);
            setEntityFlag(true, king);
            applyModifiers(getModId());
            Global.getLogger(TrashKingCondition.class).info("Trash King: " + king.getNameString() + " stepped aside on " + market.getName());
            announce(titleOf(king) + " " + king.getNameString() + " has stepped aside on " + market.getName()
                + ". Land there within " + (int) GRACE_DAYS + " days and call them back, or they are gone for good.",
                Misc.getNegativeHighlightColor());
        } else if (GRACE.equals(state) && isKingAdmin(king)) {
            // put back by other means
            m.set(K_STATE, REIGNING);
            m.unset(K_GRACE);
            setEntityFlag(false, null);
            resumeDecree();
            applyModifiers(getModId());
        }
    }

    /** Once a day: the decree and grace-period clocks. */
    private void tick(float days) {
        if (!colonized()) return;

        MemoryAPI m = mem(market);
        String state = m.getString(K_STATE);
        PersonAPI king = kingOf(market);
        if (state == null || king == null) return;

        if (REIGNING.equals(state)) {
            float left = m.getFloat(K_DECREE_LEFT) - days;
            m.set(K_DECREE_LEFT, left);
            if (left <= 0f) rollDecree();
        } else if (GRACE.equals(state)) {
            float left = m.getFloat(K_GRACE) - days;
            if (left <= 0f) {
                m.set(K_STATE, EXILED);
                m.unset(K_GRACE);
                setEntityFlag(false, null);
                Global.getLogger(TrashKingCondition.class).info("Trash King: " + king.getNameString() + " is gone from " + market.getName());
                announce(titleOf(king) + " " + king.getNameString() + " has left " + market.getName() + " for good. The people will not forget it.",
                    Misc.getNegativeHighlightColor());
            } else {
                m.set(K_GRACE, left);
            }
        }

        applyModifiers(getModId());
    }

    // ---------------------------------------------------------------- the effects

    private void clearModifiers(String id) {
        market.getStability().unmodify(id);
        market.getAccessibilityMod().unmodify(id);
        market.getHazard().unmodify(id);
        market.getIncomeMult().unmodify(id);
        market.getUpkeepMult().unmodify(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
    }

    /** How hard the deposed-king penalty is hitting, from 0 to 1. */
    private float penaltyFraction() {
        String state = stateOf(market);
        if (EXILED.equals(state)) return 1f;
        if (GRACE.equals(state)) {
            float left = mem(market).getFloat(K_GRACE);
            return Math.max(0f, Math.min(1f, 1f - left / GRACE_DAYS));
        }
        return 0f;
    }

    private void applyModifiers(String id) {
        if (market == null) return;
        clearModifiers(id);
        if (!colonized()) return;

        String state = stateOf(market);
        if (REIGNING.equals(state)) {
            Decree d = currentDecree();
            if (d == null) return;
            String label = "Decree: " + d.name;
            if (d.stability != 0f) market.getStability().modifyFlat(id, d.stability, label);
            if (d.accessibility != 0f) market.getAccessibilityMod().modifyFlat(id, d.accessibility, label);
            if (d.hazard != 0f) market.getHazard().modifyFlat(id, d.hazard, label);
            if (d.incomePercent != 0f) market.getIncomeMult().modifyMult(id, 1f + d.incomePercent / 100f, label);
            if (d.upkeepMult != 1f) market.getUpkeepMult().modifyMult(id, d.upkeepMult, label);
            if (d.groundMult != 1f) market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, d.groundMult, label);
        } else if (GRACE.equals(state) || EXILED.equals(state)) {
            float f = penaltyFraction();
            if (f > 0f) {
                market.getStability().modifyFlat(id, DEPOSED_STABILITY * f, "Deposed King");
                market.getAccessibilityMod().modifyFlat(id, DEPOSED_ACCESSIBILITY * f, "Deposed King");
            }
        }
    }

    @Override
    public void modifyIncoming(MarketAPI m, PopulationComposition incoming) {
        if (m == null || incoming == null || m != market || !REIGNING.equals(stateOf(m))) return;
        Decree d = currentDecree();
        if (d == null || d.growth == 0f) return;
        incoming.getWeight().modifyFlat("rp_trash_king_growth", d.growth, "Decree: " + d.name);
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        market.addTransientImmigrationModifier(this);
        applyModifiers(id);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.removeTransientImmigrationModifier(this);
        clearModifiers(id);
        setEntityFlag(false, null);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;
        market.addTransientImmigrationModifier(this);
        try {
            watch();
            dailyCheck.advance(Global.getSector().getClock().convertToDays(amount));
            if (dailyCheck.intervalElapsed()) tick(dailyCheck.getIntervalDuration());
        } catch (Throwable t) {
            Global.getLogger(TrashKingCondition.class).warn("Trash King turn failed: " + t);
        }
    }

    // ---------------------------------------------------------------- tooltip

    private static String signed(float v, String unit) {
        return (v > 0 ? "+" : "") + (Math.round(v * 10f) / 10f == Math.round(v) ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 10f) / 10f)) + unit;
    }

    /** Pairs of {text, "good" or "bad"} for what a decree does. */
    private static List<String[]> effectLines(Decree d) {
        List<String[]> out = new ArrayList<String[]>();
        if (d.stability != 0f) out.add(new String[] {signed(d.stability, " stability"), d.stability > 0 ? "good" : "bad"});
        if (d.accessibility != 0f) out.add(new String[] {signed(d.accessibility * 100f, "% accessibility"), d.accessibility > 0 ? "good" : "bad"});
        if (d.hazard != 0f) out.add(new String[] {signed(d.hazard * 100f, "% hazard rating"), d.hazard < 0 ? "good" : "bad"});
        if (d.incomePercent != 0f) out.add(new String[] {signed(d.incomePercent, "% colony income"), d.incomePercent > 0 ? "good" : "bad"});
        if (d.groundMult != 1f) out.add(new String[] {"ground defenses x" + d.groundMult, d.groundMult > 1f ? "good" : "bad"});
        if (d.upkeepMult != 1f) out.add(new String[] {"colony upkeep x" + d.upkeepMult, d.upkeepMult < 1f ? "good" : "bad"});
        if (d.growth != 0f) out.add(new String[] {signed(d.growth, " colony growth"), d.growth > 0 ? "good" : "bad"});
        return out;
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
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle("Trash King");
        tooltip.addPara(
            "Nobody can say how long this person has lived alone on a wrecked world, sorting the wreckage into piles only they understand. "
            + "They carry themselves with such certainty, and speak with such a commanding air, that everyone who arrives ends up respecting them, "
            + "and then adoring them. Nobody remembers voting. They are simply the Trash King, and it has never occurred to anyone to argue.",
            10f);

        SynergyTooltip.heading(tooltip, "The Court");

        if (!SynergyTooltip.isColonized(market)) {
            SynergyTooltip.uncolonized(tooltip, "Potential",
                "A colony here is ruled by the Trash King from the day it is founded. They announce a new decree every few months, each with a good side and a bad side, some mild and some wild. "
                + "Replacing them with one of your own administrators has lasting consequences.");
            return;
        }

        PersonAPI king = kingOf(market);
        String state = stateOf(market);
        if (king == null || state == null) {
            tooltip.addPara("The ruler is on their way.", 5f, Misc.getGrayColor());
            return;
        }
        String name = titleOf(king) + " " + king.getNameString();

        if (REIGNING.equals(state)) {
            SynergyTooltip.status(tooltip, "Ruler", true, name);
            Decree d = currentDecree();
            if (d != null) {
                tooltip.addPara("Current decree: %s", 8f, d.wild ? Misc.getHighlightColor() : Misc.getTextColor(), d.name);
                tooltip.addPara(d.quote, Misc.getGrayColor(), 3f);
                for (String[] line : effectLines(d)) {
                    tooltip.addPara(" - %s", 3f, "good".equals(line[1]) ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), line[0]);
                }
                if (effectLines(d).isEmpty()) tooltip.addPara(" - %s", 3f, Misc.getGrayColor(), "nothing at all");
            }
            SynergyTooltip.note(tooltip,
                "The ruler is the colony's administrator and never appears in your own list. Replacing them with one of your administrators makes them step aside: "
                + "a Deposed King penalty of " + (int) DEPOSED_STABILITY + " stability and " + (int) (DEPOSED_ACCESSIBILITY * 100f) + "% accessibility builds up over "
                + (int) GRACE_DAYS + " days. You can land at the colony in that time and call them back. After that they are gone for good and the penalty stays.");
        } else if (GRACE.equals(state)) {
            SynergyTooltip.status(tooltip, "Ruler", false, name + " (stepped aside)");
            int left = Math.max(1, Math.round(mem(market).getFloat(K_GRACE)));
            tooltip.addPara("Land at this colony within %s days to call them back.", 5f, Misc.getNegativeHighlightColor(), String.valueOf(left));
            tooltip.addPara("Deposed King: %s stability, %s accessibility, growing stronger each day.", 5f, Misc.getNegativeHighlightColor(),
                signed(DEPOSED_STABILITY, ""), signed(DEPOSED_ACCESSIBILITY * 100f, "%"));
        } else {
            SynergyTooltip.status(tooltip, "Ruler", false, name + " (gone for good)");
            tooltip.addPara("Deposed King: %s stability, %s accessibility.", 5f, Misc.getNegativeHighlightColor(),
                signed(DEPOSED_STABILITY, ""), signed(DEPOSED_ACCESSIBILITY * 100f, "%"));
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
    }
}
