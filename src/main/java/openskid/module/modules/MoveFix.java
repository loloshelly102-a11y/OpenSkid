package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.events.MoveInputEvent;
import openskid.management.RotationState;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.util.MoveUtil;
import net.minecraft.client.Minecraft;

public class MoveFix extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty groundOnly = new BooleanProperty("ground-only", false);

    public MoveFix() {
        super("MoveFix", false, false, "Fixes movement inputs while silent rotations are active.");
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
            return;
        }
        if (RotationState.isActived() && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }
}
