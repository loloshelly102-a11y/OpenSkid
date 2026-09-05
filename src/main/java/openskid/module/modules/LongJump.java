package openskid.module.modules;

import com.google.common.base.CaseFormat;
import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.*;
import openskid.management.RotationState;
import openskid.mixin.IAccessorPlayerControllerMP;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.util.*;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.PercentProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;

public class LongJump extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil fireballTimer = new TimerUtil();
    private final TimerUtil jumpTimer = new TimerUtil();
    private boolean isJumping = false;
    private int tickCounter = 0;
    private int jumpModeStage = 0;
    private boolean readyToUseFireball = false;
    private boolean fireballLaunched = false;
    private int savedHotbarSlot = -1;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"FIREBALL", "FIREBALL_MANUAL", "FIREBALL_HIGH", "FIREBALL_FLAT", "GRIM_BOAT", "GRIM_VELOCITY", "HYPIXEL", "HYPIXEL_FIREBALL", "VULCAN"});
    public final FloatProperty motion = new FloatProperty("motion", 1.0F, 1.0F, 20.0F);
    public final FloatProperty speedMotion = new FloatProperty("speed-motion", 1.0F, 1.0F, 20.0F);
    public final PercentProperty strafe = new PercentProperty("strafe", 0);
    public final BooleanProperty onyaw = new BooleanProperty("yaw", false);
    public final BooleanProperty autolag = new BooleanProperty("AutoLag", false);
    public final FloatProperty boatH = new FloatProperty("boat-h", 1.0F, 0.1F, 2.0F, () -> mode.getValue() == 4);
    public final FloatProperty boatV = new FloatProperty("boat-v", 1.0F, 0.1F, 2.0F, () -> mode.getValue() == 4);
    public final BooleanProperty boatTimer = new BooleanProperty("boat-timer", false, () -> mode.getValue() == 4);
    public final FloatProperty boatTimerSpeed = new FloatProperty("boat-timer-speed", 0.5F, 0.1F, 0.8F, () -> mode.getValue() == 4 && boatTimer.getValue());
    public final BooleanProperty veloTimer = new BooleanProperty("velo-timer", false, () -> mode.getValue() == 5);
    public final FloatProperty veloTimerSpeed = new FloatProperty("velo-timer-speed", 0.5F, 0.01F, 1.0F, () -> mode.getValue() == 5 && veloTimer.getValue());
    public final FloatProperty hypixelSpeed = new FloatProperty("hypixel-speed", 1.5F, 0.1F, 2.0F, () -> mode.getValue() == 6 || mode.getValue() == 7);
    public final BooleanProperty vulcanTp = new BooleanProperty("vulcan-tp", true, () -> mode.getValue() == 8);
    public final BooleanProperty ljAutoDisable = new BooleanProperty("auto-disable", true, () -> mode.getValue() >= 4);

    private int ljTicks = 0;
    private int ljOffGround = 0;
    private int ljJumps = 0;
    private boolean ljSelfDmg = false;
    private boolean ljBoatWasActive = false;
    private boolean ljVeloStored = false;
    private int ljVeloTick = 0;
    private double ljVeloX = 0.0;
    private double ljVeloY = 0.0;
    private double ljVeloZ = 0.0;

    private int findFireballInHotbar() {
        if (mc.thePlayer == null) {
            return -1;
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemFireball) {
                    return i;
                }
            }
            return -1;
        }
    }

    private double getMotionFactor() {
        return MoveUtil.getSpeedLevel() > 0
                ? (double) this.speedMotion.getValue()
                : (double) this.motion.getValue();
    }

    public LongJump() {
        super("LongJump", false, false, "Launches you forward with long jump movement.");
    }

    public boolean isAutoMode() {
        return this.mode.getValue() == 0 || this.mode.getValue() == 2 || this.mode.getValue() == 3 || this.mode.getValue() == 7;
    }

    public boolean isManualMode() {
        return this.mode.getValue() == 1;
    }

    public boolean isLongJumpMode() {
        return this.isAutoMode() || this.isManualMode() || this.mode.getValue() >= 4;
    }

    private boolean isNewMode() {
        return this.mode.getValue() >= 4;
    }

    private boolean isBoatActive() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        AxisAlignedBB grimBox = mc.thePlayer.getEntityBoundingBox().expand(1.0, 1.0, 1.0);
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityBoat && ((EntityBoat) o).getEntityBoundingBox().intersectsWith(grimBox)) {
                return true;
            }
        }
        return false;
    }

    private void setTimer(float value) {
        net.minecraft.util.Timer timer = ((openskid.mixin.IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            timer.timerSpeed = value;
        }
    }

    public boolean canStartJump() {
        return !this.fireballTimer.hasTimeElapsed(1000L) && !this.isJumping;
    }

    public boolean isJumping() {
        return this.isJumping;
    }

    @EventTarget(Priority.HIGHEST)
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if ((this.isManualMode() || this.isAutoMode()) && this.canStartJump()) {
                event.setCancelled(true);
                this.isJumping = true;
                this.tickCounter = 0;
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    if (this.isAutoMode() && !this.fireballLaunched && this.readyToUseFireball) {
                        int slot = this.findFireballInHotbar();
                        if (slot != -1) {
                            this.savedHotbarSlot = mc.thePlayer.inventory.currentItem;
                            mc.thePlayer.inventory.currentItem = slot;
                            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                            this.fireballTimer.reset();
                            this.fireballLaunched = true;
                        }
                    }
                    break;
                case POST:
                    if (this.savedHotbarSlot != -1) {
                        mc.thePlayer.inventory.currentItem = this.savedHotbarSlot;
                        this.savedHotbarSlot = -1;
                    }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer != null && mc.thePlayer.onGround) {
                this.ljOffGround = 0;
            } else {
                this.ljOffGround++;
            }
            if (this.isNewMode()) {
                this.updateNewMode(event);
            }
            if (this.isLongJumpMode() && this.isJumping) {
                this.tickCounter++;
                if (this.tickCounter == 1) {
                    switch (this.mode.getValue()) {
                        case 0:
                        case 1:
                        case 7:
                            this.jumpModeStage = 0;
                            break;
                        case 2:
                            this.jumpModeStage = 1;
                            break;
                        case 3:
                            this.jumpModeStage = MoveUtil.isForwardPressed() ? 2 : 1;
                    }
                }
                if (this.tickCounter == 2 && MoveUtil.isForwardPressed()) {
                    MoveUtil.setSpeed(MoveUtil.getSpeed() * this.getMotionFactor());
                }
                if (this.tickCounter >= 1 && this.tickCounter <= 30) {
                    switch (this.jumpModeStage) {
                        case 1:
                            if (this.tickCounter == 1) {
                                mc.thePlayer.motionY *= 0.75;
                            } else {
                                double motion = mc.thePlayer.motionY / 0.98F + 0.055;
                                if (motion > 0.0) {
                                    mc.thePlayer.motionY = motion;
                                }
                            }
                            break;
                        case 2:
                            if (this.tickCounter == 1) {
                                mc.thePlayer.motionY *= 0.75;
                            } else {
                                mc.thePlayer.motionY = 0.01 + (double) this.tickCounter * 0.003;
                            }
                    }
                }
                if (this.tickCounter >= 30) {
                    this.isJumping = false;
                    this.tickCounter = 0;
                    this.jumpModeStage = 0;
                    this.setTimer(1.0F);
                    if (this.isAutoMode()) {
                        this.setEnabled(false);
                    }
                    return;
                }
            }
            if (this.isAutoMode() && !this.isJumping) {
                if (this.jumpTimer.hasTimeElapsed(1500L)) {
                    this.setEnabled(false);
                    return;
                }
                this.readyToUseFireball = true;
                float yaw = !onyaw.getValue()
                        ? mc.thePlayer.rotationYaw
                        : RotationUtil.quantizeAngle(mc.thePlayer.rotationYaw - 180.0F - RandomUtil.nextFloat(0.0F, 1.0F));

                float pitch = RotationUtil.quantizeAngle(89.0F + RandomUtil.nextFloat(-0.25F, 0.25F));

                event.setRotation(yaw, pitch, 4);
                event.setPervRotation(yaw, 4);
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (RotationState.isActived()
                    && RotationState.getPriority() == 4.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (this.isLongJumpMode()
                    && this.isJumping
                    && this.tickCounter >= 5
                    && this.tickCounter <= 30
                    && this.strafe.getValue() > 0) {
                double speed = MoveUtil.getSpeed();
                MoveUtil.setSpeed(speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F), MoveUtil.getDirectionYaw());
                MoveUtil.addSpeed(
                        speed * (double) ((float) this.strafe.getValue() / 100.0F), MoveUtil.getMoveYaw()
                );
                MoveUtil.setSpeed(speed);
            }
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
            if (stack != null && stack.getItem() instanceof ItemFireball) {
                this.fireballTimer.reset();
            }
        }
    }

    // Ported from Raven GrimBoatLongJump.java: ride nearby boat momentum with optional timer.
    private void updateBoat() {
        boolean active = this.isBoatActive();
        if (active) {
            MoveUtil.setSpeed((double) this.boatH.getValue(), MoveUtil.getMoveYaw());
            mc.thePlayer.motionY = (double) this.boatV.getValue();
            if (this.boatTimer.getValue()) {
                this.setTimer(this.boatTimerSpeed.getValue());
            }
        } else if (this.boatTimer.getValue()) {
            this.setTimer(1.0F);
        }
        if (!active && this.ljBoatWasActive && this.ljAutoDisable.getValue()) {
            this.setEnabled(false);
            return;
        }
        this.ljBoatWasActive = active;
    }

    // Ported from Raven GrimVelocityLongJump.java: hold velocity, apply it two ticks late.
    private void updateVelocity() {
        if (this.ljVeloStored) {
            this.ljVeloTick++;
            if (this.veloTimer.getValue()) {
                this.setTimer(this.veloTimerSpeed.getValue());
            }
            if (this.ljVeloTick >= 2) {
                mc.thePlayer.motionX = this.ljVeloX;
                mc.thePlayer.motionY = this.ljVeloY;
                mc.thePlayer.motionZ = this.ljVeloZ;
                this.ljVeloStored = false;
                this.ljVeloTick = 0;
                if (this.veloTimer.getValue()) {
                    this.setTimer(1.0F);
                }
            }
        }
    }

    // Ported from Raven HypixelLongJump.java: four self-damage hops then low timer glide.
    private void updateHypixel(UpdateEvent event) {
        if (this.ljSelfDmg) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            if (this.ljJumps < 4) {
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42;
                    this.ljJumps++;
                }
                event.setRotation(event.getNewYaw(), event.getNewPitch(), 0);
            } else if (this.ljOffGround >= 11) {
                this.ljSelfDmg = false;
                this.ljJumps = 0;
            }
        } else {
            if (mc.thePlayer.onGround) {
                this.setTimer(1.0F);
                MoveUtil.setSpeed(MoveUtil.getAllowedHorizontalDistance() * (double) this.hypixelSpeed.getValue() - Math.random() / 100.0, MoveUtil.getMoveYaw());
                mc.thePlayer.jump();
            }
            if (this.ljOffGround == 1) {
                this.setTimer(0.2F);
                this.ljTicks++;
            }
            if (this.tickCounter > 0) {
                mc.thePlayer.motionY += 0.0239;
                MoveUtil.addSpeed(0.0039, MoveUtil.getMoveYaw());
            }
        }
    }

    // Ported from Raven VulcanLongJump.java: fake-ground teleport then alternating sink.
    private void updateVulcan() {
        this.ljTicks++;
        if (this.ljTicks == 1) {
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.onGround = true;
            if (this.vulcanTp.getValue()) {
                mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 9.9, mc.thePlayer.posZ);
            }
        }
        if (this.ljTicks > 0 && this.ljTicks <= 3) {
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.onGround = true;
        }
        if (this.ljTicks > 3 && mc.thePlayer.onGround && this.ljAutoDisable.getValue()) {
            this.setEnabled(false);
            return;
        }
        if (this.ljTicks > 3 && this.ljTicks % 2 == 0 && !mc.thePlayer.onGround) {
            mc.thePlayer.motionY = -0.155;
        } else if (this.ljTicks % 2 != 0 || mc.thePlayer.onGround) {
            mc.thePlayer.motionY = -0.098;
        }
    }

    private void updateNewMode(UpdateEvent event) {
        switch (this.mode.getValue()) {
            case 4:
                this.updateBoat();
                break;
            case 5:
                this.updateVelocity();
                break;
            case 6:
                this.updateHypixel(event);
                break;
            case 8:
                this.updateVulcan();
                break;
            default:
                break;
        }
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity && this.mode.getValue() == 5 && this.isEnabled()) {
                S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) event.getPacket();
                if (velocity.getEntityID() == mc.thePlayer.getEntityId()) {
                    event.setCancelled(true);
                    this.ljVeloX = (double) velocity.getMotionX() / 8000.0;
                    this.ljVeloY = (double) velocity.getMotionY() / 8000.0;
                    this.ljVeloZ = (double) velocity.getMotionZ() / 8000.0;
                    this.ljVeloStored = true;
                    this.ljVeloTick = 0;
                }
                return;
            }
            if (event.getPacket() instanceof S27PacketExplosion && this.mode.getValue() == 5 && this.isEnabled()) {
                event.setCancelled(true);
                return;
            }
            if (event.getPacket() instanceof S08PacketPlayerPosLook) {
                this.isJumping = false;
                this.tickCounter = 0;
                this.jumpModeStage = 0;
                this.setTimer(1.0F);
                if (this.isAutoMode()) {
                    this.setEnabled(false);
                } else if (this.isNewMode() && this.ljAutoDisable.getValue()) {
                    this.setEnabled(false);
                }
            }
        }
    }

    @Override
    public void onEnabled() {
        this.jumpTimer.reset();
        this.ljTicks = 0;
        this.ljOffGround = 0;
        this.ljJumps = 0;
        this.ljSelfDmg = this.mode.getValue() == 6;
        this.ljBoatWasActive = false;
        this.ljVeloStored = false;
        this.ljVeloTick = 0;
        if (this.isAutoMode() && this.findFireballInHotbar() == -1) {
            this.setEnabled(false);
            ChatUtil.sendFormatted(String.format("%s%s: &cNo fireball found in your hotbar!&r", OpenSkid.clientName, this.getName()));
        } else {
            if (autolag.getValue()) OpenSkid.moduleManager.modules.get(ServerLag.class).setEnabled(true);
        }
    }

    @Override
    public void onDisabled() {
        this.isJumping = false;
        this.tickCounter = 0;
        this.jumpModeStage = 0;
        this.readyToUseFireball = false;
        this.fireballLaunched = false;
        this.ljTicks = 0;
        this.ljOffGround = 0;
        this.ljJumps = 0;
        this.ljSelfDmg = false;
        this.ljBoatWasActive = false;
        this.ljVeloStored = false;
        this.ljVeloTick = 0;
        this.setTimer(1.0F);
    }

    @Override
    public String[] getSuffix() {
        String mode = this.mode.getModeString();
        return mode.contains("FIREBALL") ? new String[]{"Fireball"} : new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode)};
    }
}