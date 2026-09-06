package openskid.module.modules;

import java.lang.reflect.Field;
import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.TickEvent;
import openskid.mixin.IAccessorEntityLivingBase;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;

public class DelayRemover extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private Field clickField;
    public final BooleanProperty hitReg = new BooleanProperty("hit-reg", true);
    public final BooleanProperty jumpTicks = new BooleanProperty("jump-ticks", false);
    public final BooleanProperty notWhileScaffold = new BooleanProperty("not-while-scaffold", false, () -> this.jumpTicks.getValue());

    public DelayRemover() {
        super("DelayRemover", false, false, "Removes hit and jump delays for faster combat movement.");
    }

    private boolean scaffoldActive() {
        try {
            Module scaffold = OpenSkid.moduleManager.modules.get(Scaffold.class);
            return scaffold != null && scaffold.isEnabled();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void clearClick() {
        try {
            if (this.clickField == null) {
                this.clickField = Minecraft.class.getDeclaredField("leftClickCounter");
                this.clickField.setAccessible(true);
            }
            if (this.clickField.getInt(mc) > 0) {
                this.clickField.setInt(mc, 0);
            }
        } catch (Exception ignored) {
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.hitReg.getValue()) {
            clearClick();
        }
        if (this.jumpTicks.getValue()) {
            if (this.notWhileScaffold.getValue() && scaffoldActive()) {
                return;
            }
            ((IAccessorEntityLivingBase) mc.thePlayer).setJumpTicks(0);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.hitReg.getValue() ? "Hit" : "Jump"};
    }
}
