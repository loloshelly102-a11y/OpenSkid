package openskid.module.modules;

import openskid.OpenSkid;
import openskid.enums.BlinkModules;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.*;
import openskid.management.RotationState;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.PercentProperty;
import openskid.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    static final int ROTATION_SNAP = 7;
    static final int ROTATION_THREE_FMC = 8;
    static final int ROTATION_SNAP2 = 9;
    static final int ROTATION_HPYX2 = 10;
    private final ScaffoldSessionState session = new ScaffoldSessionState();
    private final ScaffoldPlacementExecutor executor = new ScaffoldPlacementExecutor(this, this.session);
    private final ScaffoldRotationController rotations = new ScaffoldRotationController(this, this.session);
    public final ModeProperty rotationMode = new ModeProperty("rotations", 2, new String[]{"NONE", "DEFAULT", "BACKWARDS", "SIDEWAYS", "GODBIRGDE", "SMOOTH", "Hypixel", "SNAP", "3FMC", "SNAP2", "Hpyx2"});
    public final FloatProperty tellystartrotationminspeed = new FloatProperty("telly-start-rotation-min-speed", 90.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
    public final FloatProperty tellystartrotationmaxspeed = new FloatProperty("telly-start-rotation-max-speed", 95.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
    public final FloatProperty tellynormalrotationminspeed = new FloatProperty("telly-normal-rotation-min-speed", 30.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
    public final FloatProperty tellynormalrotationmaxspeed = new FloatProperty("telly-normal-rotation-max-speed", 35.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT"});
    public final ModeProperty sprintMode = new ModeProperty("sprint", 0, new String[]{"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final ModeProperty tower = new ModeProperty("tower", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final BooleanProperty hypixeltower = new BooleanProperty("hypixeltower", false, () -> this.tower.getValue() == 3);
    public final BooleanProperty safe = new BooleanProperty("safe", false, () -> this.tower.getValue() == 3);
    public final IntProperty safeStuckDelayTicksProperty = new IntProperty("safe-delay-ticks", 1, 1, 3, () -> this.tower.getValue() == 3 && this.safe.getValue());
    public final ModeProperty keepY = new ModeProperty("keep-y", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY", "EXTRATELLY"});
    public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
    public final BooleanProperty disableWhileJumpActive = new BooleanProperty("no-keep-y-on-jump-potion", false, () -> this.keepY.getValue() != 0);
    public final ModeProperty blockIn = new ModeProperty("block-in", 0, new String[]{"OFF", "SURROUND"});
    public final IntProperty blockInRadius = new IntProperty("block-in-radius", 1, 1, 2, () -> this.blockIn.getValue() == 1);
    public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    public final BooleanProperty eagle = new BooleanProperty("eagle", false);
    public final FloatProperty edgeDistance = new FloatProperty("edge-distance", 0.13F, 0.0F, 0.5F, () -> this.eagle.getValue());
    public final IntProperty sneakDelay = new IntProperty("sneak-delay", 80, 0, 500, () -> this.eagle.getValue());
    public final BooleanProperty placeJitter = new BooleanProperty("place-jitter", false);
    public final IntProperty placeJitterMs = new IntProperty("place-jitter-ms", 30, 0, 150, () -> this.placeJitter.getValue());
    public final IntProperty blocksPerSneak = new IntProperty("blocks-per-sneak", 1, 1, 5, () -> this.eagle.getValue());
    public final BooleanProperty espOutline = new BooleanProperty("outline-esp", false);
    public final ModeProperty espColor = new ModeProperty("outline-color", 0, new String[]{"Default", "HUD"}, () -> this.espOutline.getValue());

    private boolean shouldStopSprint() {
        if (this.isThreeFmcMode() && !this.isThreeFmcTellyMode()) {
            return true;
        }
        if (this.isTowering()) {
            return false;
        } else {
            boolean stage = this.keepY.getValue() == 1 || this.keepY.getValue() == 2 || this.keepY.getValue() == 4;
            return (!stage || this.session.stage <= 0) && this.sprintMode.getValue() == 0;
        }
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) OpenSkid.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        } else {
            LongJump longJump = (LongJump) OpenSkid.moduleManager.modules.get(LongJump.class);
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
    }

    boolean isBlockInTriggered() {
        if (this.blockIn.getValue() != 1) {
            return false;
        }
        BedNuker bedNuker = (BedNuker) OpenSkid.moduleManager.modules.get(BedNuker.class);
        return bedNuker.isEnabled() && bedNuker.isReady();
    }

    boolean isThreeFmcMode() {
        return this.rotationMode.getValue() == ROTATION_THREE_FMC;
    }

    boolean isThreeFmcTellyMode() {
        return this.isThreeFmcMode() && (this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
    }

    private void updateThreeFmcState() {
        if (!this.isThreeFmcMode() || mc.thePlayer == null) {
            this.session.threeFmcAirTicks = 0;
            this.session.threeFmcGroundTicks = 0;
            this.session.threeFmcPlaceCooldown = 0;
            return;
        }

        if (mc.thePlayer.onGround) {
            this.session.threeFmcGroundTicks++;
            this.session.threeFmcAirTicks = 0;
        } else {
            this.session.threeFmcAirTicks++;
            this.session.threeFmcGroundTicks = 0;
        }

        if (this.session.threeFmcPlaceCooldown > 0) {
            this.session.threeFmcPlaceCooldown--;
        }
    }

    private void quietThreeFmcMovement() {
        if (!this.isThreeFmcMode() || this.isThreeFmcTellyMode() || mc.thePlayer == null) {
            return;
        }

        mc.thePlayer.setSprinting(false);
        if (mc.gameSettings != null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private boolean isNearEdge() {
        if (!mc.thePlayer.onGround) {
            return false;
        }
        double fracX = mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        double fracZ = mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
        double threshold = this.edgeDistance.getValue();
        double minDist = Math.min(Math.min(fracX, 1.0 - fracX), Math.min(fracZ, 1.0 - fracZ));
        return minDist <= threshold;
    }

    private boolean shouldSneak() {
        if (!this.eagle.getValue() || !mc.thePlayer.onGround) {
            return false;
        }
        if (this.session.eagleBlocksPlaced < this.blocksPerSneak.getValue()) {
            return false;
        }
        if (System.currentTimeMillis() - this.session.eagleLastSneakTime < (long) this.sneakDelay.getValue().intValue()) {
            return false;
        }
        return this.isNearEdge();
    }

    private void updateEagle() {
        if (!this.eagle.getValue()) {
            this.session.eagleSneaking = false;
            this.session.eagleSneakTicks = 0;
            return;
        }
        if (this.session.eagleSneakTicks > 0) {
            this.session.eagleSneakTicks--;
            if (this.session.eagleSneakTicks == 0) {
                this.session.eagleSneaking = false;
            }
            return;
        }
        if (this.shouldSneak()) {
            this.session.eagleSneaking = true;
            this.session.eagleSneakTicks = 2;
            this.session.eagleLastSneakTime = System.currentTimeMillis();
            this.session.eagleBlocksPlaced = 0;
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.airMotion.getValue() / 100.0F;
        } else {
            return MoveUtil.getSpeedLevel() > 0
                    ? (float) this.speedMotion.getValue() / 100.0F
                    : (float) this.groundMotion.getValue() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue()
        );
    }

    boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    public boolean isTowering() {
        if (towerModuleActive()) {
            return false;
        }
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean keepY = this.keepY.getValue() == 3 || this.keepY.getValue() == 4;
            boolean tower = this.tower.getValue() == 3;
            return keepY && this.session.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
        } else {
            return false;
        }
    }

    public Scaffold() {
        super("Scaffold", false, false, "Automatically places blocks beneath you while moving.");
    }

    // Slave rule: Tower owns vertical motion when its module is enabled, so
    // Scaffold must not tower then. Conversely Tower yields to Scaffold while
    // Scaffold runs when Tower slave-to-scaffold is on. Single tower owner.
    private boolean towerModuleActive() {
        Tower towerModule = (Tower) OpenSkid.moduleManager.modules.get(Tower.class);
        return towerModule != null && towerModule.isEnabled();
    }

    public int getSlot() {
        return this.session.lastSlot;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.session.placedThisTick = false;
            this.updateThreeFmcState();
            this.quietThreeFmcMovement();
            if (this.session.safeStuckDelayTicks > 0) {
                this.session.safeStuckDelayTicks--;
                if (this.session.safeStuckDelayTicks <= 0) {
                    this.session.safeStuckTicks = 1;
                }
            }
            if (this.session.safeStuckTicks > 0) {
                if (!this.session.safeStuckActive) {
                    this.session.savedMotionX = mc.thePlayer.motionX;
                    this.session.savedMotionY = mc.thePlayer.motionY;
                    this.session.savedMotionZ = mc.thePlayer.motionZ;
                    this.session.safeStuckActive = true;
                }
                OpenSkid.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionY = 0.0;
                mc.thePlayer.motionZ = 0.0;
            } else if (this.session.safeStuckActive) {
                OpenSkid.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                mc.thePlayer.motionX = this.session.savedMotionX;
                mc.thePlayer.motionY = this.session.savedMotionY;
                mc.thePlayer.motionZ = this.session.savedMotionZ;
                this.session.safeStuckActive = false;
            }
            if (this.session.rotationTick > 0) {
                this.session.rotationTick--;
            }
            this.updateEagle();
            if (hypixeltower.getValue() && !towerModuleActive() && mc.thePlayer.motionY <= 0.0 && Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ) <= 0.02D && mc.thePlayer.motionY >= -0.09 && !(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) ||
                    Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) ||
                    Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) ||
                    Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode())) && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                mc.thePlayer.motionY = -0.38;
            }
            if (mc.thePlayer.onGround) {
                if (this.session.stage > 0) {
                    this.session.stage--;
                }
                if (this.session.stage < 0) {
                    this.session.stage++;
                }
                if (this.session.stage == 0
                        && this.keepY.getValue() != 0
                        && (!(Boolean) this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
                        && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
                        && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.session.stage = 1;
                }
                this.session.startY = this.session.shouldKeepY ? this.session.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.session.shouldKeepY = false;
                this.session.towering = false;
            }
            if (this.canPlace()) {
                this.executor.ensureBlocks();
                boolean threeFmcMode = this.rotationMode.getValue() == ROTATION_THREE_FMC;
                boolean snapMode = this.rotationMode.getValue() == ROTATION_SNAP || this.rotationMode.getValue() == ROTATION_SNAP2;
                ScaffoldRotationController.Plan plan = this.rotations.compute(event);
                BlockData blockData = plan.blockData;
                Vec3 hitVec = plan.hitVec;
                if (threeFmcMode && blockData != null && hitVec != null) {
                    MovingObjectPosition verifiedMop = ScaffoldRotationController.getPlacementMop(blockData, plan.placeYaw, plan.placePitch);
                    if (verifiedMop == null) {
                        hitVec = null;
                    } else {
                        hitVec = verifiedMop.hitVec;
                    }
                }
                if (blockData != null && hitVec != null && plan.snapCanPlace && this.session.rotationTick <= 0) {
                    this.executor.placeSingle(blockData.blockPos(), blockData.facing(), hitVec);
                    if (snapMode) {
                        this.rotations.rememberSnapRotation();
                    }
                    if (this.multiplace.getValue() && !snapMode) {
                        for (int i = 0; i < 3; i++) {
                            blockData = ScaffoldPlacementPlanner.getBlockData(this.session.stage, this.session.shouldKeepY, this.session.startY);
                            if (blockData == null) {
                                break;
                            }
                            MovingObjectPosition mop = RotationUtil.rayTrace(this.session.yaw, this.session.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null
                                    && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                this.executor.placeSingle(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            } else {
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.xCoord - mc.thePlayer.posX;
                                double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                                float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                                if (!(Math.abs(rotations[0] - this.session.yaw) < 120.0F) || !(Math.abs(rotations[1] - this.session.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop == null
                                        || mop.typeOfHit != MovingObjectType.BLOCK
                                        || !mop.getBlockPos().equals(blockData.blockPos())
                                        || mop.sideHit != blockData.facing()) {
                                    break;
                                }
                                this.executor.placeSingle(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
                if (this.session.targetFacing != null) {
                    if (threeFmcMode) {
                        this.session.targetFacing = null;
                    } else if (this.session.rotationTick <= 0 && !this.session.placedThisTick) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.session.targetFacing, this.session.yaw, this.session.pitch);
                        this.executor.placeSingle(belowPlayer, this.session.targetFacing, hitVec);
                    }
                    this.session.targetFacing = null;
                } else if ((this.keepY.getValue() == 2 || this.keepY.getValue() == 4) && this.session.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.session.startY && mc.thePlayer.posY > (double) (this.session.startY + 1)) {
                        this.session.shouldKeepY = true;
                        blockData = ScaffoldPlacementPlanner.getBlockData(this.session.stage, this.session.shouldKeepY, this.session.startY);
                        if (blockData != null && this.session.rotationTick <= 0 && !this.session.placedThisTick) {
                            MovingObjectPosition mop = ScaffoldRotationController.getPlacementMop(blockData, this.session.yaw, this.session.pitch);
                            if (mop != null) {
                                this.executor.placeSingle(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
            }
            if (this.isBlockInTriggered() && this.executor.ensureBlocks()) {
                AutoBlockIn surroundGate = (AutoBlockIn) OpenSkid.moduleManager.modules.get(AutoBlockIn.class);
                if (surroundGate == null || !surroundGate.isEnabled()) {
                List<BlockData> surround = ScaffoldPlacementPlanner.findSurroundCells(this.blockInRadius.getValue(), mc.playerController.getBlockReachDistance());
                int surroundPlaced = 0;
                for (BlockData cell : surround) {
                    if (surroundPlaced >= 2) {
                        break;
                    }
                    MovingObjectPosition mop = ScaffoldRotationController.getPlacementMop(cell, this.session.yaw, this.session.pitch);
                    if (mop == null) {
                        continue;
                    }
                    if (this.executor.placeSingle(cell.blockPos(), cell.facing(), mop.hitVec)) {
                        surroundPlaced++;
                    }
                }
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (this.session.safeStuckTicks > 0) {
                event.setForward(0.0F);
                event.setStrafe(0.0F);
                return;
            }
            if (this.isThreeFmcMode() && !this.isThreeFmcTellyMode()) {
                this.session.towerTick = 0;
                this.session.towerDelay = 0;
                return;
            }
            if (!mc.thePlayer.isCollidedHorizontally
                    && mc.thePlayer.hurtTime <= 5
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && mc.gameSettings.keyBindJump.isKeyDown()
                    && ItemUtil.isHoldingBlock()) {
                if (towerModuleActive()) {
                    this.session.towerTick = 0;
                    this.session.towerDelay = 0;
                    return;
                }
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                switch (this.tower.getValue()) {
                    case 1:
                        switch (this.session.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.session.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.session.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    this.session.towerTick = 2;
                                    mc.thePlayer.motionY = 0.42F;
                                    if (MoveUtil.isForwardPressed()) {
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                    } else {
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                    }
                                    return;
                                } else {
                                    this.session.towerTick = 0;
                                    return;
                                }
                            case 2:
                                this.session.towerTick = 3;
                                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                                return;
                            case 3:
                                this.session.towerTick = 1;
                                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                return;
                            default:
                                this.session.towerTick = 0;
                                return;
                        }
                    case 2:
                        switch (this.session.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.session.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.session.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    if (!MoveUtil.isForwardPressed()) {
                                        this.session.towerDelay = 2;
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.session.yaw - 180.0F));
                                        double distance = this.distanceToEdge(facing);
                                        if (distance > 0.1) {
                                            if (mc.thePlayer.onGround) {
                                                Vec3i directionVec = facing.getDirectionVec();
                                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                                AxisAlignedBB nextBox = mc.thePlayer
                                                        .getEntityBoundingBox()
                                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                    mc.thePlayer.motionY = -0.0784000015258789;
                                                    mc.thePlayer
                                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                                }
                                                return;
                                            }
                                        } else {
                                            this.session.towerTick = 2;
                                            this.session.targetFacing = facing;
                                            mc.thePlayer.motionY = 0.42F;
                                        }
                                        return;
                                    } else {
                                        this.session.towerTick = 2;
                                        this.session.towerDelay++;
                                        mc.thePlayer.motionY = 0.42F;
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                        return;
                                    }
                                } else {
                                    this.session.towerTick = 0;
                                    this.session.towerDelay = 0;
                                    return;
                                }
                            case 2:
                                this.session.towerTick = 3;
                                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                                return;
                            case 3:
                                if (this.session.towerDelay >= 4) {
                                    this.session.towerTick = 4;
                                    this.session.towerDelay = 0;
                                } else {
                                    this.session.towerTick = 1;
                                    this.session.towerDelay++;
                                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                }
                                return;
                            case 4:
                                this.session.towerTick = 5;
                                return;
                            case 5:
                                if (!PlayerUtil.isAirBelow()) {
                                    this.session.towerTick = 0;
                                } else {
                                    this.session.towerTick = 1;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                }
                                return;
                            default:
                                this.session.towerTick = 0;
                                this.session.towerDelay = 0;
                                return;
                        }
                    default:
                        this.session.towerTick = 0;
                        this.session.towerDelay = 0;
                }
            } else {
                this.session.towerTick = 0;
                this.session.towerDelay = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.session.safeStuckTicks > 0) {
                mc.thePlayer.movementInput.moveForward = 0.0f;
                mc.thePlayer.movementInput.moveStrafe = 0.0f;
                mc.thePlayer.movementInput.jump = false;
                mc.thePlayer.movementInput.sneak = false;
                return;
            }
            this.quietThreeFmcMovement();
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 3.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (mc.thePlayer.onGround && this.session.stage > 0 && MoveUtil.isForwardPressed()) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.session.eagleSneaking && !mc.thePlayer.movementInput.sneak) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            if (this.session.safeStuckTicks > 0) {
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionY = 0.0;
                mc.thePlayer.motionZ = 0.0;
                this.session.safeStuckTicks--;
            }
            this.quietThreeFmcMovement();
            float speed = this.isThreeFmcMode() && !this.isThreeFmcTellyMode() ? 1.0F : this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }

            if (this.safe.getValue() && this.tower.getValue() == 3 && mc.gameSettings.keyBindJump.isKeyDown()) {
                float moveYaw = this.getCurrentYaw();
                boolean diagonal = this.isDiagonal(moveYaw);
                if (diagonal && !mc.thePlayer.onGround) {
                    double motionY = mc.thePlayer.motionY;
                    if (this.session.safePrevMotionY > 0.0 && motionY <= 0.0) {
                        double motionXZ = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
                        double motionXZSpeedBps = motionXZ * 20.0;
                        if (this.session.safeStuckDelayTicks <= 0 && this.session.safeStuckTicks <= 0 && motionXZSpeedBps >= 4.67) {
                            this.session.safeStuckDelayTicks = this.safeStuckDelayTicksProperty.getValue();
                        }
                    }
                    this.session.safePrevMotionY = motionY;
                } else {
                    this.session.safePrevMotionY = mc.thePlayer.motionY;
                }
            } else {
                this.session.safePrevMotionY = mc.thePlayer.motionY;
            }
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && this.safeWalk.getValue()) {
            if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0 && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            if (this.blockCounter.getValue()) {
                int count = 0;
                ItemStack currentBlock = null;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                    if (stack != null && stack.stackSize > 0) {
                        Item item = stack.getItem();
                        if (item instanceof ItemBlock) {
                            Block block = ((ItemBlock) item).getBlock();
                            if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                                count += stack.stackSize;
                                if (currentBlock == null) {
                                    currentBlock = stack;
                                }
                            }
                        }
                    }
                }

                ItemStack heldItem = mc.thePlayer.getHeldItem();
                if (heldItem != null && heldItem.getItem() instanceof ItemBlock) {
                    Block block = ((ItemBlock) heldItem.getItem()).getBlock();
                    if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                        currentBlock = heldItem;
                    }
                }

                if (currentBlock == null) {
                    currentBlock = new ItemStack(net.minecraft.init.Blocks.stone);
                }

                ScaledResolution sr = new ScaledResolution(mc);
                String labelText = "Amount: ";
                String countText = String.valueOf(count);

                HUD hud = (HUD) OpenSkid.moduleManager.modules.get(HUD.class);
                openskid.font.CFontRenderer fr = (hud != null && hud.fontRenderer != null) ? hud.fontRenderer : null;

                float labelWidth = fr != null ? fr.getStringWidth(labelText) : mc.fontRendererObj.getStringWidth(labelText);
                float countWidth = fr != null ? fr.getStringWidth(countText) : mc.fontRendererObj.getStringWidth(countText);
                float totalTextWidth = labelWidth + countWidth;

                float bgWidth = 4f + 16f + 4f + totalTextWidth + 8f;
                float bgHeight = 22f;
                float x = (sr.getScaledWidth() - bgWidth) / 2f;
                float y = sr.getScaledHeight() - 65f;

                if (hud != null && hud.blur.getValue()) {
                    openskid.util.shader.BlurUtils.prepareBlur();
                    openskid.util.RoundedUtils.drawRoundedRect(x, y, bgWidth, bgHeight, 8f, 0xFFFFFFFF);
                    openskid.util.shader.BlurUtils.blurEnd(2, 4.0f);
                }

                openskid.util.RoundedUtils.drawRoundedRect(x, y, bgWidth, bgHeight, 8f, 0x82000000);
                openskid.util.RoundedUtils.drawRoundedRect(x + 0.5f, y + 0.5f, bgWidth - 1f, bgHeight - 1f, 7.5f, 0x12FFFFFF);

                ItemStack iconStack = new ItemStack(currentBlock.getItem(), 1, currentBlock.getMetadata());

                GlStateManager.pushMatrix();
                GlStateManager.clear(256);
                RenderHelper.enableGUIStandardItemLighting();
                openskid.util.RenderUtil.renderItemInGUI(iconStack, (int)(x + 4f), (int)(y + 3f));
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();

                float textX = x + 4f + 16f + 4f;
                float textY = y + (bgHeight - (fr != null ? fr.getHeight() : mc.fontRendererObj.FONT_HEIGHT)) / 2f;

                int countColor = 0xFF87CEEB;
                if (fr != null) {
                    fr.drawString(labelText, textX, textY, -1);
                    fr.drawString(countText, textX + labelWidth, textY, countColor);
                } else {
                    mc.fontRendererObj.drawStringWithShadow(labelText, (int)textX, (int)textY, -1);
                    mc.fontRendererObj.drawStringWithShadow(countText, (int)(textX + labelWidth), (int)textY, countColor);
                }
            }
        }
    }

    void markPlaced(BlockPos pos) {
        if (this.espOutline.getValue()) {
            this.session.espHighlight.put(pos, System.currentTimeMillis());
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.espOutline.getValue() || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (this.session.espHighlight.isEmpty()) {
            return;
        }
        int themeColor;
        if (this.espColor.getValue() == 1) {
            HUD hud = (HUD) OpenSkid.moduleManager.modules.get(HUD.class);
            themeColor = hud.getColor(0L);
        } else {
            themeColor = Color.CYAN.getRGB();
        }
        Iterator<Map.Entry<BlockPos, Long>> iterator = this.session.espHighlight.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            long time = System.currentTimeMillis() - entry.getValue();
            if (time > 750L) {
                iterator.remove();
                continue;
            }
            int currentAlpha = (int) (210 - (time / 750.0 * 210));
            if (currentAlpha <= 0) {
                iterator.remove();
                continue;
            }
            RenderUtil.renderBlock(
                    entry.getKey(),
                    (themeColor & 0xFFFFFF) | (currentAlpha << 24),
                    true,
                    false);
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            this.session.lastSlot = event.setSlot(this.session.lastSlot);
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.session.lastSlot = ScaffoldSessionState.saveSlotOnce(-1, mc.thePlayer.inventory.currentItem);
        } else {
            this.session.lastSlot = -1;
        }
        this.session.resetOnEnable();
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && ScaffoldSessionState.hasSavedSlot(this.session.lastSlot)) {
            mc.thePlayer.inventory.currentItem = this.session.lastSlot;
        }
        OpenSkid.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        if (this.session.safeStuckActive && mc.thePlayer != null) {
            mc.thePlayer.motionX = this.session.savedMotionX;
            mc.thePlayer.motionY = this.session.savedMotionY;
            mc.thePlayer.motionZ = this.session.savedMotionZ;
        }
        this.session.resetOnDisable();
    }

    public int getBlockCount() {
        return this.session.blockCount;
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}
