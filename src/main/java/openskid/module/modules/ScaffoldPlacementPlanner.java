package openskid.module.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import openskid.util.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;

// Target-cell search split adapted from donor ScaffoldPlacementPlanner (rewritten, not copied).
public class ScaffoldPlacementPlanner {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    public static Scaffold.BlockData getBlockData(int stage, boolean shouldKeepY, int startY) {
        int startY0 = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (stage != 0 && !shouldKeepY ? Math.min(startY0, startY) : startY0) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        }
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockUtil.isReplaceable(pos)
                            && !BlockUtil.isInteractable(pos)
                            && !(mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5)
                                    > (double) mc.playerController.getBlockReachDistance())
                            && (stage == 0 || shouldKeepY || pos.getY() < startY)) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN) {
                                BlockPos blockPos = pos.offset(facing);
                                if (BlockUtil.isReplaceable(blockPos)) {
                                    positions.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(
                Comparator.comparingDouble(
                        o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)
                )
        );
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = getBestFacing(blockPos, targetPos);
        return facing == null ? null : new Scaffold.BlockData(blockPos, facing);
    }

    public static List<Scaffold.BlockData> findSurroundCells(int radius, double reach) {
        ArrayList<Scaffold.BlockData> out = new ArrayList<Scaffold.BlockData>();
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos feet = new BlockPos(px, py, pz);
        BlockPos head = feet.up();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos cell = feet.add(dx, dy, dz);
                    if (cell.equals(feet) || cell.equals(head)) {
                        continue;
                    }
                    if (!BlockUtil.isReplaceable(cell)) {
                        continue;
                    }
                    for (EnumFacing facing : EnumFacing.VALUES) {
                        BlockPos anchor = cell.offset(facing);
                        if (anchor.equals(feet) || anchor.equals(head)) {
                            continue;
                        }
                        if (BlockUtil.isReplaceable(anchor) || BlockUtil.isInteractable(anchor)) {
                            continue;
                        }
                        if (mc.thePlayer.getDistance((double) anchor.getX() + 0.5, (double) anchor.getY() + 0.5, (double) anchor.getZ() + 0.5) > reach) {
                            continue;
                        }
                        out.add(new Scaffold.BlockData(anchor, facing.getOpposite()));
                        break;
                    }
                }
            }
        }
        final double ex = mc.thePlayer.posX;
        final double ey = mc.thePlayer.posY;
        final double ez = mc.thePlayer.posZ;
        out.sort(Comparator.comparingDouble(o -> o.blockPos().distanceSqToCenter(ex, ey, ez)));
        return out;
    }
}
