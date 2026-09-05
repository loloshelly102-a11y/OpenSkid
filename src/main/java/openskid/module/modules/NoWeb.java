package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.mixin.IAccessorEntity;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

// Cancels cobweb slowdown. Normal restores motion, Sprint keeps sprinting through webs.
public class NoWeb extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Normal", "Sprint"});
    public final BooleanProperty onlyMoving = new BooleanProperty("only-moving", true);

    public NoWeb() {
        super("NoWeb", false, false, "Removes slowdown while inside cobwebs.");
    }

    private boolean isInWeb() {
        return mc.thePlayer != null && ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        if (!this.isInWeb()) {
            return;
        }
        if (this.onlyMoving.getValue() && mc.thePlayer.moveForward == 0.0F && mc.thePlayer.moveStrafing == 0.0F) {
            return;
        }
        mc.thePlayer.motionX /= 0.25;
        mc.thePlayer.motionZ /= 0.25;
        if (this.mode.getValue() == 1 && !mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(true);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
