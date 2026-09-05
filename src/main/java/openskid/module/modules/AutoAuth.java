package openskid.module.modules;

import java.util.Locale;
import openskid.event.EventTarget;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.event.types.EventType;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.property.properties.TextProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoAuth extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final TextProperty password = new TextProperty("password", "12341234");
    public final IntProperty delay = new IntProperty("delay", 1000, 100, 5000);

    private String queuedCommand;
    private long queuedAt;
    private boolean sentThisWorld = false;

    public AutoAuth() {
        super("AutoAuth", false, false, "Automatically sends login and register commands on servers.");
    }

    @Override
    public void onEnabled() {
        clearQueue();
        this.sentThisWorld = false;
    }

    @Override
    public void onDisabled() {
        clearQueue();
        this.sentThisWorld = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()
                || event.getType() != EventType.RECEIVE
                || !(event.getPacket() instanceof S02PacketChat)) {
            return;
        }

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.isChat()) {
            return;
        }
        String message = packet.getChatComponent().getUnformattedText();
        AuthPrompt prompt = AuthPrompt.from(message);
        if (prompt == null || this.sentThisWorld) {
            return;
        }

        String secret = this.password.getValue();
        if (secret == null || secret.isEmpty()) {
            return;
        }
        this.sentThisWorld = true;
        queue(prompt.buildCommand(secret));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || this.queuedCommand == null) {
            return;
        }

        if (System.currentTimeMillis() - this.queuedAt >= this.delay.getValue()) {
            if (mc.thePlayer != null && this.queuedCommand.startsWith("/")) {
                mc.thePlayer.sendChatMessage(this.queuedCommand);
            }
            clearQueue();
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        clearQueue();
        this.sentThisWorld = false;
    }

    private void queue(String command) {
        this.queuedCommand = command;
        this.queuedAt = System.currentTimeMillis();
    }

    private void clearQueue() {
        this.queuedCommand = null;
        this.queuedAt = 0L;
    }

    private enum AuthPrompt {
        REGISTER("/register "),
        SHORT_REGISTER("/reg "),
        LOGIN("/login ");

        private final String trigger;

        AuthPrompt(String trigger) {
            this.trigger = trigger;
        }

        private static AuthPrompt from(String message) {
            String normalized = message.toLowerCase(Locale.ROOT);
            for (AuthPrompt prompt : values()) {
                if (normalized.contains(prompt.trigger)) {
                    return prompt == SHORT_REGISTER ? REGISTER : prompt;
                }
            }
            return null;
        }

        private String buildCommand(String password) {
            return (this == LOGIN ? "/login " : "/register ") + password;
        }
    }
}