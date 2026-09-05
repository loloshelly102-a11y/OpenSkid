package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.ItemUtil;
import openskid.util.PacketUtil;
import openskid.util.RotationUtil;
import openskid.util.TeamUtil;
import openskid.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.Vec3;

public class ThrowAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty cooldown = new IntProperty("cooldown", 500, 0, 2000);
    public final FloatProperty maxRange = new FloatProperty("max-range", 20.0F, 3.0F, 64.0F);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SLAVE", "AIMED"});
    public final IntProperty leadTicks = new IntProperty("lead-ticks", 3, 0, 10, () -> mode.getValue() == 1);
    public final IntProperty fov = new IntProperty("fov", 360, 30, 360, () -> mode.getValue() == 1);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true, () -> mode.getValue() == 1);
    public final BooleanProperty ignoreInvis = new BooleanProperty("ignore-invis", true, () -> mode.getValue() == 1);
    private final TimerUtil timer = new TimerUtil();

    public ThrowAura() {
        super("ThrowAura", false, true, "Throws projectiles at your KillAura target automatically.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.PRE) return;

        KillAura killAura = (KillAura) openskid.OpenSkid.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return;

        EntityLivingBase target = killAura.getTarget();
        if (target == null || target.isDead) return;

        if (mode.getValue() == 0) {
            double distance = mc.thePlayer.getDistanceToEntity(target);
            if (distance > killAura.attackRange.getValue() && distance <= maxRange.getValue()) {
                int projectileCount = ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile);
                if (projectileCount > 0 && timer.hasTimeElapsed(cooldown.getValue().longValue())) {
                    int projectileSlot = findProjectileHotbarSlot();
                    if (projectileSlot != -1) {
                        float[] rotations = RotationUtil.getRotationsToBox(
                                target.getEntityBoundingBox(),
                                event.getYaw(),
                                event.getPitch(),
                                180.0F,
                                0.0F
                        );
                        event.setRotation(rotations[0], rotations[1], 1);
                        throwProjectile(projectileSlot);
                        timer.reset();
                    }
                }
            }
            return;
        }

        // AIMED mode. Shared-bus aim adapted from the donor RotationHandler
        // idea (central helpers, per-module gating), reimplemented via RotationUtil.
        if (target == mc.thePlayer) return;
        if (ignoreInvis.getValue() && target.isInvisible()) return;
        if (ignoreTeammates.getValue() && target instanceof EntityPlayer && TeamUtil.isSameTeam((EntityPlayer) target)) return;

        double distance = mc.thePlayer.getDistanceToEntity(target);
        if (distance <= killAura.attackRange.getValue() || distance > maxRange.getValue()) return;
        if (RotationUtil.angleToEntity(target) > fov.getValue().floatValue()) return;
        if (!RotationUtil.raycastValid(target, maxRange.getValue().doubleValue())) return;

        int projectileCount = ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile);
        if (projectileCount > 0 && timer.hasTimeElapsed(cooldown.getValue().longValue())) {
            int projectileSlot = findProjectileHotbarSlot();
            if (projectileSlot != -1) {
                Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
                Vec3 lead = RotationUtil.leadPoint(target, leadTicks.getValue());
                float[] rotations = RotationUtil.getRotations(
                        lead.xCoord - eye.xCoord,
                        lead.yCoord - eye.yCoord,
                        lead.zCoord - eye.zCoord,
                        event.getYaw(),
                        event.getPitch(),
                        180.0F,
                        0.0F
                );
                event.setRotation(rotations[0], rotations[1], 1);
                throwProjectile(projectileSlot);
                timer.reset();
            }
        }
    }

    private void throwProjectile(int projectileSlot) {
        int originalSlot = mc.thePlayer.inventory.currentItem;
        if (projectileSlot != originalSlot) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(projectileSlot));
        }
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(projectileSlot)));
        if (projectileSlot != originalSlot) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(originalSlot));
        }
    }

    private int findProjectileHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (ItemUtil.isProjectile(stack)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String[] getSuffix() {
        try {
            if (mc.thePlayer == null) {
                return new String[]{"0"};
            }
            int count = ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile);
            return new String[]{String.valueOf(count)};
        } catch (Exception e) {
            return new String[]{"0"};
        }
    }
}