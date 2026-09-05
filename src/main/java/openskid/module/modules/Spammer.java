package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.events.Render2DEvent;
import openskid.module.Module;
import openskid.util.TimerUtil;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

public class Spammer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private int charOffset = 19968;
    public final TextProperty text = new TextProperty("text", "meow");
    public final FloatProperty delay = new FloatProperty("delay", 3.5F, 0.0F, 3600.0F);
    public final IntProperty random = new IntProperty("random", 0, 0, 10);

    public Spammer() {
        super("Spammer", false, false, "Automatically sends a chat message on a timer.");
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        long delayMs = Math.max(2000L, (long) (this.delay.getValue() * 1000.0F));
        if (this.timer.hasTimeElapsed(delayMs)) {
            this.timer.reset();
            String text = this.text.getValue();
            if (this.random.getValue() > 0) {
                text = String.format("%s ", text);
                for (int i = 0; i < this.random.getValue(); i++) {
                    text = String.format("%s%s", text, (char) this.charOffset);
                    this.charOffset++;
                    if (this.charOffset > 40959) {
                        this.charOffset = 19968;
                    }
                }
            }
            if (mc.thePlayer != null) {
                mc.thePlayer.sendChatMessage(text);
            }
        }
    }
}
