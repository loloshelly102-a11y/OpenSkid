package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.PacketEvent;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SECOND", "CRITICALS", "W_TAP", "MOVE_SPEED", "KB_REDUCTION", "CRITICAL_HITS"});
    public final BooleanProperty moveRequireSprint = new BooleanProperty("move-require-sprint", true, () -> mode.getValue() == 3);
    public final FloatProperty moveMinDistance = new FloatProperty("move-min-distance", 2.5F, 1.0F, 6.0F, () -> mode.getValue() == 3);
    public final BooleanProperty kbRequireAir = new BooleanProperty("kb-require-air", true, () -> mode.getValue() == 4);
    public final IntProperty kbMinHurt = new IntProperty("kb-min-hurt", 1, 0, 10, () -> mode.getValue() == 4);
    public final FloatProperty critMinFall = new FloatProperty("crit-min-fall", 0.1F, 0.0F, 1.0F, () -> mode.getValue() == 5);
    public final BooleanProperty critRequireAir = new BooleanProperty("crit-require-air", true, () -> mode.getValue() == 5);

    private boolean sprintState = false;
    private boolean set = false;
    private boolean keepSprintWasEnabled = false;
    private double savedSlowdown = 0.0;

    private int blockedHits = 0;
    private int allowedHits = 0;

    public HitSelect() {
        super("HitSelect", false, false, "Blocks weak attacks and only swings under ideal conditions.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (event.getType() == EventType.POST) {
            this.resetMotion();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }

        if (event.getPacket() instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction packet = (C0BPacketEntityAction) event.getPacket();
            switch (packet.getAction()) {
                case START_SPRINTING:
                    this.sprintState = true;
                    break;
                case STOP_SPRINTING:
                    this.sprintState = false;
                    break;
            }
            return;
        }

        if (event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) event.getPacket();

            if (use.getAction() != C02PacketUseEntity.Action.ATTACK) {
                return;
            }

            Entity target = use.getEntityFromWorld(mc.theWorld);
            if (target == null || target instanceof EntityLargeFireball) {
                return;
            }

            if (!(target instanceof EntityLivingBase)) {
                return;
            }

            EntityLivingBase living = (EntityLivingBase) target;
            boolean allow = true;

            switch (this.mode.getValue()) {
                case 0: // SECOND
                    allow = this.prioritizeSecondHit(mc.thePlayer, living);
                    break;
                case 1: // CRITICALS
                    allow = this.prioritizeCriticalHits(mc.thePlayer);
                    break;
                case 2: // WTAP
                    allow = this.prioritizeWTapHits(mc.thePlayer, this.sprintState);
                    break;
                case 3: // MOVE_SPEED
                    allow = this.prioritizeMoveSpeedHits(mc.thePlayer, living, this.sprintState);
                    break;
                case 4: // KB_REDUCTION
                    allow = this.prioritizeKbReductionHits(mc.thePlayer);
                    break;
                case 5: // CRITICAL_HITS
                    allow = this.prioritizeGatedCriticalHits(mc.thePlayer);
                    break;
            }

            if (!allow) {
                event.setCancelled(true);
                this.blockedHits++;
            } else {
                this.allowedHits++;
            }
        }
    }

    private boolean prioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
        // If target is already hurt, allow the hit
        if (target.hurtTime != 0) {
            return true;
        }

        // If player hasn't recovered from hurt time, allow the hit
        if (player.hurtTime <= player.maxHurtTime - 1) {
            return true;
        }

        // If too close, allow the hit
        double dist = player.getDistanceToEntity(target);
        if (dist < 2.5) {
            return true;
        }

        // If not moving towards each other, allow the hit
        if (!this.isMovingTowards(target, player, 60.0)) {
            return true;
        }

        if (!this.isMovingTowards(player, target, 60.0)) {
            return true;
        }

        // Block the hit and fix motion
        this.fixMotion();
        return false;
    }

    private boolean prioritizeCriticalHits(EntityLivingBase player) {
        // If on ground, allow the hit
        if (player.onGround) {
            return true;
        }

        // If hurt, allow the hit
        if (player.hurtTime != 0) {
            return true;
        }

        // If falling, allow the hit (for crits)
        if (player.fallDistance > 0.0f) {
            return true;
        }

        // Block the hit and fix motion
        this.fixMotion();
        return false;
    }

    private boolean prioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
        // If against wall, allow the hit
        if (player.isCollidedHorizontally) {
            return true;
        }

        // If not moving forward, allow the hit
        if (!mc.gameSettings.keyBindForward.isKeyDown()) {
            return true;
        }

        // If already sprinting, allow the hit
        if (sprinting) {
            return true;
        }

        // Block the hit and fix motion
        this.fixMotion();
        return false;
    }

    // Move-speed preset adapted from Raven HitSelect Move speed preference: delay the hit until sprint is held.
    private boolean prioritizeMoveSpeedHits(EntityLivingBase player, EntityLivingBase target, boolean sprinting) {
        if (player.isCollidedHorizontally) {
            return true;
        }
        if (!MoveUtil.isMoving()) {
            return true;
        }
        if (player.getDistanceToEntity(target) < this.moveMinDistance.getValue()) {
            return true;
        }
        if (this.moveRequireSprint.getValue() && !sprinting) {
            this.fixMotion();
            return false;
        }
        return true;
    }

    // KB-reduction preset adapted from Raven HitSelect KB reduction preference: only swing inside the hurt window.
    private boolean prioritizeKbReductionHits(EntityLivingBase player) {
        if (this.kbRequireAir.getValue() && player.onGround) {
            this.fixMotion();
            return false;
        }
        if (player.hurtTime < this.kbMinHurt.getValue()) {
            this.fixMotion();
            return false;
        }
        if (!MoveUtil.isMoving()) {
            this.fixMotion();
            return false;
        }
        return true;
    }

    // Critical-hits preset adapted from Raven HitSelect Critical hits preference: only swing while falling.
    private boolean prioritizeGatedCriticalHits(EntityLivingBase player) {
        if (this.critRequireAir.getValue() && player.onGround) {
            this.fixMotion();
            return false;
        }
        if (player.motionY >= 0.0) {
            this.fixMotion();
            return false;
        }
        if (player.fallDistance < this.critMinFall.getValue()) {
            this.fixMotion();
            return false;
        }
        return true;
    }

    private void fixMotion() {
        if (this.set) {
            return;
        }

        KeepSprint keepSprint = (KeepSprint) OpenSkid.moduleManager.modules.get(KeepSprint.class);
        if (keepSprint == null) {
            return;
        }

        try {
            // Save the current slowdown value
            this.savedSlowdown = keepSprint.slowdown.getValue().doubleValue();
            this.keepSprintWasEnabled = keepSprint.isEnabled();

            // Temporarily enable KeepSprint silently so this internal motion fix does not spam toggles.
            if (!this.keepSprintWasEnabled) {
                keepSprint.setEnabled(true);
            }
            keepSprint.slowdown.setValue(0);

            this.set = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetMotion() {
        if (!this.set) {
            return;
        }

        KeepSprint keepSprint = (KeepSprint) OpenSkid.moduleManager.modules.get(KeepSprint.class);
        if (keepSprint == null) {
            return;
        }

        try {
            // Restore the original slowdown value
            keepSprint.slowdown.setValue((int) this.savedSlowdown);

            // Only restore the enabled state if HitSelect changed it.
            if (!this.keepSprintWasEnabled && keepSprint.isEnabled()) {
                keepSprint.setEnabled(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.set = false;
        this.keepSprintWasEnabled = false;
        this.savedSlowdown = 0.0;
    }

    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double maxAngle) {
        Vec3 currentPos = source.getPositionVector();
        Vec3 lastPos = new Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
        Vec3 targetPos = target.getPositionVector();

        // Calculate movement vector
        double mx = currentPos.xCoord - lastPos.xCoord;
        double mz = currentPos.zCoord - lastPos.zCoord;
        double movementLength = Math.sqrt(mx * mx + mz * mz);

        // If not moving, return false
        if (movementLength == 0.0) {
            return false;
        }

        // Normalize movement vector
        mx /= movementLength;
        mz /= movementLength;

        // Calculate vector to target
        double tx = targetPos.xCoord - currentPos.xCoord;
        double tz = targetPos.zCoord - currentPos.zCoord;
        double targetLength = Math.sqrt(tx * tx + tz * tz);

        // If target is at same position, return false
        if (targetLength == 0.0) {
            return false;
        }

        // Normalize target vector
        tx /= targetLength;
        tz /= targetLength;

        // Calculate dot product (cosine of angle between vectors)
        double dotProduct = mx * tx + mz * tz;

        // Check if angle is within threshold
        return dotProduct >= Math.cos(Math.toRadians(maxAngle));
    }

    @Override
    public void onEnabled() {
        this.blockedHits = 0;
        this.allowedHits = 0;
        this.sprintState = false;
    }

    @Override
    public void onDisabled() {
        this.resetMotion();
        this.sprintState = false;
        this.set = false;
        this.savedSlowdown = 0.0;
        this.blockedHits = 0;
        this.allowedHits = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
