package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.events.Render2DEvent;
import openskid.events.Render3DEvent;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ColorProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.BlockPos;

import java.util.Locale;

// Landing-spot marker with distance readout while falling. Concept adapted from the Expo FallIndicator module.
public class FallIndicator extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty minFall = new FloatProperty("min-fall", 3.0F, 1.0F, 10.0F);
    public final IntProperty maxDistance = new IntProperty("max-distance", 48, 8, 64);
    public final ModeProperty marker = new ModeProperty("marker", 0, new String[]{"CIRCLE", "BOX", "BOTH"});
    public final ColorProperty color = new ColorProperty("color", 0xFF5555);
    public final BooleanProperty showDistance = new BooleanProperty("show-distance", true);

    private float lastDistance = -1.0F;

    public FallIndicator() {
        super("FallIndicator", false, false, "Marks your landing spot and distance while falling.");
    }

    @Override
    public String[] getSuffix() {
        return lastDistance >= 0.0F
                ? new String[]{String.format(Locale.US, "%.1fm", lastDistance)}
                : new String[0];
    }

    @Override
    public void onEnabled() {
        lastDistance = -1.0F;
    }

    @Override
    public void onDisabled() {
        lastDistance = -1.0F;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        lastDistance = -1.0F;
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.onGround || mc.thePlayer.capabilities.isFlying) {
            return;
        }
        if (mc.thePlayer.fallDistance < minFall.getValue()) {
            return;
        }
        int maxD = maxDistance.getValue();
        int bx = (int) Math.floor(mc.thePlayer.posX);
        int bz = (int) Math.floor(mc.thePlayer.posZ);
        int top = (int) Math.floor(mc.thePlayer.posY) - 1;
        int groundY = -1;
        for (int y = top; y > top - maxD && y > 0; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (!mc.theWorld.isBlockLoaded(pos, false)) {
                return;
            }
            if (mc.theWorld.getBlockState(pos).getBlock().getMaterial().isSolid()) {
                groundY = y + 1;
                break;
            }
        }
        if (groundY < 0) {
            return;
        }
        float dist = (float) (mc.thePlayer.posY - groundY);
        if (dist > maxD) {
            return;
        }
        lastDistance = dist;
        int rgb = color.getValue();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double renderX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        RenderUtil.enableRenderState();
        int mode = marker.getValue();
        if (mode == 0 || mode == 2) {
            RenderUtil.drawCircle(bx + 0.5 - renderX, groundY + 0.06 - renderY, bz + 0.5 - renderZ,
                    0.6, 32, 0xFF000000 | rgb);
        }
        if (mode == 1 || mode == 2) {
            RenderUtil.drawBlockBoundingBox(new BlockPos(bx, groundY - 1, bz), 1.0, r, g, b, 255, 1.5F);
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !showDistance.getValue() || lastDistance < 0.0F || mc.thePlayer == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        String text = String.format(Locale.US, "Landing %.1fm", lastDistance);
        int w = mc.fontRendererObj.getStringWidth(text);
        mc.fontRendererObj.drawString(text, (sr.getScaledWidth() - w) / 2.0F,
                sr.getScaledHeight() / 2.0F + 12.0F, 0xFF000000 | color.getValue(), true);
    }
}
