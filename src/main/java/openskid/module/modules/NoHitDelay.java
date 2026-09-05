package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import java.lang.reflect.Field;

// Hit-only click-delay removal. Unlike NoClickDelay (always on), this zeroes
// the counter only while the crosshair holds a living target.
public class NoHitDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private Field leftClickField;

    public final BooleanProperty requireTarget = new BooleanProperty("require-target", true);

    public NoHitDelay() {
        super("NoHitDelay", true, true, "Removes click delay while aiming at targets.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.requireTarget.getValue()
                && (mc.objectMouseOver == null || !(mc.objectMouseOver.entityHit instanceof EntityLivingBase))) {
            return;
        }
        this.clearCounter();
    }

    private void clearCounter() {
        try {
            if (leftClickField == null) {
                leftClickField = Minecraft.class.getDeclaredField("leftClickCounter");
                leftClickField.setAccessible(true);
            }
            if (leftClickField.getInt(mc) > 0) {
                leftClickField.setInt(mc, 0);
            }
        } catch (Exception ignored) {
        }
    }
}
