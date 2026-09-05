package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PacketEvent;
import openskid.module.Module;
import openskid.util.ChatUtil;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class FlagDetector extends Module {
    private static final long ALERT_GAP_MS = 5000L;
    private long lastAlertMs = 0L;

    public FlagDetector() {
        super("FlagDetector", false, true, "Alerts you in chat when the server corrects your position.");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled())
            return;

        if (event.getType() != EventType.RECEIVE)
            return;

        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            long now = System.currentTimeMillis();
            if (now - lastAlertMs < ALERT_GAP_MS) {
                return;
            }
            lastAlertMs = now;
            ChatUtil.sendFormatted("&7[&cFlagDetector&7] &fServer flag detected (Lagback)!");
        }
    }
}
