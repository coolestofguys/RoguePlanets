package roguePlanets.story;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/** Drives the story: counts destroyed deployment points and starts the next stage when everything lines up. */
public class AfterlightStoryScript extends BaseCampaignEventListener implements EveryFrameScript {

    private final IntervalUtil tick = new IntervalUtil(1f, 1f);

    public AfterlightStoryScript() {
        super(false);
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        if (!AfterlightStory.isClaimed() || result == null) return;
        try {
            BattleQuestions.record(result);
            if (result.getWinnerResult() == null || result.getLoserResult() == null) {
                return;
            }
            // Ships the player lost (destroyed outright, not just disabled), win or lose, for Ellis's question about loss.
            com.fs.starfarer.api.campaign.EngagementResultForFleetAPI mine =
                result.didPlayerWin() ? result.getWinnerResult() : result.getLoserResult();
            if (mine != null && mine.isPlayer()) {
                int n = 0, lostCrew = 0;
                String first = null;
                for (FleetMemberAPI m : mine.getDestroyed()) {
                    if (m.isFighterWing()) continue;
                    if (m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX)) continue; // Ellis's own ship is not "one of yours"
                    n++;
                    lostCrew += (int) m.getMinCrew();
                    if (first == null) first = m.getShipName();
                }
                if (n > 0) {
                    AfterlightStory.recordLoss(n, lostCrew, first);
                }
            }
            // Only count the enemy side's losses when the player won.
            if (!result.didPlayerWin()) {
                return;
            }
            float dp = 0f;
            int crew = 0;
            for (FleetMemberAPI m : result.getLoserResult().getDestroyed()) {
                dp += m.getDeploymentPointsCost();
                crew += (int) m.getMinCrew();
            }
            for (FleetMemberAPI m : result.getLoserResult().getDisabled()) {
                dp += m.getDeploymentPointsCost();
                crew += (int) m.getMinCrew();
            }
            AfterlightStory.addDestroyedDp(dp);
            AfterlightStory.recordWonBattle(crew, dp);
        } catch (Throwable t) {
            Global.getLogger(AfterlightStoryScript.class).warn("Could not count engagement: " + t);
        }
    }

    @Override
    public void reportFleetJumped(com.fs.starfarer.api.campaign.CampaignFleetAPI fleet,
                                  com.fs.starfarer.api.campaign.SectorEntityToken from,
                                  com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination to) {
        Strangeness.onJump(fleet);
    }

    @Override
    public void reportPlayerMarketTransaction(com.fs.starfarer.api.campaign.PlayerMarketTransaction transaction) {
        Reflections.onTransaction(transaction);
    }

    @Override
    public void reportPlayerOpenedMarket(com.fs.starfarer.api.campaign.econ.MarketAPI market) {
        Reflections.onDock();
    }

    @Override
    public void advance(float amount) {
        GlitchJobs.checkDropInteraction(); // the Violet Dwarf jobs work whether or not the story was claimed
        GlitchJobs.tickJobs();
        if (AfterlightStory.isClaimed()) Strangeness.frame(); // runs while a dialog is open, so docking text can be added
        if (Global.getSector().isPaused()) return;
        GlitchJobs.advanceEndingJobs(amount); // lets finished Violet Dwarf jobs count down and leave the intel screen
        if (roguePlanets.AfterlightCaptainScript.captainGone()) return; // Ellis has left for good: the story is over
        // every frame, so a fast fleet cannot jump past the contact range between checks
        if (AfterlightStory.getStage() >= 5 && AfterlightStory.isClaimed()) VisitorsEvent.tick();
        if (AfterlightStory.getStage() >= 9 && AfterlightStory.isClaimed()) DoppelEvent.tick();
        tick.advance(amount);
        if (!tick.intervalElapsed()) return;
        if (!AfterlightStory.isClaimed()) return;

        StageEvents.tickPending();
        CopyWreck.recordSystem();

        // Nothing in the story plays while the Afterlight is missing (destroyed, in storage), or for a week after it returns.
        AfterlightStory.updatePresence();
        Strangeness.clearIfShipGone();
        if (!AfterlightStory.shipSettled()) return;
        CopyWreck.tick();
        if (AfterlightStory.getStage() >= 12) StageEvents.checkFortress();
        if (AfterlightStory.getStage() >= 10 && VisitorsEvent.returnDue()) VisitorsEvent.spawnReturn();

        // Stage 15a: Ellis talks to the player before the final choice. The stage counter stays at 15.
        if (Stage15a.ready() && Stage15a.begin()) return;
        if (AfterlightStory.getStage() == 15) Stage16.silenceTick();
        PostStory.tick();
        Strangeness.tick();

        int next = AfterlightStory.getStage() + 1;
        // Ellis's extra battle questions wait if a story stage that needs him is about to start
        boolean ellisStageReady = next <= StageEvents.LAST_BUILT_STAGE && StageEvents.isEllisStage(next)
            && AfterlightStory.daysSinceLastStage() >= StageEvents.minGapDays(next) && StageEvents.milestoneMet(next);
        if (!ellisStageReady && (BattleQuestions.tick() || Reflections.tick())) return;
        if (next > StageEvents.LAST_BUILT_STAGE) {
            runQueue();
            return;
        }
        if (AfterlightStory.daysSinceLastStage() < StageEvents.minGapDays(next)) return;
        if (!StageEvents.milestoneMet(next)) return;
        // Battle questions open right after the battle, so only a hostile fleet very close counts against them.
        if (!AfterlightStory.isQuietMoment(StageEvents.isBattleStage(next) ? 600f : 2000f)) return;
        // Ellis stages also respect his cooldown.
        if (StageEvents.isEllisStage(next) && !AfterlightStory.ellisCooldownReady()) return;

        if (StageEvents.run(next)) AfterlightStory.setStage(next);
        else runQueue();
    }

    /** Plays the oldest waiting Ellis conversation, one per cooldown, once the player has had time to settle in. */
    private void runQueue() {
        if (Global.getSector().getCampaignUI().isShowingDialog()) return;
        String id = AfterlightStory.peekQueue();
        if (id == null) return;
        if (AfterlightStory.daysSinceClaim() < AfterlightStory.QUEUE_START_DELAY_DAYS) return;
        if (!AfterlightStory.ellisCooldownReady() || !AfterlightStory.isQuietMoment()) return;
        AfterlightStory.popQueue();
        if (!StageEvents.runQueued(id)) AfterlightStory.queueEllis(id); // could not be shown now: back to the end of the line
    }

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return true; }
}
