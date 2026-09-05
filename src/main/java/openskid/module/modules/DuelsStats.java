package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.Render2DEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.TextProperty;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;

import java.awt.Color;
import java.util.Locale;

// Concept adapted from raven-bS DuelsStats (opponent lookup via Hypixel API).
// Rebuilt for OpenSkid as an offline session win, loss and streak tracker. No pasted code.
public class DuelsStats extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty reset = new ModeProperty("reset", 0, new String[]{"MANUAL", "ON_WORLD"});
    public final TextProperty resetCmd = new TextProperty("reset-cmd", "resetduels", () -> reset.getValue() == 0);
    public final BooleanProperty chatAlert = new BooleanProperty("chat-alert", true);
    public final BooleanProperty hud = new BooleanProperty("hud", true);
    public final BooleanProperty showStreak = new BooleanProperty("show-streak", true);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty offsetX = new IntProperty("offset-x", 4, 0, 1000);
    public final IntProperty offsetY = new IntProperty("offset-y", 90, 0, 1000);

    private int wins;
    private int losses;
    private int streak;
    private int bestStreak;

    public DuelsStats() {
        super("DuelsStats", false, false, "Tracks duel wins losses and streaks for this session.");
    }

    @Override
    public void onEnabled() {
        resetStats();
    }

    @Override
    public void onDisabled() {
        resetStats();
    }

    @Override
    public String[] getSuffix() {
        return wins + losses > 0 ? new String[]{wins + "-" + losses} : new String[0];
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C01PacketChatMessage) {
            String sent;
            try {
                sent = ((C01PacketChatMessage) event.getPacket()).getMessage();
            } catch (Exception ignored) {
                return;
            }
            if (sent != null) {
                String want = resetCmd.getValue();
                if (want != null && sent.trim().equalsIgnoreCase(want.trim())) {
                    event.setCancelled(true);
                    resetStats();
                    ChatUtil.sendFormatted(String.format("%s%s: &fSession stats reset&r", OpenSkid.clientName, getName()));
                }
            }
            return;
        }
        if (event.getType() != EventType.RECEIVE || !(event.getPacket() instanceof S02PacketChat)) {
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
        String stripped;
        try {
            stripped = EnumChatFormatting.getTextWithoutFormattingCodes(raw);
        } catch (Exception ignored) {
            return;
        }
        if (stripped == null || stripped.isEmpty()) {
            return;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        boolean win = lower.contains("you won!") || lower.contains("victory!")
                || (lower.contains("duel") && lower.contains("you win"));
        boolean loss = lower.contains("you lost") || lower.contains("defeat")
                || lower.contains("you were killed") || (lower.contains("duel") && lower.contains("you lose"));
        if (win) {
            wins++;
            streak = Math.max(0, streak) + 1;
            bestStreak = Math.max(bestStreak, streak);
            announce();
        } else if (loss) {
            losses++;
            streak = Math.min(0, streak) - 1;
            announce();
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        if (reset.getValue() == 1) {
            resetStats();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || !hud.getValue() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.gameSettings.showDebugInfo || wins + losses == 0) {
            return;
        }
        String text = "Duels: " + wins + "-" + losses;
        if (showStreak.getValue()) {
            text += " (" + streak + ")";
        }
        float scaleValue = scale.getValue();
        ScaledResolution resolution = new ScaledResolution(mc);
        float baseX = (float) offsetX.getValue();
        float baseY = (float) offsetY.getValue();
        if (baseX > resolution.getScaledWidth() || baseY > resolution.getScaledHeight()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(baseX, baseY, 0.0F);
        GlStateManager.scale(scaleValue, scaleValue, 1.0F);
        int width = mc.fontRendererObj.getStringWidth(text);
        Gui.drawRect(-2, -2, width + 2, mc.fontRendererObj.FONT_HEIGHT + 1, new Color(0, 0, 0, 120).getRGB());
        mc.fontRendererObj.drawStringWithShadow(text, 0.0F, 0.0F, 16777215);
        GlStateManager.popMatrix();
    }

    private void announce() {
        if (!chatAlert.getValue()) {
            return;
        }
        ChatUtil.sendFormatted(String.format("%s%s: &fSession &a%dW&r-&c%dL&r &7streak &e%d&r &7best &e%d&r",
                OpenSkid.clientName, getName(), wins, losses, streak, bestStreak));
    }

    private void resetStats() {
        wins = 0;
        losses = 0;
        streak = 0;
        bestStreak = 0;
    }
}
