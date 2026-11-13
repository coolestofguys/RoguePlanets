package roguePlanets;

import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Looks after the Afterlight, so the story can never be bricked by losing the ship:
 *  - It has its own permanent captain (never in the officer roster); that captain is put back if replaced, unless the
 *    player is flying the ship themselves. Now and then the captain's portrait briefly becomes the player's.
 *  - It can't be sold: if it turns up in a market's cargo it goes straight back into the fleet.
 *  - If it is destroyed it is always recoverable in that fight. If it is lost anyway, then after later battles the
 *    player takes part in there is a chance its wreck drifts to where the player is, and it can be claimed again.
 *  - If it is put in storage it rejoins the fleet on its own after a month or so.
 */
public class AfterlightCaptainScript extends BaseCampaignEventListener implements EveryFrameScript {

    private static final Logger log = Global.getLogger(AfterlightCaptainScript.class);

    public static final String HULL_PREFIX = "wolf_afterlight_";
    public static final String KEY_MEMBER = "$afterlight_member_id";
    public static final String KEY_MEMBER_OBJ = "$afterlight_member";
    public static final String KEY_PERSON = "$afterlight_captain";
    public static final String KEY_LOST = "$afterlight_lost";
    public static final String KEY_AWAY = "$afterlight_away";
    public static final String KEY_AWAY_STAMP = "$afterlight_awayStamp";
    public static final String KEY_AWAY_WAIT = "$afterlight_awayWait";
    public static final String KEY_AWAY_HOLD = "$afterlight_awayHold";
    public static final String KEY_WRECK = "$afterlight_wreck";
    public static final String KEY_CAP_AWAY = "$afterlight_captainAway";
    public static final String KEY_CAP_STAMP = "$afterlight_captainAwayStamp";
    public static final String KEY_CAP_WAIT = "$afterlight_captainAwayWait";
    public static final String KEY_CAP_GONE = "$afterlight_captainGone";
    public static final String PORTRAIT = "graphics/portraits/afterlight_captain.png";
    private static final String K_SL_MONTH = "$afterlight_slLoyaltyMonth";

    /** Chance after a battle the player is in (while the ship is lost) that the wreck drifts to the player. */
    public static final float WRECK_CHANCE = 0.20f;
    public static final float STORAGE_MIN_DAYS = 30f;
    public static final float STORAGE_EXTRA_DAYS = 15f;

    private static final String K_STORE_STAMP = "$afterlight_storeStamp";
    private static final String K_STORE_WAIT = "$afterlight_storeWait";
    private static final String P_SWAP_STAMP = "$afterlight_swapStamp";
    private static final String P_SWAP_WAIT = "$afterlight_swapWait";
    private static final String P_SWAPPED = "$afterlight_swapped";

    private float interval = 0f;
    private int missingTicks = 0;
    private final Random random = new Random();

    public AfterlightCaptainScript() {
        super(false);
    }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return false; }

    // ---------------------------------------------------------------- claiming

    /**
     * Called when the player claims the wreck. Brings back the original ship if there is one (so its refits are kept),
     * otherwise builds a fresh stock Wolf. Returns the ship, now in the player's fleet.
     */
    public static FleetMemberAPI claim() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        FleetMemberAPI member = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        if (member == null) {
            member = Global.getFactory().createFleetMember(com.fs.starfarer.api.fleet.FleetMemberType.SHIP, "wolf_CS");
            member.setShipName("Afterlight");
            member.getRepairTracker().setCR(0.7f);
        } else {
            member.getStatus().repairFully();
            member.getRepairTracker().setMothballed(false);
            member.getRepairTracker().setCR(0.5f);
        }
        if (!player.getFleetData().getMembersListCopy().contains(member)) {
            player.getFleetData().addFleetMember(member);
        }
        member.setCaptain(getCaptain());
        mem.set(KEY_MEMBER, member.getId());
        mem.set(KEY_MEMBER_OBJ, member);
        mem.unset(KEY_LOST);
        mem.unset(KEY_WRECK);
        roguePlanets.story.AfterlightStory.onClaimed();
        return member;
    }

    // ---------------------------------------------------------------- main tick

    @Override
    public void advance(float amount) {
        interval -= amount;
        if (interval > 0f) return;
        interval = 1f;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.getBoolean(KEY_CAP_GONE)) return; // the story is over: the Afterlight is an ordinary ship now

        FleetMemberAPI ship = findInFleet(player, mem);
        if (ship != null) {
            missingTicks = 0;
            mem.set(KEY_MEMBER, ship.getId());
            mem.set(KEY_MEMBER_OBJ, ship);
            mem.unset(KEY_LOST);
            mem.unset(K_STORE_STAMP);

            protect(ship);
            PersonAPI captain = getCaptain();
            // The officer is away (the story does this): the ship stays with the player, with no captain, until he returns.
            if (mem.getBoolean(KEY_CAP_AWAY)) {
                float gone = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(KEY_CAP_STAMP));
                if (gone < mem.getFloat(KEY_CAP_WAIT)) {
                    if (ship.getCaptain() == captain) ship.setCaptain(null);
                    return;
                }
                mem.unset(KEY_CAP_AWAY);
            }
            if (ship != player.getFlagship() && ship.getCaptain() != captain) ship.setCaptain(captain);
            updatePortrait(captain);
            if (ship.getCaptain() == captain) {
                // Starship Legends loyalty floor (he can't be moved off it): checked once a month
                com.fs.starfarer.api.campaign.CampaignClockAPI clock = Global.getSector().getClock();
                int month = clock.getCycle() * 12 + clock.getMonth();
                if (mem.getInt(K_SL_MONTH) != month) {
                    mem.set(K_SL_MONTH, month);
                    StarshipLegendsCompat.keepLoyal(ship, captain);
                }
            }
            return;
        }

        FleetMemberAPI stored = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        if (stored == null) return; // never claimed

        // Handed over to someone else for a few days (the story does this): it comes back on its own.
        if (mem.getBoolean(KEY_AWAY)) {
            missingTicks = 0;
            float away = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(KEY_AWAY_STAMP));
            if (away >= mem.getFloat(KEY_AWAY_WAIT) && !mem.getBoolean(KEY_AWAY_HOLD)) {
                mem.unset(KEY_AWAY);
                returnToFleet(mem, stored, player);
            }
            return;
        }

        // Not in the fleet: is it in storage, or has it been sold?
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (SubmarketAPI sub : market.getSubmarketsCopy()) {
                CargoAPI cargo = sub.getCargoNullOk();
                if (cargo == null) continue;
                if (!cargo.getMothballedShips().getMembersListCopy().contains(stored)) continue;

                missingTicks = 0;
                if (Submarkets.SUBMARKET_STORAGE.equals(sub.getSpecId())) {
                    handleStored(mem, cargo, stored, player);
                } else {
                    // Sold. Not possible: it goes straight back.
                    cargo.getMothballedShips().removeFleetMember(stored);
                    returnToFleet(mem, stored, player);
                }
                return;
            }
        }

        // Nowhere: it was destroyed and not recovered. Wait a few ticks to be sure.
        missingTicks++;
        if (missingTicks >= 3 && !mem.getBoolean(KEY_LOST)) {
            mem.set(KEY_LOST, true);
            log.info("Rogue Planets: the Afterlight is lost; its wreck may turn up after later battles.");
        }
    }

    /**
     * Ellis leaves for good (an ending). The Afterlight stays in the fleet as an ordinary Wolf with no locked captain, it stops
     * being protected, and nothing about it is tracked any more.
     */
    public static void releaseCaptain() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        PersonAPI captain = getCaptain();
        // put Ellis's own face and name back, in case the player's were showing
        captain.getMemoryWithoutUpdate().set(P_SWAPPED, false);
        captain.setPortraitSprite(PORTRAIT);
        captain.setName(new FullName("Ellis", "Hale", Gender.ANY));
        FleetMemberAPI m = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (m != null && player != null && player.getFleetData().getMembersListCopy().contains(m)) {
            if (m.getCaptain() == captain) m.setCaptain(null);
            ShipVariantAPI v = m.getVariant();
            if (v != null) {
                v.removeTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
                v.removeTag(Tags.NO_SELL);
            }
            m.getStats().getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).unmodify("roguePlanets_afterlight");
        }
        mem.unset(KEY_CAP_AWAY);
        mem.set(KEY_CAP_GONE, true);
    }

    /** Puts Ellis's own face and name back (in case the player's were showing) and, if a sprite is given, makes it Ellis's face for good. */
    public static void setEllisFace(String spritePath) {
        PersonAPI captain = getCaptain();
        captain.getMemoryWithoutUpdate().set(P_SWAPPED, false);
        captain.setPortraitSprite(spritePath == null ? PORTRAIT : spritePath);
        captain.setName(new FullName("Ellis", "Hale", Gender.ANY));
    }

    /** The Become ending: the Wolf's officer is now the player's old self, silent and locked to the ship; Ellis is gone for good. */
    public static void becomeOldSelf(String first, String last, String portrait) {
        PersonAPI captain = getCaptain();
        captain.getMemoryWithoutUpdate().set(P_SWAPPED, false);
        captain.setPortraitSprite(portrait);
        captain.setName(new FullName(first, last, Gender.ANY));
    }

    /** True once Ellis has left for good. */
    public static boolean captainGone() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(KEY_CAP_GONE);
    }

    /** True while the story has Ellis away from the ship. */
    public static boolean captainAway() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(KEY_CAP_AWAY);
    }

    /** Ellis leaves the ship for a while; the Afterlight stays in the fleet without its captain. Returns false if the ship is not in the fleet. */
    public static boolean sendCaptainAway(float minDays, float extraDays) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        FleetMemberAPI m = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        if (m == null || player == null || !player.getFleetData().getMembersListCopy().contains(m)) return false;
        mem.set(KEY_CAP_AWAY, true);
        mem.set(KEY_CAP_STAMP, Global.getSector().getClock().getTimestamp());
        mem.set(KEY_CAP_WAIT, minDays + (float) Math.random() * extraDays);
        if (m != player.getFlagship()) m.setCaptain(null);
        return true;
    }

    /** Takes the Afterlight out of the fleet for a few days. Returns false if it cannot (not claimed, or it is the flagship). */
    public static boolean handOverForDays(float minDays, float extraDays) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        FleetMemberAPI m = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        if (m == null || player == null || !player.getFleetData().getMembersListCopy().contains(m)) return false;
        if (m == player.getFlagship()) return false;
        player.getFleetData().removeFleetMember(m);
        mem.set(KEY_AWAY, true);
        mem.set(KEY_AWAY_STAMP, Global.getSector().getClock().getTimestamp());
        mem.set(KEY_AWAY_WAIT, minDays + (float) Math.random() * extraDays);
        return true;
    }

    /** While held, an Afterlight that was handed over stays away even after its wait is over, until released. */
    public static void holdAway(boolean hold) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (hold) {
            mem.set(KEY_AWAY_HOLD, true);
        } else {
            mem.unset(KEY_AWAY_HOLD);
            // Released: it comes back at the next tick, whatever was left of its wait.
            if (mem.getBoolean(KEY_AWAY)) mem.set(KEY_AWAY_WAIT, 0f);
        }
    }

    /** True if the Afterlight is in the fleet and is not the flagship (so it can be handed over). */
    public static boolean canHandOver() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        FleetMemberAPI m = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        return m != null && player != null
            && player.getFleetData().getMembersListCopy().contains(m) && m != player.getFlagship();
    }

    private FleetMemberAPI findInFleet(CampaignFleetAPI player, MemoryAPI mem) {
        String id = mem.getString(KEY_MEMBER);
        FleetMemberAPI byName = null;
        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            if (id != null && id.equals(m.getId())) return m;
            if (m.getHullSpec().getHullId().startsWith(HULL_PREFIX)) return m; // older saves
            if ("Afterlight".equals(m.getShipName()) && m.getHullSpec().getHullId().startsWith("wolf")) byName = m;
        }
        return byName;
    }

    /** Always recoverable, and marked as not for sale. Re-applied every tick because refits can replace the variant. */
    private void protect(FleetMemberAPI ship) {
        ShipVariantAPI v = ship.getVariant();
        if (v != null) {
            if (!v.hasTag(Tags.VARIANT_ALWAYS_RECOVERABLE)) v.addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
            if (!v.hasTag(Tags.NO_SELL)) v.addTag(Tags.NO_SELL);
        }
        ship.getStats().getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat("roguePlanets_afterlight", 1000f);
        roguePlanets.story.Stage16.keepEndingEffects(ship);
    }

    private void handleStored(MemoryAPI mem, CargoAPI cargo, FleetMemberAPI stored, CampaignFleetAPI player) {
        long now = Global.getSector().getClock().getTimestamp();
        if (!mem.contains(K_STORE_STAMP)) {
            mem.set(K_STORE_STAMP, now);
            mem.set(K_STORE_WAIT, STORAGE_MIN_DAYS + random.nextFloat() * STORAGE_EXTRA_DAYS);
            return;
        }
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_STORE_STAMP));
        if (elapsed < mem.getFloat(K_STORE_WAIT)) return;
        cargo.getMothballedShips().removeFleetMember(stored);
        returnToFleet(mem, stored, player);
    }

    private void returnToFleet(MemoryAPI mem, FleetMemberAPI ship, CampaignFleetAPI player) {
        ship.getRepairTracker().setMothballed(false);
        player.getFleetData().addFleetMember(ship);
        ship.setCaptain(getCaptain());
        mem.unset(K_STORE_STAMP);
        log.info("Rogue Planets: the Afterlight is back in the player's fleet.");
    }

    // ---------------------------------------------------------------- lost ship: wreck turns up after battles

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (!mem.getBoolean(KEY_LOST)) return;
        if (battle == null || !battle.isPlayerInvolved()) return;
        if (random.nextFloat() >= WRECK_CHANCE) return;

        FleetMemberAPI lost = (FleetMemberAPI) mem.get(KEY_MEMBER_OBJ);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (lost == null || player == null) return;
        LocationAPI loc = player.getContainingLocation();
        if (loc == null) return;

        // Only one wreck at a time: an old one is cleared away.
        SectorEntityToken old = (SectorEntityToken) mem.get(KEY_WRECK);
        if (old != null && old.isAlive()) Misc.fadeAndExpire(old, 1f);

        PerShipData psd = new PerShipData(lost.getVariant().clone(), ShipCondition.BATTERED, "Afterlight", Factions.NEUTRAL, 0f);
        psd.nameAlwaysKnown = true;
        psd.addDmods = false;
        psd.pruneWeapons = false;
        SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(loc, Entities.WRECK, Factions.NEUTRAL,
                new DerelictShipData(psd, false));
        wreck.getMemoryWithoutUpdate().set("$violetAfterlight", true);
        wreck.getMemoryWithoutUpdate().set("$violetAfterlightReturn", true);
        wreck.setDiscoverable(true);
        float angle = random.nextFloat() * 360f;
        float dist = 350f + random.nextFloat() * 150f;
        wreck.setLocation(player.getLocation().x + Misc.getUnitVectorAtDegreeAngle(angle).x * dist,
                          player.getLocation().y + Misc.getUnitVectorAtDegreeAngle(angle).y * dist);
        mem.set(KEY_WRECK, wreck);
        log.info("Rogue Planets: the Afterlight's wreck has drifted into " + loc.getName() + ".");
    }

    // ---------------------------------------------------------------- captain

    /** The one captain, created on first use and kept in the save. */
    public static PersonAPI getCaptain() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        PersonAPI p = (PersonAPI) mem.get(KEY_PERSON);
        if (p != null) {
            if (!Factions.NEUTRAL.equals(p.getFaction().getId())) p.setFaction(Factions.NEUTRAL);
            return p;
        }

        p = Global.getFactory().createPerson();
        p.setId("roguePlanets_afterlight_captain");
        p.setName(new FullName("Ellis", "Hale", Gender.ANY));
        p.setFaction(Factions.NEUTRAL);
        p.setPortraitSprite(PORTRAIT);
        p.setPersonality(Personalities.STEADY);
        p.setRankId(Ranks.SPACE_COMMANDER);
        p.setPostId(Ranks.POST_OFFICER);
        p.getStats().setLevel(3);
        p.getStats().setSkillLevel("helmsmanship", 2);
        p.getStats().setSkillLevel("target_analysis", 2);
        p.getStats().setSkillLevel("impact_mitigation", 2);
        mem.set(KEY_PERSON, p);
        return p;
    }

    /**
     * Ellis as he appears in his own conversations. During a glitch window his officer card shows the player's face and name,
     * but a conversation always shows him as himself, through a plain copy so nothing has to be swapped back.
     */
    public static PersonAPI getDialogueCaptain() {
        PersonAPI p = getCaptain();
        if (!p.getMemoryWithoutUpdate().getBoolean(P_SWAPPED)) return p;
        PersonAPI copy = Global.getFactory().createPerson();
        copy.setId("roguePlanets_afterlight_captain_portrait");
        copy.setName(new FullName("Ellis", "Hale", Gender.ANY));
        copy.setFaction(Factions.NEUTRAL);
        copy.setPortraitSprite(PORTRAIT);
        copy.setPersonality(Personalities.STEADY);
        copy.setRankId(Ranks.SPACE_COMMANDER);
        copy.setPostId(Ranks.POST_OFFICER);
        return copy;
    }

    /** Occasionally shows the player's face for a day or two. Never announced. */
    private void updatePortrait(PersonAPI captain) {
        if (roguePlanets.story.AfterlightStory.hasFlag("ending", "free") || roguePlanets.story.AfterlightStory.hasFlag("ending", "become")) return; // Ellis is their own person now: the player's face never shows again
        MemoryAPI pm = captain.getMemoryWithoutUpdate();
        long now = Global.getSector().getClock().getTimestamp();
        if (!pm.contains(P_SWAP_STAMP)) {
            pm.set(P_SWAP_STAMP, now);
            pm.set(P_SWAP_WAIT, 25f + random.nextFloat() * 45f);
            pm.set(P_SWAPPED, false);
            return;
        }
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(pm.getLong(P_SWAP_STAMP));
        if (elapsed < pm.getFloat(P_SWAP_WAIT)) return;

        if (!pm.getBoolean(P_SWAPPED)) {
            captain.setPortraitSprite(Global.getSector().getPlayerPerson().getPortraitSprite());
            com.fs.starfarer.api.characters.FullName mine = Global.getSector().getPlayerPerson().getName();
            captain.setName(new FullName(mine.getFirst(), mine.getLast(), Gender.ANY));
            pm.set(P_SWAPPED, true);
            pm.set(P_SWAP_WAIT, 1f + random.nextFloat() * 2f);
        } else {
            captain.setPortraitSprite(PORTRAIT);
            captain.setName(new FullName("Ellis", "Hale", Gender.ANY));
            pm.set(P_SWAPPED, false);
            pm.set(P_SWAP_WAIT, 25f + random.nextFloat() * 45f);
        }
        pm.set(P_SWAP_STAMP, now);
    }
}
