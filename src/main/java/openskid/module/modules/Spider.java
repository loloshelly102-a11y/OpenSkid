package openskid.module.modules;

import com.google.common.base.CaseFormat;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.ModeProperty;
import openskid.util.MoveUtil;
import net.minecraft.client.Minecraft;

public class Spider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SPEED", "VANILLA"});

    public Spider() {
        super("Spider", false, false, "Climbs vertical walls like a spider.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!mc.thePlayer.isCollidedHorizontally || !MoveUtil.isMoving()) {
            return;
        }
        if (this.mode.getValue() == 0) {
            mc.thePlayer.motionY = 0.35;
        } else {
            mc.thePlayer.motionY = 0.2;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
