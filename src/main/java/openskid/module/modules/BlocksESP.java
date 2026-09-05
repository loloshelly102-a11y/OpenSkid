package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.events.LoadWorldEvent;
import openskid.events.Render3DEvent;
import openskid.mixin.IAccessorMinecraft;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.concurrent.CopyOnWriteArraySet;

// Tracked-block positions with a color per type. Concept adapted from the openskid Xray tracked-block set.
public class BlocksESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int SCAN_BUDGET = 4096;
    private static final int MAX_TRACKED = 1024;

    public final IntProperty range = new IntProperty("range", 32, 8, 64);
    public final BooleanProperty diamonds = new BooleanProperty("diamonds", true);
    public final BooleanProperty gold = new BooleanProperty("gold", true);
    public final BooleanProperty iron = new BooleanProperty("iron", true);
    public final BooleanProperty emeralds = new BooleanProperty("emeralds", true);
    public final BooleanProperty coal = new BooleanProperty("coal", false);
    public final BooleanProperty redstone = new BooleanProperty("redstone", false);
    public final BooleanProperty lapis = new BooleanProperty("lapis", false);
    public final BooleanProperty outline = new BooleanProperty("outline", true);
    public final BooleanProperty tracers = new BooleanProperty("tracers", false);

    private final CopyOnWriteArraySet<BlockPos> tracked = new CopyOnWriteArraySet<BlockPos>();
    private int scanCursor = 0;

    public BlocksESP() {
        super("BlocksESP", false, false, "Highlights nearby ores through walls with tracers.");
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(tracked.size())};
    }

    @Override
    public void onEnabled() {
        tracked.clear();
        scanCursor = 0;
    }

    @Override
    public void onDisabled() {
        tracked.clear();
        scanCursor = 0;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        tracked.clear();
        scanCursor = 0;
    }

    private boolean isTrackedType(int id) {
        switch (id) {
            case 56:
                return diamonds.getValue();
            case 14:
                return gold.getValue();
            case 15:
                return iron.getValue();
            case 129:
                return emeralds.getValue();
            case 16:
                return coal.getValue();
            case 73:
            case 74:
                return redstone.getValue();
            case 21:
                return lapis.getValue();
            default:
                return false;
        }
    }

    private int paletteFor(int id) {
        switch (id) {
            case 56:
                return 0x55FFFF;
            case 14:
                return 0xFFAA00;
            case 15:
                return 0xFFFFFF;
            case 129:
                return 0x55FF55;
            case 16:
                return 0x555555;
            case 73:
            case 74:
                return 0xFF5555;
            case 21:
                return 0x5555FF;
            default:
                return 0xFFFFFF;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }
        scanSlice();
        if (tracked.isEmpty()) {
            return;
        }
        double maxDist = range.getValue().doubleValue();
        boolean drawOutline = outline.getValue();
        boolean drawTracers = tracers.getValue();
        Vec3 start = drawTracers ? tracerStart() : null;
        RenderUtil.enableRenderState();
        for (BlockPos pos : tracked) {
            int id = Block.getIdFromBlock(mc.theWorld.getBlockState(pos).getBlock());
            if (!isTrackedType(id)) {
                tracked.remove(pos);
                continue;
            }
            if (mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDist) {
                continue;
            }
            int rgb = paletteFor(id);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            RenderUtil.drawBlockBox(pos, 1.0, r, g, b);
            if (drawOutline) {
                RenderUtil.drawBlockBoundingBox(pos, 1.0, r, g, b, 255, 1.5F);
            }
            if (drawTracers && start != null) {
                RenderUtil.drawLine3D(start, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        r / 255.0F, g / 255.0F, b / 255.0F, 1.0F, 1.5F);
            }
        }
        RenderUtil.disableRenderState();
    }

    private void scanSlice() {
        int r = range.getValue();
        int size = r * 2 + 1;
        int plane = size * size;
        int total = plane * size;
        if (total <= 0) {
            return;
        }
        if (scanCursor < 0 || scanCursor >= total) {
            scanCursor = 0;
        }
        int baseX = (int) Math.floor(mc.thePlayer.posX);
        int baseY = (int) Math.floor(mc.thePlayer.posY);
        int baseZ = (int) Math.floor(mc.thePlayer.posZ);
        int end = Math.min(total, scanCursor + SCAN_BUDGET);
        for (int i = scanCursor; i < end; i++) {
            int dx = (i % size) - r;
            int dy = ((i / size) % size) - r;
            int dz = (i / plane) - r;
            BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
            if (!mc.theWorld.isBlockLoaded(pos, false)) {
                continue;
            }
            int id = Block.getIdFromBlock(mc.theWorld.getBlockState(pos).getBlock());
            if (isTrackedType(id) && tracked.size() < MAX_TRACKED) {
                tracked.add(pos);
            }
        }
        scanCursor = end >= total ? 0 : end;
    }

    private Vec3 tracerStart() {
        Vec3 vec;
        if (mc.gameSettings.thirdPersonView == 0) {
            vec = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch((float) -Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationPitch,
                            mc.getRenderViewEntity().prevRotationPitch,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)))
                    .rotateYaw((float) -Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationYaw,
                            mc.getRenderViewEntity().prevRotationYaw,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)));
        } else {
            vec = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch((float) -Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)))
                    .rotateYaw((float) -Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)));
        }
        return new Vec3(vec.xCoord, vec.yCoord + mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
    }
}
