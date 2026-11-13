package roguePlanets.story;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Saved state for the Afterlight storyline: the stage reached, the three hidden values (mercy, detachment,
 * curiosity), the big choice flags, the deployment points destroyed, the Ellis cooldown and the list of
 * conversations already used (none is ever repeated). Everything lives in the sector memory, so it is saved with
 * the game.
 */
public class AfterlightStory {

    private static final String K_STAGE = "$afl_stage";
    private static final String K_STAGE_STAMP = "$afl_stageStamp";
    private static final String K_MERCY = "$afl_mercy";
    private static final String K_DETACH = "$afl_detach";
    private static final String K_CURIOUS = "$afl_curious";
    private static final String K_DP = "$afl_dpDestroyed";
    private static final String K_ELLIS_STAMP = "$afl_ellisStamp";
    private static final String K_USED = "$afl_used";
    private static final String K_QUEUE = "$afl_queue";
    private static final String K_CLAIM_STAMP = "$afl_claimStamp";

    /** Days after claiming the ship before the first queued Ellis conversation may play. */
    public static final float QUEUE_START_DELAY_DAYS = 30f;
    private static final String FLAG_PREFIX = "$afl_flag_";

    /** Days that must pass between two Ellis conversations. */
    public static final float ELLIS_COOLDOWN_DAYS = 40f;

    public static MemoryAPI mem() {
        return Global.getSector().getMemoryWithoutUpdate();
    }

    // ---- stage

    public static int getStage() { return mem().getInt(K_STAGE); }

    public static boolean isClaimed() { return getStage() >= 1; }

    /** Called when the player first claims the Afterlight. Stage 1 begins. */
    public static void onClaimed() {
        if (getStage() < 1) {
            setStage(1);
            mem().set(K_CLAIM_STAMP, Global.getSector().getClock().getTimestamp());
        }
    }

    public static void setStage(int stage) {
        mem().set(K_STAGE, stage);
        mem().set(K_STAGE_STAMP, Global.getSector().getClock().getTimestamp());
    }

    // ---- is the Afterlight with the player?

    private static final String K_ABSENT = "$afl_shipAbsent";
    private static final String K_BACK_STAMP = "$afl_shipBackStamp";
    public static final float SETTLE_DAYS = 7f;

    /** True if the Afterlight is in the player's fleet, or was handed over by the player's own choice (stage 6) and is being held. */
    public static boolean storyShipHere() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        CampaignFleetAPI p = Global.getSector().getPlayerFleet();
        Object m = g.get(roguePlanets.AfterlightCaptainScript.KEY_MEMBER_OBJ);
        if (m != null && p != null && p.getFleetData().getMembersListCopy().contains(m)) {
            return !g.getBoolean(roguePlanets.AfterlightCaptainScript.KEY_CAP_AWAY); // without Ellis aboard, the story waits
        }
        return g.getBoolean(roguePlanets.AfterlightCaptainScript.KEY_AWAY)
            && g.getBoolean(roguePlanets.AfterlightCaptainScript.KEY_AWAY_HOLD);
    }

    /** Called about once a second. Remembers when the ship went missing and when it came back. */
    public static void updatePresence() {
        if (!storyShipHere()) {
            mem().set(K_ABSENT, true);
        } else if (mem().getBoolean(K_ABSENT)) {
            mem().unset(K_ABSENT);
            mem().set(K_BACK_STAMP, Global.getSector().getClock().getTimestamp());
        }
    }

    /** True when the ship is here and has been for a week since it last came back, so nothing plays right on its return. */
    public static boolean shipSettled() {
        if (!storyShipHere() || mem().getBoolean(K_ABSENT)) return false;
        if (!mem().contains(K_BACK_STAMP)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_BACK_STAMP)) >= SETTLE_DAYS;
    }

    public static float daysSinceLastStage() {
        if (!mem().contains(K_STAGE_STAMP)) return 0f;
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_STAGE_STAMP));
    }

    // ---- hidden values

    public static void addValues(int mercy, int detach, int curious) {
        mem().set(K_MERCY, mem().getInt(K_MERCY) + mercy);
        mem().set(K_DETACH, mem().getInt(K_DETACH) + detach);
        mem().set(K_CURIOUS, mem().getInt(K_CURIOUS) + curious);
    }

    public static int getMercy() { return mem().getInt(K_MERCY); }
    public static int getDetachment() { return mem().getInt(K_DETACH); }
    public static int getCuriosity() { return mem().getInt(K_CURIOUS); }

    /** The leading value ("mercy", "detachment", "curiosity"): whichever is higher than both others. A tie for first means no leading value. */
    public static String getLeadingValue() {
        int m = getMercy(), d = getDetachment(), c = getCuriosity();
        if (m > d && m > c) return "mercy";
        if (d > m && d > c) return "detachment";
        if (c > m && c > d) return "curiosity";
        return null;
    }

    // ---- flags

    public static void setFlag(String key, String value) { mem().set(FLAG_PREFIX + key, value); }

    public static String getFlag(String key) {
        String v = mem().getString(FLAG_PREFIX + key);
        return v == null ? "" : v;
    }

    public static boolean hasFlag(String key, String value) { return value.equals(getFlag(key)); }

    // ---- deployment points destroyed

    public static void addDestroyedDp(float dp) { mem().set(K_DP, mem().getFloat(K_DP) + dp); }

    public static float getDestroyedDp() { return mem().getFloat(K_DP); }

    // ---- Ellis conversations

    public static boolean ellisCooldownReady() {
        if (!mem().contains(K_ELLIS_STAMP)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_ELLIS_STAMP)) >= ELLIS_COOLDOWN_DAYS;
    }

    public static void markEllisSpoke() {
        mem().set(K_ELLIS_STAMP, Global.getSector().getClock().getTimestamp());
    }

    public static boolean isUsed(String id) {
        String used = mem().getString(K_USED);
        return used != null && used.contains("|" + id + "|");
    }

    public static void markUsed(String id) {
        String used = mem().getString(K_USED);
        if (used == null || used.length() == 0) used = "|";
        if (!used.contains("|" + id + "|")) used = used + id + "|";
        mem().set(K_USED, used);
    }

    /** Forgets that a conversation was used (tests only). */
    public static void clearUsed(String id) {
        String used = mem().getString(K_USED);
        if (used != null && used.contains("|" + id + "|")) mem().set(K_USED, used.replace("|" + id + "|", "|"));
    }

    // ---- the last battle the player won (used by Ellis's battle questions)

    private static final String K_PB_STAMP = "$afl_pbStamp";
    private static final String K_PB_CREW = "$afl_pbCrew";
    private static final String K_PB_DP = "$afl_pbDp";
    private static final String K_PB_USED = "$afl_pbUsed";

    /** Days a won battle stays eligible for a question. */
    public static final float BATTLE_WINDOW_DAYS = 1f;

    /** Engagements closer together than this count as one battle (a big fleet is often fought in several rounds). */
    public static final float BATTLE_MERGE_DAYS = 0.5f;

    public static void recordWonBattle(int enemyCrew, float enemyDp) {
        long now = Global.getSector().getClock().getTimestamp();
        boolean merge = mem().contains(K_PB_STAMP) && !mem().getBoolean(K_PB_USED)
            && Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_PB_STAMP)) <= BATTLE_MERGE_DAYS;
        if (merge) {
            enemyCrew += mem().getInt(K_PB_CREW);
            enemyDp += mem().getFloat(K_PB_DP);
        }
        mem().set(K_PB_STAMP, now);
        mem().set(K_PB_CREW, enemyCrew);
        mem().set(K_PB_DP, enemyDp);
        mem().set(K_PB_USED, false);
    }

    /** True if a won battle is waiting that has not been asked about yet and is recent enough. */
    public static boolean hasFreshBattle() {
        if (!mem().contains(K_PB_STAMP) || mem().getBoolean(K_PB_USED)) return false;
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_PB_STAMP)) <= BATTLE_WINDOW_DAYS;
    }

    public static int getBattleCrew() { return mem().getInt(K_PB_CREW); }
    public static float getBattleDp() { return mem().getFloat(K_PB_DP); }
    public static void useBattle() { mem().set(K_PB_USED, true); }

    // ---- the last battle in which the player lost ships (used by the stage 9 question)

    private static final String K_LS_STAMP = "$afl_lsStamp";
    private static final String K_LS_COUNT = "$afl_lsCount";
    private static final String K_LS_CREW = "$afl_lsCrew";
    private static final String K_LS_NAME = "$afl_lsName";
    private static final String K_LS_USED = "$afl_lsUsed";
    public static final float LOSS_WINDOW_DAYS = 1f;

    public static void recordLoss(int ships, int crew, String firstName) {
        long now = Global.getSector().getClock().getTimestamp();
        boolean merge = mem().contains(K_LS_STAMP) && !mem().getBoolean(K_LS_USED)
            && Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_LS_STAMP)) <= BATTLE_MERGE_DAYS;
        if (merge) {
            ships += mem().getInt(K_LS_COUNT);
            crew += mem().getInt(K_LS_CREW);
            String old = mem().getString(K_LS_NAME);
            if (old != null && old.length() > 0) firstName = old;
        }
        mem().set(K_LS_STAMP, now);
        mem().set(K_LS_COUNT, ships);
        mem().set(K_LS_CREW, crew);
        mem().set(K_LS_NAME, firstName == null ? "" : firstName);
        mem().set(K_LS_USED, false);
    }

    public static boolean hasFreshLoss() {
        if (!mem().contains(K_LS_STAMP) || mem().getBoolean(K_LS_USED)) return false;
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_LS_STAMP)) <= LOSS_WINDOW_DAYS;
    }

    public static int getLossCount() { return mem().getInt(K_LS_COUNT); }
    public static int getLossCrew() { return mem().getInt(K_LS_CREW); }
    public static String getLossName() { String n = mem().getString(K_LS_NAME); return n == null ? "" : n; }
    public static void useLoss() { mem().set(K_LS_USED, true); }

    // ---- queue of Ellis conversations (reflection questions waiting for their turn, oldest first)

    public static float daysSinceClaim() {
        if (!mem().contains(K_CLAIM_STAMP)) return 9999f; // saves from before this existed
        return Global.getSector().getClock().getElapsedDaysSince(mem().getLong(K_CLAIM_STAMP));
    }

    /** Adds a conversation to the back of the queue, unless it was already played or is already waiting. */
    public static void queueEllis(String id) {
        if (isUsed(id)) return;
        String q = mem().getString(K_QUEUE);
        if (q == null || q.length() == 0) q = "|";
        if (q.contains("|" + id + "|")) return;
        mem().set(K_QUEUE, q + id + "|");
    }

    /** The conversation at the front of the queue, or null. */
    public static String peekQueue() {
        String q = mem().getString(K_QUEUE);
        if (q == null || q.length() <= 1) return null;
        int end = q.indexOf('|', 1);
        return end < 0 ? null : q.substring(1, end);
    }

    public static void popQueue() {
        String q = mem().getString(K_QUEUE);
        if (q == null || q.length() <= 1) return;
        int end = q.indexOf('|', 1);
        if (end < 0) { mem().unset(K_QUEUE); return; }
        mem().set(K_QUEUE, "|" + q.substring(end + 1));
    }

    // ---- world checks

    /** No dialog or menu open, no fight, and no hostile fleet close by. */
    public static boolean isQuietMoment() {
        return isQuietMoment(2000f);
    }

    /** Same check, but only hostile fleets closer than the given distance count. */
    public static boolean isQuietMoment(float hostileRadius) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return false;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.isShowingDialog() || ui.isShowingMenu()) return false;
        if (player.getBattle() != null || player.isInHyperspaceTransition()) return false;
        for (CampaignFleetAPI f : player.getContainingLocation().getFleets()) {
            if (f == player) continue;
            if (f.isHostileTo(player) && Misc.getDistance(f, player) < hostileRadius) return false;
        }
        return true;
    }

    public static int getPlayerColonyCount() {
        return Misc.getPlayerMarkets(false).size();
    }

    public static int getPlayerFleetSize() {
        return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().size();
    }

    public static float getPlayerCredits() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    /** The entity a custom dialog is attached to: the player's own fleet. */
    public static SectorEntityToken dialogTarget() {
        return Global.getSector().getPlayerFleet();
    }
}
