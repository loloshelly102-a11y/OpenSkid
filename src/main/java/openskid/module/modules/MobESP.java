package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.events.Render3DEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ColorProperty;
import openskid.property.properties.IntProperty;
import openskid.util.RenderUtil;
import openskid.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

// Hostile plus passive mob boxes with per-type filters. Concept adapted from the donor MobESP module.
public class MobESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty hostiles = new BooleanProperty("hostiles", true);
    public final BooleanProperty passives = new BooleanProperty("passives", true);
    public final BooleanProperty bosses = new BooleanProperty("bosses", false);
    public final BooleanProperty creepers = new BooleanProperty("creepers", true);
    public final BooleanProperty endermen = new BooleanProperty("endermen", true);
    public final BooleanProperty blazes = new BooleanProperty("blazes", true);
    public final BooleanProperty showInvis = new BooleanProperty("show-invis", false);
    public final IntProperty distance = new IntProperty("distance", 48, 8, 64);
    public final ColorProperty hostileColor = new ColorProperty("hostile-color", 0xFF5555);
    public final ColorProperty passiveColor = new ColorProperty("passive-color", 0x55FF55);

    private final List<EntityLivingBase> hostileTargets = new ArrayList<EntityLivingBase>(64);
    private final List<EntityLivingBase> passiveTargets = new ArrayList<EntityLivingBase>(64);

    public MobESP() {
        super("MobESP", false, false, "Draws boxes around nearby mobs and animals.");
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(hostileTargets.size() + passiveTargets.size())};
    }

    @Override
    public void onEnabled() {
        hostileTargets.clear();
        passiveTargets.clear();
    }

    @Override
    public void onDisabled() {
        hostileTargets.clear();
        passiveTargets.clear();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }
        hostileTargets.clear();
        passiveTargets.clear();
        float maxDist = distance.getValue().floatValue();
        boolean showHostiles = hostiles.getValue();
        boolean showPassives = passives.getValue();
        boolean showBosses = bosses.getValue();
        boolean allowInvis = showInvis.getValue();
        if (!showHostiles && !showPassives && !showBosses) {
            return;
        }
        for (net.minecraft.entity.Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            if (entity instanceof EntityPlayer || entity instanceof EntityArmorStand) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.deathTime > 0) {
                continue;
            }
            if (!allowInvis && living.isInvisible()) {
                continue;
            }
            if (mc.getRenderViewEntity().getDistanceToEntity(living) > maxDist) {
                continue;
            }
            if (!living.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(living.getEntityBoundingBox(), 0.1)) {
                continue;
            }
            if (living instanceof EntityDragon || living instanceof EntityWither) {
                if (showBosses) {
                    hostileTargets.add(living);
                }
            } else if (living instanceof EntityCreeper) {
                if (showHostiles && creepers.getValue()) {
                    hostileTargets.add(living);
                }
            } else if (living instanceof EntityEnderman) {
                if (showHostiles && endermen.getValue()) {
                    hostileTargets.add(living);
                }
            } else if (living instanceof EntityBlaze) {
                if (showHostiles && blazes.getValue()) {
                    hostileTargets.add(living);
                }
            } else if (living instanceof EntityMob || living instanceof EntitySlime || living instanceof EntityGhast) {
                if (showHostiles) {
                    hostileTargets.add(living);
                }
            } else if (living instanceof EntityAnimal || living instanceof EntityVillager
                    || living instanceof EntitySquid || living instanceof EntityBat) {
                if (showPassives) {
                    passiveTargets.add(living);
                }
            }
        }
        if (hostileTargets.isEmpty() && passiveTargets.isEmpty()) {
            return;
        }
        int hostileRGB = hostileColor.getValue();
        int hr = (hostileRGB >> 16) & 0xFF;
        int hg = (hostileRGB >> 8) & 0xFF;
        int hb = hostileRGB & 0xFF;
        int passiveRGB = passiveColor.getValue();
        int pr = (passiveRGB >> 16) & 0xFF;
        int pg = (passiveRGB >> 8) & 0xFF;
        int pb = passiveRGB & 0xFF;
        RenderUtil.enableRenderState();
        for (EntityLivingBase living : hostileTargets) {
            RenderUtil.drawEntityBoundingBox(living, hr, hg, hb, 255, 1.5F, 0.1);
            RenderUtil.drawEntityBox(living, hr, hg, hb);
            GlStateManager.resetColor();
        }
        for (EntityLivingBase living : passiveTargets) {
            RenderUtil.drawEntityBoundingBox(living, pr, pg, pb, 255, 1.5F, 0.1);
            RenderUtil.drawEntityBox(living, pr, pg, pb);
            GlStateManager.resetColor();
        }
        RenderUtil.disableRenderState();
    }
}
