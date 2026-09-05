package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.Priority;
import openskid.events.PacketEvent;
import openskid.events.Render3DEvent;
import openskid.events.TickEvent;
import openskid.management.LagCore;
import openskid.mixin.IAccessorPlayerControllerMP;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.util.ItemUtil;
import openskid.util.RenderUtil;
import openskid.util.RotationUtil;
import openskid.util.TeamUtil;
import openskid.property.properties.*;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int tickIndex = -1;
    private long delayCounter = 0L;
    private boolean hasTarget = false;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;
    public final IntProperty delay = new IntProperty("delay", 150, 0, 1000);
    public final FloatProperty range = new FloatProperty("range", 10.0F, 3.0F, 100.0F);
    public final ModeProperty lagMode = new ModeProperty("lag-mode", 0, new String[]{"Normal", "Repel"});
    public final FloatProperty repelRange = new FloatProperty("repel-range", 4.5F, 2.0F, 8.0F, () -> this.lagMode.getValue() == 1);
    public final IntProperty repelMaxMs = new IntProperty("repel-max-ms", 350, 100, 1000, () -> this.lagMode.getValue() == 1);
    public final BooleanProperty repelReleaseOnHit = new BooleanProperty("repel-release-on-hit", true, () -> this.lagMode.getValue() == 1);
    private boolean repelling = false;
    private long repelHoldStartMs = 0L;
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final ModeProperty showPosition = new ModeProperty("show-position", 0, new String[]{"NONE", "DEFAULT", "HUD", "REALPOS"});
    // Sprint/block/potion flush gates adapted from MiauMinus LagRange aggressive mode.
    public final BooleanProperty sprintReset = new BooleanProperty("sprint-reset", true);
    public final BooleanProperty blockSword = new BooleanProperty("block-sword", true);
    public final BooleanProperty splashPotion = new BooleanProperty("splash-potion", true);
    private boolean lastSprintState = false;
    private boolean lastBlockingState = false;

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        } else {
            return false;
        }
    }

    public LagRange() {
        super("LagRange", false, false, "Delays your position updates to gain extra reach.");
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    OpenSkid.lagManager.setDelay(0);
                    this.hasTarget = false;
                    if (checkFlushGates()) {
                        this.tickIndex = -1;
                        break;
                    }
                    if (this.lagMode.getValue() == 1) {
                        this.tickRepel();
                        break;
                    }
                    BedNuker bedNuker = (BedNuker) OpenSkid.moduleManager.modules.get(BedNuker.class);
                    if ((!bedNuker.isEnabled() || !bedNuker.isReady())
                            && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !(Boolean) this.weaponsOnly.getValue()
                                    || ItemUtil.hasRawUnbreakingEnchant()
                                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()
                    )) {
                        List<EntityPlayer> players = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .collect(Collectors.toList());
                        if (players.isEmpty()) {
                            this.tickIndex = -1;
                        } else {
                            double height = mc.thePlayer.getEyeHeight();
                            Vec3 eyePosition = OpenSkid.lagManager.getLastPosition().addVector(0.0, height, 0.0);
                            Vec3 targetEyePosition = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + height, mc.thePlayer.lastTickPosZ);
                            Vec3 playerEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                            for (EntityPlayer player : players) {
                                double distance = RotationUtil.distanceToBox(player, playerEyePosition);
                                if (!(distance > (double) this.range.getValue())) {
                                    double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
                                    double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
                                    if (distance < targetDist || distance < eyeDist) {
                                        if (this.tickIndex < 0) {
                                            this.tickIndex = 0;
                                            for (this.delayCounter = this.delayCounter + (long) this.delay.getValue();
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        OpenSkid.lagManager.setDelay(this.tickIndex);
                                        this.hasTarget = true;
                                        return;
                                    }
                                }
                            }
                        }
                    } else {
                        this.tickIndex = -1;
                    }
                    break;
                case POST:
                    Vec3 savedPosition = OpenSkid.lagManager.getLastPosition();
                    if (this.currentPosition == null) {
                        this.lastPosition = savedPosition;
                    } else {
                        this.lastPosition = this.currentPosition;
                    }
                    this.currentPosition = savedPosition;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (this.shouldResetOnPacket(event.getPacket())) {
                if (this.lagMode.getValue() == 1 && this.repelReleaseOnHit.getValue()
                        && event.getPacket() instanceof C02PacketUseEntity) {
                    this.stopRepel();
                } else {
                    OpenSkid.lagManager.setDelay(0);
                    this.tickIndex = -1;
                }
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() == 3) {
                renderRealPosBox(event);
            } else if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                int color = -1;
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F).getRGB();
                        break;
                    case 2:
                        color = ((HUD) OpenSkid.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, (color >> 16 & 0xFF), (color >> 8 & 0xFF), (color & 0xFF));
                RenderUtil.disableRenderState();
            }
        }
    }

    private void tickRepel() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.stopRepel();
            return;
        }
        EntityPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;
        float height = mc.thePlayer.getEyeHeight();
        Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
        for (Object entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entity;
            if (!this.isValidTarget(player)) {
                continue;
            }
            double distance = RotationUtil.distanceToBox(player, eye);
            if (distance < nearestDist) {
                nearestDist = distance;
                nearest = player;
            }
        }
        LagCore core = OpenSkid.lagCore;
        if (nearest == null || nearestDist > (double) this.repelRange.getValue() + 1.0) {
            this.stopRepel();
            return;
        }
        this.hasTarget = true;
        if (!this.repelling) {
            this.repelling = true;
            this.repelHoldStartMs = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - this.repelHoldStartMs >= (long) this.repelMaxMs.getValue()) {
            this.stopRepel();
            return;
        }
        OpenSkid.lagManager.setDelay(this.delay.getValue());
        this.tickIndex = Math.max(this.tickIndex, 0);
    }

    private void stopRepel() {
        if (this.repelling) {
            this.repelling = false;
            LagCore core = OpenSkid.lagCore;
            if (core != null) {
                core.release(this);
            }
        }
        OpenSkid.lagManager.setDelay(0);
        this.tickIndex = -1;
    }

    private boolean checkFlushGates() {
        if (mc.thePlayer == null || mc.theWorld == null) return true;
        boolean sprintingNow = mc.thePlayer.isSprinting();
        boolean blockingNow = mc.thePlayer.isBlocking();
        boolean flush = false;
        if (this.sprintReset.getValue() && sprintingNow && !this.lastSprintState) flush = true;
        if (this.blockSword.getValue() && blockingNow && !this.lastBlockingState) flush = true;
        if (this.splashPotion.getValue() && mc.thePlayer.isUsingItem()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null && held.getItem() instanceof ItemPotion && ItemPotion.isSplash(held.getMetadata())) flush = true;
        }
        this.lastSprintState = sprintingNow;
        this.lastBlockingState = blockingNow;
        if (flush) {
            OpenSkid.lagManager.setDelay(0);
            LagCore core = OpenSkid.lagCore;
            if (core != null) core.release(this);
            this.tickIndex = -1;
        }
        return flush;
    }

    private void renderRealPosBox(Render3DEvent event) {
        if (!this.hasTarget) return;
        LagCore core = OpenSkid.lagCore;
        Vec3 server = core != null ? core.getLastServerPosition() : OpenSkid.lagManager.getLastPosition();
        if (server == null) return;
        Vec3 from = this.lastPosition != null ? this.lastPosition : server;
        // 80ms window interpolates server pos against last tick pos.
        double x = RenderUtil.lerpDouble(server.xCoord, from.xCoord, event.getPartialTicks());
        double y = RenderUtil.lerpDouble(server.yCoord, from.yCoord, event.getPartialTicks());
        double z = RenderUtil.lerpDouble(server.zCoord, from.zCoord, event.getPartialTicks());
        int color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F).getRGB();
        float size = mc.thePlayer.getCollisionBorderSize();
        AxisAlignedBB aabb = new AxisAlignedBB(
                x - (double) mc.thePlayer.width / 2.0, y, z - (double) mc.thePlayer.width / 2.0,
                x + (double) mc.thePlayer.width / 2.0, y + (double) mc.thePlayer.height, z + (double) mc.thePlayer.width / 2.0
        ).expand(size, size, size).offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
        );
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(aabb, (color >> 16 & 0xFF), (color >> 8 & 0xFF), (color & 0xFF));
        RenderUtil.disableRenderState();
    }

    @Override
    public void onEnabled() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        this.lastSprintState = false;
        this.lastBlockingState = false;
    }

    @Override
    public void onDisabled() {
        OpenSkid.lagManager.setDelay(0);
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        this.tickIndex = -1;
        this.delayCounter = 0L;
        this.hasTarget = false;
        this.repelling = false;
        this.repelHoldStartMs = 0L;
        this.lastPosition = null;
        this.currentPosition = null;
        this.lastSprintState = false;
        this.lastBlockingState = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%dms", this.delay.getValue())};
    }
}
