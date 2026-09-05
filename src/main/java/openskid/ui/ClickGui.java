package openskid.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import openskid.OpenSkid;
import openskid.font.FontProcess;
import openskid.module.Module;
import openskid.module.modules.*;
import openskid.module.modules.Timer;
import openskid.ui.components.CategoryComponent;
import openskid.ui.components.ModuleComponent;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import openskid.font.CFontRenderer;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ClickGui extends GuiScreen {
    CFontRenderer fontRenderer = FontProcess.getFont("sans");
    private static ClickGui instance;
    private final File configFile = new File("./config/OpenSkid-plus/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;
    private ModuleComponent hoveredRow;
    private long hoverStart;

    public ClickGui() {
        instance = this;


        List<Module> combatModules = new ArrayList<>();
        combatModules.add(OpenSkid.moduleManager.getModule(AimAssist.class));
        combatModules.add(OpenSkid.moduleManager.getModule(MoveFix.class));
        combatModules.add(OpenSkid.moduleManager.getModule(AutoClicker.class));
        combatModules.add(OpenSkid.moduleManager.getModule(BurstClicker.class));
        combatModules.add(OpenSkid.moduleManager.getModule(KillAura.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Wtap.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Velocity.class));
        combatModules.add(OpenSkid.moduleManager.getModule(ServerLag.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Reach.class));
        combatModules.add(OpenSkid.moduleManager.getModule(TargetStrafe.class));
        combatModules.add(OpenSkid.moduleManager.getModule(NoHitDelay.class));
        combatModules.add(OpenSkid.moduleManager.getModule(AntiFireball.class));
        combatModules.add(OpenSkid.moduleManager.getModule(KnockbackDelay.class));
        combatModules.add(OpenSkid.moduleManager.getModule(LagRange.class));
        combatModules.add(OpenSkid.moduleManager.getModule(HitBox.class));
        combatModules.add(OpenSkid.moduleManager.getModule(MoreKB.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Refill.class));
        combatModules.add(OpenSkid.moduleManager.getModule(HitSelect.class));
        combatModules.add(OpenSkid.moduleManager.getModule(JumpReset.class));
        combatModules.add(OpenSkid.moduleManager.getModule(BackTrack.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Hitflick.class));
        combatModules.add(OpenSkid.moduleManager.getModule(TimerRange.class));
        combatModules.add(OpenSkid.moduleManager.getModule(ClickAssits.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Criticals.class));
        combatModules.add(OpenSkid.moduleManager.getModule(BlockHit.class));
        combatModules.add(OpenSkid.moduleManager.getModule(AutoBlock.class));
        combatModules.add(OpenSkid.moduleManager.getModule(SprintReset.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Displace.class));
        combatModules.add(OpenSkid.moduleManager.getModule(TickBase.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Piercing.class));
        combatModules.add(OpenSkid.moduleManager.getModule(Stasis.class));
        combatModules.add(OpenSkid.moduleManager.getModule(SmartClicking.class));
        combatModules.add(OpenSkid.moduleManager.getModule(AutoRod.class));
        combatModules.add(OpenSkid.moduleManager.getModule(ForwardTrack.class));
        combatModules.add(OpenSkid.moduleManager.getModule(KeepRange.class));
        combatModules.add(OpenSkid.moduleManager.getModule(SmartBlinker.class));
        combatModules.add(OpenSkid.moduleManager.getModule(LegitReach.class));
        combatModules.add(OpenSkid.moduleManager.getModule(ProjectileAimBot.class));
        combatModules.add(OpenSkid.moduleManager.getModule(ArmorBreaker.class));

        List<Module> movementModules = new ArrayList<>();
        movementModules.add(OpenSkid.moduleManager.getModule(AntiAFK.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Fly.class));
        movementModules.add(OpenSkid.moduleManager.getModule(FastBow.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Timer.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Speed.class));
        movementModules.add(OpenSkid.moduleManager.getModule(LongJump.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Sprint.class));
        movementModules.add(OpenSkid.moduleManager.getModule(SafeWalk.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Jesus.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Blink.class));
        movementModules.add(OpenSkid.moduleManager.getModule(NoFall.class));
        movementModules.add(OpenSkid.moduleManager.getModule(NoSlow.class));
        movementModules.add(OpenSkid.moduleManager.getModule(KeepSprint.class));
        movementModules.add(OpenSkid.moduleManager.getModule(NoJumpDelay.class));
        movementModules.add(OpenSkid.moduleManager.getModule(AntiVoid.class));
        movementModules.add(OpenSkid.moduleManager.getModule(BridgeAssist.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Clutch.class));
        movementModules.add(OpenSkid.moduleManager.getModule(WaterClutch.class));
        movementModules.add(OpenSkid.moduleManager.getModule(PearlSaver.class));
        movementModules.add(OpenSkid.moduleManager.getModule(NoWeb.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Parkour.class));
        movementModules.add(OpenSkid.moduleManager.getModule(LadderClutch.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Phase.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Step.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Freecam.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Spider.class));
        movementModules.add(OpenSkid.moduleManager.getModule(NoClickDelay.class));
        movementModules.add(OpenSkid.moduleManager.getModule(Tower.class));

        List<Module> renderModules = new ArrayList<>();
        renderModules.add(OpenSkid.moduleManager.getModule(ESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Chams.class));
        renderModules.add(OpenSkid.moduleManager.getModule(FullBright.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Tracers.class));
        renderModules.add(OpenSkid.moduleManager.getModule(NameTags.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Xray.class));
        renderModules.add(OpenSkid.moduleManager.getModule(TargetESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(TargetHUD.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Indicators.class));
        renderModules.add(OpenSkid.moduleManager.getModule(BedESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(BlockOverlay.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ItemESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(BreakProgress.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ViewClip.class));
        renderModules.add(OpenSkid.moduleManager.getModule(NoHurtCam.class));
        renderModules.add(OpenSkid.moduleManager.getModule(HUD.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Notifications.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ClickGUIModule.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ClickGUIModule.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ChestESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Trajectories.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Radar.class));
        renderModules.add(OpenSkid.moduleManager.getModule(RenderFixes.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Ambience.class));
        renderModules.add(OpenSkid.moduleManager.getModule(FPScounter.class));
        renderModules.add(OpenSkid.moduleManager.getModule(WaterMark.class));
        renderModules.add(OpenSkid.moduleManager.getModule(WaterMark2.class));
        renderModules.add(OpenSkid.moduleManager.getModule(HitParticleEffects.class));
        renderModules.add(OpenSkid.moduleManager.getModule(KillEffect.class));
        renderModules.add(OpenSkid.moduleManager.getModule(KillSounds.class));
        renderModules.add(OpenSkid.moduleManager.getModule(DynamicIsland.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ESP2D.class));
        renderModules.add(OpenSkid.moduleManager.getModule(TeamHealthDisplay.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Statistics.class));
        renderModules.add(OpenSkid.moduleManager.getModule(FreeLook.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ItemPhysics.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Capes.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Animations.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Hotbar.class));
        renderModules.add(OpenSkid.moduleManager.getModule(BedPlates.class));
        renderModules.add(OpenSkid.moduleManager.getModule(FKCounter.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ClosestPlayerHUD.class));
        renderModules.add(OpenSkid.moduleManager.getModule(LeapModeHUD.class));
        renderModules.add(OpenSkid.moduleManager.getModule(MegaWallsDetector.class));
        renderModules.add(OpenSkid.moduleManager.getModule(FireBallPredict.class));
        renderModules.add(OpenSkid.moduleManager.getModule(DamageTags.class));
        renderModules.add(OpenSkid.moduleManager.getModule(TNTTimer.class));
        renderModules.add(OpenSkid.moduleManager.getModule(PotionHUD.class));
        renderModules.add(OpenSkid.moduleManager.getModule(Arrows.class));
        renderModules.add(OpenSkid.moduleManager.getModule(BlocksESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(MobESP.class));
        renderModules.add(OpenSkid.moduleManager.getModule(ItemTags.class));
        renderModules.add(OpenSkid.moduleManager.getModule(FallIndicator.class));
        renderModules.add(OpenSkid.moduleManager.getModule(InventoryHUD.class));
        renderModules.add(OpenSkid.moduleManager.getModule(KeyStrokes.class));
        renderModules.add(OpenSkid.moduleManager.getModule(EntityCulling.class));

        List<Module> playerModules = new ArrayList<>();
        playerModules.add(OpenSkid.moduleManager.getModule(AutoHeal.class));
        playerModules.add(OpenSkid.moduleManager.getModule(FakeLag.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoTool.class));
        playerModules.add(OpenSkid.moduleManager.getModule(ChestStealer.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoBedDef.class));
        playerModules.add(OpenSkid.moduleManager.getModule(BedDefender.class));
        playerModules.add(OpenSkid.moduleManager.getModule(InvManager.class));
        playerModules.add(OpenSkid.moduleManager.getModule(InvWalk.class));
        playerModules.add(OpenSkid.moduleManager.getModule(Scaffold.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoBlockIn.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoSwap.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoArmor.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoPot.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoSoup.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoWeapon.class));
        playerModules.add(OpenSkid.moduleManager.getModule(SpeedMine.class));
        playerModules.add(OpenSkid.moduleManager.getModule(FastPlace.class));
        playerModules.add(OpenSkid.moduleManager.getModule(GhostHand.class));
        playerModules.add(OpenSkid.moduleManager.getModule(MCF.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AntiDebuff.class));
        playerModules.add(OpenSkid.moduleManager.getModule(FlagDetector.class));  // i mean this use S08PacketPlayerPosLook so it suck
        playerModules.add(OpenSkid.moduleManager.getModule(AutoGapple.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoHeadHitter.class));
        playerModules.add(OpenSkid.moduleManager.getModule(ChestAura.class));
        playerModules.add(OpenSkid.moduleManager.getModule(ThrowAura.class));
        playerModules.add(OpenSkid.moduleManager.getModule(Nuker.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoPlace.class));
        playerModules.add(OpenSkid.moduleManager.getModule(LegitScaffold.class));
        playerModules.add(OpenSkid.moduleManager.getModule(BlockLadder.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoBed.class));
        playerModules.add(OpenSkid.moduleManager.getModule(AutoRespawn.class));

        List<Module> miscModules = new ArrayList<>();
        miscModules.add(OpenSkid.moduleManager.getModule(Spammer.class));
        miscModules.add(OpenSkid.moduleManager.getModule(BedNuker.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AntiBot.class));
        miscModules.add(OpenSkid.moduleManager.getModule(BedTracker.class));
        miscModules.add(OpenSkid.moduleManager.getModule(LightningTracker.class));
        miscModules.add(OpenSkid.moduleManager.getModule(StaffDetector.class));
        miscModules.add(OpenSkid.moduleManager.getModule(MurderDetector.class));
        miscModules.add(OpenSkid.moduleManager.getModule(BedProximityAlert.class));
        miscModules.add(OpenSkid.moduleManager.getModule(CheatDetector.class));
        miscModules.add(OpenSkid.moduleManager.getModule(NoRotate.class));
        miscModules.add(OpenSkid.moduleManager.getModule(NickHider.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AntiObbyTrap.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AntiObfuscate.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoAnduril.class));
        miscModules.add(OpenSkid.moduleManager.getModule(InventoryClicker.class));
        miscModules.add(OpenSkid.moduleManager.getModule(Disabler.class));
        miscModules.add(OpenSkid.moduleManager.getModule(ClientSpoofer.class));
        miscModules.add(OpenSkid.moduleManager.getModule(ModSpoofer.class));
        miscModules.add(OpenSkid.moduleManager.getModule(ExploitFixer.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AntiFalseFlag.class));
        miscModules.add(OpenSkid.moduleManager.getModule(GhostBlock.class));
        miscModules.add(OpenSkid.moduleManager.getModule(PingSpoof.class));
        miscModules.add(OpenSkid.moduleManager.getModule(MouseRawInput.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoHypixel.class));
        miscModules.add(OpenSkid.moduleManager.getModule(BedwarUtils.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoAuth.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoPlay.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoGG.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoReconnect.class));
        miscModules.add(OpenSkid.moduleManager.getModule(Panic.class));
        miscModules.add(OpenSkid.moduleManager.getModule(KillSults.class));
        miscModules.add(OpenSkid.moduleManager.getModule(KillMessage.class));
        miscModules.add(OpenSkid.moduleManager.getModule(ViewPackets.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoBuy.class));
        miscModules.add(OpenSkid.moduleManager.getModule(PartyDetector.class));
        miscModules.add(OpenSkid.moduleManager.getModule(PlayerList.class));
        miscModules.add(OpenSkid.moduleManager.getModule(SkyWars.class));
        miscModules.add(OpenSkid.moduleManager.getModule(WoolWars.class));
        miscModules.add(OpenSkid.moduleManager.getModule(SpeedBuilders.class));
        miscModules.add(OpenSkid.moduleManager.getModule(SkywarsAlerts.class));
        miscModules.add(OpenSkid.moduleManager.getModule(ThePitUtils.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoRequeue.class));
        miscModules.add(OpenSkid.moduleManager.getModule(AutoWho.class));
        miscModules.add(OpenSkid.moduleManager.getModule(BedWars.class));
        miscModules.add(OpenSkid.moduleManager.getModule(BridgeInfo.class));
        miscModules.add(OpenSkid.moduleManager.getModule(DuelsStats.class));
        miscModules.add(OpenSkid.moduleManager.getModule(MurderMystery.class));
        miscModules.add(OpenSkid.moduleManager.getModule(SumoFences.class));

        List<Module> scriptModules = new ArrayList<>(OpenSkid.moduleManager.dynamicModules.values());

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        combatModules.sort(comparator);
        movementModules.sort(comparator);
        renderModules.sort(comparator);
        playerModules.sort(comparator);
        miscModules.sort(comparator);
        scriptModules.sort(comparator);

        Set<Module> registered = new HashSet<>();
        registered.addAll(combatModules);
        registered.addAll(movementModules);
        registered.addAll(renderModules);
        registered.addAll(playerModules);
        registered.addAll(miscModules);
        registered.addAll(scriptModules);

        for (Module module : OpenSkid.moduleManager.allModules()) {
            if (!registered.contains(module)) {
                throw new RuntimeException(module.getClass().getName() + " is unregistered to click gui.");
            }
        }

        this.categoryList = new ArrayList<>();
        int topOffset = 5;

        CategoryComponent combat = new CategoryComponent("Combat", combatModules);
        combat.setY(topOffset);
        categoryList.add(combat);
        topOffset += 20;

        CategoryComponent movement = new CategoryComponent("Movement", movementModules);
        movement.setY(topOffset);
        categoryList.add(movement);
        topOffset += 20;

        CategoryComponent render = new CategoryComponent("Render", renderModules);
        render.setY(topOffset);
        categoryList.add(render);
        topOffset += 20;

        CategoryComponent player = new CategoryComponent("Player", playerModules);
        player.setY(topOffset);
        categoryList.add(player);
        topOffset += 20;

        CategoryComponent misc = new CategoryComponent("Misc", miscModules);
        misc.setY(topOffset);
        categoryList.add(misc);
        topOffset += 20;

        CategoryComponent scripts = new CategoryComponent("Scripts", scriptModules);
        scripts.setY(topOffset);
        categoryList.add(scripts);

        loadPositions();
    }

    public static ClickGui getInstance() {
        if (instance == null) {
            instance = new ClickGui();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public void initGui() {
        super.initGui();
    }

    public void drawScreen(int x, int y, float p) {
        drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 100).getRGB());

        fontRenderer.drawStringWithShadow("OpenSkid " + OpenSkid.version, 4, this.height - 3 - fontRenderer.FONT_HEIGHT * 2, new Color(60, 162, 253).getRGB());
        fontRenderer.drawStringWithShadow("dev, nespola", 4, this.height - 3 - fontRenderer.FONT_HEIGHT, new Color(60, 162, 253).getRGB());

        for (CategoryComponent category : categoryList) {
            category.render(this.mc.fontRendererObj);
            category.handleDrag(x, y);

            for (Component module : category.getModules()) {
                module.update(x, y);
            }
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int scrollDir = wheel > 0 ? 1 : -1;
            for (CategoryComponent category : categoryList) {
                category.onScroll(x, y, scrollDir);
            }
        }

        renderModuleTooltip(x, y);
    }

    private void renderModuleTooltip(int x, int y) {
        ModuleComponent row = null;
        for (CategoryComponent category : categoryList) {
            if (!category.isOpened()) continue;
            for (Component module : category.getModules()) {
                if (module instanceof ModuleComponent && ((ModuleComponent) module).isHovered(x, y)) {
                    row = (ModuleComponent) module;
                    break;
                }
            }
            if (row != null) break;
        }
        if (row == null) {
            hoveredRow = null;
            hoverStart = 0L;
            return;
        }
        if (row != hoveredRow) {
            hoveredRow = row;
            hoverStart = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - hoverStart < 400) return;
        String desc = row.mod.getDescription();
        if (desc == null || desc.trim().isEmpty()) return;
        int pad = 4;
        int bw = fontRenderer.getStringWidth(desc) + pad * 2;
        int bh = fontRenderer.FONT_HEIGHT + pad * 2;
        int bx = x + 12;
        int by = y + 12;
        if (bx + bw > this.width - 2) bx = x - bw - 8;
        if (by + bh > this.height - 2) by = y - bh - 8;
        if (bx < 2) bx = 2;
        if (by < 2) by = 2;
        drawRect(bx - 1, by - 1, bx + bw + 1, by + bh + 1, new Color(60, 162, 253).getRGB());
        drawRect(bx, by, bx + bw, by + bh, new Color(0, 0, 0, 220).getRGB());
        fontRenderer.drawStringWithShadow(desc, bx + pad, by + pad, -1);
    }

    public void mouseClicked(int x, int y, int mouseButton) {
        Iterator<CategoryComponent> btnCat = categoryList.iterator();
        while (true) {
            CategoryComponent category;
            do {
                do {
                    if (!btnCat.hasNext()) {
                        return;
                    }

                    category = btnCat.next();
                    if (category.insideArea(x, y) && !category.isHovered(x, y) && !category.mousePressed(x, y) && mouseButton == 0) {
                        category.mousePressed(true);
                        category.xx = x - category.getX();
                        category.yy = y - category.getY();
                    }

                    if (category.mousePressed(x, y) && mouseButton == 0) {
                        category.setOpened(!category.isOpened());
                    }

                    if (category.isHovered(x, y) && mouseButton == 0) {
                        category.setPin(!category.isPin());
                    }
                } while (!category.isOpened());
            } while (category.getModules().isEmpty());

            for (Component c : category.getModules()) {
                c.mouseDown(x, y, mouseButton);
            }
        }

    }

    public void mouseReleased(int x, int y, int mouseButton) {
        Iterator<CategoryComponent> iterator = categoryList.iterator();

        CategoryComponent categoryComponent;
        while (iterator.hasNext()) {
            categoryComponent = iterator.next();
            if (mouseButton == 0) {
                categoryComponent.mousePressed(false);
            }
        }

        iterator = categoryList.iterator();

        while (true) {
            do {
                do {
                    if (!iterator.hasNext()) {
                        return;
                    }

                    categoryComponent = iterator.next();
                } while (!categoryComponent.isOpened());
            } while (categoryComponent.getModules().isEmpty());

            for (Component component : categoryComponent.getModules()) {
                component.mouseReleased(x, y, mouseButton);
            }
        }
    }

    public void keyTyped(char typedChar, int key) {
        Module clickGUIModule = OpenSkid.moduleManager.getModule("ClickGUI");
        if (key == Keyboard.KEY_ESCAPE || (clickGUIModule != null && key == clickGUIModule.getKey())) {
            this.mc.displayGuiScreen(null);
        } else {
            Iterator<CategoryComponent> btnCat = categoryList.iterator();

            while (true) {
                CategoryComponent cat;
                do {
                    do {
                        if (!btnCat.hasNext()) {
                            return;
                        }

                        cat = btnCat.next();
                    } while (!cat.isOpened());
                } while (cat.getModules().isEmpty());

                for (Component component : cat.getModules()) {
                    component.keyTyped(typedChar, key);
                }
            }
        }
    }

    public void onGuiClosed() {
        savePositions();
        Module clickGUIModule = OpenSkid.moduleManager.getModule("ClickGUI");
        if (clickGUIModule instanceof ClickGUIModule
                && ((ClickGUIModule) clickGUIModule).isSwitchingGuiStyle()) {
            return;
        }
        if (clickGUIModule != null) {
            clickGUIModule.setEnabled(false);
        }
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private void savePositions() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        JsonObject json = new JsonObject();
        for (CategoryComponent cat : categoryList) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (CategoryComponent cat : categoryList) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
