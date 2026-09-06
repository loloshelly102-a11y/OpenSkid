package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.AttackEvent;
import openskid.events.MoveInputEvent;
import openskid.events.PacketEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.PercentProperty;
import openskid.util.RandomUtil;
import openskid.util.TimerUtil;
import openskid.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

// WTap modes WTap/SprintReset/MoreKB are canonical for WTap style movement gating.
// Standalone SprintReset and MoreKB modules are distinct implementations. Keep separate.
public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    public final FloatProperty delay = new FloatProperty("delay", 5.5F, 0.0F, 10.0F);
    public final FloatProperty duration = new FloatProperty("duration", 1.5F, 1.0F, 5.0F);
    private final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"WTap", "SprintReset", "MoreKB"});
    private final PercentProperty chance = new PercentProperty("chance", 100, () -> this.mode.getValue() != 0);
    private final BooleanProperty notWhenHurt = new BooleanProperty("not-when-hurt", true, () -> this.mode.getValue() == 1);
    private final BooleanProperty playersOnly = new BooleanProperty("players-only", true, () -> this.mode.getValue() == 2);
    private final IntProperty reSprintDelay = new IntProperty("re-sprint-delay", 2, 0, 9, () -> this.mode.getValue() == 2);
    private final IntProperty releaseDelayMs = new IntProperty("release-delay", 0, 0, 1000, () -> this.mode.getValue() == 0);
    private final IntProperty repressDelayMs = new IntProperty("repress-delay", 0, 0, 1000, () -> this.mode.getValue() == 0);
    private final PercentProperty jumpResetChance = new PercentProperty("jump-reset-chance", 70, () -> this.mode.getValue() == 0);
    private long lastWtapMs = 0L;
    private EntityLivingBase lastTarget;
    private long lastAttackTime;
    private int attackSelfHurtTime;
    private boolean srShouldReset;
    private int srDelayTicks;
    private int srTickCounter;
    private EntityLivingBase kbTarget;
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil reSprintTimer = new TimerUtil();
    private boolean resyncNeeded;
    private int nextSprintTime;

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying) && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    public Wtap() {
        super("WTap", false, false, "Releases forward briefly after hits for combos.");
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && mc.theWorld != null) {
            if (this.mode.getValue() == 1) {
                // Ported from MiauMinus ghost SprintReset.java (hurt-time gated reset).
                this.active = false;
                this.tickSprintReset();
                if (this.shouldSprintReset() && mc.thePlayer.movementInput != null) {
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                    mc.thePlayer.movementInput.moveStrafe = 0.0F;
                }
                return;
            }
            if (this.mode.getValue() == 2) {
                // Ported from MiauMinus ghost MoreKB.java (WTap re-sprint sync).
                this.active = false;
                this.tickMoreKB();
                if (this.shouldMoreKB() && mc.thePlayer.movementInput != null) {
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                    mc.thePlayer.movementInput.moveStrafe = 0.0F;
                }
                return;
            }
        }
        if (this.active) {
            if (!this.stopForward && !this.canTrigger()) {
                this.active = false;
                while (this.delayTicks > 0L) {
                    this.delayTicks -= 50L;
                }
                while (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                }
            } else if (this.delayTicks > 0L) {
                this.delayTicks -= 50L;
            } else {
                if (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                    this.stopForward = true;
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                }
                if (this.durationTicks <= 0L) {
                    this.active = false;
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) event.getPacket()).getAction() == Action.ATTACK
                    && !this.active
                    && this.mode.getValue() == 0
                    && this.timer.hasTimeElapsed(500L)
                    && mc.thePlayer.isSprinting()) {
                // Repress delay is the minimum gap between resets, adapted from
                // raven delay-between-reset. Release delay pushes the forward
                // stop later, adapted from raven delay-until-reset. Both default
                // to 0 so old timing is unchanged.
                long now = System.currentTimeMillis();
                if (now - this.lastWtapMs < (long) this.repressDelayMs.getValue()) {
                    return;
                }
                this.lastWtapMs = now;
                this.timer.reset();
                this.active = true;
                this.stopForward = false;
                this.delayTicks = this.delayTicks + (long) (50.0F * this.delay.getValue()) + (long) this.releaseDelayMs.getValue();
                this.durationTicks = this.durationTicks + (long) (50.0F * this.duration.getValue());
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }
        // Ported from raven SimpleSprintReset.java (chance gate on attack).
        if (this.mode.getValue() != 0 && RandomUtil.nextInt(1, 100) > this.chance.getValue()) {
            return;
        }
        // Jump reset hops on hit for extra KB, with a chance gate so some hits
        // skip the jump for a legit look. Adapted from donor jump-reset chance.
        if (this.mode.getValue() == 0
                && this.jumpResetChance.getValue() > 0
                && mc.thePlayer.onGround
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isOnLadder()
                && RandomUtil.nextInt(1, 100) <= this.jumpResetChance.getValue()) {
            mc.thePlayer.jump();
        }
        if (this.mode.getValue() == 1) {
            this.lastTarget = (EntityLivingBase) event.getTarget();
            this.lastAttackTime = System.currentTimeMillis();
            this.attackSelfHurtTime = mc.thePlayer.hurtTime;
        } else if (this.mode.getValue() == 2) {
            if (this.playersOnly.getValue() && !(event.getTarget() instanceof EntityPlayer)) {
                return;
            }
            this.kbTarget = (EntityLivingBase) event.getTarget();
            this.attackTimer.reset();
        }
    }

    private void tickSprintReset() {
        if (this.lastTarget != null && !this.lastTarget.isDead
                && System.currentTimeMillis() - this.lastAttackTime <= 250L
                && this.lastTarget.hurtTime == 10 && mc.thePlayer.isSprinting()) {
            this.srShouldReset = true;
            this.srDelayTicks = RandomUtil.nextInt(2, 4);
            this.srTickCounter = 0;
        }
        if (this.srShouldReset) {
            this.srTickCounter++;
            if (this.srTickCounter >= this.srDelayTicks) {
                this.srShouldReset = false;
            }
        }
    }

    private boolean shouldSprintReset() {
        if (!this.srShouldReset || this.lastTarget == null) {
            return false;
        }
        if (this.attackSelfHurtTime != 0 && mc.thePlayer.hurtTime <= 2) {
            return false;
        }
        boolean inCritFall = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround
                && !mc.thePlayer.isInWater() && !mc.thePlayer.isOnLadder();
        if (inCritFall) {
            return false;
        }
        return mc.thePlayer.hurtTime == 0 || !this.notWhenHurt.getValue();
    }

    private void tickMoreKB() {
        if (this.kbTarget == null || this.kbTarget.isDead
                || mc.thePlayer.getDistanceToEntity(this.kbTarget) > 4.5F) {
            this.kbTarget = this.getTarget(4.5D);
        }
        if (this.kbTarget != null && this.kbTarget.hurtTime == 10
                && !this.attackTimer.hasTimeElapsed(250L) && mc.thePlayer.isSprinting()) {
            this.resyncNeeded = true;
            this.nextSprintTime = this.reSprintDelay.getValue();
            this.reSprintTimer.reset();
        }
    }

    private boolean shouldMoreKB() {
        if (this.resyncNeeded && this.reSprintTimer.hasTimeElapsed((long) this.nextSprintTime * 50L)) {
            this.resyncNeeded = false;
            return true;
        }
        return false;
    }

    private EntityLivingBase getTarget(double range) {
        EntityLivingBase best = null;
        double bestDist = range;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityLivingBase && o != mc.thePlayer) {
                double d = mc.thePlayer.getDistanceToEntity((EntityLivingBase) o);
                if (d <= bestDist) {
                    bestDist = d;
                    best = (EntityLivingBase) o;
                }
            }
        }
        return best;
    }

    @Override
    public void onEnabled() {
        this.resetState();
    }

    @Override
    public void onDisabled() {
        this.resetState();
    }

    private void resetState() {
        this.active = false;
        this.stopForward = false;
        this.delayTicks = 0L;
        this.durationTicks = 0L;
        this.timer.reset();
        this.lastTarget = null;
        this.lastAttackTime = 0L;
        this.attackSelfHurtTime = 0;
        this.srShouldReset = false;
        this.srDelayTicks = 0;
        this.srTickCounter = 0;
        this.kbTarget = null;
        this.attackTimer.reset();
        this.reSprintTimer.reset();
        this.resyncNeeded = false;
        this.nextSprintTime = 0;
        this.lastWtapMs = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
