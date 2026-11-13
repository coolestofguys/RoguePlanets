package roguePlanets.story;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Glitched jobs: rare bar events that copy something the game already does, with one detail wrong.
 * Five of them send the player to the Violet Dwarf (dead drop, pilgrims, satellite, instrument package, VIP). Three are
 * deliveries that come out wrong, and one is an old freighter that already knows the player. None of them advance the
 * story, none gives a power advantage, and nothing in the text says it is a glitch.
 */
public class GlitchJobs {

    public static final String[] STAR_KINDS = {"deaddrop", "pilgrims", "satellite", "instrument", "vip"};
    public static final String[] ALL_KINDS = {"deaddrop", "pilgrims", "satellite", "instrument", "vip",
        "tohere", "nothing", "wrongperson", "freighter", "hijack"};

    // ---------------------------------------------------------------- registering the bar events

    /** Weight of one glitched job next to the game's own bar events (each of which has weight 10): about 2%. */
    private static final float WEIGHT = 0.2f;
    /** Days before the same glitched job can come around again. */
    private static final float TIMEOUT_DAYS = 120f;

    public abstract static class Creator extends BaseBarEventCreator {
        abstract String kind();
        @Override public PortsideBarEvent createBarEvent() {
            String k = kind();
            if (k.equals("tohere") || k.equals("nothing")) return new GlitchDeliveryEvent(k);
            return new GlitchBarEvent(k);
        }
        @Override public float getBarEventFrequencyWeight() { return mayGenerate() ? WEIGHT : 0f; }
        @Override public float getBarEventTimeoutDuration() { return TIMEOUT_DAYS; }
        @Override public float getBarEventAcceptedTimeoutDuration() { return TIMEOUT_DAYS; }
        @Override public float getBarEventActiveDuration() { return 30f; }
        @Override public String getBarEventId() { return "afl_glitch_" + kind(); }
    }
    public static class DeadDropCreator extends Creator { String kind() { return "deaddrop"; } }
    public static class PilgrimsCreator extends Creator { String kind() { return "pilgrims"; } }
    public static class SatelliteCreator extends Creator { String kind() { return "satellite"; } }
    public static class InstrumentCreator extends Creator { String kind() { return "instrument"; } }
    public static class VipCreator extends Creator { String kind() { return "vip"; } }
    public static class ToHereCreator extends Creator { String kind() { return "tohere"; } }
    public static class NothingCreator extends Creator { String kind() { return "nothing"; } }
    public static class WrongPersonCreator extends Creator { String kind() { return "wrongperson"; } }
    public static class FreighterCreator extends Creator { String kind() { return "freighter"; } }
    public static class HijackCreator extends Creator { String kind() { return "hijack"; } }

    /** Adds the glitched bar events to the game's bar event manager (once). */
    public static void register() {
        BarEventManager m = BarEventManager.getInstance();
        if (m == null) return;
        add(m, new DeadDropCreator());
        add(m, new PilgrimsCreator());
        add(m, new SatelliteCreator());
        add(m, new InstrumentCreator());
        add(m, new VipCreator());
        add(m, new ToHereCreator());
        add(m, new NothingCreator());
        add(m, new WrongPersonCreator());
        add(m, new FreighterCreator());
        add(m, new HijackCreator());
    }

    private static void add(BarEventManager m, Creator c) {
        if (!m.hasEventCreator(c.getClass())) m.addEventCreator(c);
    }

    // ---------------------------------------------------------------- one glitched job at a time

    /** Sector memory key (expiring): while set, no glitched job is offered. Set when one is accepted. */
    private static final String COOLDOWN_KEY = "$afl_glitch_cooldown";
    private static final float COOLDOWN_DAYS = 60f;

    private static boolean storyAllows() {
        return AfterlightStory.isClaimed() && !roguePlanets.AfterlightCaptainScript.captainGone()
            && !Stage16.done(); // no new glitched jobs once any ending has been chosen (one already accepted can still be finished)
    }

    private static boolean inCooldown() {
        return Global.getSector().getMemoryWithoutUpdate().contains(COOLDOWN_KEY);
    }

    static void startCooldown() {
        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);
    }

    /** True while a Violet Dwarf job is still in progress. */
    private static boolean starJobRunning() {
        for (Object o : Global.getSector().getIntelManager().getIntel(StarJobIntel.class)) {
            StarJobIntel job = (StarJobIntel) o;
            if (!job.isEnded() && !job.isEnding()) return true;
        }
        return false;
    }

    private static boolean isGlitch(PortsideBarEvent e) {
        return e instanceof GlitchBarEvent || e instanceof GlitchDeliveryEvent;
    }

    /** The only glitched job allowed on offer is the first one in the sector's bar list. */
    private static boolean isFirstGlitchOnOffer(PortsideBarEvent self) {
        for (PortsideBarEvent e : PortsideBarData.getInstance().getEvents()) {
            if (isGlitch(e)) return e == self;
        }
        return false;
    }

    private static boolean anyGlitchOnOffer() {
        for (PortsideBarEvent e : PortsideBarData.getInstance().getEvents()) {
            if (isGlitch(e)) return true;
        }
        return false;
    }

    /** Whether the game may create a new glitched job right now (otherwise it would sit hidden in the bar list). */
    private static boolean mayGenerate() {
        try {
            return storyAllows() && !inCooldown() && !starJobRunning() && !anyGlitchOnOffer();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether this glitched job may be shown in a bar right now. */
    private static boolean mayShow(PortsideBarEvent self) {
        try {
            return storyAllows() && !inCooldown() && !starJobRunning() && isFirstGlitchOnOffer(self);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String credits(int n) { return Misc.getDGSCredits(n); }

    private static void pay(int n) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(n);
        Global.getSector().getCampaignUI().addMessage("Received " + credits(n) + ".");
    }

    // ---------------------------------------------------------------- the two glitched deliveries (the game's own delivery job, one detail wrong)

    /**
     * The game's own cargo delivery job, with its own screen, map, negotiation and real cargo. "tohere": the destination is
     * the market the player is already in. "nothing": the job is for 0 units, so no cargo is handed over.
     */
    public static class GlitchDeliveryEvent extends com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEvent {
        private final String kind;

        public GlitchDeliveryEvent(String kind) { this.kind = kind; }

        @Override
        public boolean shouldShowAtMarket(MarketAPI m) {
            if (!mayShow(this)) return false;
            if (m == null || m.isHidden() || m.getFaction() == null || m.getFaction().isPlayerFaction()) return false;
            try {
                regen(m);
                return data != null && destination != null;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        protected void doStandardConfirmActions() {
            super.doStandardConfirmActions();
            startCooldown();
        }

        @Override
        protected void regen(MarketAPI m) {
            super.regen(m);
            if (data == null || destination == null) return;
            Random r = new Random(seed ^ 0x5eedL);
            if (kind.equals("tohere")) {
                destination = m;
                data.dest = m;
                data.hasPA = false; data.hasCells = false; data.fromHasPA = false; data.fromHasCells = false;
                data.illegal = false;
                reward = Math.max(reward, 8000 + r.nextInt(7000));
            } else {
                quantity = 0;
                escrow = 0;
                reward = Math.max(reward, 6000 + r.nextInt(6000));
            }
        }
    }

    // ---------------------------------------------------------------- the bar event

    public static class GlitchBarEvent extends BaseBarEventWithPerson {
        private final String kind;
        private final int reward;
        private final int oddReward;
        private boolean accepted;
        private boolean finished;
        private String marketName = "this market";

        public GlitchBarEvent(String kind) {
            this.kind = kind;
            Random r = new Random();
            boolean star = !(kind.equals("tohere") || kind.equals("nothing") || kind.equals("wrongperson") || kind.equals("freighter") || kind.equals("hijack"));
            this.reward = star ? 22000 + r.nextInt(18000) : 8000 + r.nextInt(7000);
            this.oddReward = (4000 + r.nextInt(8000)) | 1;
        }

        @Override
        protected com.fs.starfarer.api.characters.FullName.Gender getPersonGender() {
            return kind.equals("hijack") ? com.fs.starfarer.api.characters.FullName.Gender.MALE : super.getPersonGender();
        }

        @Override
        protected String getPersonFaction() {
            return kind.equals("pilgrims") ? Factions.LUDDIC_CHURCH : super.getPersonFaction();
        }

        @Override
        public boolean shouldShowAtMarket(MarketAPI market) {
            if (accepted || finished) return false; // taken (or finished): never offered again, even before the bar clears it
            return super.shouldShowAtMarket(market) && mayShow(this);
        }

        private String prompt() {
            if (kind.equals("deaddrop")) return "At the end of the bar, a rakish " + getManOrWoman() + " is met by a variety of low-life characters who scurry away again almost immediately.";
            if (kind.equals("pilgrims")) return "A small group of Luddic pilgrims in plain robes waits near the door, looking from ship to ship in the docking schedule.";
            if (kind.equals("satellite")) return "A person in a sensible coat sits at a table with no drink in front of them, watching the room.";
            if (kind.equals("instrument")) return "A few researchers at a corner table argue quietly over a case of equipment.";
            if (kind.equals("vip")) return "An impatient, well-dressed person is shouting at a bar clerk about a delayed transport.";
            if (kind.equals("hijack")) return "A tough-looking man in a powered armor undersuit downs acrid liquor, intimidating anyone who comes too close with a feral stare.";
            if (kind.equals("tohere") || kind.equals("nothing") || kind.equals("wrongperson"))
                return "At a corner table, a concerned-looking " + getManOrWoman() + " glumly examines " + getHisOrHer() + " TriPad.";
            return "A weathered person nurses a drink and looks at the docking bays through the window as if they were saying goodbye to something.";
        }

        private String label() {
            if (kind.equals("deaddrop")) return "Find out what the rakish " + getManOrWoman() + " at the end of the bar is up to";
            if (kind.equals("pilgrims")) return "Speak with the pilgrims";
            if (kind.equals("satellite")) return "Join the person in the sensible coat";
            if (kind.equals("instrument")) return "Ask the researchers what they need";
            if (kind.equals("vip")) return "Offer the VIP your ship";
            if (kind.equals("hijack")) return "Chat with the tough-looking man wearing the powered armor undersuit";
            if (kind.equals("tohere") || kind.equals("nothing") || kind.equals("wrongperson"))
                return "Nod to the concerned " + getManOrWoman() + " and walk over to " + getHisOrHer() + " table";
            return "Sit down with the weathered stranger";
        }

        @Override
        public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
            super.addPromptAndOption(dialog, memoryMap);
            regen(dialog.getInteractionTarget().getMarket());
            dialog.getTextPanel().addPara(prompt());
            dialog.getOptionPanel().addOption(label(), this);
        }

        @Override
        public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
            super.init(dialog, memoryMap);
            dialog.getVisualPanel().showPersonInfo(person, true);
            if (dialog.getInteractionTarget() != null && dialog.getInteractionTarget().getMarket() != null) {
                marketName = dialog.getInteractionTarget().getMarket().getName();
            }
            offer();
            if (!(kind.equals("tohere") || kind.equals("nothing") || kind.equals("wrongperson") || kind.equals("freighter") || kind.equals("hijack"))) showDestination(dialog);
        }

        private float distToStarLY() {
            try {
                SectorEntityToken here = dialogTarget();
                PlanetAPI star = LightSpeaks.starEntity();
                if (here != null && star != null) return Misc.getDistanceLY(here, star);
            } catch (Throwable t) { /* fall through */ }
            return 12f;
        }

        private SectorEntityToken dialogTarget() {
            return dialog != null ? dialog.getInteractionTarget() : null;
        }

        private String starSystemName() {
            PlanetAPI star = LightSpeaks.starEntity();
            return star != null && star.getStarSystem() != null ? star.getStarSystem().getBaseName() + " system" : "the Violet Dwarf system";
        }

        private void showDestination(InteractionDialogAPI d) {
            try {
                SectorEntityToken dp = dropPoint();
                if (dp == null) dp = LightSpeaks.starEntity();
                if (dp == null) return;
                java.util.Set<String> tags = new java.util.HashSet<String>();
                tags.add(Tags.INTEL_ACCEPTED);
                d.getVisualPanel().showMapMarker(dp, "Destination: " + starSystemName(), Misc.getHighlightColor(), true,
                    Global.getSettings().getSpriteName("intel", "important"), null, tags);
            } catch (Throwable t) {
                Global.getLogger(GlitchJobs.class).warn("Could not show the map marker: " + t);
            }
        }

        private String flagshipClass() {
            FleetMemberAPI f = Global.getSector().getPlayerFleet().getFlagship();
            if (f == null) {
                for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    if (!m.isFighterWing()) { f = m; break; }
                }
            }
            if (f == null) return "unlisted";
            String tech = "";
            try {
                String style = String.valueOf(f.getHullSpec().getManufacturer()).toUpperCase();
                if (style.contains("LOW")) tech = " (L)";
                else if (style.contains("MIDLINE")) tech = " (M)";
                else if (style.contains("HIGH")) tech = " (H)";
            } catch (Throwable t) { /* no tech tag */ }
            return f.getHullSpec().getHullName() + tech + " class " + f.getHullSpec().getDesignation();
        }

        private int hijackMarines() {
            FleetMemberAPI f = Global.getSector().getPlayerFleet().getFlagship();
            if (f == null) return 50;
            switch (f.getHullSpec().getHullSize()) {
                case FRIGATE: return 20;
                case DESTROYER: return 30;
                case CRUISER: return 50;
                default: return 80;
            }
        }

        private void hijackIntro() {
            text.addPara("You approach, maintaining your cool despite the withering stare. The man laughs at your composure and welcomes you to sit with a gust of eye-watering liquor fumes. \"I like you,\" he exclaims to the entire bar, which ignores the shout, \"You're not a scared little dirtworm!\"");
            text.addPara("Your new best friend is full of boasts and brags, and you're surprised to discover that he is sharper than he looks as he slips pointed questions about your background and capabilities into his braggadocio.");
            text.addPara("Adopting a lower voice, but keeping his eyes scanning the room, he says, \"I'll level, captain. You can handle your flux, I respect that. I'm wondering if you want to get in on a job with me.\" He smiles, showing off a full set of titanium teeth. \"Wanna know more?\"");
            options.clearOptions();
            options.addOption("\"Do tell.\"", "h_dotell");
        }

        private void hijackPitch() {
            int price = 44000;
            try {
                FleetMemberAPI f = Global.getSector().getPlayerFleet().getFlagship();
                if (f != null) price = Math.max(20000, Math.round(f.getBaseBuyValue() * 0.3f / 1000f) * 1000);
            } catch (Throwable t) { /* default */ }
            int marines = hijackMarines();
            text.addPara("\"There's a " + flagshipClass() + " collecting space dust in a lightly guarded orbital hangar. I made a plan to jack the ship. My original sponsor got cold feet. Dirtworm, like I said. It'd be a waste not to do the job after all that work, yeah? I just don't got any use for a starship.\"");
            text.addPara("\"I've got schematics of the hangar, the security codes, the guards' schedule, everything. I'll even come along on the operation so you know it's on the up-and-up.\"");
            text.addPara("\"My price is " + credits(price).replace("\u00a2", "") + " credits, non-negotiable. And you'll need to provide at least " + marines
                + " marines for this to work cleanly. I don't expect active resistance, but all the guards gotta be covered to ensure no one tries to be a hero. You in?\"");
            text.highlightInLastPara(Misc.getHighlightColor(), credits(price).replace("\u00a2", ""), "" + marines);
            options.clearOptions();
            options.addOption("\"That's my ship. I already own it.\"", "h_mine1");
            options.addOption("\"That's the ship I'm sitting in right now.\"", "h_mine2");
        }

        private void hijackFlee() {
            text.addPara("The man's grin stays where it is for a moment after everything behind it has gone. \"No,\" he says. \"No, that's... I checked. I checked the registry.\"");
            text.addPara("He is on his feet before you finish the sentence, the chair going over behind him. He does not look at you again. The door closes on the sound of his boots, and the bar, which ignored the shouting, ignores this too.");
            finished = true; // he is gone: the event leaves the bar
            leaveOption();
        }

        private void offer() {
            if (kind.equals("hijack")) { hijackIntro(); return; }
            options.clearOptions();
            String r = credits(reward);
            if (kind.equals("deaddrop")) {
                String ly = "" + Math.max(1, Math.round(distToStarLY()));
                String sys = starSystemName();
                String he = getHeOrShe();
                String she = Character.toUpperCase(he.charAt(0)) + he.substring(1);
                text.addPara("The " + getManOrWoman() + " watches your approach. \"You want in on the business, that right?\" " + he + " says, amused at your audacity.");
                text.addPara("\"Let me give you a job so easy that any rad-addled spacer could manage it. But,\" " + he + " shrugs regretfully, \"It's a real shame how many 'em just can't follow instructions and end up getting tossed out an airlock.\" " + she + " laughs like this is a good joke.");
                text.addPara("\"Deliver the item to the specified location in " + sys + ", " + ly + " light-years away.\" " + she + " flips a TriPad out of " + getHisOrHer()
                    + " coat. The display reads \"the light\" and flickers when you look at it directly. \"This is it. The reward is " + r.replace("\u00a2", "")
                    + " credits. Don't be seen making the drop, and don't tell anyone about it.\"");
                text.highlightInLastPara(Misc.getHighlightColor(), ly, r.replace("\u00a2", ""));
            } else if (kind.equals("pilgrims")) {
                text.addPara("The eldest of them steps forward. \"We have heard there is a light, out past the edge of the charts. "
                    + "We were told that it looks back.\"");
                text.addPara("\"Will you take us to it? We can pay " + r + ". It is everything we have gathered.\"");
                text.highlightInLastPara(Misc.getHighlightColor(), r);
            } else if (kind.equals("satellite")) {
                text.addPara("\"I need a small satellite put into orbit around a particular star,\" the person says. \"Passive sensors only. "
                    + "My employers are curious about a violet dwarf they cannot find in any survey.\"");
                text.addPara("\"It pays " + r + ". You deploy it and you tell me when it is reporting.\"");
                text.highlightInLastPara(Misc.getHighlightColor(), r);
            } else if (kind.equals("instrument")) {
                text.addPara("\"Our grant requires one spectrographic run on a violet dwarf,\" says the one with the most tired eyes. "
                    + "\"We cannot afford the trip ourselves. The package runs itself. You only have to hold position nearby while it works.\"");
                text.addPara("\"We can offer " + r + ".\"");
                text.highlightInLastPara(Misc.getHighlightColor(), r);
            } else if (kind.equals("vip")) {
                text.addPara("\"You. You have a ship. I need to go to a star system, immediately, and I am told it is lovely this time of year. "
                    + "Name your price. I will double it.\"");
                text.addPara("They settle on " + r + " after a few seconds of visible suffering.");
                text.highlightInLastPara(Misc.getHighlightColor(), r);
            } else if (kind.equals("tohere")) {
                text.addPara("\"I need 140 units of heavy machinery delivered to " + marketName + ",\" the trader says, tapping the manifest. "
                    + "\"It's urgent, and it pays.\"");
            } else if (kind.equals("nothing")) {
                text.addPara("\"Medical supplies, to the recipient's desk across the room. Quick job.\" They hold out a manifest. "
                    + "\"It pays " + r + ".\"");
                text.highlightInLastPara(Misc.getHighlightColor(), r);
            } else if (kind.equals("wrongperson")) {
                text.addPara("After a brief introduction, " + getHeOrShe() + " wastes no time in getting to the point.");
                text.addPara("\"I've got 140 units of machine parts that urgently need to be delivered to Corin Vale, at the next table over. "
                    + "We've had some disputes with the regular shipping company, and it's left us in the lurch. Should be a milk run for someone like you, though.\"");
                text.highlightInLastPara(Misc.getHighlightColor(), "140");
                text.addPara(Misc.ucFirst(getHeOrShe()) + " double-checks something on " + getHisOrHer() + " pad. \"The offer is " + r
                    + " credits, payable upon delivery within 90 days. You in?\"");
                text.highlightInLastPara(Misc.getHighlightColor(), r, "90");
            } else {
                text.addPara("\"My old freighter has been sitting in bay nine for years,\" the stranger says. \"I can't fly her anymore, and I can't sell her. "
                    + "I would rather she went to someone who will.\"");
            }
            boolean trader = kind.equals("wrongperson");
            options.addOption(kind.equals("freighter") ? "Take the freighter" : trader ? "Accept the delivery contract" : "Accept", "accept");
            options.addOption(trader ? "Decline the offer, explaining that you've got other plans" : "Decline", "decline");
        }

        private void leaveOption() {
            options.clearOptions();
            options.addOption("Leave", "leave");
        }

        @Override
        public void optionSelected(String optionText, Object optionData) {
            if (!(optionData instanceof String)) return;
            String id = (String) optionData;
            if (id.equals("decline")) {
                text.addPara("You decline. They shrug and turn back to the room.");
                leaveOption();
            } else if (id.equals("leave")) {
                if (accepted) finished = true;
                done = true;
            } else if (id.equals("accept")) {
                accepted = true;
                startCooldown();
                onAccept();
            } else if (id.equals("h_dotell")) {
                hijackPitch();
            } else if (id.equals("h_mine1") || id.equals("h_mine2")) {
                hijackFlee();
            } else if (id.equals("wp3")) {
                text.addPara("The trader looks at the same crate, then at you. \"Fine,\" they say, and for a moment there is no one behind their eyes. "
                    + "Then it all comes back, friendly and new. \"Your pay. Thank you for your trouble.\"");
                AddRemoveCommodity.addCreditsGainText(reward, text);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward);
                leaveOption();
            }
        }

        private void onAccept() {
            if (kind.equals("tohere")) {
                text.addPara("The trader glances down at the manifest and frowns. \"Hold on. It says here the destination is... here. This market.\" "
                    + "They look at you, at the floor, and back at the pad.");
                text.addPara("\"Well,\" they say. \"I wasn't expecting this, but thank you.\" They count out exactly " + credits(oddReward) + ".");
                text.highlightInLastPara(Misc.getHighlightColor(), credits(oddReward));
                AddRemoveCommodity.addCreditsGainText(oddReward, text);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(oddReward);
                leaveOption();
            } else if (kind.equals("nothing")) {
                text.addPara("You take the manifest. It lists the commodity at zero units. You walk it across the room anyway.");
                text.addPara("The recipient looks at the number, then at you, and smiles. \"Thank you,\" they say, as if it had been full. "
                    + "The credits arrive without any further discussion.");
                AddRemoveCommodity.addCreditsGainText(reward, text);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward);
                leaveOption();
            } else if (kind.equals("wrongperson")) {
                text.addPara("You carry the crate to the next table. Corin Vale looks at it, then up at you. \"I didn't order anything,\" he says. "
                    + "\"I placed no order.\" He pushes the crate gently back across the table, as if returning a borrowed plate.");
                options.clearOptions();
                options.addOption("Take it back to the trader", "wp3");
            } else if (kind.equals("freighter")) {
                giveFreighter();
                leaveOption();
            } else {
                startStarJob(kind, reward, person == null ? "a stranger" : person.getName().getFullName(), person == null ? null : person.getPortraitSprite());
                if (kind.equals("deaddrop")) {
                    text.addPara("The person nods once. \"The coordinates are on the pad. Don't open it again.\" "
                        + "When you look, there is a courier drone waiting in your bay, though you do not remember it arriving.");
                } else if (kind.equals("pilgrims")) {
                    text.addPara("\"Bless you,\" the eldest says. They file aboard in silence, one by one.");
                } else if (kind.equals("satellite")) {
                    text.addPara("\"The coordinates are on the pad,\" the person says, and lets the satellite crate be loaded without a signature.");
                } else if (kind.equals("instrument")) {
                    text.addPara("\"Wonderful,\" the researchers say, and begin to argue about who is allowed to carry the case.");
                } else {
                    text.addPara("\"Finally,\" the VIP says. \"Someone competent.\" They are aboard before you finish saying yes.");
                }
                leaveOption();
            }
        }

        private void giveFreighter() {
            FleetMemberAPI m = null;
            String[] hulls = {"mudskipper", "buffalo", "mule", "hound"};
            for (String h : hulls) {
                try {
                    m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, h + "_Hull");
                    if (m != null) break;
                } catch (Throwable t) { m = null; }
            }
            if (m == null) {
                text.addPara("The stranger is gone before you can finish the sentence, and the bay is empty.");
                return;
            }
            String name = Reflections.firstShipName();
            m.setShipName(name);
            try { com.fs.starfarer.api.impl.campaign.DModManager.addDMods(m, true, 2, new Random()); } catch (Throwable t) { /* ok without */ }
            m.getRepairTracker().setCR(0.5f);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
            text.addPara("The stranger presses a key into your hand and is gone before you can say anything more.");
            text.addPara("In bay nine you find an old freighter, scuffed and quiet. The name on her hull is \"" + name + "\".");
            text.addPara("You open her logs. They list " + Global.getSector().getPlayerPerson().getName().getFullName()
                + " as the captain, and the entries are in your own handwriting.");
        }

        @Override
        public boolean shouldRemoveEvent() {
            return finished;
        }
    }


    // ---------------------------------------------------------------- the drop point beside the Violet Dwarf

    private static final String DROP_ID = "rp_glitch_droppoint";

    /** The marker beside the star where the star jobs are completed. Made once, and found again after loading. */
    public static SectorEntityToken dropPoint() {
        try {
            PlanetAPI star = LightSpeaks.starEntity();
            if (star == null) return null;
            com.fs.starfarer.api.campaign.StarSystemAPI sys = star.getStarSystem();
            if (sys == null) return null;
            SectorEntityToken e = sys.getEntityById(DROP_ID);
            if (e != null) return e;
            e = sys.addCustomEntity(DROP_ID, null, com.fs.starfarer.api.impl.campaign.ids.Entities.MISSION_LOCATION, Factions.NEUTRAL);
            Misc.makeImportant(e, "$afl_glitch_drop");
            float r = roguePlanets.VioletSystemContent.freeOrbit(sys, star, star.getRadius() + 900f, 250f);
            e.setCircularOrbit(star, new Random().nextFloat() * 360f, r, r / 11f);
            return e;
        } catch (Throwable t) {
            Global.getLogger(GlitchJobs.class).warn("Could not make the drop point: " + t);
            return null;
        }
    }


    private static Object lastDropDialog = null;

    private static java.util.List<StarJobIntel> openJobs() {
        java.util.List<StarJobIntel> out = new ArrayList<StarJobIntel>();
        for (Object o : Global.getSector().getIntelManager().getIntel(StarJobIntel.class)) {
            StarJobIntel job = (StarJobIntel) o;
            if (!job.isEnded() && !job.isEnding() && !job.arrived()) out.add(job);
        }
        return out;
    }

    /** What the Target Location shows when the player interacts with it while a Violet Dwarf job is active. */
    public static class DropDialog implements com.fs.starfarer.api.campaign.InteractionDialogPlugin {
        private InteractionDialogAPI dialog;

        @Override
        public void init(InteractionDialogAPI d) {
            dialog = d;
            d.getTextPanel().clear();
            try {
                d.getVisualPanel().showImageVisual(new com.fs.starfarer.api.InteractionDialogImageVisual(
                    Global.getSettings().getSpriteName("illustrations", "survey"), 480, 300));
            } catch (Throwable t) { /* no picture */ }
            d.getTextPanel().addPara("Your nav officer reports that your fleet has arrived at the coordinates beside the star. The glare is steady, and nothing else is out here.");
            showOptions();
        }

        private void showOptions() {
            dialog.getOptionPanel().clearOptions();
            for (StarJobIntel job : openJobs()) dialog.getOptionPanel().addOption(job.optionLabel(), job);
            dialog.getOptionPanel().addOption("Leave", "leave");
            dialog.setOptionOnEscape("Leave", "leave");
        }

        @Override
        public void optionSelected(String optionText, Object optionData) {
            if (optionData instanceof StarJobIntel) {
                StarJobIntel job = (StarJobIntel) optionData;
                dialog.getTextPanel().addPara(optionText, Misc.getBasePlayerColor());
                job.complete(dialog.getTextPanel());
                showOptions();
            } else {
                dialog.dismiss();
            }
        }

        @Override public void optionMousedOver(String optionText, Object optionData) { }
        @Override public void advance(float amount) { }
        @Override public void backFromEngagement(com.fs.starfarer.api.combat.EngagementResultAPI battleResult) { }
        @Override public Object getContext() { return null; }
        @Override public java.util.Map<String, MemoryAPI> getMemoryMap() { return new java.util.HashMap<String, MemoryAPI>(); }
    }

    /** Takes the Target Location off the map once no Violet Dwarf job is waiting for it (never while a dialog is open). */
    private static void removeDropPointIfUnused() {
        try {
            if (!openJobs().isEmpty()) return;
            if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) return;
            PlanetAPI star = LightSpeaks.starEntity();
            if (star == null || star.getStarSystem() == null) return;
            SectorEntityToken e = star.getStarSystem().getEntityById(DROP_ID);
            if (e == null) return;
            Misc.makeUnimportant(e, "$afl_glitch_drop");
            star.getStarSystem().removeEntity(e);
        } catch (Throwable t) {
            Global.getLogger(GlitchJobs.class).warn("Could not remove the drop point: " + t);
        }
    }

    private static long lastJobTick = 0L;

    /** Called every frame from the story script (so the jobs never depend on the intel list being advanced): checks every active Violet job about once a second. */
    public static void tickJobs() {
        long now = System.currentTimeMillis();
        if (now - lastJobTick < 1000L) return;
        lastJobTick = now;
        try {
            removeDropPointIfUnused();
            java.util.List<?> jobs = Global.getSector().getIntelManager().getIntel(StarJobIntel.class);
            for (Object o : jobs) {
                StarJobIntel job = (StarJobIntel) o;
                if (job.isEnded() || job.isEnding()) continue;
                try { job.tickJob(); } catch (Throwable t) { Global.getLogger(GlitchJobs.class).warn("Violet job tick failed: " + t); }
            }
        } catch (Throwable t) {
            Global.getLogger(GlitchJobs.class).warn("Violet jobs tick failed: " + t);
        }
    }

    /**
     * Finished or abandoned Violet Dwarf jobs are "ending": they stay on the intel screen for a day, then go.
     * The job intel is not a game script, so nothing else counts that day down; this does (called every unpaused frame).
     */
    public static void advanceEndingJobs(float amount) {
        try {
            com.fs.starfarer.api.campaign.comm.IntelManagerAPI im = Global.getSector().getIntelManager();
            for (Object o : new ArrayList<Object>(im.getIntel(StarJobIntel.class))) {
                StarJobIntel job = (StarJobIntel) o;
                if (job.isEnded()) {
                    im.removeIntel(job);
                } else if (job.isEnding()) {
                    job.advance(amount); // only counts down the ending time while ending
                    if (job.isEnded()) im.removeIntel(job);
                }
            }
        } catch (Throwable t) {
            Global.getLogger(GlitchJobs.class).warn("Clearing finished Violet jobs failed: " + t);
        }
    }

    /** Called every frame (even with a dialog open): if the player clicked the Target Location, close its dialog and play the job's arrival. */
    public static void checkDropInteraction() {
        try {
            InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            if (d == null) { lastDropDialog = null; return; }
            if (d == lastDropDialog) return;
            lastDropDialog = d;
            SectorEntityToken t = d.getInteractionTarget();
            if (t == null || !DROP_ID.equals(t.getId())) return;
            if (!openJobs().isEmpty()) {
                DropDialog dd = new DropDialog();
                d.setPlugin(dd);
                dd.init(d);
            }
        } catch (Throwable t) {
            Global.getLogger(GlitchJobs.class).warn("Drop point check failed: " + t);
        }
    }

    /** True when the player is next to the drop point (or, if it could not be made, near the star). */
    public static boolean atDropPoint(CampaignFleetAPI p) {
        SectorEntityToken dp = dropPoint();
        if (dp == null) return LightSpeaks.playerAtStar();
        return p != null && p.getContainingLocation() == dp.getContainingLocation() && Misc.getDistance(p, dp) < 450f;
    }

    // ---------------------------------------------------------------- the star jobs

    /** Starts a job to the Violet Dwarf. */
    public static void startStarJob(String kind, int reward, String giver) { startStarJob(kind, reward, giver, null); }

    public static void startStarJob(String kind, int reward, String giver, String portrait) {
        dropPoint(); // make sure the marker exists
        StarJobIntel intel = new StarJobIntel(kind, reward, giver);
        intel.portrait = portrait;
        Global.getSector().getIntelManager().addIntel(intel);
    }

    public static class StarJobIntel extends BaseIntelPlugin {
        private final String kind;
        private final int reward;
        private final String giver;
        private final long started;
        private boolean shown;
        private boolean doppelSpawned;
        private boolean hailed;
        private boolean doppelDone;
        private CampaignFleetAPI copy;
        private float acc = 0f;

        private String portrait;
        boolean arrived() { return shown; }

        public StarJobIntel(String kind, int reward, String giver) {
            this.kind = kind;
            this.reward = reward;
            this.giver = giver;
            this.started = Global.getSector().getClock().getTimestamp();
        }

        private static final float DAYS_TO_COMPLETE = 120f;

        @Override
        protected String getName() {
            if (kind.equals("deaddrop")) return "Dead Drop";
            if (kind.equals("pilgrims")) return "Pilgrim Transport";
            if (kind.equals("satellite")) return "Satellite Deployment";
            if (kind.equals("instrument")) return "Instrument Package";
            return "VIP Transport";
        }

        private String systemName() {
            PlanetAPI star = LightSpeaks.starEntity();
            return star != null && star.getStarSystem() != null ? star.getStarSystem().getBaseName() : "Violet Dwarf";
        }

        private String shortTask() {
            if (kind.equals("deaddrop")) return "Deliver \"the light\" to the specified location in the " + systemName() + " system";
            if (kind.equals("pilgrims")) return "Carry the pilgrims to the " + systemName() + " system";
            if (kind.equals("satellite")) return "Deploy a passive satellite at the " + systemName() + " star";
            if (kind.equals("instrument")) return "Hold position near the " + systemName() + " star for one spectrographic run";
            return "Take the VIP to the " + systemName() + " system";
        }

        private String description() {
            if (kind.equals("deaddrop")) return "Deliver \"the light\" to the dead drop location at the specified coordinates in the " + systemName() + " system. The courier pad's display flickers.";
            if (kind.equals("pilgrims")) return "Carry the pilgrims to the " + systemName() + " system, so that they can see the light.";
            if (kind.equals("satellite")) return "Deploy a passive satellite in orbit around the " + systemName() + " star and report back.";
            if (kind.equals("instrument")) return "Hold position near the " + systemName() + " star while the researchers' instrument package completes one spectrographic run.";
            return "Take the VIP to the " + systemName() + " system as quickly as you can.";
        }

        private String giverRole() {
            if (kind.equals("deaddrop")) return "smuggler";
            if (kind.equals("pilgrims")) return "pilgrim";
            if (kind.equals("satellite")) return "client";
            if (kind.equals("instrument")) return "researcher";
            return "VIP";
        }

        private String giverFactionId() {
            if (kind.equals("deaddrop")) return Factions.PIRATES;
            if (kind.equals("pilgrims")) return Factions.LUDDIC_CHURCH;
            return Factions.INDEPENDENT;
        }

        private int daysLeft() {
            return Math.max(1, (int) Math.ceil(DAYS_TO_COMPLETE - Global.getSector().getClock().getElapsedDaysSince(started)));
        }

        @Override
        public boolean isImportant() { return !isEnding() && !isEnded(); }

        @Override
        public boolean hasImportantButton() { return false; }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color gray = Misc.getGrayColor();
            info.setBulletedListMode(BULLET);
            if (isEnding() || isEnded()) {
                info.addPara(shown ? "Completed" : "Abandoned", 3f, gray);
            } else if (mode == ListInfoMode.IN_DESC) {
                info.addPara("%s reward", 10f, Misc.getTextColor(), Misc.getHighlightColor(), credits(reward));
                info.addPara("%s days to complete", 0f, Misc.getTextColor(), Misc.getHighlightColor(), "" + daysLeft());
            } else {
                info.addPara(shortTask(), 3f, gray);
                info.addPara("%s days to complete", 0f, gray, Misc.getHighlightColor(), "" + daysLeft());
            }
            info.setBulletedListMode(null);
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            if ("completed".equals(getListInfoParam())) {
                info.addPara(getName() + " - Completed", getTitleColor(mode), 0f);
                return;
            }
            info.addPara(getName(), getTitleColor(mode), 0f);
            addBulletPoints(info, mode);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            com.fs.starfarer.api.campaign.FactionAPI f = Global.getSector().getFaction(giverFactionId());
            try {
                if (portrait != null && f != null) info.addImages(width, 128f, 10f, 10f, portrait, f.getCrest());
                else if (f != null) info.addImages(width, 128f, 10f, 10f, f.getCrest());
            } catch (Throwable t) { /* no pictures */ }
            if (f != null) {
                info.addPara("Mission given by " + giverRole() + " " + giver + ", affiliated with %s.", 10f, Misc.getTextColor(),
                    f.getBaseUIColor(), f.getDisplayNameWithArticle());
            } else {
                info.addPara("Mission given by " + giverRole() + " " + giver + ".", 10f);
            }
            info.addPara(description(), 10f);
            addBulletPoints(info, ListInfoMode.IN_DESC);
            if (!isEnding() && !isEnded()) {
                ButtonAPI b = info.addButton("Abandon", BUTTON_ABANDON, new Color(255, 120, 100), new Color(110, 25, 25),
                    width, 25f, 10f);
                b.setShortcut(Keyboard.KEY_U, true);
            }
        }

        private static final String BUTTON_ABANDON = "abandon";

        @Override
        protected float getBaseDaysAfterEnd() { return 1f; }

        @Override
        public void buttonPressConfirmed(Object buttonId, com.fs.starfarer.api.ui.IntelUIAPI ui) {
            if (BUTTON_ABANDON.equals(buttonId)) {
                dismissCopy();
                endAfterDelay();
                ui.updateUIForItem(this);
                return;
            }
            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public boolean doesButtonHaveConfirmDialog(Object buttonId) { return BUTTON_ABANDON.equals(buttonId); }

        @Override
        public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
            prompt.addPara("Abandon this job? There is no penalty.", 0f);
        }

        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            if (isEnding() || isEnded()) return null;
            SectorEntityToken dp = dropPoint();
            return dp != null ? dp : LightSpeaks.starEntity();
        }

        @Override
        public String getIcon() {
            try {
                if (kind.equals("deaddrop")) return Global.getSettings().getCommoditySpec("supplies").getIconName();
                if (kind.equals("pilgrims")) return Global.getSector().getFaction(Factions.LUDDIC_CHURCH).getCrest();
                if (kind.equals("satellite")) return Global.getSettings().getSpriteName("intel", "comm_sniffer");
                if (kind.equals("instrument")) return Global.getSettings().getSpriteName("intel", "remote_survey_data");
                if (portrait != null) return portrait;
            } catch (Throwable t) { /* fall back */ }
            return super.getIcon();
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);
            return tags;
        }

        @Override
        protected void advanceImpl(float amount) {
            super.advanceImpl(amount);
            if (isEnding() || isEnded()) return;
            acc += amount;
            if (acc < 1f) return;
            acc = 0f;
            try {
                tickJob();
            } catch (Throwable t) {
                Global.getLogger(GlitchJobs.class).warn("Glitched job tick failed: " + t);
            }
        }

        private void tickJob() {
            if (Global.getSector().getClock().getElapsedDaysSince(started) > 120f) {
                dismissCopy();
                endAfterDelay();
                return;
            }
            CampaignFleetAPI p = Global.getSector().getPlayerFleet();
            PlanetAPI star = LightSpeaks.starEntity();
            if (p == null || star == null) return;
            boolean inSystem = p.getContainingLocation() == star.getContainingLocation();

            if (kind.equals("deaddrop") && !doppelDone) tickCopy(p, star, inSystem);
        }

        // ---- the doppelganger near the drop (dead drop only)

        private void tickCopy(CampaignFleetAPI p, PlanetAPI star, boolean inSystem) {
            if (copy != null) {
                boolean gone = !copy.isAlive() || copy.getContainingLocation() != p.getContainingLocation();
                if (gone) {
                    dismissCopyOnly();
                    doppelSpawned = false; // comes again if the player returns
                    return;
                }
                if (!hailed && Misc.getDistance(copy, p) < 300f && AfterlightStory.isQuietMoment(0f)) hail();
                return;
            }
            if (!doppelSpawned && inSystem && Misc.getDistance(p, star) < 9000f) spawnCopy(p);
        }

        private void spawnCopy(CampaignFleetAPI player) {
            LocationAPI loc = player.getContainingLocation();
            if (loc == null) return;
            CampaignFleetAPI f = makeCopyFleet(player);
            if (f == null) return;
            double angle = new Random().nextDouble() * Math.PI * 2;
            float dist = 2500f;
            loc.addEntity(f);
            f.setLocation(player.getLocation().x + (float) Math.cos(angle) * dist,
                          player.getLocation().y + (float) Math.sin(angle) * dist);
            f.addAssignment(FleetAssignment.INTERCEPT, player, 100f, "closing in on you");
            copy = f;
            doppelSpawned = true;
            hailed = false;
        }

        private void hail() {
            hailed = true;
            String who = Global.getSector().getPlayerPerson().getName().getFullName();
            StoryDialogue d = new StoryDialogue("h", null, false);
            d.node("h").person(Global.getSector().getPlayerPerson())
                .voice(who, "Greetings, star-farer. Bad news: you possess something my superiors desire, and now you must not surrender it to anyone.")
                .voice(who, "Now, is this going to be easy or hard? Think, now. But don't take too long, please.")
                .voice(who, "The demand is outright obedience, and any law-abiding faction would be embarrassed to condemn you for refusing.");
            d.node("h").choice("Accept, close comms and power down weapons.", "g");
            d.node("h").choice("Agree not to hand over the coordinates.", "g");
            d.node("g").say("The channel closes. A moment later the fleet is simply gone from your scope, as if it had never been there.");
            d.onEnd(new Runnable() {
                public void run() {
                    dismissCopy();
                }
            });
            if (!d.show()) hailed = false;
        }

        private void dismissCopyOnly() {
            if (copy != null) {
                try { Misc.fadeAndExpire(copy); } catch (Throwable t) { /* already gone */ }
                copy = null;
            }
        }

        private void dismissCopy() {
            dismissCopyOnly();
            doppelDone = true;
        }

        // ---- arriving

        String optionLabel() {
            if (kind.equals("deaddrop")) return "Drop it off in a stable orbit";
            if (kind.equals("pilgrims")) return "Open the viewports for the pilgrims";
            if (kind.equals("satellite")) return "Release the satellite";
            if (kind.equals("instrument")) return "Start the instrument package";
            return "Tell the VIP that you have arrived";
        }

        private String[] sceneLines() {
            if (kind.equals("deaddrop")) return new String[] {
                "The bay doors open, and the courier drone extends its arms. Then, gently, its manipulators release.",
                "Nothing visibly leaves the drone. The light of the star dims for a moment, then returns.",
                "It is now done. Whatever 'it' is."};
            if (kind.equals("pilgrims")) return new String[] {
                "The pilgrims gather at the forward viewport. The star is small and violet and very still. For a long time none of them speak.",
                "One of them says, quietly, \"It looked at me.\"",
                "The eldest presses the credits into your hand. The pilgrims file back to the passenger bay to wait for the next port, and for the rest of the trip you can hear them through the bulkhead, each describing the same light in the same words."};
            if (kind.equals("satellite")) return new String[] {
                "You release the satellite. It unfolds, finds the star, and begins to report.",
                "A message arrives from your contact, almost at once.",
                "\"The readings are coming back,\" they say. \"They are about you.\" They do not explain, and the channel closes."};
            if (kind.equals("instrument")) return new String[] {
                "The instrument package runs for an hour while you hold position. When it finishes, the lead researcher sends a short note.",
                "\"The spectrum is ordinary, except for a faint repeating pattern. Our software matched it against known signals. It matched your transponder.\""};
            return new String[] {
                "The VIP has not stopped complaining since you left the market. At the first sight of the star they fall silent.",
                "\"Oh,\" they say. \"It is exactly as I was told. Lovely this time of year.\"",
                "You check the credit transfer. It has cleared, with a generous tip attached.",
                "You look up. You are standing in your quarters, talking to nobody."};
        }

        /** Plays this job's scene into the dialog, pays out in the game's own style and posts the "Completed" card. */
        void complete(com.fs.starfarer.api.campaign.TextPanelAPI t) {
            shown = true;
            for (String line : sceneLines()) t.addPara(line);
            AddRemoveCommodity.addCreditsGainText(reward, t);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward);
            dismissCopy();
            sendUpdate("completed", t);
            endAfterDelay();
        }
    }

    // ---------------------------------------------------------------- a copy of the player's fleet (for the dead drop)

    private static boolean isAfterlight(FleetMemberAPI m) {
        return m.getHullSpec().getHullId().startsWith(roguePlanets.AfterlightCaptainScript.HULL_PREFIX);
    }

    /** Builds a fleet that looks like the player's, fast and able to see the player from anywhere. */
    private static CampaignFleetAPI makeCopyFleet(CampaignFleetAPI player) {
        PersonAPI me = Global.getSector().getPlayerPerson();
        PersonAPI person = Global.getFactory().createPerson();
        person.setName(me.getName());
        person.setPortraitSprite(me.getPortraitSprite());
        person.setFaction(Factions.NEUTRAL);
        person.setRankId(Ranks.SPACE_CAPTAIN);
        person.setPostId(Ranks.POST_FLEET_COMMANDER);

        CampaignFleetAPI f = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, me.getName().getFullName() + "'s Fleet", true);
        f.setNoFactionInName(true);

        List<FleetMemberAPI> mine = new ArrayList<FleetMemberAPI>();
        float total = 0f;
        FleetMemberAPI flag = player.getFlagship();
        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            if (m.isFighterWing()) continue;
            mine.add(m);
            total += m.getDeploymentPointsCost();
        }
        Collections.sort(mine, new Comparator<FleetMemberAPI>() {
            public int compare(FleetMemberAPI a, FleetMemberAPI b) {
                return Float.compare(b.getDeploymentPointsCost(), a.getDeploymentPointsCost());
            }
        });
        float budget = total * 0.75f;
        float used = 0f;
        FleetMemberAPI copyFlag = null;
        List<FleetMemberAPI> order = new ArrayList<FleetMemberAPI>();
        if (flag != null && !flag.isFighterWing()) order.add(flag);
        for (FleetMemberAPI m : mine) if (m != flag && isAfterlight(m)) order.add(m);
        for (FleetMemberAPI m : mine) if (m != flag && !isAfterlight(m)) order.add(m);
        for (FleetMemberAPI m : order) {
            float cost = m.getDeploymentPointsCost();
            if (!f.getFleetData().getMembersListCopy().isEmpty() && !isAfterlight(m) && used + cost > budget) continue;
            try {
                FleetMemberAPI c = Global.getFactory().createFleetMember(FleetMemberType.SHIP, m.getVariant().clone());
                c.setShipName(m.getShipName());
                f.getFleetData().addFleetMember(c);
                used += cost;
                if (copyFlag == null) copyFlag = c;
            } catch (Throwable t) {
                Global.getLogger(GlitchJobs.class).warn("Glitch copy: could not copy " + m.getShipName() + ": " + t);
            }
        }
        if (copyFlag == null) return null;
        f.setCommander(person);
        copyFlag.setCaptain(person);
        f.getFleetData().setFlagship(copyFlag);
        for (FleetMemberAPI c : f.getFleetData().getMembersListCopy()) c.getRepairTracker().setCR(0.7f);

        MemoryAPI fm = f.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fm.set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
        fm.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        f.setTransponderOn(true);
        // fast enough to catch the player (burn 25), and able to see the player from anywhere in the system
        f.getStats().getFleetwideMaxBurnMod().modifyFlat("rp_glitch_burn", 15f);
        f.getStats().getSensorRangeMod().modifyFlat("rp_glitch_sensor", 100000f);
        f.getFleetData().setSyncNeeded();
        f.getFleetData().syncIfNeeded();
        return f;
    }
}
