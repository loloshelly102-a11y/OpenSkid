package openskid.module.modules;

import openskid.events.UpdateEvent;
import openskid.util.BlockUtil;
import openskid.util.MoveUtil;
import openskid.util.RandomUtil;
import openskid.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

// 11-mode rotation dispatch split adapted from donor ScaffoldRotationController (rewritten, not copied).
public class ScaffoldRotationController {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125,
            0.09375,
            0.15625,
            0.21875,
            0.28125,
            0.34375,
            0.40625,
            0.46875,
            0.53125,
            0.59375,
            0.65625,
            0.71875,
            0.78125,
            0.84375,
            0.90625,
            0.96875
    };

    public static class Plan {
        public Scaffold.BlockData blockData;
        public Vec3 hitVec;
        public boolean snapCanPlace = true;
        public boolean towerRotating = false;
        public float placeYaw;
        public float placePitch;
    }

    private final Scaffold scaffold;
    private final ScaffoldSessionState session;

    public ScaffoldRotationController(Scaffold scaffold, ScaffoldSessionState session) {
        this.scaffold = scaffold;
        this.session = session;
    }

    public static MovingObjectPosition getPlacementMop(Scaffold.BlockData blockData, float yaw, float pitch) {
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mop == null
                || mop.typeOfHit != MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(blockData.blockPos())
                || mop.sideHit != blockData.facing()) {
            return null;
        }
        return mop;
    }

    public void rememberSnapRotation() {
        this.session.lastSnapPlaceYaw = this.session.yaw;
        this.session.lastSnapPlacePitch = this.session.pitch;
    }

    private boolean isDuplicateSnapRotation(float yaw, float pitch) {
        return !Float.isNaN(this.session.lastSnapPlaceYaw)
                && Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.session.lastSnapPlaceYaw)) < 0.35F;
    }

    private float[] getSnapRotation(Scaffold.BlockData blockData, float yaw, float pitch) {
        float baseYaw = RotationUtil.quantizeAngle(yaw);
        float basePitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(pitch, -90.0F, 90.0F));

        if (!this.isDuplicateSnapRotation(baseYaw, basePitch)) {
            return new float[]{baseYaw, basePitch};
        }

        for (int i = 0; i < 24; i++) {
            float yawStep = 0.35F + 0.075F * (float) (i / 2);
            float pitchStep = 0.025F + 0.01F * (float) (i / 3);
            float testYaw = RotationUtil.quantizeAngle(baseYaw + (i % 2 == 0 ? yawStep : -yawStep));
            float testPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(basePitch + (i % 4 < 2 ? pitchStep : -pitchStep), -90.0F, 90.0F));

            if (!this.isDuplicateSnapRotation(testYaw, testPitch) && getPlacementMop(blockData, testYaw, testPitch) != null) {
                return new float[]{testYaw, testPitch};
            }
        }

        return null;
    }

    public Plan compute(UpdateEvent event) {
        Plan plan = new Plan();
        float currentYaw = this.scaffold.getCurrentYaw();
        float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
        float diagonalYaw = this.scaffold.isDiagonal(currentYaw)
                ? yawDiffTo180
                : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
        boolean snapMode = this.scaffold.rotationMode.getValue() == Scaffold.ROTATION_SNAP || this.scaffold.rotationMode.getValue() == Scaffold.ROTATION_SNAP2;
        boolean threeFmcMode = this.scaffold.rotationMode.getValue() == Scaffold.ROTATION_THREE_FMC;
        boolean threeFmcTelly = this.scaffold.isThreeFmcTellyMode();
        this.session.snapRotating = false;
        if (!this.session.canRotate) {
            switch (this.scaffold.rotationMode.getValue()) {
                case 1:
                    if (this.session.yaw == -180.0F && this.session.pitch == 0.0F) {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                        this.session.pitch = RotationUtil.quantizeAngle(85.0F);
                    } else {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    }
                    break;
                case 2:
                case Scaffold.ROTATION_HPYX2:
                    if (this.session.yaw == -180.0F && this.session.pitch == 0.0F) {
                        this.session.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                        this.session.pitch = RotationUtil.quantizeAngle(85.0F);
                    } else {
                        this.session.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    }
                    break;
                case 3:
                    if (this.session.yaw == -180.0F && this.session.pitch == 0.0F) {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                        this.session.pitch = RotationUtil.quantizeAngle(85.0F);
                    } else {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    }
                    break;
                case 4:
                    float roundedYaw = Math.round(currentYaw / 45.0f) * 45.0f;
                    this.session.yaw = RotationUtil.quantizeAngle(roundedYaw);
                    if (this.session.pitch == 0.0F || !this.session.canRotate) {
                        float godBridgePitch = 79.3f;
                        this.session.pitch = RotationUtil.quantizeAngle(godBridgePitch);
                    }
                    break;
                case 5:
                    if (this.session.yaw == -180.0F && this.session.pitch == 0.0F) {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                        this.session.pitch = RotationUtil.quantizeAngle(85.0F);
                    } else {
                        float targetYaw = this.scaffold.isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180;
                        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - this.session.yaw);
                        float pitchDiff = MathHelper.wrapAngleTo180_float(85.0F - this.session.pitch);
                        float yawTolerance = this.session.rotationTick >= 2 ? RandomUtil.nextFloat(this.scaffold.tellystartrotationminspeed.getValue(), this.scaffold.tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(this.scaffold.tellynormalrotationminspeed.getValue(), this.scaffold.tellynormalrotationmaxspeed.getValue());
                        float pitchTolerance = this.session.rotationTick >= 2 ? RandomUtil.nextFloat(this.scaffold.tellystartrotationminspeed.getValue(), this.scaffold.tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(this.scaffold.tellynormalrotationminspeed.getValue(), this.scaffold.tellynormalrotationmaxspeed.getValue());
                        this.session.yaw = RotationUtil.quantizeAngle(this.session.yaw + RotationUtil.clampAngle(yawDiff, yawTolerance));
                        this.session.pitch = RotationUtil.quantizeAngle(this.session.pitch + RotationUtil.clampAngle(pitchDiff, pitchTolerance));
                    }
                    break;
                case 6:
                    if (this.session.yaw == -180.0F && this.session.pitch == 0.0F) {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                        this.session.pitch = RotationUtil.quantizeAngle(85.0F);
                    } else {
                        this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    }
                    break;
                case Scaffold.ROTATION_SNAP:
                case Scaffold.ROTATION_SNAP2:
                    this.session.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    this.session.pitch = RotationUtil.quantizeAngle(85.0F);
                    break;
                case Scaffold.ROTATION_THREE_FMC:
                    if (this.session.yaw == -180.0F && this.session.pitch == 0.0F) {
                        this.session.yaw = RotationUtil.quantizeAngle(event.getYaw());
                        this.session.pitch = RotationUtil.quantizeAngle(event.getPitch());
                    }
                    break;
            }
        }
        Scaffold.BlockData blockData = ScaffoldPlacementPlanner.getBlockData(this.session.stage, this.session.shouldKeepY, this.session.startY);
        plan.blockData = blockData;

        Vec3 hitVec = null;
        if (blockData != null) {
            double[] x = placeOffsets;
            double[] y = placeOffsets;
            double[] z = placeOffsets;
            switch (blockData.facing()) {
                case NORTH:
                    z = new double[]{0.0};
                    break;
                case EAST:
                    x = new double[]{1.0};
                    break;
                case SOUTH:
                    z = new double[]{1.0};
                    break;
                case WEST:
                    x = new double[]{0.0};
                    break;
                case DOWN:
                    y = new double[]{0.0};
                    break;
                case UP:
                    y = new double[]{1.0};
            }
            float bestYaw = -180.0F;
            float bestPitch = 0.0F;
            float bestDiff = 0.0F;
            for (double dx : x) {
                for (double dy : y) {
                    for (double dz : z) {
                        double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                        double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                        double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                        float baseYaw = RotationUtil.wrapAngleDiff(this.session.yaw, event.getYaw());
                        float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.session.pitch);
                        MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                        if (mop != null
                                && mop.typeOfHit == MovingObjectType.BLOCK
                                && mop.getBlockPos().equals(blockData.blockPos())
                                && mop.sideHit == blockData.facing()) {
                            float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.session.pitch);
                            if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                bestYaw = rotations[0];
                                bestPitch = rotations[1];
                                bestDiff = totalDiff;
                                hitVec = mop.hitVec;
                            }
                        }
                    }
                }
            }
            if (bestYaw != -180.0F || bestPitch != 0.0F) {
                this.session.yaw = bestYaw;
                this.session.pitch = bestPitch;
                this.session.canRotate = true;
            } else if (threeFmcMode) {
                this.session.canRotate = false;
            }
        }
        boolean towerRotating = this.session.towering || this.scaffold.isTowering();
        plan.towerRotating = towerRotating;
        if (snapMode && !towerRotating && blockData != null) {
            MovingObjectPosition currentMop = getPlacementMop(blockData, event.getYaw(), event.getPitch());
            if (currentMop != null) {
                float[] snapRotation = this.getSnapRotation(blockData, event.getYaw(), event.getPitch());
                if (snapRotation == null) {
                    plan.snapCanPlace = false;
                    hitVec = null;
                } else {
                    this.session.yaw = snapRotation[0];
                    this.session.pitch = snapRotation[1];
                    this.session.canRotate = true;
                    MovingObjectPosition snapMop = getPlacementMop(blockData, this.session.yaw, this.session.pitch);
                    hitVec = snapMop != null ? snapMop.hitVec : currentMop.hitVec;
                    this.session.snapRotating = true;
                    int snapDelay = this.scaffold.rotationMode.getValue() == Scaffold.ROTATION_SNAP2 ? 0 : 1;
                    if (this.session.rotationTick > snapDelay) {
                        this.session.rotationTick = snapDelay;
                    }
                }
            } else if (hitVec != null && this.session.canRotate) {
                float[] snapRotation = this.getSnapRotation(blockData, this.session.yaw, this.session.pitch);
                if (snapRotation == null) {
                    plan.snapCanPlace = false;
                    hitVec = null;
                } else {
                    this.session.yaw = snapRotation[0];
                    this.session.pitch = snapRotation[1];
                    MovingObjectPosition snapMop = getPlacementMop(blockData, this.session.yaw, this.session.pitch);
                    if (snapMop != null) {
                        hitVec = snapMop.hitVec;
                    }
                    this.session.snapRotating = true;
                    int snapDelay = this.scaffold.rotationMode.getValue() == Scaffold.ROTATION_SNAP2 ? 0 : 1;
                    if (this.session.rotationTick > snapDelay) {
                        this.session.rotationTick = snapDelay;
                    }
                }
            }
        }
        if (this.session.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.session.yaw)) < 90.0F) {
            switch (this.scaffold.rotationMode.getValue()) {
                case 2:
                case Scaffold.ROTATION_HPYX2:
                    this.session.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    break;
                case 3:
                    this.session.yaw = RotationUtil.quantizeAngle(diagonalYaw);
            }
        }
        float placeYaw = this.session.yaw;
        float placePitch = this.session.pitch;
        if (this.scaffold.rotationMode.getValue() != 0 && (!snapMode || this.session.snapRotating || towerRotating)) {
            float targetYaw = this.session.yaw;
            float targetPitch = this.session.pitch;
            if ((!threeFmcMode || threeFmcTelly) && this.session.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.session.startY + 1))) {
                float yawDiff = MathHelper.wrapAngleTo180_float(this.session.yaw - event.getYaw());
                float tolerance = this.session.rotationTick >= 2 ? RandomUtil.nextFloat(this.scaffold.tellystartrotationminspeed.getValue(), this.scaffold.tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(this.scaffold.tellynormalrotationminspeed.getValue(), this.scaffold.tellynormalrotationmaxspeed.getValue());
                if (Math.abs(yawDiff) > tolerance) {
                    float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                    targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                    this.session.rotationTick = Math.max(this.session.rotationTick, 1);
                }
            }
            if (towerRotating && this.scaffold.isTowering()) {
                if (!threeFmcMode || threeFmcTelly) {
                    float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                    targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                    targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                }
                this.session.rotationTick = 3;
                this.session.towering = true;
            }

            if (this.scaffold.rotationMode.getValue() == Scaffold.ROTATION_HPYX2 && !towerRotating) {
                float yawSpeed = this.session.rotationTick >= 2
                        ? RandomUtil.nextFloat(75.0F, 90.0F)
                        : RandomUtil.nextFloat(32.0F, 45.0F);
                float pitchSpeed = this.session.rotationTick >= 2
                        ? RandomUtil.nextFloat(35.0F, 45.0F)
                        : RandomUtil.nextFloat(17.0F, 27.0F);
                float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                float pitchDelta = MathHelper.clamp_float(targetPitch - event.getPitch(), -90.0F, 90.0F);
                targetYaw = RotationUtil.quantizeAngle(event.getYaw() + RotationUtil.clampAngle(yawDelta, yawSpeed));
                targetPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(
                        event.getPitch() + RotationUtil.clampAngle(pitchDelta, pitchSpeed), -90.0F, 90.0F));

                if (blockData != null && hitVec != null) {
                    MovingObjectPosition hpyx2Mop = getPlacementMop(blockData, targetYaw, targetPitch);
                    if (hpyx2Mop == null) {
                        plan.snapCanPlace = false;
                    } else {
                        hitVec = hpyx2Mop.hitVec;
                    }
                }
            }
            placeYaw = targetYaw;
            placePitch = targetPitch;
            event.setRotation(targetYaw, targetPitch, 3);
            if (this.scaffold.moveFix.getValue() == 1) {
                event.setPervRotation(targetYaw, 3);
            }
        }
        plan.hitVec = hitVec;
        plan.placeYaw = placeYaw;
        plan.placePitch = placePitch;
        return plan;
    }
}
