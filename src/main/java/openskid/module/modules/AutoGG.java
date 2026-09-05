package openskid.module.modules;

// Adapted from MiauMinus misc/AutoGG (game-end token match with two custom messages).
// Thread pool replaced with the house tick queue pattern from AutoAuth.
import java.util.Locale;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.TextProperty;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoGG extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] GAME_END_TOKENS = {
        "1st Killer -", "1st Place -", "Winner:", "Winning Team", "Winners:",
        "won the game!", "Last team standing!", "Top Survivors", "WINNER"
    };

    public final BooleanProperty sendFirst = new BooleanProperty("first-message", true);
    public final TextProperty firstText = new TextProperty("first-text", "gg", this.sendFirst::getValue);
    public final IntProperty delay = new IntProperty("delay-ms", 500, 0, 5000);
    public final BooleanProperty sendSecond = new BooleanProperty("second-message", false);
    public final TextProperty secondText = new TextProperty("second-text", "gl", this.sendSecond::getValue);
    public final IntProperty secondDelay = new IntProperty("second-delay-ms", 500, 0, 5000, this.sendSecond::getValue);

    private boolean activated;
    private String queuedFirst;
    private String queuedSecond;
    private long queuedAt;

    public AutoGG() {
        super("AutoGG", false, false, "Automatically sends chat messages when a game ends.");
    }

    @Override
    public void onEnabled() {
        clear();
    }

    @Override
    public void onDisabled() {
        clear();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.activated ? "sent" : "idle"};
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE
                || !(event.getPacket() instanceof S02PacketChat) || this.activated) {
            return;
        }
        String message;
        try {
            message = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        } catch (Exception ignored) {
            return;
        }
        if (message == null || message.contains(":")) {
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        boolean ended = false;
        for (String token : GAME_END_TOKENS) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                ended = true;
                break;
            }
        }
        if (!ended) {
            return;
        }
        this.activated = true;
        this.queuedAt = System.currentTimeMillis();
        if (this.sendFirst.getValue() && !this.firstText.getValue().isEmpty()) {
            this.queuedFirst = this.firstText.getValue();
        }
        if (this.sendSecond.getValue() && !this.secondText.getValue().isEmpty()) {
            this.queuedSecond = this.secondText.getValue();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.queuedAt;
        if (this.queuedFirst != null && elapsed >= this.delay.getValue()) {
            ChatUtil.sendMessage(this.queuedFirst);
            this.queuedFirst = null;
        }
        if (this.queuedSecond != null && elapsed >= this.secondDelay.getValue()) {
            ChatUtil.sendMessage(this.queuedSecond);
            this.queuedSecond = null;
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        clear();
    }

    private void clear() {
        this.activated = false;
        this.queuedFirst = null;
        this.queuedSecond = null;
        this.queuedAt = 0L;
    }
}
