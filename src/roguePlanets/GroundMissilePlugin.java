package roguePlanets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.Misc;
import roguePlanets.econ.conditions.GroundMissileDefenseCondition;

/**
 * Registered in data/config/settings.json. In a real campaign battle inside a star system, DELAY seconds in, every colony
 * in that system with an active Ground-to-Space Missile Defense launches a salvo of Pilum missiles for the side it belongs
 * to: the player's side if the colony is yours (or its faction fights beside you), the enemy's side if its faction is fighting you.
 */
public class GroundMissilePlugin extends BaseEveryFrameCombatPlugin {

    private static final String CONDITION_ID = "ground_missile_defense";
    private static final String WEAPON_ID = "pilum";

    private static final Random rand = new Random();
    private CombatEngineAPI engine;
    private boolean armed = false;
    private float elapsed = 0f;
    private int playerColonies = 0;
    private int enemyColonies = 0;
    private int wave = 0;
    private int playerPending = 0;
    private int enemyPending = 0;
    private float playerCredit = 0f;
    private float enemyCredit = 0f;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        try {
            if (engine == null || !engine.isInCampaign() || engine.isSimulation()) return;
            CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
            if (pf == null) return;
            LocationAPI loc = pf.getContainingLocation();
            if (loc.isHyperspace()) return;
            BattleAPI battle = pf.getBattle();
            if (battle == null) return;

            Set<String> mine = new HashSet<String>();
            Set<String> theirs = new HashSet<String>();
            for (CampaignFleetAPI f : battle.getPlayerSide()) mine.add(f.getFaction().getId());
            for (CampaignFleetAPI f : battle.getNonPlayerSide()) theirs.add(f.getFaction().getId());

            for (MarketAPI m : Global.getSector().getEconomy().getMarkets(loc)) {
                if (m == null || !m.hasCondition(CONDITION_ID)) continue;
                if (!(m.getCondition(CONDITION_ID).getPlugin() instanceof GroundMissileDefenseCondition)) continue;
                GroundMissileDefenseCondition c = (GroundMissileDefenseCondition) m.getCondition(CONDITION_ID).getPlugin();
                if (!c.synergyActive()) continue;
                String fid = m.getFactionId();
                if (m.isPlayerOwned() || mine.contains(fid)) {
                    playerColonies++;
                } else if (theirs.contains(fid)) {
                    enemyColonies++;
                }
            }
            armed = playerColonies > 0 || enemyColonies > 0;
        } catch (Throwable t) {
            armed = false;
        }
    }

    private ShipAPI randomShip(FleetSide side) {
        List<ShipAPI> ok = new ArrayList<ShipAPI>();
        for (ShipAPI s : engine.getShips()) {
            if (s.getOwner() != (side == FleetSide.PLAYER ? 0 : 1)) continue;
            if (!s.isAlive() || s.isFighter() || s.isHulk() || s.isShuttlePod()) continue;
            ok.add(s);
        }
        return ok.isEmpty() ? null : ok.get(rand.nextInt(ok.size()));
    }

    /** Launches one missile for the given side; returns false if there was no ship to launch or aim at. */
    private boolean launch(FleetSide side) {
        FleetSide other = side == FleetSide.PLAYER ? FleetSide.ENEMY : FleetSide.PLAYER;
        ShipAPI source = randomShip(side);
        ShipAPI target = randomShip(other);
        if (source == null || target == null) return false;

        float w = engine.getMapWidth(), h = engine.getMapHeight();
        float x = (rand.nextFloat() - 0.5f) * w * 0.8f;
        float y = (side == FleetSide.PLAYER ? -1f : 1f) * h * 0.5f;
        Vector2f from = new Vector2f(x, y);
        float angle = Misc.getAngleInDegrees(from, target.getLocation());

        CombatEntityAPI e = engine.spawnProjectile(source, null, WEAPON_ID, from, angle, new Vector2f());
        if (e instanceof MissileAPI) {
            MissileAPI m = (MissileAPI) e;
            // the map is far wider than a Pilum's normal burn, so let it fly the whole way
            m.setMaxFlightTime(180f);
            MissileAIPlugin ai = m.getUnwrappedMissileAI();
            if (ai instanceof GuidedMissileAI) ((GuidedMissileAI) ai).setTarget(target);
        }
        return true;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!armed || engine == null || engine.isPaused()) return;
        elapsed += amount;
        try {
            // a new wave every WAVE_GAP seconds once the opening delay has passed
            while (wave < GroundMissileDefenseCondition.WAVES
                    && elapsed >= GroundMissileDefenseCondition.DELAY_SECONDS + wave * GroundMissileDefenseCondition.WAVE_GAP_SECONDS) {
                wave++;
                int size = GroundMissileDefenseCondition.WAVE_SIZE;
                if (playerColonies > 0) {
                    playerPending += size * playerColonies;
                    engine.getCombatUI().addMessage(1, Misc.getPositiveHighlightColor(),
                        "Planetary batteries: " + (size * playerColonies) + " Pilum missiles launched.");
                }
                if (enemyColonies > 0) {
                    enemyPending += size * enemyColonies;
                    engine.getCombatUI().addMessage(1, Misc.getNegativeHighlightColor(),
                        "Hostile planetary batteries: " + (size * enemyColonies) + " Pilum missiles launched.");
                }
            }
            // each side's salvo is spread evenly over SPREAD_SECONDS, however many colonies add to it
            float spread = GroundMissileDefenseCondition.SPREAD_SECONDS;
            // credit only builds while missiles are waiting, so nothing is saved up during the opening delay
            playerCredit = playerPending > 0 ? playerCredit + amount * (GroundMissileDefenseCondition.WAVE_SIZE * playerColonies / spread) : 0f;
            enemyCredit = enemyPending > 0 ? enemyCredit + amount * (GroundMissileDefenseCondition.WAVE_SIZE * enemyColonies / spread) : 0f;
            while (playerPending > 0 && playerCredit >= 1f) {
                playerCredit -= 1f;
                if (launch(FleetSide.PLAYER)) playerPending--; else break;
            }
            while (enemyPending > 0 && enemyCredit >= 1f) {
                enemyCredit -= 1f;
                if (launch(FleetSide.ENEMY)) enemyPending--; else break;
            }
            if (wave >= GroundMissileDefenseCondition.WAVES && playerPending <= 0 && enemyPending <= 0) armed = false;
        } catch (Throwable t) {
            armed = false;
            Global.getLogger(GroundMissilePlugin.class).warn("Ground missile salvo failed: " + t);
        }
    }
}
