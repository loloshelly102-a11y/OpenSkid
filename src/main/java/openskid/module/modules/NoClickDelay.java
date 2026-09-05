package openskid.module.modules;

import java.lang.reflect.Field;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import net.minecraft.client.Minecraft;

// Ported from MiauMinus ghost/NoClickDelay (leftClickCounter reset pattern).
// NOTE: unregistered follow-up, not wired into OpenSkid.java or ClickGui.
// Uses reflection guarded by try/catch since no Mixin accessor may be added.
public class NoClickDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Field leftClickField;

    public NoClickDelay() {
        super("NoClickDelay", false, false, "Removes the delay between left clicks.");
    }

    @Override
    public void onEnabled() {
        this.clearCounter();
    }

    @Override
    public void onDisabled() {
        this.clearCounter();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"0ms"};
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
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
