package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.TextProperty;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;

import java.util.Locale;

// Concept adapted from raven-bS AutoRequeue (delayed play-again queue).
// Rebuilt for OpenSkid: chat-trigger queue with delay slider. No pasted code.
public class AutoRequeue extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty trigger = new ModeProperty("trigger", 0, new String[]{"ANY_END", "WIN_ONLY", "LOSS_ONLY"});
    public final BooleanProperty playAgain = new BooleanProperty("play-again", true);
    public final BooleanProperty requeue = new BooleanProperty("requeue", true);
    public final TextProperty winText = new TextProperty("win-text", "VICTORY", () -> trigger.getValue() == 1);
    public final TextProperty lossText = new TextProperty("loss-text", "DEFEAT", () -> trigger.getValue() == 2);
    public final FloatProperty delay = new FloatProperty("delay", 1.5F, 0.0F, 5.0F);
    public final BooleanProperty alert = new BooleanProperty("alert", true);

    private String queued;
    private long queuedAt;

    public AutoRequeue() {
        super("AutoRequeue", false, false, "Automatically queues a new game after matches finish.");
    }

    @Override
    public void onEnabled() {
        clearQueue();
    }

    @Override
    public void onDisabled() {
        clearQueue();
    }

    @Override
    public String[] getSuffix() {
        if (queued != null) {
            return new String[]{"QUEUED"};
        }
        return new String[]{trigger.getModeString()};
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || !(event.getPacket() instanceof S02PacketChat)) {
            return;
        }
        String raw;
        try {
            raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        } catch (Exception ignored) {
            return;
        }
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        boolean prompt = lower.contains("play again") || lower.contains("click here");
        boolean win = matchesWin(raw);
        boolean loss = matchesLoss(raw);
        if (trigger.getValue() == 1) {
            if (!win) {
                return;
            }
        } else if (trigger.getValue() == 2) {
            if (!loss) {
                return;
            }
        } else if (!prompt && !win && !loss) {
            return;
        }
        if (!playAgain.getValue() && !requeue.getValue()) {
            return;
        }
        if (queued == null) {
            queued = "/play again";
            queuedAt = System.currentTimeMillis();
            if (alert.getValue()) {
                ChatUtil.sendFormatted(String.format("%s%s: &fRequeueing in &e%.1fs&r", OpenSkid.clientName, getName(), delay.getValue()));
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || queued == null) {
            return;
        }
        if (System.currentTimeMillis() - queuedAt >= (long) (delay.getValue() * 1000.0F)) {
            if (mc.thePlayer != null) {
                mc.thePlayer.sendChatMessage(queued);
            }
            clearQueue();
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        clearQueue();
    }

    private boolean matchesWin(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String custom = winText.getValue();
        if (custom != null && !custom.trim().isEmpty() && lower.contains(custom.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return lower.contains("victory") || lower.contains("you won") || lower.contains("you win") || lower.contains("#1");
    }

    private boolean matchesLoss(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String custom = lossText.getValue();
        if (custom != null && !custom.trim().isEmpty() && lower.contains(custom.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return lower.contains("defeat") || lower.contains("you lost") || lower.contains("game over") || lower.contains("eliminated");
    }

    private void clearQueue() {
        queued = null;
        queuedAt = 0L;
    }
}
