package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.TextProperty;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

// Block clutch ported from Raven bS-16 Clutch, rewritten for OpenSkid.
// Aims at the best cell below while falling, places on alignment, snaps back after.
// Set clutch-key (e.g. X) to arm only while held, empty means the sneak rule decides.
public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty reach = new FloatProperty("reach", 4.5F, 0.5F, 6.0F);
    public final FloatProperty aimSpeed = new FloatProperty("aim-speed", 8.0F, 0.0F, 100.0F);
    public final FloatProperty snapbackSpeed = new FloatProperty("snapback-speed", 12.0F, 0.0F, 100.0F);
    public final IntProperty maxDistance = new IntProperty("max-distance", 10, 0, 20);
    public final FloatProperty rotationTolerance = new FloatProperty("rotation-tolerance", 25.0F, 20.0F, 100.0F);
    public final BooleanProperty simulateFuture = new BooleanProperty("simulate-future", true);
    public final BooleanProperty autoClutch = new BooleanProperty("auto-clutch", false);
    public final FloatProperty minFall = new FloatProperty("min-fall", 10.0F, 3.0F, 20.0F);
    public final BooleanProperty requireSneak = new BooleanProperty("require-sneak", true);
    public final TextProperty clutchKey = new TextProperty("clutch-key", "");

    private float aimYaw;
    private float aimPitch;
    private boolean hasAim = false;
    private boolean placing = false;
    private boolean resetting = false;
    private boolean slotWasSwapped = false;
    private boolean autoClickerWasOn = false;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private int clutchBlocksPlaced = 0;
    private boolean autoClutchActive = false;
    private boolean autoClutchChecking = false;
    private int autoClutchCheckCounter = 0;
    private boolean autoClutchLandedGuard = false;
    private int autoClutchLandedTick = 0;
    private int prevHurtTime = -1;

    public Clutch() {
        super("Clutch", false, false, "Automatically places blocks below you to clutch falls.");
    }

    @Override
    public void onEnabled() {
        this.hasAim = false;
        this.resetting = false;
        this.clutchBlocksPlaced = 0;
        this.autoClutchActive = false;
        this.autoClutchChecking = false;
        this.autoClutchCheckCounter = 0;
        this.autoClutchLandedGuard = false;
        this.autoClutchLandedTick = 0;
        this.prevHurtTime = -1;
    }

    @Override
    public void onDisabled() {
        this.clearAim(true);
        this.disablePlacing(true);
        this.autoClutchActive = false;
        this.autoClutchChecking = false;
        this.autoClutchLandedGuard = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            return;
        }
        if (mc.thePlayer.onGround) {
            this.clutchBlocksPlaced = 0;
        }
        if (this.resetting && !this.hasAim) {
            float[] smoothed = this.smoothRotation(event.getYaw(), event.getPitch(),
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, true);
            event.setRotation(smoothed[0], smoothed[1], 2);
            if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - mc.thePlayer.rotationYaw)) < 0.5F
                    && Math.abs(smoothed[1] - mc.thePlayer.rotationPitch) < 0.5F) {
                this.resetting = false;
            }
            return;
        }
        if (!this.isEnabled()) {
            return;
        }
        this.updateAutoClutch();
        boolean armed;
        String keyName = this.clutchKey.getValue();
        if (keyName != null && !keyName.trim().isEmpty()) {
            armed = this.isKeyHeld(keyName);
        } else {
            armed = !this.requireSneak.getValue() || mc.thePlayer.movementInput.sneak || mc.gameSettings.keyBindSneak.isKeyDown();
        }
        boolean active = armed || this.autoClutchActive;
        if (mc.currentScreen != null || !active) {
            this.clearAim(true);
            this.disablePlacing(false);
            return;
        }
        if (mc.thePlayer.onGround || mc.thePlayer.motionY >= 0.0) {
            this.clearAim(true);
            this.disablePlacing(false);
            return;
        }
        int slot = this.pickBlockSlot();
        if (slot == -1) {
            this.clearAim(true);
            this.disablePlacing(false);
            return;
        }
        this.plannedSlot = slot;
        AimTarget aim = this.clutchAim();
        if (aim == null) {
            this.clearAim(true);
            this.disablePlacing(false);
            return;
        }
        this.targetHitPos = aim.pos;
        this.targetSide = aim.side;
        this.hasAim = true;
        this.resetting = false;
        if (!this.placing) {
            this.enablePlacing();
        }
        this.equipPlannedSlot();
        float[] smoothed = this.smoothRotation(event.getYaw(), event.getPitch(), aim.yaw, aim.pitch, false);
        event.setRotation(smoothed[0], smoothed[1], 2);
        MovingObjectPosition mop = this.rayCast(this.reach.getValue(), smoothed[0], smoothed[1]);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && this.targetHitPos.equals(mop.getBlockPos()) && this.targetSide == mop.sideHit) {
            float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - event.getYaw()));
            float pitchDelta = Math.abs(smoothed[1] - event.getPitch());
            if (yawDelta <= this.rotationTolerance.getValue() && pitchDelta <= this.rotationTolerance.getValue()) {
                int maxBlocks = this.maxDistance.getValue();
                if (maxBlocks == 0 || this.clutchBlocksPlaced < maxBlocks) {
                    ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
                    if (stack != null && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                            mop.getBlockPos(), mop.sideHit, mop.hitVec)) {
                        mc.thePlayer.swingItem();
                        if (mop.sideHit != EnumFacing.UP) {
                            this.clutchBlocksPlaced++;
                        }
                    }
                }
            }
        }
    }

    private boolean isKeyHeld(String keyName) {
        int code = Keyboard.getKeyIndex(keyName.trim().toUpperCase());
        return code != 0 && Keyboard.isKeyDown(code);
    }

    private void updateAutoClutch() {
        if (!this.autoClutch.getValue()) {
            this.autoClutchActive = false;
            this.autoClutchChecking = false;
            this.autoClutchLandedGuard = false;
            this.prevHurtTime = mc.thePlayer.hurtTime;
            return;
        }
        int curHurt = mc.thePlayer.hurtTime;
        if (curHurt > this.prevHurtTime) {
            this.autoClutchChecking = true;
            this.autoClutchCheckCounter = 0;
            this.autoClutchLandedGuard = false;
        }
        this.prevHurtTime = curHurt;
        if (this.autoClutchChecking && !this.autoClutchActive && !this.autoClutchLandedGuard) {
            if ((this.autoClutchCheckCounter == 0 || this.autoClutchCheckCounter % 3 == 0)
                    && this.willFallFar(this.minFall.getValue())) {
                this.autoClutchActive = true;
            }
            this.autoClutchCheckCounter++;
        }
        if (this.autoClutchLandedGuard) {
            boolean expired = mc.thePlayer.ticksExisted - this.autoClutchLandedTick >= 10;
            boolean jumped = mc.gameSettings.keyBindJump.isKeyDown();
            boolean airborneUp = !mc.thePlayer.onGround && mc.thePlayer.motionY > 0.0;
            if (expired || jumped || airborneUp) {
                this.autoClutchActive = false;
                this.autoClutchChecking = false;
                this.autoClutchLandedGuard = false;
            }
        }
        if (this.autoClutchActive && mc.thePlayer.onGround && mc.thePlayer.hurtTime < mc.thePlayer.maxHurtTime - 2 && !this.autoClutchLandedGuard) {
            this.autoClutchLandedGuard = true;
            this.autoClutchLandedTick = mc.thePlayer.ticksExisted;
            if (!this.willFallFar(2.0)) {
                this.autoClutchActive = false;
                this.autoClutchChecking = false;
                this.autoClutchLandedGuard = false;
            }
        }
        if (!this.autoClutchActive && !this.autoClutchLandedGuard && mc.thePlayer.onGround && mc.thePlayer.hurtTime == 0) {
            this.autoClutchChecking = false;
            this.autoClutchCheckCounter = 0;
        }
    }

    private boolean willFallFar(double minFall) {
        double startY = mc.thePlayer.posY;
        PredictionState prediction = PredictionState.fromPlayer();
        for (int t = 0; t < 60; t++) {
            prediction.tick(false);
            if (prediction.onGround) {
                return false;
            }
            if (startY - prediction.posY > minFall) {
                return true;
            }
        }
        return false;
    }

    private AimTarget clutchAim() {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 futurePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (this.simulateFuture.getValue()) {
            PredictionState prediction = PredictionState.fromPlayer();
            for (int t = 0; t < 20; t++) {
                prediction.tick(false);
                if (prediction.posY < mc.thePlayer.posY - 2.0 || prediction.onGround) {
                    break;
                }
            }
            futurePos = prediction.getPos();
        }
        int feetX = MathHelper.floor_double(mc.thePlayer.posX);
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        int feetZ = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos bestCell = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = feetY - 1; y >= feetY - 4; y--) {
            for (int x = feetX - 5; x <= feetX + 4; x++) {
                for (int z = feetZ - 5; z <= feetZ + 4; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (this.canPlaceThrough(pos)) {
                        continue;
                    }
                    double currentDist = this.distToBox(futurePos, pos);
                    double score = this.simulateFuture.getValue()
                            ? this.distToBox(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ), pos) * 0.3 + currentDist * 0.7
                            : currentDist;
                    if (score < bestScore) {
                        bestScore = score;
                        bestCell = pos;
                    }
                }
            }
        }
        if (bestCell == null) {
            return null;
        }
        boolean underPlayer = bestCell.getY() < feetY
                && bestCell.getX() == feetX && bestCell.getZ() == feetZ;
        float[] rotations;
        EnumFacing face;
        if (underPlayer) {
            rotations = openskid.util.RotationUtil.getRotationsTo(bestCell.getX() + 0.5, bestCell.getY() + 1.0, bestCell.getZ() + 0.5,
                    eventYaw(), eventPitch());
            face = EnumFacing.UP;
        } else {
            face = this.bestSideFace(bestCell, eye);
            Vec3 hit = new Vec3(bestCell.getX() + 0.5 + face.getFrontOffsetX() * 0.5,
                    bestCell.getY() + 0.5 + face.getFrontOffsetY() * 0.5,
                    bestCell.getZ() + 0.5 + face.getFrontOffsetZ() * 0.5);
            rotations = openskid.util.RotationUtil.getRotationsTo(hit.xCoord, hit.yCoord, hit.zCoord, eventYaw(), eventPitch());
        }
        return new AimTarget(bestCell, face, rotations[0], rotations[1]);
    }

    private float eventYaw() {
        return openskid.util.RotationUtil.serverYaw;
    }

    private float eventPitch() {
        return openskid.util.RotationUtil.serverPitch;
    }

    private EnumFacing bestSideFace(BlockPos cell, Vec3 eye) {
        boolean faceSouth = Math.abs(eye.zCoord - (cell.getZ() + 1)) < Math.abs(eye.zCoord - cell.getZ());
        boolean faceEast = Math.abs(eye.xCoord - (cell.getX() + 1)) < Math.abs(eye.xCoord - cell.getX());
        double dx = Math.abs(eye.xCoord - (cell.getX() + 0.5));
        double dz = Math.abs(eye.zCoord - (cell.getZ() + 0.5));
        if (dx >= dz) {
            return faceEast ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return faceSouth ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private double distToBox(Vec3 point, BlockPos pos) {
        double dx = Math.max(pos.getX() - point.xCoord, Math.max(0.0, point.xCoord - (pos.getX() + 1)));
        double dy = Math.max(pos.getY() - point.yCoord, Math.max(0.0, point.yCoord - (pos.getY() + 1)));
        double dz = Math.max(pos.getZ() - point.zCoord, Math.max(0.0, point.zCoord - (pos.getZ() + 1)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private MovingObjectPosition rayCast(double reachVal, float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        float yawRad = -yaw * (float) Math.PI / 180.0F - (float) Math.PI;
        float pitchRad = -pitch * (float) Math.PI / 180.0F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = -MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        Vec3 dir = new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
        Vec3 end = eye.addVector(dir.xCoord * reachVal, dir.yCoord * reachVal, dir.zCoord * reachVal);
        return mc.theWorld.rayTraceBlocks(eye, end, false, false, true);
    }

    private float[] smoothRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;
        if (Math.abs(deltaYaw) < 0.1F) {
            currentYaw = targetYaw;
        }
        if (Math.abs(deltaPitch) < 0.1F) {
            currentPitch = targetPitch;
        }
        if (currentYaw == targetYaw && currentPitch == targetPitch) {
            return new float[]{currentYaw, currentPitch};
        }
        float maxStep = (snapback ? this.snapbackSpeed.getValue() : this.aimSpeed.getValue());
        maxStep *= 1.0F - (float) (Math.random() * 0.2);
        float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        if (totalDelta <= maxStep) {
            return new float[]{targetYaw, targetPitch};
        }
        if (maxStep <= 0.0F) {
            return new float[]{currentYaw, currentPitch};
        }
        float scale = maxStep / totalDelta;
        return new float[]{currentYaw + deltaYaw * scale, currentPitch + deltaPitch * scale};
    }

    private int pickBlockSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (this.isBlockSlot(current)) {
            return current;
        }
        for (int slot = 8; slot >= 0; slot--) {
            if (this.isBlockSlot(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isBlockSlot(int slot) {
        if (slot < 0 || slot > 8) {
            return false;
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock;
    }

    private void equipPlannedSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (this.plannedSlot != -1 && this.plannedSlot != current) {
            mc.thePlayer.inventory.currentItem = this.plannedSlot;
            this.slotWasSwapped = true;
        }
    }

    private void enablePlacing() {
        if (!this.placing) {
            this.placing = true;
            if (!this.slotWasSwapped) {
                this.prevSlot = mc.thePlayer.inventory.currentItem;
            }
            Module autoClicker = OpenSkid.moduleManager.getModule(AutoClicker.class);
            if (autoClicker != null && autoClicker.isEnabled()) {
                this.autoClickerWasOn = true;
                autoClicker.setEnabled(false);
            }
        }
    }

    private void restoreSlot() {
        if (this.slotWasSwapped && this.prevSlot != -1 && mc.thePlayer != null
                && this.prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = this.prevSlot;
            this.slotWasSwapped = false;
        }
    }

    private void disablePlacing(boolean forceRestore) {
        if (this.placing || forceRestore) {
            this.placing = false;
            this.plannedSlot = -1;
            if (forceRestore || !this.hasAim) {
                this.restoreSlot();
            }
            if (forceRestore) {
                this.prevSlot = -1;
                this.restoreAutoClicker();
            }
        }
    }

    private void clearAim(boolean allowSnapback) {
        this.restoreSlot();
        this.targetHitPos = null;
        this.targetSide = null;
        if (allowSnapback && this.hasAim) {
            this.resetting = true;
        }
        this.hasAim = false;
        this.prevSlot = -1;
    }

    private void restoreAutoClicker() {
        if (this.autoClickerWasOn) {
            Module autoClicker = OpenSkid.moduleManager.getModule(AutoClicker.class);
            if (autoClicker != null) {
                autoClicker.setEnabled(true);
            }
            this.autoClickerWasOn = false;
        }
    }

    private boolean canPlaceThrough(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Material material = block.getMaterial();
        return material.isReplaceable() || block == Blocks.snow_layer;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.clutchBlocksPlaced)};
    }

    private static class AimTarget {
        final BlockPos pos;
        final EnumFacing side;
        final float yaw;
        final float pitch;

        AimTarget(BlockPos pos, EnumFacing side, float yaw, float pitch) {
            this.pos = pos;
            this.side = side;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class PredictionState {
        private AxisAlignedBB box;
        private double motionX;
        private double motionY;
        private double motionZ;
        private double posY;
        private boolean onGround;

        static PredictionState fromPlayer() {
            PredictionState state = new PredictionState();
            state.box = mc.thePlayer.getEntityBoundingBox();
            state.motionX = mc.thePlayer.motionX;
            state.motionY = mc.thePlayer.motionY;
            state.motionZ = mc.thePlayer.motionZ;
            state.posY = mc.thePlayer.posY;
            state.onGround = mc.thePlayer.onGround;
            return state;
        }

        Vec3 getPos() {
            return new Vec3((this.box.minX + this.box.maxX) / 2.0, this.box.minY, (this.box.minZ + this.box.maxZ) / 2.0);
        }

        void tick(boolean stopHorizontal) {
            if (stopHorizontal) {
                this.motionX = 0.0;
                this.motionZ = 0.0;
            }
            this.motionY -= 0.08;
            this.move(this.motionX, this.motionY, this.motionZ);
            this.motionY *= 0.98;
            this.motionX *= 0.91;
            this.motionZ *= 0.91;
        }

        private void move(double x, double y, double z) {
            double originalX = x;
            double originalY = y;
            double originalZ = z;
            List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, this.box.offset(x, y, z));
            for (AxisAlignedBB collision : collisions) {
                y = collision.calculateYOffset(this.box, y);
            }
            this.box = this.box.offset(0.0, y, 0.0);
            for (AxisAlignedBB collision : collisions) {
                x = collision.calculateXOffset(this.box, x);
            }
            this.box = this.box.offset(x, 0.0, 0.0);
            for (AxisAlignedBB collision : collisions) {
                z = collision.calculateZOffset(this.box, z);
            }
            this.box = this.box.offset(0.0, 0.0, z);
            this.onGround = originalY != y && originalY < 0.0;
            this.posY = this.box.minY;
            if (originalX != x) {
                this.motionX = 0.0;
            }
            if (originalY != y) {
                this.motionY = 0.0;
            }
            if (originalZ != z) {
                this.motionZ = 0.0;
            }
        }
    }
}
