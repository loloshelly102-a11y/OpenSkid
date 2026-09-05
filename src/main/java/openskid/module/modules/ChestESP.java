package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.events.Render3DEvent;
import openskid.mixin.IAccessorMinecraft;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ColorProperty;
import openskid.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ColorProperty chest = new ColorProperty("chest", new Color(255, 170, 0).getRGB());
    public final ColorProperty trappedChest = new ColorProperty("trapped-chest", new Color(255, 43, 0).getRGB());
    public final ColorProperty enderChest = new ColorProperty("ender-chest", new Color(26, 17, 0).getRGB());
    public final BooleanProperty tracers = new BooleanProperty("tracers", false);
    private static final double MAX_DIST_SQ = 64.0 * 64.0;

    public ChestESP() {
        super("ChestESP", false, false, "Highlights normal, trapped and ender chests through walls.");
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }
        int chestRGB = this.chest.getValue();
        int trappedRGB = this.trappedChest.getValue();
        int enderRGB = this.enderChest.getValue();
        double renderX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        double viewX = mc.getRenderViewEntity().posX;
        double viewY = mc.getRenderViewEntity().posY + (double) mc.getRenderViewEntity().getEyeHeight();
        double viewZ = mc.getRenderViewEntity().posZ;
        boolean drawTracers = this.tracers.getValue();
        Vec3 tracerStart = null;
        float tracerOpacity = 0.0F;
        if (drawTracers) {
            tracerStart = this.getTracerStart();
            tracerOpacity = (float) ((Tracers) OpenSkid.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
        }
        RenderUtil.enableRenderState();
        for (TileEntity chest : mc.theWorld.loadedTileEntityList) {
            if (!(chest instanceof TileEntityChest) && !(chest instanceof TileEntityEnderChest)) {
                continue;
            }
            double dx = (double) chest.getPos().getX() + 0.5 - viewX;
            double dy = (double) chest.getPos().getY() + 0.5 - viewY;
            double dz = (double) chest.getPos().getZ() + 0.5 - viewZ;
            if (dx * dx + dy * dy + dz * dz > MAX_DIST_SQ) {
                continue;
            }
                Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
                double minX, minZ, maxX, maxZ;
                int rgb;
                minX = minZ = 0.0625;
                maxX = maxZ = 0.9375;
                if (block instanceof BlockChest) {
                    if (block.canProvidePower()) {
                        rgb = trappedRGB;
                    } else {
                        rgb = chestRGB;
                    }
                    EnumFacing facing = mc.theWorld.getBlockState(chest.getPos()).getValue(BlockChest.FACING);
                    switch (facing) {
                        case NORTH:
                            if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) {
                                minX -= 1;
                            }
                            break;
                        case SOUTH:
                            if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) {
                                maxX += 1;
                            }
                            break;
                        case WEST:
                            if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) {
                                maxZ += 1;
                            }
                            break;
                        case EAST:
                            if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) {
                                minZ -= 1;
                            }
                            break;
                        default:
                            continue;
                    }
                } else {
                    rgb = enderRGB;
                }
                int cr = (rgb >> 16) & 0xFF;
                int cg = (rgb >> 8) & 0xFF;
                int cb = rgb & 0xFF;
                AxisAlignedBB aabb = new AxisAlignedBB(
                        (double) chest.getPos().getX() + minX,
                        (double) chest.getPos().getY() + 0.0,
                        (double) chest.getPos().getZ() + minZ,
                        (double) chest.getPos().getX() + maxX,
                        (double) chest.getPos().getY() + 0.875,
                        (double) chest.getPos().getZ() + maxZ
                )
                        .offset(
                                -renderX,
                                -renderY,
                                -renderZ
                        );
                RenderUtil.drawBoundingBox(
                        aabb, cr, cg, cb, 255, 1.5F
                );
                if (drawTracers && tracerStart != null) {
                    RenderUtil.drawLine3D(
                            tracerStart,
                            (double) chest.getPos().getX() + 0.5,
                            (double) chest.getPos().getY() + 0.5,
                            (double) chest.getPos().getZ() + 0.5,
                            (float) cr / 255.0F,
                            (float) cg / 255.0F,
                            (float) cb / 255.0F,
                            tracerOpacity,
                            1.5F
                    );
                }
            }
            RenderUtil.disableRenderState();
    }

    private Vec3 getTracerStart() {
        Vec3 vec;
        if (mc.gameSettings.thirdPersonView == 0) {
            vec = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch(
                            (float) (
                                    -Math.toRadians(
                                            RenderUtil.lerpFloat(
                                                    mc.getRenderViewEntity().rotationPitch,
                                                    mc.getRenderViewEntity().prevRotationPitch,
                                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                            )
                                    )
                            )
                    )
                    .rotateYaw(
                            (float) (
                                    -Math.toRadians(
                                            RenderUtil.lerpFloat(
                                                    mc.getRenderViewEntity().rotationYaw,
                                                    mc.getRenderViewEntity().prevRotationYaw,
                                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                            )
                                    )
                            )
                    );
        } else {
            vec = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch(
                            (float) (
                                    -Math.toRadians(
                                            RenderUtil.lerpFloat(
                                                    mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                            )
                                    )
                            )
                    )
                    .rotateYaw(
                            (float) (
                                    -Math.toRadians(
                                            RenderUtil.lerpFloat(
                                                    mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                            )
                                    )
                            )
                    );
        }
        return new Vec3(vec.xCoord, vec.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
    }
}