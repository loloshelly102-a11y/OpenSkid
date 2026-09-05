package openskid.module.modules;

import openskid.OpenSkid;
import net.minecraft.client.Minecraft;
import openskid.event.EventTarget;
import openskid.events.Render2DEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.util.RenderUtil;
import openskid.font.impl.UFontRenderer; // 必须导入自定义字体类

public class WaterMark2 extends Module {
    public final IntProperty rectLeft = new IntProperty("RectLeft", 2, 0, 20);
    public final IntProperty rectTop = new IntProperty("RectTop", 2, 0, 20);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);

    public WaterMark2() {
        super("WaterMark2", false, false, "Shows a small client watermark on screen.");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        UFontRenderer fr = OpenSkid.fontManagers.getFont(20);
        String text = "OpenSkid";

        float textWidth = (float) fr.getStringWidth(text);
        float textHeight = (float) fr.getHeight();

        float padX = 6.0F;
        float padY = 4.0F;

        float startX = (float) rectLeft.getValue();
        float startY = (float) rectTop.getValue();

        float rectRight = startX + textWidth + (padX);
        float rectBottom = startY + textHeight + (padY);

        float radius = 4.0f;

        HUD hud = (HUD) OpenSkid.moduleManager.modules.get(HUD.class);

        int fillColor = 0x80000000;
        int hudColor = (hud != null && hud.isEnabled()) ? hud.getColor(System.currentTimeMillis()) : 0xFFFFFFFF;

        RenderUtil.drawRoundedGradientOutlinedRectangle(
                startX, startY, rectRight, rectBottom,
                radius, fillColor, hudColor, hudColor
        );

        fr.drawString(
                text,
                startX + padX / 2,
                startY,
                hudColor,
                shadow.getValue()
        );
    }
}