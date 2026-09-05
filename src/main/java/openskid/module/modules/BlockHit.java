package openskid.module.modules;

import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.AttackEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.*;
import openskid.util.ItemUtil;
import openskid.util.KeyBindUtil;
import openskid.util.RandomUtil;
import openskid.util.TimerUtil;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Helper", "Auto", "Lag", "Manual", "Legit", "Combo", "FakeBlock"});
    private final IntProperty stopTime = new IntProperty("StopTicks", 2, 1, 5, () -> this.mode.getValue() == 0);
    private final ModeProperty autoBlockTime = new ModeProperty("AutoBlockTime", 0, new String[]{"Delay", "HurtTime", "Sag"}, () -> this.mode.getValue() == 1);
    private final ModeProperty autoMode = new ModeProperty("AutoMode", 0, new String[]{"Spam", "Hold"}, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty holdTick = new IntProperty("HoldTick", 2, 2, 5, () -> this.mode.getValue() == 1 && this.autoMode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty blockDelay = new IntProperty("BlockDelay", 100, 0, 1000, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty minHurtTime = new IntProperty("MinHurtTime", 10, 1, 10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty maxHurtTime = new IntProperty("MaxHurtTime", 10, 1, 10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty delayPacketTick = new IntProperty("DelayPacketTick", 2, 1, 10, () -> this.mode.getValue() == 2);
    private final IntProperty blockTick = new IntProperty("BlockTick", 3, 1, 5, () -> this.mode.getValue() == 2);
    private final PercentProperty chance = new PercentProperty("BlockHitChance", 50, () -> this.mode.getValue() == 1);
    private final BooleanProperty smart = new BooleanProperty("Smart", true, () -> this.mode.getValue() == 1);
    private final BooleanProperty autoBlockRange = new BooleanProperty("AutoBlockRange", true, () -> this.mode.getValue() == 1);
    private final FloatProperty range = new FloatProperty("Range", 3.0f, 1f, 4f, () -> autoBlockRange.getValue() && mode.getValue() == 1);
    private final IntProperty manualHitEvery = new IntProperty("ManualHitEvery", 2, 1, 10, () -> this.mode.getValue() == 3);
    private final IntProperty manualActionTicks = new IntProperty("ManualActionTicks", 3, 1, 10, () -> this.mode.getValue() == 3);
    private final FloatProperty manualRange = new FloatProperty("ManualRange", 3.0f, 1f, 6f, () -> this.mode.getValue() == 3);
    private final FloatProperty manualFov = new FloatProperty("ManualFov", 90.0f, 10f, 180f, () -> this.mode.getValue() == 3);
    private final PercentProperty manualChance = new PercentProperty("ManualChance", 100, () -> this.mode.getValue() == 3);
    private final IntProperty legitHurt = new IntProperty("LegitHurt", 2, 0, 10, () -> this.mode.getValue() == 4);
    private final FloatProperty legitRange = new FloatProperty("LegitRange", 3.0f, 1f, 6f, () -> this.mode.getValue() == 4);
    private final FloatProperty legitFov = new FloatProperty("LegitFov", 90.0f, 10f, 180f, () -> this.mode.getValue() == 4);
    private final BooleanProperty rmbOnly = new BooleanProperty("RmbOnly", true, () -> this.mode.getValue() == 4);
    private final IntProperty comboHurt = new IntProperty("ComboHurt", 4, 0, 10, () -> this.mode.getValue() == 5);
    private final FloatProperty comboRange = new FloatProperty("ComboRange", 3.0f, 1f, 6f, () -> this.mode.getValue() == 5);
    private final BooleanProperty comboRequireAttack = new BooleanProperty("ComboRequireAttack", true, () -> this.mode.getValue() == 5);
    private final TimerUtil timer = new TimerUtil();
    private int holdTicks, stopTick;
    private boolean manualDown;
    private int manualActionTick;
    private int manualHitsSince;
    private boolean legitDown;
    private EntityLivingBase legitTarget;

    private boolean startBlocking;
    private boolean attacking;
    private int attackTicks;
    private int sagTicks = 0;
    private int blockTicks = 0;
    private EntityLivingBase target;

    public BlockHit() {
        super("BlockHit", false, false, "Combines attacking and blocking with your sword automatically.");
    } //67:D

    @Override
    public void onEnabled() {
        this.manualDown = false;
        this.manualActionTick = 0;
        this.manualHitsSince = 0;
        this.legitDown = false;
        this.legitTarget = null;
        this.startBlocking = false;
        this.attacking = false;
        this.attackTicks = 0;
        this.sagTicks = 0;
        this.blockTicks = 0;
        this.target = null;
        this.timer.reset();
    }

    @Override
    public void onDisabled() {
        OpenSkid.lagManager.setDelay(0);
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        this.manualDown = false;
        this.manualActionTick = 0;
        this.manualHitsSince = 0;
        this.legitDown = false;
        this.legitTarget = null;
        this.startBlocking = false;
        this.attacking = false;
        this.target = null;
    }


    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() == EventType.PRE) {
            if (this.mode.getValue() == 0) {
                if (mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (mc.thePlayer.isBlocking()) {
                        startBlocking = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    }
                }
                if (startBlocking) stopTick++;
                if (stopTick == 2) {
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (stopTick > stopTime.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    startBlocking = false;
                    stopTick = 0;
                }
            }
            if (this.mode.getValue() == 1) {
                if (target == null) return;
                if (attacking) {
                    attackTicks++;
                }
                if (attackTicks > 5) {
                    reset();
                    target = null;
                    return;
                }
                if (Math.random() * 100.0 > chance.getValue()) {
                    reset();
                    return;
                }
                if (autoBlockRange.getValue() && mc.thePlayer.getDistanceToEntity(target) >= range.getValue()) {
                    reset();
                    return;
                }
                if (smart.getValue() && target.hurtTime >= 8 && target.hurtTime <= 10) {
                    reset();
                    return;
                }
                if (attacking) {
                    if (autoBlockTime.getValue() == 0) {
                        if (timer.hasTimeElapsed(blockDelay.getValue().longValue())) {
                            if (this.autoMode.getValue() == 0) {
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                                timer.reset();
                                reset();
                            }
                            if (this.autoMode.getValue() == 1) {
                                startBlocking = true;
                            }
                            if (startBlocking) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                                holdTicks++;
                            }
                            if (holdTicks > holdTick.getValue()) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                startBlocking = false;
                                holdTicks = 0;
                                timer.reset();
                            }
                        }
                    }
                    if (autoBlockTime.getValue() == 1) {
                        if (mc.thePlayer.hurtTime >= minHurtTime.getValue() && mc.thePlayer.hurtTime <= maxHurtTime.getValue()) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            startBlocking = true;
                        } else if (startBlocking) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            startBlocking = false;
                        }
                    }
                    if (autoBlockTime.getValue() == 2) {
                        if (sagTicks < 10) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            sagTicks++;
                        }
                        if (sagTicks >= 10) {
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            sagTicks = 0;
                        }
                    }
                }
            }
            if (this.mode.getValue() == 2) {
                if (mc.thePlayer.hurtTime == 10) {
                    blockTicks = 1;
                }
                OpenSkid.lagManager.setDelay(delayPacketTick.getValue());
                if (blockTicks >= 1) {
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    blockTicks++;
                }
                if (blockTicks > blockTick.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    OpenSkid.lagManager.setDelay(0);
                    blockTicks = 0;
                }
            } else OpenSkid.lagManager.setDelay(0);
            if (this.mode.getValue() == 3) {
                // Ported from raven BlockHit.java (hold-RMB combo with range gate).
                boolean manualHolding = mc.gameSettings.keyBindAttack.isKeyDown()
                        && mc.gameSettings.keyBindUseItem.isKeyDown()
                        && ItemUtil.isHoldingSword();
                EntityLivingBase manualTarget = mc.objectMouseOver != null
                        && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                        ? (EntityLivingBase) mc.objectMouseOver.entityHit : null;
                boolean manualValid = manualHolding && manualTarget != null && !manualTarget.isDead
                        && mc.thePlayer.getDistanceToEntity(manualTarget) <= this.manualRange.getValue()
                        && this.inFov(manualTarget, this.manualFov.getValue());
                if (!manualValid) {
                    if (this.manualDown) {
                        this.releaseBlock();
                    }
                    this.manualActionTick = 0;
                } else if (!this.manualDown
                        && this.manualHitsSince >= this.manualHitEvery.getValue()
                        && RandomUtil.nextInt(1, 100) <= this.manualChance.getValue()) {
                    this.manualDown = true;
                    this.manualActionTick = 0;
                    this.manualHitsSince = 0;
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                } else if (this.manualDown) {
                    this.manualActionTick++;
                    if (this.manualActionTick >= this.manualActionTicks.getValue()) {
                        this.releaseBlock();
                    }
                }
            }
            if (this.mode.getValue() == 4) {
                // Ported from MiauMinus ghost BlockHit.java (Legit hurt-time hold).
                if (this.legitTarget != null && (this.legitTarget.isDead
                        || mc.thePlayer.getDistanceToEntity(this.legitTarget) > this.legitRange.getValue())) {
                    this.legitTarget = null;
                }
                boolean rmbOk = !this.rmbOnly.getValue() || mc.gameSettings.keyBindUseItem.isKeyDown();
                boolean legitValid = this.legitTarget != null && ItemUtil.isHoldingSword() && rmbOk
                        && this.inFov(this.legitTarget, this.legitFov.getValue());
                if (legitValid && this.legitTarget.hurtTime > this.legitHurt.getValue()) {
                    if (!this.legitDown) {
                        this.legitDown = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    }
                } else if (this.legitDown) {
                    this.releaseBlock();
                }
            }
            if (this.mode.getValue() == 5) {
                // Combo: block only inside trade windows while the target sits in hitstun.
                if (this.legitTarget != null && (this.legitTarget.isDead
                        || mc.thePlayer.getDistanceToEntity(this.legitTarget) > this.comboRange.getValue())) {
                    this.legitTarget = null;
                }
                boolean traded = !this.comboRequireAttack.getValue() || this.attacking;
                boolean comboValid = this.legitTarget != null && ItemUtil.isHoldingSword() && traded
                        && this.legitTarget.hurtTime >= 0 && this.legitTarget.hurtTime <= this.comboHurt.getValue();
                if (comboValid) {
                    if (!this.legitDown) {
                        this.legitDown = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    }
                } else if (this.legitDown) {
                    this.releaseBlock();
                }
            }
            if (this.mode.getValue() == 6) {
                // FakeBlock: hold the visual block pose client-side only, never let placements leave.
                if (ItemUtil.isHoldingSword() && (this.attacking || this.legitTarget != null)) {
                    if (!this.legitDown) {
                        this.legitDown = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    }
                } else if (this.legitDown) {
                    this.releaseBlock();
                }
            }
        }
    }

    private void releaseBlock() {
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        this.manualDown = false;
        this.manualActionTick = 0;
        this.legitDown = false;
    }

    private boolean inFov(EntityLivingBase entity, float maxAngle) {
        // Ported from raven SimpleSprintReset.java (FOV gate).
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        float diff = targetYaw - mc.thePlayer.rotationYaw;
        while (diff > 180.0F) {
            diff -= 360.0F;
        }
        while (diff <= -180.0F) {
            diff += 360.0F;
        }
        return Math.abs(diff) <= maxAngle;
    }

    private void reset() {
        attacking = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = 0;
        manualDown = false;
        manualActionTick = 0;
        legitDown = false;
        timer.reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && ItemUtil.isHoldingSword()) {
            attacking = true;
            attackTicks = 0;
            target = (EntityLivingBase) event.getTarget();
            if (this.mode.getValue() == 3) {
                this.manualHitsSince++;
            }
            if ((this.mode.getValue() == 4 || this.mode.getValue() == 5 || this.mode.getValue() == 6)
                    && event.getTarget() instanceof EntityLivingBase) {
                this.legitTarget = (EntityLivingBase) event.getTarget();
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 6) {
            return;
        }
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            event.setCancelled(true);
        }
    }

    @Override
    public void verifyValue(String mode) {
        if (this.minHurtTime.getValue() > this.maxHurtTime.getValue()) {
            if (this.minHurtTime.getName().equals(mode)) {
                this.maxHurtTime.setValue(this.minHurtTime.getValue());
            } else if (this.maxHurtTime.getName().equals(mode)) {
                this.minHurtTime.setValue(this.maxHurtTime.getValue());
            } else {
                this.maxHurtTime.setValue(this.minHurtTime.getValue());
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}