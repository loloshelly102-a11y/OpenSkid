package openskid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.florianmichael.viamcp.ViaMCP;
import openskid.command.CommandManager;
import openskid.command.commands.*;
import openskid.config.Config;
import openskid.event.EventManager;
import openskid.font.FontManagers;
import openskid.management.*;
import openskid.module.Module;
import openskid.module.ModuleManager;
import openskid.module.modules.HUD;
import openskid.module.modules.Hotbar;
import openskid.module.modules.*;
import openskid.property.Property;
import openskid.property.PropertyManager;
import openskid.ui.impl.clickgui.normal.ClickGuiScreen;
import openskid.util.font.FontManager;
import org.lwjgl.opengl.Display;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public class OpenSkid {
    public static String clientName = "&7[&bOpenSkid&7]&r ";
    public static String version;
    private static final String MC_VERSION = "1.8.9";
    public static RotationManager rotationManager;
    public static FloatManager floatManager;
    public static LagCore lagCore;
    public static BlinkManager blinkManager;
    public static DelayManager delayManager;
    public static LagManager lagManager;
    public static PlayerStateManager playerStateManager;
    public static FriendManager friendManager;
    public static TargetManager targetManager;
    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static NotificationManager notificationManager;
    public static CommandManager commandManager;
    private static boolean anticheatRegistered;
    public static FontManagers fontManagers;

    public OpenSkid() {
        this.init();
    }

    public void init() {
        rotationManager = new RotationManager();
        floatManager = new FloatManager();
        lagCore = new LagCore();
        blinkManager = new BlinkManager();
        delayManager = new DelayManager();
        lagManager = new LagManager();
        playerStateManager = new PlayerStateManager();
        friendManager = new FriendManager();
        targetManager = new TargetManager();
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();
        notificationManager = new NotificationManager();
        commandManager = new CommandManager();
        fontManagers = new FontManagers();
        fontManagers.load();
        EventManager.register(rotationManager);
        EventManager.register(floatManager);
        EventManager.register(blinkManager);
        EventManager.register(delayManager);
        EventManager.register(lagManager);
        EventManager.register(lagCore);
        EventManager.register(moduleManager);
        EventManager.register(commandManager);
        registerClientAnticheat();
        moduleManager.modules.put(AimAssist.class, new AimAssist());
        moduleManager.modules.put(AntiAFK.class, new AntiAFK());
        moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
        moduleManager.modules.put(AntiFireball.class, new AntiFireball());
        moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
        moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
        moduleManager.modules.put(AntiVoid.class, new AntiVoid());
        moduleManager.modules.put(AutoClicker.class, new AutoClicker());
        moduleManager.modules.put(AutoAnduril.class, new AutoAnduril());
        moduleManager.modules.put(KnockbackDelay.class, new KnockbackDelay());
        moduleManager.modules.put(TargetESP.class, new TargetESP());
        moduleManager.modules.put(AutoHeal.class, new AutoHeal());
        moduleManager.modules.put(AutoTool.class, new AutoTool());
        moduleManager.modules.put(AutoSwap.class, new AutoSwap());
        moduleManager.modules.put(AutoArmor.class, new AutoArmor());
        moduleManager.modules.put(AutoPot.class, new AutoPot());
        moduleManager.modules.put(AutoSoup.class, new AutoSoup());
        moduleManager.modules.put(AutoWeapon.class, new AutoWeapon());
        moduleManager.modules.put(ArmorBreaker.class, new ArmorBreaker());
        moduleManager.modules.put(BedNuker.class, new BedNuker());
        moduleManager.modules.put(BedESP.class, new BedESP());
        moduleManager.modules.put(BedTracker.class, new BedTracker());
        moduleManager.modules.put(Blink.class, new Blink());
        moduleManager.modules.put(BackTrack.class, new BackTrack());
        moduleManager.modules.put(Hitflick.class, new Hitflick());
        moduleManager.modules.put(AutoHeadHitter.class, new AutoHeadHitter());
        moduleManager.modules.put(FPScounter.class, new FPScounter());
        moduleManager.modules.put(Chams.class, new Chams());
        moduleManager.modules.put(WaterMark.class, new WaterMark());
        moduleManager.modules.put(ChestESP.class, new ChestESP());
        moduleManager.modules.put(ClickGUIModule.class, new ClickGUIModule());
        moduleManager.modules.put(ChestStealer.class, new ChestStealer());
        moduleManager.modules.put(ESP.class, new ESP());
        moduleManager.modules.put(FastPlace.class, new FastPlace());
        moduleManager.modules.put(ServerLag.class, new ServerLag());
        moduleManager.modules.put(Fly.class, new Fly());
        moduleManager.modules.put(FakeLag.class, new FakeLag());
        moduleManager.modules.put(FullBright.class, new FullBright());
        moduleManager.modules.put(GhostHand.class, new GhostHand());
        moduleManager.modules.put(HitSelect.class, new HitSelect());
        moduleManager.modules.put(JumpReset.class, new JumpReset());
        moduleManager.modules.put(AutoHypixel.class, new AutoHypixel());
        moduleManager.modules.put(HUD.class, new HUD());
        moduleManager.modules.put(Notifications.class, new Notifications());
        moduleManager.modules.put(Hotbar.class, new Hotbar());
        moduleManager.modules.put(MoreKB.class, new MoreKB());
        moduleManager.modules.put(Indicators.class, new Indicators());
        moduleManager.modules.put(InventoryClicker.class, new InventoryClicker());
        moduleManager.modules.put(InvManager.class, new InvManager());
        moduleManager.modules.put(InvWalk.class, new InvWalk());
        moduleManager.modules.put(Criticals.class, new Criticals());
        moduleManager.modules.put(FastBow.class, new FastBow());
        moduleManager.modules.put(BlockHit.class, new BlockHit());
        moduleManager.modules.put(AutoBlock.class, new AutoBlock());
        moduleManager.modules.put(ThrowAura.class, new ThrowAura());
        moduleManager.modules.put(ESP2D.class, new ESP2D());
        moduleManager.modules.put(ClientSpoofer.class, new ClientSpoofer());
        moduleManager.modules.put(ModSpoofer.class, new ModSpoofer());
        moduleManager.modules.put(ExploitFixer.class, new ExploitFixer());
        moduleManager.modules.put(AntiFalseFlag.class, new AntiFalseFlag());
        moduleManager.modules.put(GhostBlock.class, new GhostBlock());
        moduleManager.modules.put(PingSpoof.class, new PingSpoof());
        moduleManager.modules.put(ItemESP.class, new ItemESP());
        moduleManager.modules.put(Jesus.class, new Jesus());
        moduleManager.modules.put(Disabler.class, new Disabler());
        moduleManager.modules.put(Displace.class, new Displace());
        moduleManager.modules.put(KeepSprint.class, new KeepSprint());
        moduleManager.modules.put(FlagDetector.class, new FlagDetector());
        moduleManager.modules.put(HitBox.class, new HitBox());
        moduleManager.modules.put(KillAura.class, new KillAura());
        moduleManager.modules.put(ForwardTrack.class, new ForwardTrack());
        moduleManager.modules.put(KeepRange.class, new KeepRange());
        moduleManager.modules.put(SmartBlinker.class, new SmartBlinker());
        moduleManager.modules.put(LegitReach.class, new LegitReach());
        moduleManager.modules.put(ProjectileAimBot.class, new ProjectileAimBot());
        moduleManager.modules.put(LagRange.class, new LagRange());
        moduleManager.modules.put(LightningTracker.class, new LightningTracker());
        moduleManager.modules.put(StaffDetector.class, new StaffDetector());
        moduleManager.modules.put(MurderDetector.class, new MurderDetector());
        moduleManager.modules.put(BedProximityAlert.class, new BedProximityAlert());
        moduleManager.modules.put(CheatDetector.class, new CheatDetector());
        moduleManager.modules.put(LongJump.class, new LongJump());
        moduleManager.modules.put(MCF.class, new MCF());
        moduleManager.modules.put(Ambience.class, new Ambience());
        moduleManager.modules.put(ChestAura.class, new ChestAura());
        moduleManager.modules.put(NameTags.class, new NameTags());
        moduleManager.modules.put(NickHider.class, new NickHider());
        moduleManager.modules.put(NoFall.class, new NoFall());
        moduleManager.modules.put(Stasis.class, new Stasis());
        moduleManager.modules.put(NoHitDelay.class, new NoHitDelay());
        moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
        moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
        moduleManager.modules.put(NoRotate.class, new NoRotate());
        moduleManager.modules.put(BlockOverlay.class, new BlockOverlay());
        moduleManager.modules.put(MouseRawInput.class, new MouseRawInput());
        moduleManager.modules.put(Piercing.class, new Piercing());
        moduleManager.modules.put(BedwarUtils.class, new BedwarUtils());
        moduleManager.modules.put(NoSlow.class, new NoSlow());
        moduleManager.modules.put(AutoAuth.class, new AutoAuth());
        moduleManager.modules.put(Capes.class, new Capes());
        moduleManager.modules.put(MoveFix.class, new MoveFix());
        moduleManager.modules.put(ClickAssits.class, new ClickAssits());
        moduleManager.modules.put(Timer.class, new Timer());
        moduleManager.modules.put(BreakProgress.class , new BreakProgress());
        moduleManager.modules.put(SprintReset.class, new SprintReset());
        moduleManager.modules.put(Radar.class, new Radar());
        moduleManager.modules.put(Reach.class, new Reach());
        moduleManager.modules.put(RenderFixes.class, new RenderFixes());
        moduleManager.modules.put(EntityCulling.class, new EntityCulling());
        moduleManager.modules.put(Refill.class, new Refill());
        moduleManager.modules.put(SafeWalk.class, new SafeWalk());
        moduleManager.modules.put(DynamicIsland.class, new DynamicIsland());
        moduleManager.modules.put(Scaffold.class, new Scaffold());
        moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
        moduleManager.modules.put(AntiBot.class, new AntiBot());
        moduleManager.modules.put(AutoBedDef.class, new AutoBedDef());
        moduleManager.modules.put(BedDefender.class, new BedDefender());
        moduleManager.modules.put(TickBase.class, new TickBase());
        moduleManager.modules.put(Statistics.class, new Statistics());
        moduleManager.modules.put(FreeLook.class, new FreeLook());
        moduleManager.modules.put(ItemPhysics.class, new ItemPhysics());
        moduleManager.modules.put(Spammer.class, new Spammer());
        moduleManager.modules.put(Speed.class, new Speed());
        moduleManager.modules.put(SpeedMine.class, new SpeedMine());
        moduleManager.modules.put(Sprint.class, new Sprint());
        moduleManager.modules.put(TargetHUD.class, new TargetHUD());
        moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
        moduleManager.modules.put(Tracers.class, new Tracers());
        moduleManager.modules.put(WaterMark2.class, new WaterMark2());
        moduleManager.modules.put(TimerRange.class, new TimerRange());
        moduleManager.modules.put(Trajectories.class, new Trajectories());
        moduleManager.modules.put(Velocity.class, new Velocity());
        moduleManager.modules.put(ViewClip.class, new ViewClip());
        moduleManager.modules.put(Wtap.class, new Wtap());
        moduleManager.modules.put(Xray.class, new Xray());
        moduleManager.modules.put(TeamHealthDisplay.class, new TeamHealthDisplay());
        moduleManager.modules.put(Animations.class, new Animations());
        moduleManager.modules.put(AutoGapple.class, new AutoGapple());
        moduleManager.modules.put(HitParticleEffects.class, new HitParticleEffects());
        moduleManager.modules.put(KillEffect.class, new KillEffect());
        moduleManager.modules.put(KillSounds.class, new KillSounds());
        moduleManager.modules.put(BridgeAssist.class, new BridgeAssist());
        moduleManager.modules.put(Clutch.class, new Clutch());
        moduleManager.modules.put(WaterClutch.class, new WaterClutch());
        moduleManager.modules.put(PearlSaver.class, new PearlSaver());
        moduleManager.modules.put(NoWeb.class, new NoWeb());
        moduleManager.modules.put(Parkour.class, new Parkour());
        moduleManager.modules.put(LadderClutch.class, new LadderClutch());
        moduleManager.modules.put(SmartClicking.class, new SmartClicking());
        moduleManager.modules.put(Phase.class, new Phase());
        moduleManager.modules.put(Step.class, new Step());
        moduleManager.modules.put(Freecam.class, new Freecam());
        moduleManager.modules.put(Spider.class, new Spider());
        moduleManager.modules.put(Tower.class, new Tower());
        moduleManager.modules.put(BedPlates.class, new BedPlates());
        moduleManager.modules.put(FKCounter.class, new FKCounter());
        moduleManager.modules.put(ClosestPlayerHUD.class, new ClosestPlayerHUD());
        moduleManager.modules.put(LeapModeHUD.class, new LeapModeHUD());
        moduleManager.modules.put(MegaWallsDetector.class, new MegaWallsDetector());
        moduleManager.modules.put(FireBallPredict.class, new FireBallPredict());
        moduleManager.modules.put(DamageTags.class, new DamageTags());
        moduleManager.modules.put(TNTTimer.class, new TNTTimer());
        moduleManager.modules.put(PotionHUD.class, new PotionHUD());
        moduleManager.modules.put(AutoPlay.class, new AutoPlay());
        moduleManager.modules.put(AutoGG.class, new AutoGG());
        moduleManager.modules.put(AutoReconnect.class, new AutoReconnect());
        moduleManager.modules.put(AutoRespawn.class, new AutoRespawn());
        moduleManager.modules.put(BurstClicker.class, new BurstClicker());
        moduleManager.modules.put(Panic.class, new Panic());
        moduleManager.modules.put(KillSults.class, new KillSults());
        moduleManager.modules.put(KillMessage.class, new KillMessage());
        moduleManager.modules.put(ViewPackets.class, new ViewPackets());
        moduleManager.modules.put(AutoBuy.class, new AutoBuy());
        moduleManager.modules.put(PartyDetector.class, new PartyDetector());
        moduleManager.modules.put(PlayerList.class, new PlayerList());
        moduleManager.modules.put(AutoRequeue.class, new AutoRequeue());
        moduleManager.modules.put(AutoWho.class, new AutoWho());
        moduleManager.modules.put(BedWars.class, new BedWars());
        moduleManager.modules.put(BridgeInfo.class, new BridgeInfo());
        moduleManager.modules.put(DuelsStats.class, new DuelsStats());
        moduleManager.modules.put(MurderMystery.class, new MurderMystery());
        moduleManager.modules.put(SumoFences.class, new SumoFences());
        moduleManager.modules.put(AutoRod.class, new AutoRod());
        moduleManager.modules.put(Nuker.class, new Nuker());
        moduleManager.modules.put(AutoPlace.class, new AutoPlace());
        moduleManager.modules.put(LegitScaffold.class, new LegitScaffold());
        moduleManager.modules.put(BlockLadder.class, new BlockLadder());
        moduleManager.modules.put(AutoBed.class, new AutoBed());
        moduleManager.modules.put(NoClickDelay.class, new NoClickDelay());
        moduleManager.modules.put(Arrows.class, new Arrows());
        moduleManager.modules.put(BlocksESP.class, new BlocksESP());
        moduleManager.modules.put(MobESP.class, new MobESP());
        moduleManager.modules.put(ItemTags.class, new ItemTags());
        moduleManager.modules.put(FallIndicator.class, new FallIndicator());
        moduleManager.modules.put(InventoryHUD.class, new InventoryHUD());
        moduleManager.modules.put(KeyStrokes.class, new KeyStrokes());
        moduleManager.modules.put(SkyWars.class, new SkyWars());
        moduleManager.modules.put(WoolWars.class, new WoolWars());
        moduleManager.modules.put(SpeedBuilders.class, new SpeedBuilders());
        moduleManager.modules.put(SkywarsAlerts.class, new SkywarsAlerts());
        moduleManager.modules.put(ThePitUtils.class, new ThePitUtils());
        commandManager.commands.add(new AltsCommand());
        commandManager.commands.add(new BindCommand());
        commandManager.commands.add(new ClickGuiCommand());
        commandManager.commands.add(new ConfigCommand());
        commandManager.commands.add(new DenickCommand());
        commandManager.commands.add(new FriendCommand());
        commandManager.commands.add(new HelpCommand());
        commandManager.commands.add(new HideCommand());
        commandManager.commands.add(new IgnCommand());
        commandManager.commands.add(new ItemCommand());
        commandManager.commands.add(new ListCommand());
        commandManager.commands.add(new ModuleCommand());
        commandManager.commands.add(new OnlineConfigCommand());
        commandManager.commands.add(new PlayerCommand());
        commandManager.commands.add(new SetupCommand());
        commandManager.commands.add(new ShowCommand());
        commandManager.commands.add(new ScriptCommand());
        commandManager.commands.add(new TargetCommand());
        commandManager.commands.add(new ToggleCommand());
        commandManager.commands.add(new UserConfigCommand());
        commandManager.commands.add(new VclipCommand());
        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();
            for (final Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                final Object obj;
                try {
                    obj = field.get(module);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (obj instanceof Property<?>) {
                    ((Property<?>) obj).setOwner(module);
                    properties.add((Property<?>) obj);
                }
            }
            propertyManager.properties.put(module, properties);
            EventManager.register(module);
        }
        Config config = new Config("default", true);
        if (config.file.exists()) {
            config.load();
        }
        if (friendManager.file.exists()) {
            friendManager.load();
        }
        if (targetManager.file.exists()) {
            targetManager.load();
        }
        try {
            openskid.altmanager.AltStore.load();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            openskid.script.ScriptManager.getInstance().loadAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
        FontManager.initializeFonts();
        ClickGuiScreen.getInstance();

        Runtime.getRuntime().addShutdownHook(new Thread(config::save));

        me.ksyz.accountmanager.AccountManager.init();
        ViaMCP.create();

        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(OpenSkid.class.getResourceAsStream("/version.json")), StandardCharsets.UTF_8)) {
            JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
            version = modInfo.get("version").getAsString();
        } catch (Exception e) {
            version = "dev";
        }
        updateDisplayTitle();

    }

    public static String getDisplayTitle() {
        String versionText = version == null || version.isEmpty() ? "dev" : version;
        return "OpenSkid (Main) - " + versionText + " | MC " + MC_VERSION;
    }

    public static void updateDisplayTitle() {
        if (Display.isCreated()) {
            Display.setTitle(getDisplayTitle());
        }
    }

    private void registerClientAnticheat() {
        if (anticheatRegistered) {
            return;
        }

        EventManager.register(new openskid.anticheat.flag());
        EventManager.register(new openskid.anticheat.AutoBlock());
        EventManager.register(new openskid.anticheat.Noslow());
        EventManager.register(new openskid.anticheat.KillAura());
        EventManager.register(new openskid.anticheat.Scaffold());
        anticheatRegistered = true;
    }
}
