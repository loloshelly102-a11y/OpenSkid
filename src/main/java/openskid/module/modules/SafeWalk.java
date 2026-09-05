package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.SafeWalkEvent;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.util.ItemUtil;
import openskid.util.MoveUtil;
import openskid.util.PlayerUtil;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

// SafeWalk owns SafeWalkEvent edge guard. BridgeAssist owns sneak edge guard only. Do not enable both.
public class SafeWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty motion = new FloatProperty("motion", 1.0F, 0.5F, 1.0F);
    public final FloatProperty speedMotion = new FloatProperty("speed-motion", 1.0F, 0.5F, 1.5F);
    public final BooleanProperty air = new BooleanProperty("air", false);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty pitCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", false);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final FloatProperty edgeOffset = new FloatProperty("edge-offset", 1.0F, 0.5F, 2.0F);
    public final IntProperty sneakTicks = new IntProperty("sneak-ticks", 3, 0, 10);
    public final BooleanProperty tellyFix = new BooleanProperty("telly-fix", false);
    private int holdTicks = 0;

    private boolean isTellying() {
        // Adapted from MiauMinus BslLegitTellyFix diagonal-guard idea, rewritten for SafeWalk.
        return mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    private boolean canSafeWalk() {
        Scaffold scaffold = (Scaffold) OpenSkid.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.pitCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (this.blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) {
            return false;
        } else if (this.tellyFix.getValue() && this.isTellying()) {
            return false;
        } else {
            float scale = this.edgeOffset.getValue();
            return (!this.requirePress.getValue() || mc.gameSettings.keyBindUseItem.isKeyDown()) && (mc.thePlayer.onGround && PlayerUtil.canMove(mc.thePlayer.motionX * scale, mc.thePlayer.motionZ * scale, -1.0)
                    || this.air.getValue() && PlayerUtil.canMove(mc.thePlayer.motionX * scale, mc.thePlayer.motionZ * scale, -2.0));
        }
    }

    public SafeWalk() {
        super("SafeWalk", false, false, "Prevents you from walking off block edges.");
    }

    @Override
    public void onDisabled() {
        this.holdTicks = 0;
    }

    @EventTarget
    public void onMove(SafeWalkEvent event) {
        if (this.isEnabled()) {
            if (this.holdTicks > 0) {
                this.holdTicks--;
            }
            if (this.canSafeWalk()) {
                event.setSafeWalk(true);
                if (this.sneakTicks.getValue() > 0) {
                    this.holdTicks = this.sneakTicks.getValue();
                }
            } else if (this.holdTicks > 0) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && this.canSafeWalk()) {
                if (MoveUtil.getSpeedLevel() <= 0) {
                    if (this.motion.getValue() != 1.0F) {
                        MoveUtil.setSpeed(MoveUtil.getSpeed() * (double) this.motion.getValue());
                    }
                } else if (this.speedMotion.getValue() != 1.0F) {
                    MoveUtil.setSpeed(MoveUtil.getSpeed() * (double) this.speedMotion.getValue());
                }
            }
        }
    }
}
