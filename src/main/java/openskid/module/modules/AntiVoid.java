package openskid.module.modules;

import com.google.common.base.CaseFormat;
import openskid.OpenSkid;
import openskid.enums.BlinkModules;
import openskid.event.EventTarget;
import openskid.event.types.Priority;
import openskid.events.KeyEvent;
import openskid.events.PlayerUpdateEvent;
import openskid.module.Module;
import openskid.mixin.IAccessorMinecraft;
import openskid.util.MoveUtil;
import openskid.util.PlayerUtil;
import openskid.util.RandomUtil;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.AxisAlignedBB;

public class AntiVoid extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean isInVoid = false;
    private boolean wasInVoid = false;
    private double[] lastSafePosition = null;
    private int stuckTicks = 0;
    private int backtrackTicks = 0;
    private boolean timerSlowed = false;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"BLINK", "HYPIXEL", "AIR_STUCK", "VULCAN", "GRIM_AC", "BACKTRACK", "NCP"});
    public final FloatProperty distance = new FloatProperty("distance", 5.0F, 0.0F, 16.0F);
    public final FloatProperty radius = new FloatProperty("radius", 3.0F, 0.0F, 8.0F, () -> mode.getValue() != 0);
    public final IntProperty rewindTicks = new IntProperty("rewind-ticks", 10, 0, 60, () -> mode.getValue() == 5);
    public final FloatProperty voidTimer = new FloatProperty("void-timer", 0.5F, 0.1F, 1.0F, () -> mode.getValue() == 3);

    private void resetBlink() {
        OpenSkid.blinkManager.setBlinkState(false, BlinkModules.ANTI_VOID);
        this.lastSafePosition = null;
    }

    private void resetTimer() {
        if (this.timerSlowed) {
            this.timerSlowed = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        }
    }

    private void resetState() {
        this.stuckTicks = 0;
        this.backtrackTicks = 0;
        this.resetTimer();
    }

    private boolean canUseAntiVoid() {
        LongJump longJump = (LongJump) OpenSkid.moduleManager.modules.get(LongJump.class);
        return !longJump.isJumping();
    }

    public AntiVoid() {
        super("AntiVoid", false, false, "Saves you from falling into the void using rewind modes.");
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled()) {
            this.isInVoid = !mc.thePlayer.capabilities.allowFlying && PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox());
            if (!this.isInVoid && this.canUseAntiVoid()) {
                if (this.mode.getValue() != 0 || OpenSkid.blinkManager.getBlinkingModule() != BlinkModules.ANTI_VOID) {
                    this.lastSafePosition = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
                }
                this.resetState();
            }
            switch (this.mode.getValue()) {
                case 0:
                    this.handleBlink();
                    break;
                case 1:
                    // Donor Hypixel-style void catch: freeze the fall, drift back over ground.
                    if (this.isInVoid && this.canUseAntiVoid()) {
                        mc.thePlayer.motionY = 0.0;
                        if (this.lastSafePosition != null) {
                            double dx = this.lastSafePosition[0] - mc.thePlayer.posX;
                            double dz = this.lastSafePosition[2] - mc.thePlayer.posZ;
                            double dist = Math.hypot(dx, dz);
                            if (dist > this.radius.getValue()) {
                                MoveUtil.setSpeed(0.2, (float) Math.toDegrees(Math.atan2(-dx, dz)));
                            } else {
                                MoveUtil.setSpeed(0.0);
                            }
                        }
                    }
                    break;
                case 2:
                    // Donor AirStuck hold (donor AirStuck module): full motion freeze, release on landing.
                    if (this.isInVoid && this.canUseAntiVoid()) {
                        this.stuckTicks++;
                        mc.thePlayer.motionX = 0.0;
                        mc.thePlayer.motionY = 0.0;
                        mc.thePlayer.motionZ = 0.0;
                        if (this.stuckTicks > 200) {
                            this.setEnabled(false);
                        }
                    } else {
                        this.stuckTicks = 0;
                    }
                    break;
                case 3:
                    // Donor Vulcan-style void catch: slow the timer and brake the fall.
                    if (this.isInVoid && this.canUseAntiVoid()) {
                        if (!this.timerSlowed) {
                            this.timerSlowed = true;
                        }
                        ((IAccessorMinecraft) mc).getTimer().timerSpeed = this.voidTimer.getValue();
                        mc.thePlayer.motionY *= 0.5;
                        if (MoveUtil.isMoving()) {
                            MoveUtil.setSpeed(MoveUtil.getBaseMoveSpeed() * 0.6);
                        }
                    } else {
                        this.resetTimer();
                    }
                    break;
                case 4:
                    // Donor GrimAC-style rewind: snap back to the last safe ground position.
                    if (this.isInVoid && this.canUseAntiVoid() && this.lastSafePosition != null
                            && this.lastSafePosition[1] - this.distance.getValue().floatValue() > mc.thePlayer.posY) {
                        mc.thePlayer.setPosition(this.lastSafePosition[0], this.lastSafePosition[1], this.lastSafePosition[2]);
                        mc.thePlayer.motionY = 0.0;
                        mc.thePlayer.fallDistance = 0.0F;
                    }
                    break;
                case 5:
                    // Donor backtrack-style void clutch: blink, then rewind packets after a delay.
                    this.handleBlink();
                    if (OpenSkid.blinkManager.getBlinkingModule() == BlinkModules.ANTI_VOID && this.lastSafePosition != null) {
                        this.backtrackTicks++;
                        if (this.backtrackTicks >= this.rewindTicks.getValue()
                                && this.lastSafePosition[1] - (double) this.distance.getValue().floatValue() > mc.thePlayer.posY) {
                            OpenSkid.blinkManager.blinkedPackets.offerFirst(
                                    new C04PacketPlayerPosition(
                                            this.lastSafePosition[0], this.lastSafePosition[1] - RandomUtil.nextDouble(10.0, 20.0), this.lastSafePosition[2], false
                                    )
                            );
                            this.resetBlink();
                            this.backtrackTicks = 0;
                        }
                    } else {
                        this.backtrackTicks = 0;
                    }
                    break;
                case 6:
                    // Donor NCP-style void glide: gentle sink that buys time to steer back.
                    if (this.isInVoid && this.canUseAntiVoid()) {
                        if (mc.thePlayer.motionY < -0.08) {
                            mc.thePlayer.motionY = -0.08;
                        }
                        if (MoveUtil.isMoving() && this.lastSafePosition != null) {
                            double dx = this.lastSafePosition[0] - mc.thePlayer.posX;
                            double dz = this.lastSafePosition[2] - mc.thePlayer.posZ;
                            if (Math.hypot(dx, dz) > this.radius.getValue()) {
                                MoveUtil.setSpeed(MoveUtil.getBaseMoveSpeed(), (float) Math.toDegrees(Math.atan2(-dx, dz)));
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
            this.wasInVoid = this.isInVoid;
        }
    }

    private void handleBlink() {
        if (this.mode.getValue() != 0 && this.mode.getValue() != 5) {
            return;
        }
        if (!this.isInVoid) {
            this.resetBlink();
        }
        if (this.lastSafePosition != null) {
            float subWidth = mc.thePlayer.width / 2.0F;
            float height = mc.thePlayer.height;
            if (PlayerUtil.checkInWater(
                    new AxisAlignedBB(
                            this.lastSafePosition[0] - (double) subWidth,
                            this.lastSafePosition[1],
                            this.lastSafePosition[2] - (double) subWidth,
                            this.lastSafePosition[0] + (double) subWidth,
                            this.lastSafePosition[1] + (double) height,
                            this.lastSafePosition[2] + (double) subWidth
                    )
            )) {
                this.resetBlink();
            }
        }
        if (!this.wasInVoid && this.isInVoid && this.canUseAntiVoid()) {
            OpenSkid.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            if (OpenSkid.blinkManager.setBlinkState(true, BlinkModules.ANTI_VOID)) {
                this.lastSafePosition = new double[]{mc.thePlayer.prevPosX, mc.thePlayer.prevPosY, mc.thePlayer.prevPosZ};
            }
        }
        if (this.mode.getValue() == 0
                && OpenSkid.blinkManager.getBlinkingModule() == BlinkModules.ANTI_VOID
                && this.lastSafePosition != null
                && this.lastSafePosition[1] - (double) this.distance.getValue().floatValue() > mc.thePlayer.posY) {
            OpenSkid.blinkManager
                    .blinkedPackets
                    .offerFirst(
                            new C04PacketPlayerPosition(
                                    this.lastSafePosition[0], this.lastSafePosition[1] - RandomUtil.nextDouble(10.0, 20.0), this.lastSafePosition[2], false
                            )
                    );
            this.resetBlink();
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();
            if (currentItem != null && currentItem.getItem() instanceof ItemEnderPearl) {
                this.resetBlink();
                this.resetState();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.isInVoid = false;
        this.wasInVoid = false;
        this.resetBlink();
        this.resetState();
    }

    @Override
    public void onDisabled() {
        OpenSkid.blinkManager.setBlinkState(false, BlinkModules.ANTI_VOID);
        this.resetState();
    }

    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
