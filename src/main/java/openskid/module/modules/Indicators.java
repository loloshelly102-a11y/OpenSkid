package openskid.module.modules;

import openskid.enums.ChatColors;
import openskid.event.EventTarget;
import openskid.events.Render2DEvent;
import openskid.module.Module;
import openskid.util.RenderUtil;
import openskid.util.RotationUtil;
import openskid.util.TeamUtil;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Indicators extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty scale = new FloatProperty("scale", 1.0f, 0.5f, 1.5f);
    public final FloatProperty offset = new FloatProperty("offset", 50.0f, 0.0f, 255.0f);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty fireballs = new BooleanProperty("fireballs", true);
    public final BooleanProperty pearls = new BooleanProperty("pearls", true);
    public final BooleanProperty arrows = new BooleanProperty("arrows", true);
    public final BooleanProperty egg = new BooleanProperty("egg", true);
    public final BooleanProperty snowball = new BooleanProperty("snowball", true);
    public final BooleanProperty onlyApproaching = new BooleanProperty("only-approaching", false);
    public final IntProperty maxDistance = new IntProperty("max-distance", 64, 8, 128);

    private final Map<Integer, Double> lastDistances = new HashMap<Integer, Double>();
    private final List<Entity> indicatorEntities = new ArrayList<Entity>(32);
    private static final Color FIREBALL_COLOR = new Color(12676363);
    private static final Color PEARL_COLOR = new Color(2458740);
    private static final Color ARROW_COLOR = new Color(0x969696);
    private static final Color DEFAULT_COLOR = new Color(-1);
    private final ItemStack fireChargeStack = new ItemStack(Items.fire_charge);
    private final ItemStack pearlStack = new ItemStack(Items.ender_pearl);
    private final ItemStack arrowStack = new ItemStack(Items.arrow);
    private final ItemStack eggStack = new ItemStack(Items.egg);
    private final ItemStack snowballStack = new ItemStack(Items.snowball);

    private boolean shouldRender(Entity entity) {
        double d = (entity.posX - entity.lastTickPosX) * (Indicators.mc.thePlayer.posX - entity.posX) + (entity.posY - entity.lastTickPosY) * (Indicators.mc.thePlayer.posY + (double) Indicators.mc.thePlayer.getEyeHeight() - entity.posY - (double) entity.height / 2.0) + (entity.posZ - entity.lastTickPosZ) * (Indicators.mc.thePlayer.posZ - entity.posZ);
        if (d == 0.0) {
            return false;
        }
        if (d < 0.0) {
            if (this.directionCheck.getValue()) {
                return false;
            }
        }
        double dist = Indicators.mc.thePlayer.getDistanceToEntity(entity);
        if (dist > (double) this.maxDistance.getValue()) {
            return false;
        }
        if (this.onlyApproaching.getValue()) {
            Double last = this.lastDistances.get(Integer.valueOf(entity.getEntityId()));
            this.lastDistances.put(Integer.valueOf(entity.getEntityId()), Double.valueOf(dist));
            if (last == null || last.doubleValue() - dist <= 1.0) {
                return false;
            }
        }
        if (this.fireballs.getValue() && entity instanceof EntityFireball) return true;
        if (this.pearls.getValue() && entity instanceof EntityEnderPearl) return true;
        if (this.arrows.getValue() && entity instanceof EntityArrow) return true;
        if (this.egg.getValue() && entity instanceof EntityEgg) return true;
        if (this.snowball.getValue() && entity instanceof EntitySnowball) return true;
        return false;
    }

    private ItemStack getIndicatorStack(Entity entity) {
        if (entity instanceof EntityFireball) {
            return this.fireChargeStack;
        }
        if (entity instanceof EntityEnderPearl) {
            return this.pearlStack;
        }
        if (entity instanceof EntityArrow) {
            return this.arrowStack;
        }
        if (entity instanceof EntityEgg) {
            return this.eggStack;
        }
        if (entity instanceof EntitySnowball) {
            return this.snowballStack;
        }
        return this.arrowStack;
    }

    private Item getIndicatorItem(Entity entity) {
        if (entity instanceof EntityFireball) {
            return Items.fire_charge;
        }
        if (entity instanceof EntityEnderPearl) {
            return Items.ender_pearl;
        }
        if (entity instanceof EntityArrow) {
            return Items.arrow;
        }
        if (entity instanceof EntityEgg) {
            return Items.egg;
        }
        if (entity instanceof EntitySnowball) {
            return Items.snowball;
        }
        return new Item();
    }

    private Color getIndicatorColor(Entity entity) {
        if (entity instanceof EntityFireball) {
            return FIREBALL_COLOR;
        }
        if (entity instanceof EntityEnderPearl) {
            return PEARL_COLOR;
        }
        if (entity instanceof EntityArrow) {
            return ARROW_COLOR;
        }
        return DEFAULT_COLOR;
    }

    public Indicators() {
        super("Indicators", false, true, "Shows offscreen direction markers for incoming projectiles.");
    }

    @Override
    public void onDisabled() {
        this.lastDistances.clear();
    }

    @EventTarget
    public void onRender(Render2DEvent render2DEvent) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.lastDistances.size() > 512) {
            this.lastDistances.clear();
        }
        this.indicatorEntities.clear();
        for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (this.shouldRender(entity)) {
                this.indicatorEntities.add(entity);
            }
        }
        if (this.indicatorEntities.isEmpty()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float centerX = (float) sr.getScaledWidth() / 2.0f / this.scale.getValue();
        float centerY = (float) sr.getScaledHeight() / 2.0f / this.scale.getValue();
        float scaleValue = this.scale.getValue();
        float baseOffset = 10.0f + this.offset.getValue();
        double selfX = RenderUtil.lerpDouble(Indicators.mc.thePlayer.posX, Indicators.mc.thePlayer.prevPosX, render2DEvent.getPartialTicks());
        double selfZ = RenderUtil.lerpDouble(Indicators.mc.thePlayer.posZ, Indicators.mc.thePlayer.prevPosZ, render2DEvent.getPartialTicks());
        int grayColor = ChatColors.GRAY.toAwtColor() & 0xFFFFFF | 0xBF000000;
        RenderUtil.enableRenderState();
        for (Entity entity : this.indicatorEntities) {
            float yawBetween = RotationUtil.getYawBetween(selfX, selfZ, RenderUtil.lerpDouble(entity.posX, entity.prevPosX, render2DEvent.getPartialTicks()), RenderUtil.lerpDouble(entity.posZ, entity.prevPosZ, render2DEvent.getPartialTicks()));
            if (Indicators.mc.gameSettings.thirdPersonView == 2) {
                yawBetween += 180.0f;
            }
            float x = (float) Math.sin(Math.toRadians(yawBetween));
            float z = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0f;
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.scale(scaleValue, scaleValue, 0.0f);
            GlStateManager.translate(centerX, centerY, 0.0f);
            GlStateManager.pushMatrix();
            GlStateManager.translate(baseOffset * x - 8.0f, baseOffset * z - 8.0f, -300.0f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(this.getIndicatorStack(entity), 0, 0);
            GlStateManager.popMatrix();
            String string = String.format("%dm", (int) Indicators.mc.thePlayer.getDistanceToEntity(entity));
            GlStateManager.pushMatrix();
            GlStateManager.translate(baseOffset * x - (float) Indicators.mc.fontRendererObj.getStringWidth(string) / 2.0f + 1.0f, baseOffset * z + 1.0f, -100.0f);
            Indicators.mc.fontRendererObj.drawStringWithShadow(string, 0.0f, 0.0f, grayColor);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.translate((baseOffset + 15.0f) * x + 1.0f, (baseOffset + 15.0f) * z + 1.0f, -100.0f);
            RenderUtil.drawArrow(0.0f, 0.0f, (float) (Math.atan2(z, x) + Math.PI), 7.5f, 1.5f, this.getIndicatorColor(entity).getRGB());
            GlStateManager.popMatrix();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
        RenderUtil.disableRenderState();
    }
}
