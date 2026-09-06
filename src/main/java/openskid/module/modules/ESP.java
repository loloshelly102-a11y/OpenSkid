package openskid.module.modules;

import openskid.OpenSkid;
import openskid.enums.ChatColors;
import openskid.event.EventTarget;
import openskid.event.types.Priority;
import openskid.events.Render2DEvent;
import openskid.events.Render3DEvent;
import openskid.events.ResizeEvent;
import openskid.mixin.IAccessorEntityRenderer;
import openskid.mixin.IAccessorMinecraft;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.util.ColorUtil;
import openskid.util.RenderUtil;
import openskid.util.TeamUtil;
import openskid.util.shader.GlowShader;
import openskid.util.shader.OutlineShader;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final OutlineShader outlineRenderer = new OutlineShader();
    private final GlowShader glowShader = new GlowShader();
    private Framebuffer framebuffer = null;
    private boolean outline = true;
    private boolean glow = true;
    public final ModeProperty mode = new ModeProperty("mode", 2, new String[]{"NONE", "2D", "3D", "OUTLINE", "FAKECORNER", "FAKE2D", "GLOW", "RING", "SHADED"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final ModeProperty healthBar = new ModeProperty("health-bar", 0, new String[]{"NONE", "2D", "SKID"});
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);
    public final FloatProperty glowWidth = new FloatProperty("glow-width", 1.5F, 0.5F, 5.0F, () -> this.mode.getValue() == 6);
    public final IntProperty glowPasses = new IntProperty("glow-passes", 3, 1, 3, () -> this.mode.getValue() == 6);
    public final FloatProperty glowExpand = new FloatProperty("glow-expand", 0.12F, 0.02F, 0.4F, () -> this.mode.getValue() == 6);
    public final BooleanProperty redOnDamage = new BooleanProperty("red-on-damage", false);
    public final IntProperty maxDistance = new IntProperty("max-distance", 512, 0, 512);
    private final List<EntityPlayer> espPlayers = new ArrayList<EntityPlayer>(64);
    private static final Color TEAM_BLUE = new Color(ChatColors.BLUE.toAwtColor());
    private static final Color TEAM_RED = new Color(ChatColors.RED.toAwtColor());
    private static final Color WHITE = new Color(-1);
    private long hudColorBucket = -1L;
    private Color hudColorCached = WHITE;

    private void collectPlayers() {
        this.espPlayers.clear();
        if (mc.theWorld == null) {
            return;
        }
        for (net.minecraft.entity.Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (entity instanceof EntityPlayer && this.shouldRenderPlayer((EntityPlayer) entity)) {
                this.espPlayers.add((EntityPlayer) entity);
            }
        }
    }

    private boolean shouldRenderPlayer(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > (float) this.maxDistance.getValue()) {
            return false;
        } else if (!entityPlayer.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entityPlayer.getEntityBoundingBox(), 0.1F)) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.bots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.friends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.enemies.getValue() : this.players.getValue();
            }
        } else {
            return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (this.redOnDamage.getValue() && entityPlayer.hurtTime > 0) {
            return new Color(Color.RED.getRGB());
        } else if (TeamUtil.isFriend(entityPlayer)) {
            return OpenSkid.friendManager.getColor();
        } else if (TeamUtil.isTarget(entityPlayer)) {
            return OpenSkid.targetManager.getColor();
        } else {
            switch (this.color.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, 1.0F);
                case 1:
                    return TeamUtil.isSameTeam(entityPlayer) ? TEAM_BLUE : TEAM_RED;
                case 2:
                    long bucket = System.currentTimeMillis() / 50L;
                    if (bucket != this.hudColorBucket) {
                        this.hudColorBucket = bucket;
                        int hudColor = ((HUD) OpenSkid.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                        this.hudColorCached = new Color(hudColor);
                    }
                    return this.hudColorCached;
                default:
                    return WHITE;
            }
        }
    }

    public ESP() {
        super("ESP", false, false, "Highlights players through walls with boxes and glow.");
    }

    // Adapted from Expo Chams glow-ish layered-shell idea, rebuilt on RenderUtil box.
    private void renderGlow(EntityPlayer player, Color color) {
        int passes = this.glowPasses.getValue();
        if (passes < 1) passes = 1;
        if (passes > 3) passes = 3;
        float width = this.glowWidth.getValue();
        float step = this.glowExpand.getValue();
        int alpha = color.getAlpha();
        if (alpha <= 0) alpha = 255;
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        for (int i = 0; i < passes; i++) {
            double expand = 0.1D + step * i;
            int ai = alpha / (1 + i);
            if (ai < 8) ai = 8;
            if (ai > 255) ai = 255;
            float w = width - i * 0.4F;
            if (w < 0.6F) w = 0.6F;
            RenderUtil.drawEntityBoundingBox(player, red, green, blue, ai, w, expand);
        }
        GlStateManager.resetColor();
    }

    private void renderRing(EntityPlayer player, Color color) {
        float partialTicks = ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
        double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, partialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, partialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY() + 0.05;
        double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, partialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        RenderUtil.drawCircle(x, y, z, 0.7, 45, color.getRGB());
        GlStateManager.resetColor();
    }

    private void renderShaded(EntityPlayer player, Color color) {
        RenderUtil.drawEntityBox(player, color.getRed(), color.getGreen(), color.getBlue());
        GlStateManager.resetColor();
    }

    public boolean isOutlineEnabled() {
        return this.outline;
    }

    public boolean isGlowEnabled() {
        return this.glow;
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
        }
        this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    }

    @EventTarget(Priority.HIGH)
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == 1 || this.mode.getValue() == 3 || this.healthBar.getValue() == 1)) {
            this.collectPlayers();
            if (!this.espPlayers.isEmpty()) {
                if (this.mode.getValue() == 3) {
                    GlStateManager.pushMatrix();
                    GlStateManager.pushAttrib();
                    if (this.framebuffer == null) {
                        this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
                    }
                    this.framebuffer.bindFramebuffer(false);
                    ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
                    boolean shadow = mc.gameSettings.entityShadows;
                    mc.gameSettings.entityShadows = false;
                    this.outline = false;
                    this.glow = false;
                    this.glowShader.use();
                    for (EntityPlayer player : this.espPlayers) {
                        Color entityColor = this.getEntityColor(player);
                        this.glowShader.W(entityColor);
                        boolean invisible = player.isInvisible();
                        player.setInvisible(false);
                        mc.getRenderManager().renderEntityStatic(player, event.getPartialTicks(), true);
                        player.setInvisible(invisible);
                    }
                    this.glowShader.stop();
                    this.glow = true;
                    this.outline = true;
                    mc.gameSettings.entityShadows = shadow;
                    mc.entityRenderer.disableLightmap();
                    mc.entityRenderer.setupOverlayRendering();
                    mc.getFramebuffer().bindFramebuffer(false);
                    this.outlineRenderer.use();
                    RenderUtil.drawFramebuffer(this.framebuffer);
                    this.outlineRenderer.stop();
                    this.framebuffer.framebufferClear();
                    mc.getFramebuffer().bindFramebuffer(false);
                    GlStateManager.popAttrib();
                    GlStateManager.popMatrix();
                }
                if (this.mode.getValue() == 1 || this.healthBar.getValue() == 1) {
                    RenderUtil.enableRenderState();
                    ScaledResolution sr = new ScaledResolution(mc);
                    double scaleFactor = sr.getScaleFactor();
                    double scale = scaleFactor / Math.pow(scaleFactor, 2.0);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, scale);
                    ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
                    for (EntityPlayer player : this.espPlayers) {
                        Vector4d screenPosition = RenderUtil.projectToScreen(player, scaleFactor);
                        if (screenPosition != null) {
                            mc.entityRenderer.setupOverlayRendering();
                            float x = (float) screenPosition.x;
                            float y = (float) screenPosition.y;
                            float z = (float) screenPosition.z;
                            float w = (float) screenPosition.w;
                            if (this.mode.getValue() == 1) {
                                int color = this.getEntityColor(player).getRGB();
                                RenderUtil.drawOutlineRect(x, y, z, w, 3.0F, 0, (color & 16579836) >> 2 | color & 0xFF000000);
                                RenderUtil.drawOutlineRect(x, y, z, w, 1.5F, 0, color);
                            }
                            if (this.healthBar.getValue() == 1) {
                                float heal = player.getHealth() + player.getAbsorptionAmount();
                                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                                float box = (z - x) * 0.08F;
                                Color healthColor = ColorUtil.getHealthBlend(percent);
                                RenderUtil.drawLine(x - box, y, x - box, w, 3.0F, ColorUtil.darker(healthColor, 0.2F).getRGB());
                                RenderUtil.drawLine(x - box, w, x - box, w + (y - w) * percent, 1.5F, healthColor.getRGB());
                            }
                        }
                    }
                    GlStateManager.popMatrix();
                    RenderUtil.disableRenderState();
                }
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == 2 || this.mode.getValue() == 4 || this.mode.getValue() == 5 || this.mode.getValue() == 6 || this.mode.getValue() == 7 || this.mode.getValue() == 8 || this.healthBar.getValue() == 2)) {
            this.collectPlayers();
            RenderUtil.enableRenderState();
            double renderX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
            double renderY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
            double renderZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
            int renderMode = this.mode.getValue();
            int barMode = this.healthBar.getValue();
            for (EntityPlayer player : this.espPlayers) {
                if (player.ignoreFrustumCheck || RenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) {
                    if (renderMode == 2 || renderMode == 4 || renderMode == 5 || renderMode == 6 || renderMode == 7 || renderMode == 8) {
                        Color color = this.getEntityColor(player);
                        float r = (float) color.getRed() / 255.0F;
                        float g = (float) color.getGreen() / 255.0F;
                        float b = (float) color.getBlue() / 255.0F;
                        if (renderMode == 2) {
                            RenderUtil.drawEntityBoundingBox(player, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 1.5F, 0.1F);
                            GlStateManager.resetColor();
                        }
                        if (renderMode == 4) {
                            // is it me or this fucking line looks cursed
                            RenderUtil.drawCornerESP(player, r, g, b);
                        }
                        if (renderMode == 5) {
                            RenderUtil.drawFake2DESP(player, r, g, b);
                        }
                        if (renderMode == 6) {
                            this.renderGlow(player, color);
                        }
                        if (renderMode == 7) {
                            this.renderRing(player, color);
                        }
                        if (renderMode == 8) {
                            this.renderShaded(player, color);
                        }
                    }
                    if (barMode == 2) {
                        double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks()) - renderX;
                        double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - renderY - 0.1F;
                        double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks()) - renderZ;
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(x, y, z);
                        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                        float heal = player.getHealth() + player.getAbsorptionAmount();
                        float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                        Color healthColor = ColorUtil.getHealthBlend(percent);
                        float height = player.height + 0.2F;
                        RenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, Color.black.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, Color.darkGray.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor.getRGB());
                        GlStateManager.popMatrix();
                    }
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}