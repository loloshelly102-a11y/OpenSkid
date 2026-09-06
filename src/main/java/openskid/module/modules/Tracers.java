package openskid.module.modules;

import openskid.OpenSkid;
import openskid.enums.ChatColors;
import openskid.event.EventTarget;
import openskid.events.Render2DEvent;
import openskid.events.Render3DEvent;
import openskid.mixin.IAccessorMinecraft;
import openskid.module.Module;
import openskid.util.RenderUtil;
import openskid.util.RotationUtil;
import openskid.util.TeamUtil;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.PercentProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Tracers extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final BooleanProperty drawLines = new BooleanProperty("lines", true);
    public final BooleanProperty drawArrows = new BooleanProperty("arrows", false);
    public final PercentProperty opacity = new PercentProperty("opacity", 100);
    public final IntProperty distance = new IntProperty("distance", 512, 0, 512);
    public final BooleanProperty showPlayers = new BooleanProperty("players", true);
    public final BooleanProperty showFriends = new BooleanProperty("friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("bots", false);
    public final FloatProperty lineWidth = new FloatProperty("line-width", 1.5F, 0.5F, 5.0F);
    public final BooleanProperty showInvis = new BooleanProperty("show-invis", true);
    public final BooleanProperty rainbow = new BooleanProperty("rainbow", false);
    private final List<EntityPlayer> tracerPlayers = new ArrayList<EntityPlayer>(64);
    private float cachedAlpha = -1.0F;
    private long cachedHudBucket = -1L;
    private int cachedHudBase = 0;
    private Color cachedBlue;
    private Color cachedRed;
    private Color cachedHud;
    private Color cachedWhite;

    private void collectPlayers() {
        this.tracerPlayers.clear();
        if (mc.theWorld == null) {
            return;
        }
        for (net.minecraft.entity.Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)) {
                this.tracerPlayers.add((EntityPlayer) entity);
            }
        }
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (!this.showInvis.getValue() && entityPlayer.isInvisible()) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > (float) this.distance.getValue()) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.showBots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.showFriends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.showEnemies.getValue() : this.showPlayers.getValue();
            }
        } else {
            return false;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer, float alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = OpenSkid.friendManager.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = OpenSkid.targetManager.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else {
            switch (this.rainbow.getValue() ? 2 : this.colorMode.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, alpha);
                case 1: {
                    boolean sameTeam = TeamUtil.isSameTeam(entityPlayer);
                    if (this.cachedBlue == null || this.cachedRed == null || this.cachedAlpha != alpha) {
                        int teamBlue = ChatColors.BLUE.toAwtColor();
                        int teamRed = ChatColors.RED.toAwtColor();
                        this.cachedBlue = new Color(teamBlue & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
                        this.cachedRed = new Color(teamRed & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
                        this.cachedAlpha = alpha;
                    }
                    return sameTeam ? this.cachedBlue : this.cachedRed;
                }
                case 2: {
                    long bucket = System.currentTimeMillis() / 50L;
                    int base = ((HUD) OpenSkid.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                    if (this.cachedHud == null || bucket != this.cachedHudBucket || base != this.cachedHudBase || this.cachedAlpha != alpha) {
                        this.cachedHudBucket = bucket;
                        this.cachedHudBase = base;
                        this.cachedAlpha = alpha;
                        this.cachedHud = new Color(base & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
                    }
                    return this.cachedHud;
                }
                default:
                    if (this.cachedWhite == null || this.cachedAlpha != alpha) {
                        this.cachedWhite = new Color(1.0F, 1.0F, 1.0F, alpha);
                        this.cachedAlpha = alpha;
                    }
                    return this.cachedWhite;
            }
        }
    }

    public Tracers() {
        super("Tracers", false, false, "Draws lines from you to nearby players.");
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && this.drawLines.getValue()) {
            RenderUtil.enableRenderState();
            Vec3 position;
            if (mc.gameSettings.thirdPersonView == 0) {
                position = new Vec3(0.0, 0.0, 1.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationPitch,
                                                        mc.getRenderViewEntity().prevRotationPitch,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationYaw,
                                                        mc.getRenderViewEntity().prevRotationYaw,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        );
            } else {
                position = new Vec3(0.0, 0.0, 0.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                                        )
                                )
                        );
            }
            position = new Vec3(position.xCoord, position.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), position.zCoord);
            this.collectPlayers();
            float lineAlpha = (float) this.opacity.getValue() / 100.0F;
            for (EntityPlayer player : this.tracerPlayers) {
                Color color = this.getEntityColor(player, lineAlpha);
                double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - (player.isSneaking() ? 0.125 : 0.0);
                double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks());
                RenderUtil.drawLine3D(
                        position,
                        x,
                        y + (double) player.getEyeHeight(),
                        z,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        (float) color.getAlpha() / 255.0F,
                        this.lineWidth.getValue()
                );
            }
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && this.drawArrows.getValue()) {
            this.collectPlayers();
            if (this.tracerPlayers.isEmpty()) {
                return;
            }
            HUD hud = (HUD) OpenSkid.moduleManager.modules.get(HUD.class);
            float hudScale = hud.scale.getValue();
            ScaledResolution sr = new ScaledResolution(mc);
            float centerX = (float) sr.getScaledWidth() / 2.0F / hudScale;
            float centerY = (float) sr.getScaledHeight() / 2.0F / hudScale;
            float baseOpacity = this.opacity.getValue().floatValue() / 100.0F;
            double selfX = RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.getPartialTicks());
            double selfZ = RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.getPartialTicks());
            GlStateManager.pushMatrix();
            GlStateManager.scale(hudScale, hudScale, 0.0F);
            GlStateManager.translate(centerX, centerY, 0.0F);
            RenderUtil.enableRenderState();
            for (EntityPlayer player : this.tracerPlayers) {
                float yawBetween = RotationUtil.getYawBetween(
                        selfX,
                        selfZ,
                        RenderUtil.lerpDouble(player.posX, player.prevPosX, event.getPartialTicks()),
                        RenderUtil.lerpDouble(player.posZ, player.prevPosZ, event.getPartialTicks())
                );
                if (mc.gameSettings.thirdPersonView == 2) {
                    yawBetween += 180.0F;
                }
                float arrowDirX = (float) Math.sin(Math.toRadians(yawBetween));
                float arrowDirY = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0F;
                float opacity = baseOpacity;
                yawBetween = Math.abs(MathHelper.wrapAngleTo180_float(yawBetween));
                if (yawBetween < 30.0F) {
                    opacity = 0.0F;
                } else if (yawBetween < 60.0F) {
                    opacity *= (yawBetween - 30.0F) / 30.0F;
                }
                GlStateManager.pushMatrix();
                GlStateManager.translate(55.0F * arrowDirX + 1.0F, 55.0F * arrowDirY + 1.0F, -100.0F);
                RenderUtil.drawTriangle(
                        0.0F,
                        0.0F,
                        (float) (Math.atan2(arrowDirY, arrowDirX) + Math.PI),
                        10.0F,
                        this.getEntityColor(player, opacity).getRGB()
                );
                GlStateManager.popMatrix();
            }
            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();
        }
    }
}