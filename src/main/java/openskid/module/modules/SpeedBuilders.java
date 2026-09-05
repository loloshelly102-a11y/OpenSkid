package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.Render2DEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Concept adapted from raven-bS SpeedBuilders (auto-place, auto-swap and block ESP).
// Rebuilt for OpenSkid as a passive helper only: build-phase timer plus hotbar block counts. No auto-build, no auto-swap, no rendering of target blocks.
public class SpeedBuilders extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty display = new ModeProperty("display", 2, new String[]{"CHAT", "HUD", "BOTH"});
    public final BooleanProperty phaseTimer = new BooleanProperty("phase-timer", true);
    public final BooleanProperty blockHelper = new BooleanProperty("block-helper", true);
    public final IntProperty warnSecs = new IntProperty("warn-secs", 10, 1, 60, () -> display.getValue() == 1);
    public final BooleanProperty announcePhase = new BooleanProperty("announce-phase", true, () -> display.getValue() == 2);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty offsetX = new IntProperty("offset-x", 4, 0, 1000);
    public final IntProperty offsetY = new IntProperty("offset-y", 120, 0, 1000);

    private String phase = "";
    private long phaseAt;
    private int blocks;
    private int tick;
    private boolean warned;

    public SpeedBuilders() {
        super("SpeedBuilders", false, false, "Tracks Speed Builders phases and block counts.");
    }

    @Override
    public void onEnabled() {
        reset();
    }

    @Override
    public void onDisabled() {
        reset();
    }

    @Override
    public String[] getSuffix() {
        return phase.isEmpty() ? new String[0] : new String[]{phase};
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
        String next = null;
        if (lower.contains("recreate the build") || lower.contains("game starts in")) {
            next = "SHOWING";
        } else if (lower.contains("build phase") || lower.contains("start building") || lower.contains("time left")) {
            next = "BUILDING";
        } else if (lower.contains("judging") || lower.contains("vote")) {
            next = "JUDGING";
        } else if (lower.contains("perfect build") || lower.contains("round over")) {
            next = "DONE";
        }
        if (next != null && !next.equals(phase)) {
            setPhase(next);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        if (++tick % 20 != 0) {
            return;
        }
        if (blockHelper.getValue()) {
            blocks = countBlocks();
        }
        if (phaseTimer.getValue() && "BUILDING".equals(phase) && !warned && warnSecs.getValue() > 0 && phaseAt > 0L) {
            long elapsed = (System.currentTimeMillis() - phaseAt) / 1000L;
            if (elapsed >= warnSecs.getValue()) {
                warned = true;
                if (display.getValue() == 0 || display.getValue() == 2) {
                    ChatUtil.sendFormatted(String.format("%s%s: &fBuild time check &7(%ds in, %d blocks held)&r",
                            OpenSkid.clientName, getName(), elapsed, blocks));
                }
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (display.getValue() == 0 || mc.gameSettings.showDebugInfo || phase.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        if (phaseTimer.getValue()) {
            long secs = phaseAt > 0L ? (System.currentTimeMillis() - phaseAt) / 1000L : 0L;
            lines.add("Phase: " + phase + " " + secs + "s");
        }
        if (blockHelper.getValue()) {
            lines.add("Blocks: " + blocks);
        }
        if (lines.isEmpty()) {
            return;
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
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, mc.fontRendererObj.getStringWidth(line));
        }
        int height = lines.size() * (mc.fontRendererObj.FONT_HEIGHT + 1);
        Gui.drawRect(-2, -2, width + 2, height, new Color(0, 0, 0, 120).getRGB());
        float y = 0.0F;
        for (String line : lines) {
            mc.fontRendererObj.drawStringWithShadow(line, 0.0F, y, 16777215);
            y += mc.fontRendererObj.FONT_HEIGHT + 1.0F;
        }
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        reset();
    }

    private void setPhase(String next) {
        phase = next;
        phaseAt = System.currentTimeMillis();
        warned = false;
        if (phaseTimer.getValue() && (display.getValue() == 0 || (display.getValue() == 2 && announcePhase.getValue()))) {
            ChatUtil.sendFormatted(String.format("%s%s: &fPhase &e%s&r", OpenSkid.clientName, getName(), phase));
        }
    }

    private int countBlocks() {
        int count = 0;
        try {
            if (mc.thePlayer.inventory == null || mc.thePlayer.inventory.mainInventory == null) {
                return 0;
            }
            for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
                if (stack != null && stack.getItem() instanceof ItemBlock) {
                    count += stack.stackSize;
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    private void reset() {
        phase = "";
        phaseAt = 0L;
        blocks = 0;
        tick = 0;
        warned = false;
    }
}
