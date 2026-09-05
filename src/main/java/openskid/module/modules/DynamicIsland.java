package openskid.module.modules;

import java.awt.Color;
import openskid.event.EventTarget;
import openskid.events.Render2DEvent;
import openskid.font.CFontRenderer;
import openskid.module.Module;
import openskid.module.modules.Scaffold;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ColorProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.GlowUtils;
import openskid.util.RenderUtil;
import openskid.util.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;

public class DynamicIsland extends Module { // nah bro i took 2 hour just to did this shit
    private boolean showScaffold = false;
    private long scaffoldTime = 0;
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ColorProperty textColor = new ColorProperty("AccentColor", new Color(255, 30, 0).getRGB());
    public final BooleanProperty textShadow = new BooleanProperty("TextShadow", true);
    public final BooleanProperty enableGlow = new BooleanProperty("Glow", true);

    private final int bgAlpha = 130;
    private final float radius = 8f;
    private String cachedText;
    private float cachedWidth;
    private float cachedOpenSkidW;
    private float cachedPart1W;
    private float cachedPart2W;
    private boolean cachedCustomFont;
    private CFontRenderer cachedFont;
    private int cachedAccentRGB = -1;
    private Color cachedOuter;
    private Color cachedMid;
    private Color cachedInner;
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 120);

    public DynamicIsland() { // we always love ai right(no)? credit: ChatGPT and Horaizion
        super("DynamicIsland", true, false, "Shows a top bar with ping server and FPS info.");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Scaffold scaffold = (Scaffold) openskid.OpenSkid.moduleManager.getModule(Scaffold.class);
        boolean scaffoldEnabled = scaffold != null && scaffold.isEnabled();
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);

        String username = mc.thePlayer.getName();
        int ping = getPing();
        int fps = Minecraft.getDebugFPS();
        String server = getServerIP();

        String text = "OpenSkid  ·  " + username + "  ·  " + ping + "ms to " + server + "  ·  " + fps + "fps";

        HUD hud = (HUD) openskid.OpenSkid.moduleManager.getModule(HUD.class);
        CFontRenderer fr = (hud != null && hud.fontRenderer != null) ? hud.fontRenderer : null;
        boolean customFont = fr != null;

        String part1Head = "  ·  " + username + "  ·  ";
        String part2Head = ping + "ms";
        String restHead = " to " + server + "  ·  " + fps + "fps";

        float wOpenSkid;
        float wPart1;
        float wPart2;
        float width;
        if (text.equals(this.cachedText) && customFont == this.cachedCustomFont && fr == this.cachedFont) {
            wOpenSkid = this.cachedOpenSkidW;
            wPart1 = this.cachedPart1W;
            wPart2 = this.cachedPart2W;
            width = this.cachedWidth;
        } else {
            if (customFont) {
                wOpenSkid = fr.getStringWidth("OpenSkid");
                wPart1 = fr.getStringWidth("OpenSkid" + part1Head) - wOpenSkid;
                wPart2 = fr.getStringWidth("OpenSkid" + part1Head + part2Head) - wOpenSkid - wPart1;
                width = fr.getStringWidth(text) + 24f;
            } else {
                wOpenSkid = (float) mc.fontRendererObj.getStringWidth("OpenSkid");
                wPart1 = (float) (mc.fontRendererObj.getStringWidth("OpenSkid" + part1Head) - wOpenSkid);
                wPart2 = (float) (mc.fontRendererObj.getStringWidth("OpenSkid" + part1Head + part2Head) - wOpenSkid - wPart1);
                width = (float) mc.fontRendererObj.getStringWidth(text) + 24f;
            }
            this.cachedText = text;
            this.cachedOpenSkidW = wOpenSkid;
            this.cachedPart1W = wPart1;
            this.cachedPart2W = wPart2;
            this.cachedWidth = width;
            this.cachedCustomFont = customFont;
            this.cachedFont = fr;
        }
        float height = 26f;

        float x = sr.getScaledWidth() / 2f - width / 2f;
        float y = 8f;

        // Glassmorphism Blur Pass
        openskid.util.shader.BlurUtils.prepareBlur();
        RoundedUtils.drawRoundedRect(x, y, width, height, this.radius, 0xFFFFFFFF);
        openskid.util.shader.BlurUtils.blurEnd(2, 4.0f);

        drawBackground(x, y, width, height);

        float textY = y + (height - (fr != null ? fr.getHeight() : mc.fontRendererObj.FONT_HEIGHT)) / 2f;
        float startX = x + 12f;

        int accentRGB = 0xFF000000 | (this.textColor.getValue() & 0xFFFFFF);

        if (fr != null) {
            fr.drawString("OpenSkid", startX, textY, accentRGB);
            fr.drawString(part1Head, startX + wOpenSkid, textY, 0xFFFFFF);

            fr.drawString(part2Head, startX + wOpenSkid + wPart1, textY, accentRGB);

            fr.drawString(restHead, startX + wOpenSkid + wPart1 + wPart2, textY, 0xFFFFFF);
        } else {
            mc.fontRendererObj.drawStringWithShadow("OpenSkid", (int) startX, (int) textY, accentRGB);
            mc.fontRendererObj.drawStringWithShadow(part1Head, (int) (startX + wOpenSkid), (int) textY, 0xFFFFFF);

            mc.fontRendererObj.drawStringWithShadow(part2Head, (int) (startX + wOpenSkid + wPart1), (int) textY, accentRGB);

            mc.fontRendererObj.drawStringWithShadow(restHead, (int) (startX + wOpenSkid + wPart1 + wPart2), (int) textY, 0xFFFFFF);
        }
    }

    private void drawBackground(float x, float y, float w, float h) {
        RenderUtil.enableRenderState();

        // ── Drop shadow (dark, offset downward) ──
        GlowUtils.drawGlow(
                x + 2f, y + 4f,
                w, h,
                40,
                SHADOW_COLOR);

        if (this.enableGlow.getValue()) {
            int raw = this.textColor.getValue();
            if (raw != this.cachedAccentRGB || this.cachedOuter == null) {
                int r = (raw >> 16) & 0xFF;
                int g = (raw >> 8) & 0xFF;
                int b = raw & 0xFF;
                this.cachedAccentRGB = raw;
                this.cachedOuter = new Color(r, g, b, 35);
                this.cachedMid = new Color(r, g, b, 70);
                this.cachedInner = new Color(r, g, b, 110);
            }
            // ── Outer bloom – large, faint ──
            GlowUtils.drawGlow(
                    x, y,
                    w, h,
                    90,
                    this.cachedOuter);

            // ── Mid bloom – medium, moderate ──
            GlowUtils.drawGlow(
                    x, y,
                    w, h,
                    55,
                    this.cachedMid);

            // ── Inner bloom – tight, vibrant ──
            GlowUtils.drawGlow(
                    x, y,
                    w, h,
                    25,
                    this.cachedInner);
        }

        RoundedUtils.drawRoundedRect(
                x, y,
                w, h,
                this.radius,
                new Color(0, 0, 0, this.bgAlpha).getRGB());

        RoundedUtils.drawRoundedRect(
                x + 0.5f, y + 0.5f,
                w - 1f, h - 1f,
                this.radius - 0.5f,
                new Color(255, 255, 255, 18).getRGB());

        RenderUtil.disableRenderState();
    }

    private int getPing() {
        try {
            if (mc.thePlayer == null || mc.getNetHandler() == null) {
                return 0;
            }
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getName());
            if (playerInfo != null) {
                return playerInfo.getResponseTime();
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private String getServerIP() {
        try {
            if (mc.theWorld != null) {
                if (mc.isIntegratedServerRunning()) {
                    return "SinglePlayer";
                } else if (mc.getNetHandler() != null && mc.getNetHandler().getNetworkManager() != null) {
                    return mc.getCurrentServerData().serverIP;
                }
            }
        } catch (Exception e) {
        }
        return "SinglePlayer";
    }
}
