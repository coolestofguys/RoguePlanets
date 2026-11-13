package roguePlanets.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * Stage 8: a dead copy of the player's own flagship. It follows the player from system to system (only systems with
 * no populated markets) until its salvage dialogue is opened. Every time it appears it matches the flagship the
 * player is flying at that moment.
 */
public class CopyWreck {

    private static final String K_ACTIVE = "$afl_copyActive";
    private static final String K_WRECK = "$afl_copyWreck";
    private static final String K_RECENT = "$afl_recentSystems";
    private static final Random random = new Random();

    public static boolean isActive() {
        return AfterlightStory.mem().getBoolean(K_ACTIVE);
    }

    /** Starts the hunt. The stage counter moves on only when the player finds the wreck. */
    public static void begin() {
        AfterlightStory.mem().set(K_ACTIVE, true);
    }

    /** A system the wreck may appear in: a real star system with nobody living in it, and not the Violet Dwarf. */
    public static boolean qualifies(LocationAPI loc) {
        if (!(loc instanceof StarSystemAPI)) return false;
        StarSystemAPI sys = (StarSystemAPI) loc;
        if (sys.getStar() != null && "star_violet".equals(sys.getStar().getTypeId())) return false;
        return com.fs.starfarer.api.util.Misc.getMarketsInLocation(loc).isEmpty();
    }

    /** Remembers the last few systems the player has been in, for the wreck's log. */
    public static void recordSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !(player.getContainingLocation() instanceof StarSystemAPI)) return;
        String name = ((StarSystemAPI) player.getContainingLocation()).getBaseName();
        if (name == null || name.length() == 0) return;
        MemoryAPI mem = AfterlightStory.mem();
        String cur = mem.getString(K_RECENT);
        List<String> list = new ArrayList<String>();
        if (cur != null && cur.length() > 0) for (String s : cur.split("\\|")) list.add(s);
        if (!list.isEmpty() && list.get(list.size() - 1).equals(name)) return;
        list.add(name);
        while (list.size() > 6) list.remove(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(list.get(i));
        }
        mem.set(K_RECENT, sb.toString());
    }

    /** Called about once a second while the story is running. */
    public static void tick() {
        if (!isActive()) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;
        LocationAPI here = player.getContainingLocation();
        MemoryAPI mem = AfterlightStory.mem();

        SectorEntityToken wreck = (SectorEntityToken) mem.get(K_WRECK);
        if (wreck != null) {
            if (wreck.isAlive() && wreck.getContainingLocation() == here) {
                // keep the dialogue's name in step (also covers wrecks spawned before the name was stored globally)
                String n = wreck.getMemoryWithoutUpdate().getString("$violetCopyName");
                if (n != null && n.length() > 0) Global.getSector().getMemoryWithoutUpdate().set("$afl_copyName", n);
                return; // still in the player's system, waiting
            }
            // The player left without finding it: it goes away and will turn up in the next system.
            if (wreck.getContainingLocation() != null) wreck.getContainingLocation().removeEntity(wreck);
            mem.unset(K_WRECK);
        }

        if (!qualifies(here) || player.isInHyperspace()) return;
        FleetMemberAPI flag = player.getFlagship();
        if (flag == null || flag.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX)) return;
        spawn((StarSystemAPI) here, flag);
    }

    private static void spawn(StarSystemAPI sys, FleetMemberAPI flag) {
        String name = flag.getShipName();
        PerShipData psd = new PerShipData(flag.getVariant().clone(), com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition.BATTERED,
                name, Factions.NEUTRAL, 0f);
        psd.nameAlwaysKnown = true;
        psd.addDmods = false;
        psd.pruneWeapons = false;
        SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(sys, Entities.WRECK, Factions.NEUTRAL,
                new DerelictShipData(psd, false));

        List<PlanetAPI> planets = new ArrayList<PlanetAPI>();
        for (PlanetAPI p : sys.getPlanets()) if (!p.isStar()) planets.add(p);
        if (!planets.isEmpty()) {
            PlanetAPI p = planets.get(random.nextInt(planets.size()));
            float dist = p.getRadius() + 150f + random.nextFloat() * 450f;
            wreck.setCircularOrbit(p, random.nextFloat() * 360f, dist, 25f + random.nextFloat() * 40f);
        } else if (sys.getCenter() != null) {
            float dist = 2500f + random.nextFloat() * 4000f;
            wreck.setCircularOrbit(sys.getCenter(), random.nextFloat() * 360f, dist, 120f + random.nextFloat() * 120f);
        }
        wreck.setDiscoverable(true);
        wreck.getMemoryWithoutUpdate().set("$violetCopy", true);
        wreck.getMemoryWithoutUpdate().set("$violetCopyName", name);
        Global.getSector().getMemoryWithoutUpdate().set("$afl_copyName", name); // what the wreck's dialogue text shows
        AfterlightStory.mem().set(K_WRECK, wreck);
        Global.getLogger(CopyWreck.class).info("Rogue Planets: the copy of '" + name + "' has appeared in " + sys.getName());
    }

    /** Called by the rule command when the player opens the wreck's dialogue. Ends the hunt and moves the story on. */
    public static void found() {
        MemoryAPI mem = AfterlightStory.mem();
        mem.unset(K_ACTIVE);
        mem.unset(K_WRECK);
        AfterlightStory.setStage(8);

        String cur = mem.getString(K_RECENT);
        List<String> list = new ArrayList<String>();
        if (cur != null && cur.length() > 0) for (String s : cur.split("\\|")) list.add(s);
        int from = Math.max(0, list.size() - 3);
        List<String> last = list.subList(from, list.size());
        String joined;
        if (last.isEmpty()) joined = "places that are not on any chart";
        else if (last.size() == 1) joined = last.get(0);
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < last.size(); i++) {
                if (i > 0) sb.append(i == last.size() - 1 ? " and " : ", ");
                sb.append(last.get(i));
            }
            joined = sb.toString();
        }
        Global.getSector().getMemoryWithoutUpdate().set("$afl_logList", joined);
        Global.getSector().getMemoryWithoutUpdate().set("$afl_logLast", last.isEmpty() ? "somewhere" : last.get(last.size() - 1));
    }
}
