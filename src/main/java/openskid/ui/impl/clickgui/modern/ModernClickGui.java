package openskid.ui.impl.clickgui.modern;

import openskid.OpenSkid;
import openskid.module.Module;
import openskid.module.modules.*;
import openskid.module.modules.Timer;
import openskid.util.RenderUtil;
import openskid.util.font.FontManager;
import openskid.util.shader.BlurUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ModernClickGui extends GuiScreen {
    private static final double FRICTION = 0.85;
    private static final double SNAP_STRENGTH = 0.15;
    private static final long ANIMATION_DURATION = 250L;
    private static ModernClickGui instance;
    private final ArrayList<Frame> frames;
    private Frame draggingComponent = null;
    private int scrollY = 0;
    private int targetScrollY = 0;
    private double velocity = 0;
    private boolean isClosing = false;
    private long openTime = 0L;
    private long lastFrameTime;
    private String searchText = "";
    private boolean searchFocused = false;
    private long lastSearchBlink = 0L;
    private boolean searchCursorVisible = true;
    private static final int SEARCH_W = 200;
    private static final int SEARCH_H = 16;

    public ModernClickGui() {
        this.frames = new ArrayList<>();

        List<Module> combatModules = Arrays.asList(
                OpenSkid.moduleManager.getModule(AimAssist.class),
                OpenSkid.moduleManager.getModule(MoveFix.class),
                OpenSkid.moduleManager.getModule(AutoClicker.class),
                OpenSkid.moduleManager.getModule(BurstClicker.class),
                OpenSkid.moduleManager.getModule(KillAura.class),
                OpenSkid.moduleManager.getModule(Wtap.class),
                OpenSkid.moduleManager.getModule(Velocity.class),
                OpenSkid.moduleManager.getModule(ServerLag.class),
                OpenSkid.moduleManager.getModule(Reach.class),
                OpenSkid.moduleManager.getModule(TargetStrafe.class),
                OpenSkid.moduleManager.getModule(NoHitDelay.class),
                OpenSkid.moduleManager.getModule(AntiFireball.class),
                OpenSkid.moduleManager.getModule(KnockbackDelay.class),
                OpenSkid.moduleManager.getModule(LagRange.class),
                OpenSkid.moduleManager.getModule(HitBox.class),
                OpenSkid.moduleManager.getModule(MoreKB.class),
                OpenSkid.moduleManager.getModule(Refill.class),
                OpenSkid.moduleManager.getModule(HitSelect.class),
                OpenSkid.moduleManager.getModule(JumpReset.class),
                OpenSkid.moduleManager.getModule(BackTrack.class),
                OpenSkid.moduleManager.getModule(Hitflick.class),
                OpenSkid.moduleManager.getModule(TimerRange.class),
                OpenSkid.moduleManager.getModule(ClickAssits.class),
                OpenSkid.moduleManager.getModule(Criticals.class),
                OpenSkid.moduleManager.getModule(BlockHit.class),
                OpenSkid.moduleManager.getModule(AutoBlock.class),
                OpenSkid.moduleManager.getModule(SprintReset.class),
                OpenSkid.moduleManager.getModule(Displace.class),
                OpenSkid.moduleManager.getModule(Piercing.class),
                OpenSkid.moduleManager.getModule(Stasis.class),
                OpenSkid.moduleManager.getModule(TickBase.class),
                OpenSkid.moduleManager.getModule(SmartClicking.class),
                OpenSkid.moduleManager.getModule(AutoRod.class),
                OpenSkid.moduleManager.getModule(ForwardTrack.class),
                OpenSkid.moduleManager.getModule(KeepRange.class),
                OpenSkid.moduleManager.getModule(SmartBlinker.class),
                OpenSkid.moduleManager.getModule(LegitReach.class),
                OpenSkid.moduleManager.getModule(ProjectileAimBot.class),
                OpenSkid.moduleManager.getModule(ArmorBreaker.class)
        );

        List<Module> movementModules = Arrays.asList(
                OpenSkid.moduleManager.getModule(AntiAFK.class),
                OpenSkid.moduleManager.getModule(Fly.class),
                OpenSkid.moduleManager.getModule(FastBow.class),
                OpenSkid.moduleManager.getModule(Timer.class),
                OpenSkid.moduleManager.getModule(Speed.class),
                OpenSkid.moduleManager.getModule(LongJump.class),
                OpenSkid.moduleManager.getModule(Sprint.class),
                OpenSkid.moduleManager.getModule(SafeWalk.class),
                OpenSkid.moduleManager.getModule(Jesus.class),
                OpenSkid.moduleManager.getModule(Blink.class),
                OpenSkid.moduleManager.getModule(NoFall.class),
                OpenSkid.moduleManager.getModule(NoSlow.class),
                OpenSkid.moduleManager.getModule(KeepSprint.class),
                OpenSkid.moduleManager.getModule(NoJumpDelay.class),
                OpenSkid.moduleManager.getModule(AntiVoid.class),
                OpenSkid.moduleManager.getModule(BridgeAssist.class),
                OpenSkid.moduleManager.getModule(Clutch.class),
                OpenSkid.moduleManager.getModule(WaterClutch.class),
                OpenSkid.moduleManager.getModule(PearlSaver.class),
                OpenSkid.moduleManager.getModule(NoWeb.class),
                OpenSkid.moduleManager.getModule(Parkour.class),
                OpenSkid.moduleManager.getModule(LadderClutch.class),
                OpenSkid.moduleManager.getModule(Phase.class),
                OpenSkid.moduleManager.getModule(Step.class),
                OpenSkid.moduleManager.getModule(Freecam.class),
                OpenSkid.moduleManager.getModule(Spider.class),
                OpenSkid.moduleManager.getModule(Tower.class),
                OpenSkid.moduleManager.getModule(NoClickDelay.class)
        );

        List<Module> renderModules = Arrays.asList(
                OpenSkid.moduleManager.getModule(ESP.class),
                OpenSkid.moduleManager.getModule(Chams.class),
                OpenSkid.moduleManager.getModule(FullBright.class),
                OpenSkid.moduleManager.getModule(BlockOverlay.class),
                OpenSkid.moduleManager.getModule(Tracers.class),
                OpenSkid.moduleManager.getModule(NameTags.class),
                OpenSkid.moduleManager.getModule(Xray.class),
                OpenSkid.moduleManager.getModule(TargetESP.class),
                OpenSkid.moduleManager.getModule(TargetHUD.class),
                OpenSkid.moduleManager.getModule(Indicators.class),
                OpenSkid.moduleManager.getModule(BedESP.class),
                OpenSkid.moduleManager.getModule(ItemESP.class),
                OpenSkid.moduleManager.getModule(BreakProgress.class),
                OpenSkid.moduleManager.getModule(ViewClip.class),
                OpenSkid.moduleManager.getModule(NoHurtCam.class),
                OpenSkid.moduleManager.getModule(HUD.class),
                OpenSkid.moduleManager.getModule(Notifications.class),
                OpenSkid.moduleManager.getModule(ChestESP.class),
                OpenSkid.moduleManager.getModule(Trajectories.class),
                OpenSkid.moduleManager.getModule(Radar.class),
                OpenSkid.moduleManager.getModule(FPScounter.class),
                OpenSkid.moduleManager.getModule(WaterMark.class),
                OpenSkid.moduleManager.getModule(WaterMark2.class),
                OpenSkid.moduleManager.getModule(HitParticleEffects.class),
                OpenSkid.moduleManager.getModule(KillEffect.class),
                OpenSkid.moduleManager.getModule(KillSounds.class),
                OpenSkid.moduleManager.getModule(DynamicIsland.class),
                OpenSkid.moduleManager.getModule(ESP2D.class),
                OpenSkid.moduleManager.getModule(ClickGUIModule.class),
                OpenSkid.moduleManager.getModule(TeamHealthDisplay.class),
                OpenSkid.moduleManager.getModule(Statistics.class),
                OpenSkid.moduleManager.getModule(Animations.class),
                OpenSkid.moduleManager.getModule(Hotbar.class),
                OpenSkid.moduleManager.getModule(Capes.class),
                OpenSkid.moduleManager.getModule(Ambience.class),
                OpenSkid.moduleManager.getModule(RenderFixes.class),
                OpenSkid.moduleManager.getModule(FreeLook.class),
                OpenSkid.moduleManager.getModule(ItemPhysics.class),
                OpenSkid.moduleManager.getModule(ClickGUIModule.class),
                OpenSkid.moduleManager.getModule(BedPlates.class),
                OpenSkid.moduleManager.getModule(FKCounter.class),
                OpenSkid.moduleManager.getModule(ClosestPlayerHUD.class),
                OpenSkid.moduleManager.getModule(LeapModeHUD.class),
                OpenSkid.moduleManager.getModule(MegaWallsDetector.class),
                OpenSkid.moduleManager.getModule(FireBallPredict.class),
                OpenSkid.moduleManager.getModule(DamageTags.class),
                OpenSkid.moduleManager.getModule(TNTTimer.class),
                OpenSkid.moduleManager.getModule(PotionHUD.class),
                OpenSkid.moduleManager.getModule(Arrows.class),
                OpenSkid.moduleManager.getModule(BlocksESP.class),
                OpenSkid.moduleManager.getModule(MobESP.class),
                OpenSkid.moduleManager.getModule(ItemTags.class),
                OpenSkid.moduleManager.getModule(FallIndicator.class),
                OpenSkid.moduleManager.getModule(InventoryHUD.class),
                OpenSkid.moduleManager.getModule(KeyStrokes.class),
                OpenSkid.moduleManager.getModule(EntityCulling.class)
        );

        List<Module> playerModules = Arrays.asList(
                OpenSkid.moduleManager.getModule(AutoHeal.class),
                OpenSkid.moduleManager.getModule(FakeLag.class),
                OpenSkid.moduleManager.getModule(AutoTool.class),
                OpenSkid.moduleManager.getModule(ChestStealer.class),
                OpenSkid.moduleManager.getModule(ChestAura.class),
                OpenSkid.moduleManager.getModule(AutoBedDef.class),
                OpenSkid.moduleManager.getModule(BedDefender.class),
                OpenSkid.moduleManager.getModule(InvManager.class),
                OpenSkid.moduleManager.getModule(InvWalk.class),
                OpenSkid.moduleManager.getModule(Scaffold.class),
                OpenSkid.moduleManager.getModule(AutoBlockIn.class),
                OpenSkid.moduleManager.getModule(AutoSwap.class),
                OpenSkid.moduleManager.getModule(AutoArmor.class),
                OpenSkid.moduleManager.getModule(AutoPot.class),
                OpenSkid.moduleManager.getModule(AutoSoup.class),
                OpenSkid.moduleManager.getModule(AutoWeapon.class),
                OpenSkid.moduleManager.getModule(SpeedMine.class),
                OpenSkid.moduleManager.getModule(FastPlace.class),
                OpenSkid.moduleManager.getModule(GhostHand.class),
                OpenSkid.moduleManager.getModule(MCF.class),
                OpenSkid.moduleManager.getModule(AntiDebuff.class),
                OpenSkid.moduleManager.getModule(FlagDetector.class),
                OpenSkid.moduleManager.getModule(AutoGapple.class),
                OpenSkid.moduleManager.getModule(AutoHeadHitter.class),
                OpenSkid.moduleManager.getModule(ThrowAura.class),
                OpenSkid.moduleManager.getModule(Nuker.class),
                OpenSkid.moduleManager.getModule(AutoPlace.class),
                OpenSkid.moduleManager.getModule(LegitScaffold.class),
                OpenSkid.moduleManager.getModule(BlockLadder.class),
                OpenSkid.moduleManager.getModule(AutoBed.class),
                OpenSkid.moduleManager.getModule(AutoRespawn.class)
        );

        List<Module> miscModules = Arrays.asList(
                OpenSkid.moduleManager.getModule(Spammer.class),
                OpenSkid.moduleManager.getModule(BedNuker.class),
                OpenSkid.moduleManager.getModule(AntiBot.class),
                OpenSkid.moduleManager.getModule(BedTracker.class),
                OpenSkid.moduleManager.getModule(LightningTracker.class),
                OpenSkid.moduleManager.getModule(StaffDetector.class),
                OpenSkid.moduleManager.getModule(MurderDetector.class),
                OpenSkid.moduleManager.getModule(BedProximityAlert.class),
                OpenSkid.moduleManager.getModule(CheatDetector.class),
                OpenSkid.moduleManager.getModule(NoRotate.class),
                OpenSkid.moduleManager.getModule(NickHider.class),
                OpenSkid.moduleManager.getModule(AntiObbyTrap.class),
                OpenSkid.moduleManager.getModule(AntiObfuscate.class),
                OpenSkid.moduleManager.getModule(AutoAnduril.class),
                OpenSkid.moduleManager.getModule(InventoryClicker.class),
                OpenSkid.moduleManager.getModule(Disabler.class),
                OpenSkid.moduleManager.getModule(ClientSpoofer.class),
                OpenSkid.moduleManager.getModule(ModSpoofer.class),
                OpenSkid.moduleManager.getModule(ExploitFixer.class),
                OpenSkid.moduleManager.getModule(AntiFalseFlag.class),
                OpenSkid.moduleManager.getModule(GhostBlock.class),
                OpenSkid.moduleManager.getModule(PingSpoof.class),
                OpenSkid.moduleManager.getModule(MouseRawInput.class),
                OpenSkid.moduleManager.getModule(BedwarUtils.class),
                OpenSkid.moduleManager.getModule(AutoAuth.class),
                OpenSkid.moduleManager.getModule(AutoHypixel.class),
                OpenSkid.moduleManager.getModule(AutoPlay.class),
                OpenSkid.moduleManager.getModule(AutoGG.class),
                OpenSkid.moduleManager.getModule(AutoReconnect.class),
                OpenSkid.moduleManager.getModule(Panic.class),
                OpenSkid.moduleManager.getModule(KillSults.class),
                OpenSkid.moduleManager.getModule(KillMessage.class),
                OpenSkid.moduleManager.getModule(ViewPackets.class),
                OpenSkid.moduleManager.getModule(AutoBuy.class),
                OpenSkid.moduleManager.getModule(PartyDetector.class),
                OpenSkid.moduleManager.getModule(PlayerList.class),
                OpenSkid.moduleManager.getModule(SkyWars.class),
                OpenSkid.moduleManager.getModule(WoolWars.class),
                OpenSkid.moduleManager.getModule(SpeedBuilders.class),
                OpenSkid.moduleManager.getModule(SkywarsAlerts.class),
                OpenSkid.moduleManager.getModule(ThePitUtils.class),
                OpenSkid.moduleManager.getModule(AutoRequeue.class),
                OpenSkid.moduleManager.getModule(AutoWho.class),
                OpenSkid.moduleManager.getModule(BedWars.class),
                OpenSkid.moduleManager.getModule(BridgeInfo.class),
                OpenSkid.moduleManager.getModule(DuelsStats.class),
                OpenSkid.moduleManager.getModule(MurderMystery.class),
                OpenSkid.moduleManager.getModule(SumoFences.class)
        );

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());

        int currentX = 20;
        int currentY = 20;
        int frameWidth = 110;
        int frameHeight = 24;

        List<Module> combat = new ArrayList<>(combatModules);
        combat.removeIf(m -> m == null);
        combat.sort(comparator);
        if (!combat.isEmpty()) {
            frames.add(new Frame("Combat", combat, currentX, currentY, frameWidth, frameHeight));
            currentX += (frameWidth + 15);
        }

        List<Module> movement = new ArrayList<>(movementModules);
        movement.removeIf(m -> m == null);
        movement.sort(comparator);
        if (!movement.isEmpty()) {
            frames.add(new Frame("Movement", movement, currentX, currentY, frameWidth, frameHeight));
            currentX += (frameWidth + 15);
        }

        List<Module> render = new ArrayList<>(renderModules);
        render.removeIf(m -> m == null);
        render.sort(comparator);
        if (!render.isEmpty()) {
            frames.add(new Frame("Render", render, currentX, currentY, frameWidth, frameHeight));
            currentX += (frameWidth + 15);
        }

        List<Module> player = new ArrayList<>(playerModules);
        player.removeIf(m -> m == null);
        player.sort(comparator);
        if (!player.isEmpty()) {
            frames.add(new Frame("Player", player, currentX, currentY, frameWidth, frameHeight));
            currentX += (frameWidth + 15);
        }

        List<Module> misc = new ArrayList<>(miscModules);
        misc.removeIf(m -> m == null);
        misc.sort(comparator);
        if (!misc.isEmpty()) {
            frames.add(new Frame("Misc", misc, currentX, currentY, frameWidth, frameHeight));
        }
    }

    public static ModernClickGui getInstance() {
        if (instance == null) {
            instance = new ModernClickGui();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    @Override
    public void initGui() {
        super.initGui();
        openskid.util.font.FontManager.initializeFonts();
        this.isClosing = false;
        this.openTime = System.currentTimeMillis();
        this.lastFrameTime = System.nanoTime();
        this.scrollY = 0;
        this.targetScrollY = 0;
        this.velocity = 0;
        this.searchText = "";
        this.searchFocused = false;
        applySearchFilter();
    }

    public void close() {
        if (isClosing) return;
        this.isClosing = true;
        this.openTime = System.currentTimeMillis();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long currentFrameTime = System.nanoTime();
        float deltaTime = (currentFrameTime - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = currentFrameTime;
        updateScroll();
        long elapsedTime = System.currentTimeMillis() - openTime;
        if (isClosing && elapsedTime > ANIMATION_DURATION) {
            mc.displayGuiScreen(null);
            return;
        }
        float screenAlpha = isClosing ? (1.0f - Math.min(1.0f, (float) elapsedTime / ANIMATION_DURATION)) : Math.min(1.0f, (float) elapsedTime / ANIMATION_DURATION);
        screenAlpha = (float) (1.0 - Math.pow(1.0 - screenAlpha, 3));
        if (screenAlpha > 0.01f) {
            int renderScroll = scrollY - Math.round((1.0f - screenAlpha) * 10.0f);
            Module clickGUI = OpenSkid.moduleManager.getModule("ClickGUI");
            boolean useGlass = clickGUI instanceof ClickGUIModule && ((ClickGUIModule) clickGUI).glass.getValue();

            // Pass 1: Blur Mask
            if (useGlass) {
                BlurUtils.prepareBlur();
                for (Frame frame : frames) {
                    frame.renderBlurMask(renderScroll);
                }
                BlurUtils.blurEnd(2, 4.0f);
            }

            // Pass 2: Visuals
            for (Frame frame : frames) {
                frame.render(mouseX, mouseY, partialTicks, screenAlpha, false, renderScroll, deltaTime);
            }

            renderSearchBar(screenAlpha);
            for (Frame frame : frames) {
                frame.drawTooltips(mouseX, mouseY, renderScroll, screenAlpha);
            }
        }
        try {
            Module invWalkModule = OpenSkid.moduleManager.getModule("InvWalk");
            if (invWalkModule != null && invWalkModule.isEnabled()) {
                handleInvWalk();
            }
        } catch (Exception ignored) {
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderSearchBar(float animationProgress) {
        ScaledResolution sr = new ScaledResolution(mc);
        int bx = sr.getScaledWidth() / 2 - SEARCH_W / 2;
        int by = 4;
        int a = (int) (255 * animationProgress);
        if (a < 5) return;

        int bg = new Color(15, 15, 15, (int) (220 * animationProgress)).getRGB();
        RenderUtil.drawRoundedRect((float) bx, (float) by, (float) SEARCH_W, (float) SEARCH_H,
                4.0f, bg, true, true, true, true);
        int border = searchFocused
                ? MaterialTheme.getRGBWithAlpha(MaterialTheme.PRIMARY_COLOR, a)
                : new Color(255, 255, 255, (int) (40 * animationProgress)).getRGB();
        RenderUtil.drawRoundedRectOutline((float) bx, (float) by, (float) SEARCH_W, (float) SEARCH_H,
                4.0f, 1.0f, border, true, true, true, true);

        if (searchFocused) {
            long now = System.currentTimeMillis();
            if (now - lastSearchBlink > 500) {
                searchCursorVisible = !searchCursorVisible;
                lastSearchBlink = now;
            }
        }

        String shown = searchText.isEmpty() ? "Search..." : searchText;
        int textColor = searchText.isEmpty()
                ? new Color(120, 120, 120, a).getRGB()
                : new Color(240, 240, 240, a).getRGB();
        float textY = by + (SEARCH_H - 8) / 2f;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(shown, bx + 6, textY, textColor);
            if (searchFocused && searchCursorVisible) {
                float cursorX = (float) (bx + 6 + FontManager.productSans16.getStringWidth(shown));
                float cursorBaseX = searchText.isEmpty() ? bx + 6 : cursorX;
                RenderUtil.drawLine(cursorBaseX, textY, cursorBaseX, textY + 10, 1.0f, textColor);
            }
        } else {
            mc.fontRendererObj.drawStringWithShadow(shown, bx + 6, textY, textColor);
        }
    }

    private boolean isMouseOverSearch(int mouseX, int mouseY) {
        ScaledResolution sr = new ScaledResolution(mc);
        int bx = sr.getScaledWidth() / 2 - SEARCH_W / 2;
        int by = 4;
        return mouseX >= bx && mouseX <= bx + SEARCH_W && mouseY >= by && mouseY <= by + SEARCH_H;
    }

    private void applySearchFilter() {
        for (Frame frame : frames) {
            frame.setSearchFilter(searchText);
        }
        targetScrollY = 0;
    }

    private void handleInvWalk() {
        KeyBinding[] keys = {
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSprint,
                mc.gameSettings.keyBindSneak
        };
        for (KeyBinding key : keys) {
            KeyBinding.setKeyBindState(key.getKeyCode(), Keyboard.isKeyDown(key.getKeyCode()));
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (isClosing) return;
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            velocity += wheel > 0 ? -30 : 30;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (isClosing) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (isMouseOverSearch(mouseX, mouseY)) {
            if (mouseButton == 0) {
                searchFocused = true;
                searchCursorVisible = true;
                lastSearchBlink = System.currentTimeMillis();
            }
            return;
        }
        searchFocused = false;
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame frame = frames.get(i);
            if (frame.mouseClicked(mouseX, mouseY, mouseButton, scrollY)) {
                draggingComponent = frame;
                frames.remove(i);
                frames.add(frame);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (isClosing) return;
        super.mouseReleased(mouseX, mouseY, state);
        if (draggingComponent != null) {
            draggingComponent.mouseReleased(mouseX, mouseY, state, scrollY);
            draggingComponent = null;
        }
        for (Frame frame : frames) {
            frame.mouseReleased(mouseX, mouseY, state, scrollY);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (isClosing) return;
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (draggingComponent != null) {
            draggingComponent.updatePosition(mouseX, mouseY);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (isClosing) return;
        if (System.currentTimeMillis() - this.openTime < 100) return;
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchText = "";
                applySearchFilter();
                searchFocused = false;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    applySearchFilter();
                }
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                searchFocused = false;
                return;
            }
            if (typedChar != '\0' && !Character.isISOControl(typedChar)) {
                searchText += typedChar;
                applySearchFilter();
            }
            return;
        }
        boolean isBindingKey = false;
        for (Frame frame : frames) {
            if (frame.isAnyComponentBinding()) {
                isBindingKey = true;
                break;
            }
        }
        if (isBindingKey) {
            for (Frame frame : frames) {
                frame.keyTyped(typedChar, keyCode);
            }
            return;
        }
        Module clickGUIModule = OpenSkid.moduleManager.getModule("ClickGUI");
        if (keyCode == Keyboard.KEY_ESCAPE || (clickGUIModule != null && keyCode == clickGUIModule.getKey())) {
            if (keyCode == Keyboard.KEY_ESCAPE && !searchText.isEmpty()) {
                searchText = "";
                applySearchFilter();
                searchFocused = false;
                return;
            }
            close();
            return;
        }
        for (Frame frame : frames) {
            frame.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateScroll() {
        targetScrollY += (int) velocity;
        velocity *= FRICTION;
        int maxScroll = getMaxScroll();
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));
        int delta = targetScrollY - scrollY;
        scrollY += (int) (delta * SNAP_STRENGTH);
        if (Math.abs(velocity) < 0.5) velocity = 0;
        if (Math.abs(delta) < 1 && Math.abs(velocity) < 0.5) scrollY = targetScrollY;
    }

    private int getMaxScroll() {
        int max = 0;
        for (Frame frame : frames) {
            int bottom = frame.getY() + (int) frame.getCurrentHeight();
            if (bottom > max) max = bottom;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        return Math.max(0, max - sr.getScaledHeight() + 20);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Module guiModule = OpenSkid.moduleManager.getModule("ClickGUI");
        if (guiModule instanceof ClickGUIModule && ((ClickGUIModule) guiModule).isSwitchingGuiStyle()) {
            return;
        }
        if (guiModule != null) {
            guiModule.setEnabled(false);
        }
    }
}
