package roguePlanets.story;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Background strangeness: small things that are slightly wrong, drawn from one shared pool with a shared cooldown.
 * Nothing here has a real effect: no money, reputation, ships or crew ever change. Nothing fires during a dialogue.
 */
public class Strangeness {

    private static final String K_LAST = "$afl_sx_last";          // timestamp of the last event
    private static final String K_DAY = "$afl_sx_day";            // last day number rolled
    private static final String K_MERC_COUNT = "$afl_sx_mercCount";
    private static final String K_MERC_STAMP = "$afl_sx_mercStamp";
    private static final String K_MERC = "$afl_sx_merc";          // the merc fleet
    private static final String K_MERC_STATE = "$afl_sx_mercState"; // 1 approaching, 2 fighting
    private static final String K_MERC_ENGAGE = "$afl_sx_mercEngage";
    private static final String K_MERC_START = "$afl_sx_mercStart";
    private static final String K_LOST_DONE = "$afl_sx_lostDone";

    private static final Random rand = new Random();

    // ---------------------------------------------------------------- shared cooldown

    /** Days between two events: shorter as the story advances. */
    private static float cooldownDays() {
        int stage = AfterlightStory.getStage();
        return Math.max(12f, 30f - stage * 1.5f);
    }

    /** Chance per day that the pool is drawn from once the cooldown is over: a little higher as the story advances. */
    private static float dailyChance() {
        return 0.06f + 0.008f * AfterlightStory.getStage();
    }

    private static boolean storyReady() {
        return AfterlightStory.isClaimed() && AfterlightStory.getStage() >= 3
            && !roguePlanets.AfterlightCaptainScript.captainGone();
    }

    public static boolean cooldownOver() {
        MemoryAPI m = AfterlightStory.mem();
        if (!m.contains(K_LAST)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(m.getLong(K_LAST)) >= cooldownDays();
    }

    public static void markFired() {
        AfterlightStory.mem().set(K_LAST, Global.getSector().getClock().getTimestamp());
    }

    /** Whether the combat plugin may pick an event for this battle (it marks the cooldown only if it really fires one). */
    public static boolean combatMayFire() {
        if (!storyReady() || !cooldownOver()) return false;
        return rand.nextFloat() < dailyChance() * 1.5f;
    }

    // ---------------------------------------------------------------- the daily draw

    private static final String[] POOL = {"bounty", "income", "distress", "zerorep", "lostship", "crewstay", "merc"};

    /** Per-kind cooldown on top of the shared one, for the kinds that can almost always fire (and so came up too often). */
    private static final String K_KIND_LAST = "$afl_sx_kindLast_";
    private static float kindCooldownDays(String kind) {
        if (kind.equals("bounty") || kind.equals("lostship")) return 120f;
        return 0f;
    }
    private static boolean kindReady(String kind) {
        float cd = kindCooldownDays(kind);
        if (cd <= 0f) return true;
        MemoryAPI m = AfterlightStory.mem();
        String key = K_KIND_LAST + kind;
        if (!m.contains(key)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(m.getLong(key)) >= cd;
    }

    /** Called once per second from the story script. */
    public static void tick() {
        if (!storyReady()) return;
        mercTick();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;
        if (!cooldownOver() || !AfterlightStory.isQuietMoment(1500f)) return;
        if (mercActive()) return;

        int day = Global.getSector().getClock().getDay() + Global.getSector().getClock().getMonth() * 40
            + Global.getSector().getClock().getCycle() * 500;
        MemoryAPI m = AfterlightStory.mem();
        if (m.getInt(K_DAY) == day) return;
        m.set(K_DAY, day);
        if (rand.nextFloat() >= dailyChance()) return;
        fireRandom(null);
    }

    /** Fires one event now (kind null = random). Returns the kind that fired, or null. */
    public static String fireRandom(String forceKind) {
        List<String> options = new ArrayList<String>();
        if (forceKind != null) options.add(forceKind);
        else for (String k : POOL) options.add(k);
        java.util.Collections.shuffle(options, rand);
        for (String k : options) {
            if (forceKind == null && !kindReady(k)) continue;
            if (fire(k)) {
                markFired();
                AfterlightStory.mem().set(K_KIND_LAST + k, Global.getSector().getClock().getTimestamp());
                return k;
            }
        }
        return null;
    }

    private static boolean fire(String kind) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (kind.equals("bounty")) {
            Global.getSector().getIntelManager().addIntel(new BountyIntel((int) player.getCargo().getCredits().get()));
            return true;
        }
        if (kind.equals("income")) {
            float credits = player.getCargo().getCredits().get();
            int amount = (int) Math.max(100f, credits / 2f);
            boolean neg = rand.nextBoolean();
            String s = Misc.getDGSCredits(amount);
            com.fs.starfarer.api.impl.campaign.intel.MessageIntel mi = new com.fs.starfarer.api.impl.campaign.intel.MessageIntel(
                neg ? "Monthly expenses: " + s : "Monthly income: " + s, Misc.getTextColor(), new String[] {s},
                neg ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor());
            mi.setIcon(Global.getSettings().getSpriteName("reports", neg ? "generic_expense" : "generic_income"));
            Global.getSector().getCampaignUI().addMessage(mi, com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction.NOTHING);
            return true;
        }
        if (kind.equals("distress")) {
            if (!(player.getContainingLocation() instanceof StarSystemAPI)) return false;
            Global.getSector().getIntelManager().addIntel(new DistressIntel((StarSystemAPI) player.getContainingLocation(), fleetName()));
            return true;
        }
        if (kind.equals("zerorep")) {
            String[] ids = {Factions.HEGEMONY, Factions.TRITACHYON, Factions.PERSEAN, Factions.LUDDIC_CHURCH, Factions.INDEPENDENT, Factions.PIRATES};
            com.fs.starfarer.api.campaign.FactionAPI f = Global.getSector().getFaction(ids[rand.nextInt(ids.length)]);
            if (f == null) return false;
            Global.getSector().getCampaignUI().addMessage(
                "Reputation with " + f.getDisplayName() + " changed by 0",
                Misc.getTextColor(), f.getDisplayName(), "0", f.getBaseUIColor(), Misc.getHighlightColor());
            return true;
        }
        if (kind.equals("lostship")) return lostShip(player);
        if (kind.equals("crewstay")) return crewStay(player);
        if (kind.equals("merc")) return mercSpawn(player);
        return false;
    }

    private static String fleetName() {
        return Global.getSector().getPlayerPerson().getName().getFullName() + "'s Fleet";
    }

    // ---------------------------------------------------------------- simple one-button popup

    private static class Popup implements InteractionDialogPlugin {
        private final String[] paragraphs;
        private final Color[] colors;
        private final String illustration;
        private final FleetMemberAPI member;
        private InteractionDialogAPI dialog;

        Popup(String[] paragraphs, Color[] colors, String illustration, FleetMemberAPI member) {
            this.paragraphs = paragraphs; this.colors = colors; this.illustration = illustration; this.member = member;
        }

        @Override public void init(InteractionDialogAPI d) {
            this.dialog = d;
            d.setBackgroundDimAmount(0.5f);
            try {
                if (member != null) d.getVisualPanel().showFleetMemberInfo(member, true);
                else if (illustration != null) {
                    d.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(
                        Global.getSettings().getSpriteName("illustrations", illustration), 480, 300));
                }
            } catch (Throwable t) {
                Global.getLogger(Strangeness.class).warn("Popup picture failed: " + t);
            }
            for (int i = 0; i < paragraphs.length; i++) {
                d.getTextPanel().addParagraph(paragraphs[i], colors[i] == null ? Misc.getTextColor() : colors[i]);
            }
            d.getOptionPanel().addOption("Continue", "go");
            d.setOptionOnEscape("Continue", "go");
        }
        @Override public void optionSelected(String t, Object data) { dialog.dismiss(); }
        @Override public void optionMousedOver(String t, Object data) { }
        @Override public void advance(float amount) { }
        @Override public void backFromEngagement(EngagementResultAPI r) { }
        @Override public Object getContext() { return null; }
        @Override public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
    }

    private static boolean showPopup(Popup p) {
        return Global.getSector().getCampaignUI().showInteractionDialog(p, AfterlightStory.dialogTarget());
    }

    // ---------------------------------------------------------------- crew staying (inverted crew-leaving popup)

    private static final String K_CREW_ARMED = "$afl_sx_crewArmed";
    private static final String K_CREW_STAMP = "$afl_sx_crewStamp";
    private static Object lastDialog = null;

    /** Arms the event: it plays the next time the player docks at a colony. */
    private static boolean crewStay(CampaignFleetAPI player) {
        if ((int) player.getCargo().getCrew() <= 0) return false;
        AfterlightStory.mem().set(K_CREW_ARMED, true);
        AfterlightStory.mem().set(K_CREW_STAMP, Global.getSector().getClock().getTimestamp());
        return true;
    }

    /** Called every frame: when the player docks at a colony, adds the inverted crew-leaving text to the docking screen. */
    public static void frame() {
        MemoryAPI mem = AfterlightStory.mem();
        if (!mem.getBoolean(K_CREW_ARMED)) { lastDialog = null; return; }
        if (Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_CREW_STAMP)) > 45f) { mem.unset(K_CREW_ARMED); return; }
        InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (d == null) { lastDialog = null; return; }
        if (d == lastDialog) return;
        lastDialog = d;
        try {
            SectorEntityToken t = d.getInteractionTarget();
            if (t == null || t instanceof CampaignFleetAPI || t.getMarket() == null) return;
            MarketAPI m = t.getMarket();
            if (m.isHidden() || m.isPlanetConditionMarketOnly()) return;
            mem.unset(K_CREW_ARMED);
            d.getTextPanel().addPara("The consistent pay over the last few months has caused contentment among your crew. "
                + "A number take this opportunity to remain in your employment.");
            d.getTextPanel().addPara("Lost 0\u00d7 crew", Misc.getNegativeHighlightColor());
            d.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), "0\u00d7");
            d.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(
                Global.getSettings().getSpriteName("illustrations", "crew_leaving"), 480, 300));
        } catch (Throwable t) {
            Global.getLogger(Strangeness.class).warn("Crew staying text failed: " + t);
        }
    }

    // ---------------------------------------------------------------- lost ship (a ship the player never owned)

    private static final String[] FAKE_HULLS = {"wolf", "lasher", "hound", "mudskipper", "buffalo", "kite", "condor", "sunder", "hammerhead", "centurion", "eagle", "falcon"};
    private static final String[] FAKE_NAMES = {"Quiet Harbour", "Second Light", "Slow Return", "Patient Hour", "Borrowed Time", "Low Orbit", "Unwritten", "Long Meadow"};

    /** Picks a real ship from the fleet (never Ellis's ship). When onlyBelowMax, only ships below full readiness count. */
    private static FleetMemberAPI pickShip(CampaignFleetAPI player, boolean onlyBelowMax) {
        List<FleetMemberAPI> options = new ArrayList<FleetMemberAPI>();
        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            if (m.isFighterWing()) continue;
            if (m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX)) continue;
            if (onlyBelowMax && m.getRepairTracker().getCR() >= m.getRepairTracker().getMaxCR() - 0.005f) continue;
            options.add(m);
        }
        if (options.isEmpty()) return null;
        return options.get(rand.nextInt(options.size()));
    }

    /** An accident report for one of the player's own ships. Nothing is lost. Needs some ship below full readiness. */
    private static boolean lostShip(CampaignFleetAPI player) {
        FleetMemberAPI m = pickShip(player, true);
        if (m == null) return false;
        return showAccident(m);
    }

    /** The game's "Accident Report" look: a framed box with severity, cause, recommendation, and what was lost. */
    private static boolean showAccident(final FleetMemberAPI fake) {
        final int crPct = Math.round(fake.getRepairTracker().getCR() * 100f);
        InteractionDialogPlugin host = new InteractionDialogPlugin() {
            private InteractionDialogAPI dialog;
            @Override public void init(InteractionDialogAPI d) {
                this.dialog = d;
                d.setBackgroundDimAmount(0.5f);
                final InteractionDialogAPI dd = d;
                d.showCustomDialog(680f, 440f, new com.fs.starfarer.api.campaign.CustomDialogDelegate() {
                    @Override public void createCustomDialog(com.fs.starfarer.api.ui.CustomPanelAPI panel,
                                                             com.fs.starfarer.api.campaign.CustomDialogDelegate.CustomDialogCallback cb) {
                        Color orange = Misc.getNegativeHighlightColor();
                        TooltipMakerAPI t = panel.createUIElement(660f, 350f, false);
                        t.addTitle("Accident Report").setAlignment(com.fs.starfarer.api.ui.Alignment.MID);
                        t.addPara("A lack of maintenance and the consequent lack of combat readiness has caused an accident that has "
                            + "resulted in a loss of the " + fake.getShipName() + " and a loss of crew.", 10f);
                        t.addPara("Severity:  %s", 14f, Misc.getTextColor(), orange, "Critical");
                        t.addPara("Likely cause:  %s", 4f, Misc.getTextColor(), orange, crPct + "% combat readiness");
                        t.addPara("Recommendation:  Take steps to restore the combat readiness of your ships as soon as possible. "
                            + "Ships at zero combat readiness continue to be in danger of suffering accidents, unless they're already being repaired.", 4f);
                        panel.addUIElement(t).inTL(10f, 10f);

                        TooltipMakerAPI ship = panel.createUIElement(160f, 100f, false);
                        try { ship.addImage(fake.getHullSpec().getSpriteName(), 64f, 64f, 0f); } catch (Throwable x) { /* no picture */ }
                        ship.addPara("lost ship", orange, 2f);
                        panel.addUIElement(ship).inTL(200f, 290f);

                        TooltipMakerAPI crew = panel.createUIElement(160f, 100f, false);
                        try { crew.addImage(Global.getSettings().getSpriteName("reports", "crew"), 48f, 48f, 0f); } catch (Throwable x) { /* no picture */ }
                        crew.addPara("lost crew", orange, 2f);
                        panel.addUIElement(crew).inTL(400f, 290f);
                    }
                    @Override public boolean hasCancelButton() { return false; }
                    @Override public String getConfirmText() { return "Dismiss"; }
                    @Override public String getCancelText() { return "Dismiss"; }
                    @Override public void customDialogConfirm() { dd.dismiss(); }
                    @Override public void customDialogCancel() { dd.dismiss(); }
                    @Override public com.fs.starfarer.api.campaign.CustomUIPanelPlugin getCustomPanelPlugin() {
                        return new com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin();
                    }
                });
            }
            @Override public void optionSelected(String t, Object data) { }
            @Override public void optionMousedOver(String t, Object data) { }
            @Override public void advance(float amount) { }
            @Override public void backFromEngagement(EngagementResultAPI r) { }
            @Override public Object getContext() { return null; }
            @Override public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
        };
        return Global.getSector().getCampaignUI().showInteractionDialog(host, AfterlightStory.dialogTarget());
    }

    // ---------------------------------------------------------------- bounty on the player (lasts 3 days)

    public static class BountyIntel extends BaseIntelPlugin {
        private final int reward;
        private final long start;

        public BountyIntel(int reward) {
            this.reward = Math.max(1000, reward);
            this.start = Global.getSector().getClock().getTimestamp();
        }
        private String who() { return Global.getSector().getPlayerPerson().getName().getFullName(); }
        private int daysLeft() {
            return Math.max(1, (int) Math.ceil(3f - Global.getSector().getClock().getElapsedDaysSince(start)));
        }
        @Override protected String getName() { return "Personal Bounty - " + who(); }
        @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);
            info.setBulletedListMode(BULLET);
            info.addPara("Faction: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), "Unknown");
            info.addPara("%s reward, %s days remaining", 0f, Misc.getGrayColor(), Misc.getHighlightColor(),
                Misc.getDGSCredits(reward), "" + daysLeft());
            info.setBulletedListMode(null);
        }
        @Override public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            try {
                info.addImage(Global.getSector().getPlayerPerson().getPortraitSprite(), width, 128f, 10f);
            } catch (Throwable t) { /* no picture */ }
            info.addPara("An unknown party has posted a bounty for bringing " + who() + ", commanding " + fleetName() + ", to justice.", 10f);
            info.setBulletedListMode(BULLET);
            info.addPara("%s reward", 10f, Misc.getTextColor(), Misc.getHighlightColor(), Misc.getDGSCredits(reward));
            info.addPara("%s days remaining", 0f, Misc.getTextColor(), Misc.getHighlightColor(), "" + daysLeft());
            info.setBulletedListMode(null);
            info.addPara("Posted by: unknown.", 10f);
            info.addPara("The bounty posting also contains partial intel on some of the ships under their command.", 10f);
            try {
                List<FleetMemberAPI> ships = new ArrayList<FleetMemberAPI>();
                for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    if (!m.isFighterWing() && ships.size() < 4) ships.add(m);
                }
                if (!ships.isEmpty()) info.addShipList(4, 1, 40f, Misc.getBasePlayerColor(), ships, 10f);
            } catch (Throwable t) { /* skip ship list */ }
        }
        @Override public SectorEntityToken getMapLocation(SectorMapAPI map) { return null; }
        @Override public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_BOUNTY);
            return tags;
        }
        @Override public String getIcon() { return Global.getSector().getPlayerPerson().getPortraitSprite(); }
        @Override protected void advanceImpl(float amount) {
            super.advanceImpl(amount);
            if (isEnding() || isEnded()) return;
            if (Global.getSector().getClock().getElapsedDaysSince(start) >= 3f) endAfterDelay();
        }
    }

    // ---------------------------------------------------------------- your own distress call

    public static class DistressIntel extends BaseIntelPlugin {
        private final StarSystemAPI system;
        private final String who;
        private final long start;

        public DistressIntel(StarSystemAPI system, String who) {
            this.system = system; this.who = who;
            this.start = Global.getSector().getClock().getTimestamp();
        }
        @Override protected String getName() { return "Distress call: " + who; }
        @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);
            info.setBulletedListMode(BULLET);
            info.addPara("Location: %s", 0f, Misc.getTextColor(), Misc.getHighlightColor(), system.getNameWithLowercaseType());
            info.setBulletedListMode(null);
        }
        @Override public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            info.addPara("A distress call is being broadcast from " + who + " in the " + system.getNameWithLowercaseType()
                + ". It asks for anyone nearby to respond.", 10f);
        }
        @Override public SectorEntityToken getMapLocation(SectorMapAPI map) { return system.getCenter(); }
        @Override public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_LOCAL);
            return tags;
        }
        @Override protected void advanceImpl(float amount) {
            super.advanceImpl(amount);
            if (isEnding() || isEnded()) return;
            if (Global.getSector().getClock().getElapsedDaysSince(start) >= 6f) endAfterDelay();
        }
    }

    // ---------------------------------------------------------------- bar rumor

    public static class RumorCreator extends BaseBarEventCreator {
        @Override public PortsideBarEvent createBarEvent() { return new RumorBarEvent(); }
        @Override public float getBarEventFrequencyWeight() { return 0.2f; }
        @Override public float getBarEventTimeoutDuration() { return 90f; }
        @Override public float getBarEventAcceptedTimeoutDuration() { return 90f; }
        @Override public float getBarEventActiveDuration() { return 30f; }
        @Override public String getBarEventId() { return "afl_strange_rumor"; }
    }

    public static void register() {
        BarEventManager m = BarEventManager.getInstance();
        if (m != null && !m.hasEventCreator(RumorCreator.class)) m.addEventCreator(new RumorCreator());
    }

    public static class RumorBarEvent extends BaseBarEventWithPerson {
        private boolean finished;

        @Override public boolean shouldShowAtMarket(MarketAPI market) {
            return super.shouldShowAtMarket(market) && storyReady() && cooldownOver();
        }
        @Override public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
            super.addPromptAndOption(dialog, memoryMap);
            regen(dialog.getInteractionTarget().getMarket());
            dialog.getTextPanel().addPara("A patron at the end of the bar is telling the room about a fleet, in the voice of someone who has told it before.");
            dialog.getOptionPanel().addOption("Listen to the patron", this);
        }
        @Override public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
            super.init(dialog, memoryMap);
            dialog.getVisualPanel().showPersonInfo(person, true);
            text.addPara("\"A fleet just like yours left an hour ago,\" the patron says, to you, as if you had been there all along. \"They paid in your name.\"");
            options.clearOptions();
            options.addOption("Leave it", "leave");
            markFired();
        }
        @Override public void optionSelected(String optionText, Object optionData) {
            if ("leave".equals(optionData)) { finished = true; done = true; }
        }
        @Override public boolean shouldRemoveEvent() { return finished; }
    }

    // ---------------------------------------------------------------- jump glitch

    private static final String SCRAMBLE = "#%&@?*/\\|~";

    private static String scramble(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isLetter(ch) && rand.nextFloat() < 0.12f) sb.append(SCRAMBLE.charAt(rand.nextInt(SCRAMBLE.length())));
            else sb.append(ch);
        }
        return sb.toString();
    }

    /** Called when any fleet jumps; only the player's fleet counts. */
    public static void onJump(CampaignFleetAPI fleet) {
        try {
            if (fleet == null || !fleet.isPlayerFleet()) return;
            if (!storyReady() || !cooldownOver()) return;
            if (Global.getSector().getCampaignUI().isShowingDialog()) return;
            if (rand.nextFloat() >= 0.05f + 0.005f * AfterlightStory.getStage()) return;
            float r = rand.nextFloat();
            if (r < 0.6f) {
                Global.getSector().getCampaignUI().addMessage(scramble("Transit complete. Elapsed: 0 days. Arrival: before departure."), Misc.getGrayColor());
            } else if (r < 0.85f) {
                spawnMirror(fleet);
            } else {
                Global.getSector().getCampaignUI().showMessageDialog("You emerge. You were already here.");
            }
            markFired();
        } catch (Throwable t) {
            Global.getLogger(Strangeness.class).warn("Jump glitch failed: " + t);
        }
    }

    private static void spawnMirror(CampaignFleetAPI player) {
        LocationAPI loc = player.getContainingLocation();
        if (loc == null) return;
        final CampaignFleetAPI mirror = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "", true);
        mirror.setNoFactionInName(true);
        mirror.setName(fleetName());
        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            if (m.isFighterWing()) continue;
            try {
                FleetMemberAPI c = Global.getFactory().createFleetMember(FleetMemberType.SHIP, m.getVariant().clone());
                c.setShipName(m.getShipName());
                mirror.getFleetData().addFleetMember(c);
            } catch (Throwable t) { /* skip */ }
        }
        if (mirror.getFleetData().getNumMembers() == 0) return;
        MemoryAPI fm = mirror.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
        fm.set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        mirror.setTransponderOn(true);
        mirror.getStats().getSensorRangeMod().modifyFlat("rp_mirror", 100000f);
        float angle = rand.nextFloat() * 360f;
        final float ox = (float) Math.cos(Math.toRadians(angle)) * 2000f;
        final float oy = (float) Math.sin(Math.toRadians(angle)) * 2000f;
        loc.addEntity(mirror);
        mirror.setLocation(player.getLocation().x + ox, player.getLocation().y + oy);
        mirror.getFleetData().setSyncNeeded();
        mirror.getFleetData().syncIfNeeded();
        Global.getSector().addScript(new com.fs.starfarer.api.EveryFrameScript() {
            float t = 0f;
            boolean done = false;
            public void advance(float amount) {
                if (done) return;
                CampaignFleetAPI p = Global.getSector().getPlayerFleet();
                t += Global.getSector().getClock().convertToDays(amount) * 1f;
                elapsedReal += amount;
                if (elapsedReal > 20f || p == null || p.getContainingLocation() != mirror.getContainingLocation() || !mirror.isAlive()) {
                    done = true;
                    if (mirror.isAlive() && mirror.getContainingLocation() != null) mirror.getContainingLocation().removeEntity(mirror);
                    return;
                }
                mirror.setLocation(p.getLocation().x + ox, p.getLocation().y + oy);
                mirror.setMoveDestination(p.getLocation().x + ox + p.getVelocity().x, p.getLocation().y + oy + p.getVelocity().y);
                mirror.setVelocity(p.getVelocity().x, p.getVelocity().y);
            }
            float elapsedReal = 0f;
            public boolean isDone() { return done; }
            public boolean runWhilePaused() { return false; }
        });
    }

    // ---------------------------------------------------------------- the wrong wavelength (a mercenary fleet)

    private static final float MERC_CONTACT = 700f;
    private static final float MERC_GIVE_UP_DAYS = 12f;

    public static boolean mercActive() {
        return AfterlightStory.mem().getInt(K_MERC_STATE) != 0;
    }

    private static CampaignFleetAPI mercFleet() {
        Object o = AfterlightStory.mem().get(K_MERC);
        return o instanceof CampaignFleetAPI ? (CampaignFleetAPI) o : null;
    }

    private static boolean mercSpawn(CampaignFleetAPI player) {
        MemoryAPI mem = AfterlightStory.mem();
        if (AfterlightStory.getStage() < 5) return false;
        if (mem.getInt(K_MERC_COUNT) >= 2) return false;
        if (mem.contains(K_MERC_STAMP) && Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_MERC_STAMP)) < 90f) return false;
        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return false;
        if (mercActive()) return false;

        float pts = Math.max(30f, Math.min(160f, player.getFleetPoints() * 0.5f));
        FleetParamsV3 params = new FleetParamsV3(player.getLocationInHyperspace(), Factions.INDEPENDENT, 1f,
            FleetTypes.MERC_PRIVATEER, pts, 0f, 0f, 0f, 0f, 0f, 0f);
        CampaignFleetAPI f = FleetFactoryV3.createFleet(params);
        if (f == null || f.getFleetData().getNumMembers() == 0) return false;
        f.setName("Hired Fleet");
        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        f.setTransponderOn(true);
        f.getStats().getSensorRangeMod().modifyFlat("rp_merc", 100000f);
        player.getContainingLocation().addEntity(f);
        float angle = rand.nextFloat() * 360f;
        f.setLocation(player.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * 2200f,
            player.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * 2200f);
        f.addAssignment(FleetAssignment.INTERCEPT, player, 100000f, "searching");
        mem.set(K_MERC, f);
        mem.set(K_MERC_STATE, 1);
        mem.set(K_MERC_START, Global.getSector().getClock().getTimestamp());
        mem.set(K_MERC_COUNT, mem.getInt(K_MERC_COUNT) + 1);
        mem.set(K_MERC_STAMP, Global.getSector().getClock().getTimestamp());
        return true;
    }

    private static void mercClear() {
        MemoryAPI mem = AfterlightStory.mem();
        CampaignFleetAPI f = mercFleet();
        if (f != null && f.isAlive()) Misc.fadeAndExpire(f);
        mem.unset(K_MERC);
        mem.unset(K_MERC_STATE);
        mem.unset(K_MERC_ENGAGE);
    }

    private static void mercTick() {
        MemoryAPI mem = AfterlightStory.mem();
        int state = mem.getInt(K_MERC_STATE);
        if (state == 0) return;
        CampaignFleetAPI f = mercFleet();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (f == null || player == null) { mercClear(); return; }
        float age = Global.getSector().getClock().getElapsedDaysSince(mem.getLong(K_MERC_START));

        if (state == 2) {
            if (!AfterlightStory.isQuietMoment(0f)) return;
            if (mem.getBoolean(K_MERC_ENGAGE) && f.isAlive()) {
                mem.unset(K_MERC_ENGAGE);
                f.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE);
                Misc.makeHostile(f);
                FIDConfig cfg = new FIDConfig();
                cfg.straightToEngage = true;
                cfg.noLeaveOptionOnFirstEngagement = true;
                cfg.leaveAlwaysAvailable = false;
                cfg.showCommLinkOption = false;
                cfg.showWarningDialogWhenNotHostile = false;
                cfg.impactsEnemyReputation = false;
                cfg.impactsAllyReputation = false;
                cfg.pullInStations = false;
                Global.getSector().getCampaignUI().showInteractionDialog(new FleetInteractionDialogPluginImpl(cfg), f);
                return;
            }
            // battle over: forget the fleet
            if (!f.isAlive() || f.getFleetData().getNumMembers() == 0 || age > MERC_GIVE_UP_DAYS + 6f) {
                mem.unset(K_MERC); mem.unset(K_MERC_STATE);
            }
            return;
        }

        boolean gone = !f.isAlive() || f.getContainingLocation() != player.getContainingLocation();
        if (gone || age > MERC_GIVE_UP_DAYS) { mercClear(); return; }
        if (Misc.getDistance(f, player) > MERC_CONTACT) return;
        if (!AfterlightStory.isQuietMoment(0f)) return;
        f.clearAssignments();
        f.addAssignment(FleetAssignment.HOLD, null, 3f, "waiting");
        mercDialogue(f);
    }

    private static void mercDialogue(final CampaignFleetAPI f) {
        final String who = "Mercenary captain";
        StoryDialogue d = new StoryDialogue("m_hail", "strange_merc_" + AfterlightStory.mem().getInt(K_MERC_COUNT), false);
        d.onEnd(new Runnable() {
            public void run() {
                if (AfterlightStory.mem().getInt(K_MERC_STATE) == 2) return; // the fight goes on after the box closes
                mercClear();
            }
        });
        d.node("m_hail").fleetInfo(f).colored("You're being hailed by " + f.getName() + ".", f.getFaction().getBaseUIColor());
        d.node("m_hail").choice("Open a comm link", "m0");
        d.node("m_hail").choice("Ignore", "m0");

        d.node("m0").fleetInfo(f).voice(who, "Hold. Scanning.");
        d.node("m0").choice("...", "m1").quiet();
        d.node("m1").fleetInfo(f).voice(who, "Wrong wavelength. We were paid to find a violet one.");
        d.node("m1").choice("...", "m2").quiet();
        d.node("m2").fleetInfo(f).voice(who, "Yours reads close. Too close.");
        addMercOptions(d, "m2", f, true);

        d.node("m_who").fleetInfo(f).voice(who, "A voice. It paid in light.");
        addMercOptions(d, "m_who", f, false);

        d.node("m_not").fleetInfo(f).voice(who, "No. No, you're not. Apologies, Captain. We'll be out of your way.");
        d.node("m_go").fleetInfo(f).say("The fleet turns and burns away from you. The channel closes without another word.");
        d.node("m_fire").fleetInfo(f).say("You give the order. The mercenary fleet answers it.");

        if (!d.show()) mercClear();
    }

    private static void addMercOptions(StoryDialogue d, String node, final CampaignFleetAPI f, boolean withWho) {
        if (withWho) d.node(node).choice("\"Who hired you?\"", "m_who");
        d.node(node).choice("\"I'm not what you're looking for.\"", "m_not");
        d.node(node).choice("\"Let them go.\"", "m_go");
        d.node(node).choice("\"Attack them.\"", "m_fire").run(new Runnable() {
            public void run() {
                MemoryAPI mem = AfterlightStory.mem();
                mem.set(K_MERC_STATE, 2);
                mem.set(K_MERC_ENGAGE, true);
                mem.set(K_MERC_START, Global.getSector().getClock().getTimestamp());
            }
        });
    }
}
