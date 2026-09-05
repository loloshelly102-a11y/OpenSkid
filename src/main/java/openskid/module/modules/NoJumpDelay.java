package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.TickEvent;
import openskid.mixin.IAccessorEntityLivingBase;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class NoJumpDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("delay", 3, 0, 8);
    public final BooleanProperty onlyMoving = new BooleanProperty("only-moving", false);
    public final FloatProperty heightCap = new FloatProperty("height-cap", 1.0F, 0.2F, 2.0F);
    public final BooleanProperty groundOnly = new BooleanProperty("ground-only", false);
    public final BooleanProperty onlySprint = new BooleanProperty("only-sprint", false);

    public NoJumpDelay() {
        super("NoJumpDelay", false, false, "Reduces the delay between jumps.");
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer == null) return;
            if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
                return;
            }
            if (this.onlySprint.getValue() && !mc.thePlayer.isSprinting()) {
                return;
            }
            if (this.onlyMoving.getValue() && mc.thePlayer.moveForward == 0.0F && mc.thePlayer.moveStrafing == 0.0F) {
                return;
            }
            ((IAccessorEntityLivingBase) mc.thePlayer)
                    .setJumpTicks(Math.min(((IAccessorEntityLivingBase) mc.thePlayer).getJumpTicks(), this.delay.getValue() + 1));
            if (mc.thePlayer.onGround && mc.thePlayer.motionY > this.heightCap.getValue()) {
                mc.thePlayer.motionY = this.heightCap.getValue();
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.delay.getValue().toString()};
    }
}
