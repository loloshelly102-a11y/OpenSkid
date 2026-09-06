package openskid.module.modules;

import com.google.common.base.CaseFormat;
import openskid.OpenSkid;
import openskid.enums.ChatColors;
import openskid.enums.DelayModules;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.*;
import openskid.management.RotationState;
import openskid.mixin.IAccessorPlayerControllerMP;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.PercentProperty;
import openskid.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class BedNuker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long WHITELIST_SCAN_DELAY_MS = 1000L;
    private final TimerUtil timer = new TimerUtil();
    private final ArrayList<BlockPos> bedWhitelist = new ArrayList<BlockPos>();
    private final Color colorRed = new Color(ChatColors.RED.toAwtColor());
    private final Color colorYellow = new Color(ChatColors.YELLOW.toAwtColor());
    private final Color colorGreen = new Color(ChatColors.GREEN.toAwtColor());
    private BlockPos targetBed = null;
    private int breakStage = 0;
    private int tickCounter = 0;
    private float breakProgress = 0.0F;
    private boolean isBed = false;
    private int savedSlot = -1;
    private boolean readyToBreak = false;
    private boolean breaking = false;
    private boolean waitingForStart = false;
    private long whitelistScanAt = -1L;
    private BlockPos ownBedAnchor = null;
    private static final int MODE_INSTANT = 3;
    private static final int MODE_LEGIT = 4;
    // Adapted from donor BedAura respawn anchor recapture.
    private boolean waitingForRespawn = false;
    private long respawnMessageTime = 0L;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"NORMAL", "SWAP", "PROTECT", "INSTANT", "LEGIT"});
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 6.0F);
    public final PercentProperty speed = new PercentProperty("speed", 0);
    public final BooleanProperty groundSpeed = new BooleanProperty("ground-spoof", false);
    public final ModeProperty ignoreVelocity = new ModeProperty("ignore-velocity", 0, new String[]{"NONE", "CANCEL", "DELAY"});
    public final BooleanProperty surroundings = new BooleanProperty("surroundings", true);
    public final BooleanProperty toolCheck = new BooleanProperty("tool-check", true);
    public final BooleanProperty whiteList = new BooleanProperty("whitelist", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
    public final ModeProperty showTarget = new ModeProperty("show-target", 1, new String[]{"NONE", "DEFAULT", "HUD"});
    public final ModeProperty showProgress = new ModeProperty("show-progress", 1, new String[]{"NONE", "DEFAULT", "HUD"});
    // Protect awareness adapted from donor BedAura own-bed whitelist plus spawn anchor idea.
    public final BooleanProperty whitelistOwnBed = new BooleanProperty("whitelist-own-bed", true);
    public final FloatProperty ownBedRadius = new FloatProperty("own-bed-radius", 8.0F, 3.0F, 20.0F,
            () -> this.whitelistOwnBed.getValue() || this.mode.getValue() == 2);
    // Adapted from donor BedAura breakNearBlock/isCovered: break cover first when true.
    public final BooleanProperty coverFirst = new BooleanProperty("cover-first", true);
    // Adapted from donor BedAura footHeadPair: reject lone bed halves.
    public final BooleanProperty pairCheck = new BooleanProperty("pair-check", true);
    // Adapted from donor BedAura scoreChoice: order cover by dig rate.
    public final BooleanProperty digScore = new BooleanProperty("dig-score", true);
    // Adapted from donor BedAura spawn anchor capture.
    public final BooleanProperty spawnAnchor = new BooleanProperty("spawn-anchor", true);
    // Adapted from donor BedAura shouldYieldToKillAura.
    public final BooleanProperty yieldAura = new BooleanProperty("yield-aura", false);
    // Instant-only effects toggle, gated per-mode.
    public final BooleanProperty instantEffects = new BooleanProperty("instant-effects", true,
            () -> this.mode.getValue() == MODE_INSTANT);
    public final BooleanProperty onlyVisible = new BooleanProperty("only-visible", false);
    public final BooleanProperty silentSwing = new BooleanProperty("silent-swing", false);
    public final FloatProperty rotationSpeed = new FloatProperty("rotation-speed", 20.0F, 2.0F, 20.0F);

    private void resetBreaking() {
        if (this.targetBed != null && mc.theWorld != null && mc.thePlayer != null) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), this.targetBed, -1);
        }
        this.targetBed = null;
        this.breakStage = 0;
        this.tickCounter = 0;
        this.breakProgress = 0.0F;
        this.isBed = false;
        this.readyToBreak = false;
        this.breaking = false;
    }

    private void scheduleWhitelistScan() {
        this.whitelistScanAt = System.currentTimeMillis() + WHITELIST_SCAN_DELAY_MS;
    }

    private void runPendingWhitelistScan() {
        if (this.whitelistScanAt == -1L || System.currentTimeMillis() < this.whitelistScanAt) {
            return;
        }
        this.whitelistScanAt = -1L;
        this.bedWhitelist.clear();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        this.ownBedAnchor = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ));

        int sX = MathHelper.floor_double(mc.thePlayer.posX);
        int sY = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
        int sZ = MathHelper.floor_double(mc.thePlayer.posZ);
        for (int i = sX - 25; i <= sX + 25; i++) {
            for (int j = sY - 25; j <= sY + 25; j++) {
                for (int k = sZ - 25; k <= sZ + 25; k++) {
                    BlockPos blockPos = new BlockPos(i, j, k);
                    Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                    if (block instanceof BlockBed) {
                        this.bedWhitelist.add(blockPos);
                    }
                }
            }
        }
    }

    private float calcProgress() {
        if (this.targetBed == null) {
            return 0.0F;
        } else {
            float progress = this.breakProgress;
            if (this.groundSpeed.getValue()) {
                int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(this.targetBed).getBlock());
                progress = (float) this.tickCounter * this.getBreakDelta(mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, true);
            }
            return Math.min(1.0F, progress / (1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)));
        }
    }

    private void restoreSlot() {
        if (ScaffoldSessionState.hasSavedSlot(this.savedSlot) && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = this.savedSlot;
            this.syncHeldItem();
        }
        this.savedSlot = -1;
    }

    private void syncHeldItem() {
        int currentPlayerItem = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
        if (mc.thePlayer.inventory.currentItem != currentPlayerItem) {
            mc.thePlayer.stopUsingItem();
        }
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private boolean hasProperTool(Block block) {
        Material material = block.getMaterial();
        if (material != Material.iron && material != Material.anvil && material != Material.rock) {
            return true;
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null) {
                    Item item = stack.getItem();
                    if (item instanceof ItemPickaxe) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private EnumFacing getHitFacing(BlockPos blockPos) {
        double x = (double) blockPos.getX() + 0.5 - mc.thePlayer.posX;
        double y = (double) blockPos.getY() + 0.25 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
        double z = (double) blockPos.getZ() + 0.5 - mc.thePlayer.posZ;
        float[] rotations = RotationUtil.getRotationsTo(x, y, z, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], 8.0, 1.0F);
        return mop == null ? EnumFacing.UP : mop.sideHit;
    }

    private float getDigSpeed(IBlockState iBlockState, int slot, boolean boolean5) {
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
        float digSpeed = item == null ? 1.0F : item.getItem().getDigSpeed(item, iBlockState);
        if (digSpeed > 1.0F) {
            int enchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
            if (enchantmentLevel > 0) {
                digSpeed += (float) (enchantmentLevel * enchantmentLevel + 1);
            }
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            digSpeed *= 1.0F + (float) (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        }
        if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                case 0:
                    digSpeed *= 0.3F;
                    break;
                case 1:
                    digSpeed *= 0.09F;
                    break;
                case 2:
                    digSpeed *= 0.0027F;
                    break;
                default:
                    digSpeed *= 8.1E-4F;
            }
        }
        if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
            digSpeed /= 5.0F;
        }
        if (!boolean5) {
            digSpeed /= 5.0F;
        }
        return digSpeed;
    }

    boolean canHarvest(Block block, int slot) {
        if (block.getMaterial().isToolNotRequired()) {
            return true;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null && stack.canHarvestBlock(block);
        }
    }

    private float getBreakDelta(IBlockState iBlockState, BlockPos blockPos, int slot, boolean boolean5) {
        Block block = iBlockState.getBlock();
        float hardness = block.getBlockHardness(mc.theWorld, blockPos);
        float boost = this.canHarvest(block, slot) ? 30.0F : 100.0F;
        return hardness < 0.0F ? 0.0F : this.getDigSpeed(iBlockState, slot, boolean5) / hardness / boost;
    }

    private float calcBlockStrength(BlockPos blockPos) {
        IBlockState blockState = mc.theWorld.getBlockState(blockPos);
        int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, blockState.getBlock());
        return this.getBreakDelta(blockState, blockPos, slot, mc.thePlayer.onGround);
    }

    // Adapted from donor BedAura isCovered, matched to local validate facings (UP+N+E+S+W).
    private boolean isCoveredBed(BlockPos bedPos) {
        if (mc.theWorld == null) {
            return false;
        }
        for (EnumFacing facing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
            if (BlockUtil.isReplaceable(bedPos.offset(facing))) {
                return false;
            }
        }
        return true;
    }

    // Adapted from donor BedAura footHeadPair: validate foot/head consistency.
    private BlockPos[] footHeadPair(BlockPos at) {
        if (mc.theWorld == null) {
            return null;
        }
        IBlockState st = mc.theWorld.getBlockState(at);
        if (!(st.getBlock() instanceof BlockBed)) {
            return null;
        }
        EnumPartType part = st.getValue(BlockBed.PART);
        EnumFacing facing = st.getValue(BlockBed.FACING);
        BlockPos foot = part == EnumPartType.FOOT ? at : at.offset(facing.getOpposite());
        IBlockState footSt = mc.theWorld.getBlockState(foot);
        if (!(footSt.getBlock() instanceof BlockBed)) {
            return null;
        }
        if (footSt.getValue(BlockBed.PART) != EnumPartType.FOOT) {
            return null;
        }
        EnumFacing footFacing = footSt.getValue(BlockBed.FACING);
        BlockPos head = foot.offset(footFacing);
        IBlockState headSt = mc.theWorld.getBlockState(head);
        if (!(headSt.getBlock() instanceof BlockBed)) {
            return null;
        }
        if (headSt.getValue(BlockBed.PART) != EnumPartType.HEAD) {
            return null;
        }
        if (headSt.getValue(BlockBed.FACING) != footFacing) {
            return null;
        }
        return new BlockPos[]{foot, head};
    }

    // Adapted from donor BedAura shouldYieldToKillAura via ChestAura yield pattern.
    private boolean isYieldingToKillAura() {
        if (!this.yieldAura.getValue()) {
            return false;
        }
        try {
            KillAura killAura = (KillAura) OpenSkid.moduleManager.modules.get(KillAura.class);
            return killAura != null && killAura.isEnabled() && killAura.getTarget() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private BlockPos validateBedPlacement(BlockPos bedPosition) {
        IBlockState blockState = mc.theWorld.getBlockState(bedPosition);
        if (blockState.getBlock() instanceof BlockBed) {
            ArrayList<BlockPos> pos = new ArrayList<>();
            EnumPartType partType = blockState.getValue(BlockBed.PART);
            EnumFacing facing = blockState.getValue(BlockBed.FACING);
            for (BlockPos blockPos : Arrays.asList(bedPosition, bedPosition.offset(partType == EnumPartType.HEAD ? facing.getOpposite() : facing))) {
                if (!this.isCoveredBed(blockPos)) {
                    return null;
                }
                for (EnumFacing enumFacing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                    Block block = mc.theWorld.getBlockState(blockPos.offset(enumFacing)).getBlock();
                    if (!(block instanceof BlockBed)) {
                        pos.add(blockPos.offset(enumFacing));
                    }
                }
            }
            if (!pos.isEmpty()) {
                pos.sort(
                        (blockPos, blockPos2) -> {
                            if (this.digScore.getValue()) {
                                int o = Float.compare(this.calcBlockStrength(blockPos2), this.calcBlockStrength(blockPos));
                                if (o != 0) {
                                    return o;
                                }
                            }
                            return Double.compare(
                                    blockPos.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                                    blockPos2.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)
                            );
                        }
                );
                return pos.get(0);
            }
        }
        return null;
    }

    private BlockPos findOwnBed() {
        if (this.ownBedAnchor == null || this.bedWhitelist.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : this.bedWhitelist) {
            double dist = pos.distanceSq(this.ownBedAnchor);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    private boolean isProtectedBed(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!this.whitelistOwnBed.getValue() && this.mode.getValue() != 2) {
            return false;
        }
        BlockPos ownBed = this.findOwnBed();
        if (ownBed == null) {
            return false;
        }
        double radius = this.ownBedRadius.getValue().doubleValue();
        return pos.distanceSq(ownBed) <= radius * radius;
    }

    private BlockPos findNearestBed() {
        return this.findTargetBed(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    private BlockPos findTargetBed(double x, double y, double z) {
        ArrayList<BlockPos> targets = new ArrayList<>();
        int sX = MathHelper.floor_double(x);
        int sY = MathHelper.floor_double(y);
        int sZ = MathHelper.floor_double(z);
        for (int i = sX - 6; i <= sX + 6; i++) {
            for (int j = sY - 6; j <= sY + 6; j++) {
                for (int k = sZ - 6; k <= sZ + 6; k++) {
                    BlockPos newPos = new BlockPos(i, j, k);
                    if (this.isProtectedBed(newPos)) {
                        continue;
                    }
                    if (!(Boolean) this.whiteList.getValue() || !this.bedWhitelist.contains(newPos)) {
                        Block block = mc.theWorld.getBlockState(newPos).getBlock();
                        if (block instanceof BlockBed
                                && PlayerUtil.isBlockWithinReach(newPos, x, y, z, this.range.getValue().doubleValue())
                                && (!this.pairCheck.getValue() || this.footHeadPair(newPos) != null)
                                && (!this.onlyVisible.getValue() || this.hasLineOfSight(newPos))) {
                            targets.add(newPos);
                        }
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return null;
        } else {
            targets.sort(
                    Comparator.comparingDouble(
                            blockPos -> blockPos.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)
                    )
            );
            for (BlockPos blockPos : targets) {
                if (this.surroundings.getValue() && this.coverFirst.getValue()) {
                    BlockPos pos = this.validateBedPlacement(blockPos);
                    if (pos != null) {
                        Block block = mc.theWorld.getBlockState(pos).getBlock();
                        if (this.toolCheck.getValue() && !this.hasProperTool(block)) {
                            continue;
                        }
                        return pos;
                    }
                }
                return blockPos;
            }
            return null;
        }
    }

    private void doSwing() {
        if (this.silentSwing.getValue()) {
            PacketUtil.sendPacket(new C0APacketAnimation());
        } else if (this.swing.getValue()) {
            mc.thePlayer.swingItem();
        } else {
            PacketUtil.sendPacket(new C0APacketAnimation());
        }
    }

    private boolean hasLineOfSight(BlockPos pos) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, target);
        return mop == null || mop.typeOfHit != MovingObjectType.BLOCK || mop.getBlockPos().equals(pos);
    }

    private float rotMove(float target, float current, float speed) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        if (diff > speed) {
            return current + speed;
        }
        if (diff < -speed) {
            return current - speed;
        }
        return target;
    }

    // Adapted from donor BedAura Instant mode: START plus STOP in the same tick.
    private void breakInstant(int slot) {
        int prevSlot = mc.thePlayer.inventory.currentItem;
        boolean swapped = slot != prevSlot;
        if (swapped) {
            mc.thePlayer.inventory.currentItem = slot;
            this.syncHeldItem();
        }
        this.doSwing();
        EnumFacing facing = this.getHitFacing(this.targetBed);
        PacketUtil.sendPacket(
                new C07PacketPlayerDigging(Action.START_DESTROY_BLOCK, this.targetBed, facing)
        );
        PacketUtil.sendPacket(
                new C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, this.targetBed, facing)
        );
        this.doSwing();
        if (this.instantEffects.getValue()) {
            mc.effectRenderer.addBlockHitEffects(this.targetBed, facing);
        }
        IBlockState blockState = mc.theWorld.getBlockState(this.targetBed);
        Block block = blockState.getBlock();
        if (block.getMaterial() != Material.air) {
            if (this.instantEffects.getValue()) {
                mc.theWorld.playAuxSFX(2001, this.targetBed, Block.getStateId(blockState));
            }
            mc.theWorld.setBlockToAir(this.targetBed);
        }
        if (block instanceof BlockBed) {
            this.timer.reset();
        }
        this.breaking = false;
        if (swapped) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            this.syncHeldItem();
        }
    }

    private Color getProgressColor(int mode) {
        switch (mode) {
            case 1:
                float progress = this.calcProgress();
                if (progress <= 0.5F) {
                    return ColorUtil.interpolate(progress / 0.5F, this.colorRed, this.colorYellow);
                }
                return ColorUtil.interpolate((progress - 0.5F) / 0.5F, this.colorYellow, this.colorGreen);
            case 2:
                return new Color(((HUD) OpenSkid.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), true);
            default:
                return new Color(-1);
        }
    }

    public BedNuker() {
        super("BedNuker", false, false, "Automatically breaks nearby enemy beds and covers.");
    }

    public boolean isReady() {
        return this.targetBed != null && this.readyToBreak;
    }

    public net.minecraft.util.BlockPos getTargetBed() {
        return this.targetBed;
    }

    public boolean isBreaking() {
        return this.targetBed != null && this.breaking;
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            this.runPendingWhitelistScan();
            if (!this.isEnabled()) {
                return;
            }
            AutoBlockIn autoBlockIn = (AutoBlockIn) OpenSkid.moduleManager.modules.get(AutoBlockIn.class);
            if(autoBlockIn.isEnabled()) return;
            // Adapted from donor BedAura shouldYieldToKillAura.
            if (this.isYieldingToKillAura()) {
                return;
            }
            if (this.targetBed != null) {
                if (mc.theWorld.isAirBlock(this.targetBed) || !PlayerUtil.canReach(this.targetBed, this.range.getValue().doubleValue())) {
                    this.restoreSlot();
                    this.resetBreaking();
                } else if (this.isProtectedBed(this.targetBed)) {
                    this.restoreSlot();
                    this.resetBreaking();
                } else if (!this.isBed) {
                    BlockPos nearestBed = this.findNearestBed();
                    if (nearestBed != null && mc.theWorld.getBlockState(nearestBed).getBlock() instanceof BlockBed) {
                        this.resetBreaking();
                    }
                }
            }
            if (this.targetBed != null) {
                int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(this.targetBed).getBlock());
                if ((this.mode.getValue() == 0 || this.mode.getValue() == MODE_LEGIT) && !ScaffoldSessionState.hasSavedSlot(this.savedSlot)) {
                    this.savedSlot = ScaffoldSessionState.saveSlotOnce(this.savedSlot, mc.thePlayer.inventory.currentItem);
                    mc.thePlayer.inventory.currentItem = slot;
                    this.syncHeldItem();
                }
                switch (this.breakStage) {
                    case 0:
                        if (!mc.thePlayer.isUsingItem()) {
                            if (this.mode.getValue() == MODE_INSTANT) {
                                this.breakInstant(slot);
                                this.breakStage = 2;
                                break;
                            }
                            this.doSwing();
                            PacketUtil.sendPacket(
                                    new C07PacketPlayerDigging(Action.START_DESTROY_BLOCK, this.targetBed, this.getHitFacing(this.targetBed))
                            );
                            this.doSwing();
                            mc.effectRenderer.addBlockHitEffects(this.targetBed, this.getHitFacing(this.targetBed));
                            this.breakStage = 1;
                        }
                        break;
                    case 1:
                        if (this.mode.getValue() == 1) {
                            this.readyToBreak = false;
                        }
                        this.breaking = true;
                        this.tickCounter++;
                        boolean legit = this.mode.getValue() == MODE_LEGIT;
                        float gained = this.getBreakDelta(mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, mc.thePlayer.onGround);
                        if (legit) {
                            gained *= 0.55F;
                            if (this.tickCounter % 4 == 0) {
                                this.doSwing();
                            }
                        }
                        this.breakProgress = this.breakProgress + gained;
                        float tick = (float) this.tickCounter;
                        IBlockState blockState = mc.theWorld.getBlockState(this.targetBed);
                        boolean canBreak = mc.thePlayer.onGround && this.groundSpeed.getValue();
                        BlockPos target = this.targetBed;
                        float delta = tick * this.getBreakDelta(blockState, target, slot, canBreak) * (legit ? 0.55F : 1.0F);
                        mc.effectRenderer.addBlockHitEffects(this.targetBed, this.getHitFacing(this.targetBed));
                        if (this.breakProgress >= 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)
                                || delta >= 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)) {
                            if (this.mode.getValue() == 1) {
                                this.readyToBreak = true;
                                this.savedSlot = ScaffoldSessionState.saveSlotOnce(this.savedSlot, mc.thePlayer.inventory.currentItem);
                                mc.thePlayer.inventory.currentItem = slot;
                                this.syncHeldItem();
                                if (mc.thePlayer.isUsingItem()) {
                                    mc.thePlayer.inventory.currentItem = (mc.thePlayer.inventory.currentItem + 1) % 9;
                                    this.syncHeldItem();
                                }
                            }
                            this.breaking = false;
                            PacketUtil.sendPacket(
                                    new C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, this.targetBed, this.getHitFacing(this.targetBed))
                            );
                            this.doSwing();
                            IBlockState blockState_ = mc.theWorld.getBlockState(this.targetBed);
                            Block block = blockState_.getBlock();
                            if (block.getMaterial() != Material.air) {
                                mc.theWorld.playAuxSFX(2001, this.targetBed, Block.getStateId(blockState_));
                                mc.theWorld.setBlockToAir(this.targetBed);
                            }
                            if (block instanceof BlockBed) {
                                this.timer.reset();
                            }
                            this.breakStage = 2;
                        }
                        break;
                    case 2:
                        this.restoreSlot();
                        this.resetBreaking();
                }
                if (this.targetBed != null) {
                    return;
                }
            }
            if (mc.thePlayer.capabilities.allowEdit && this.timer.hasTimeElapsed(500)) {
                this.targetBed = this.findNearestBed();
                this.breakStage = 0;
                this.tickCounter = 0;
                this.breakProgress = 0.0F;
                this.isBed = this.targetBed != null && mc.theWorld.getBlockState(this.targetBed).getBlock() instanceof BlockBed;
                this.restoreSlot();
                if (this.targetBed != null) {
                    this.readyToBreak = true;
                }
            }
            if (this.targetBed == null) {
                OpenSkid.delayManager.setDelayState(false, DelayModules.BED_NUKER);
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            AutoBlockIn autoBlockIn = (AutoBlockIn) OpenSkid.moduleManager.modules.get(AutoBlockIn.class);
            if(autoBlockIn.isEnabled()) return;
            if (this.isYieldingToKillAura()) {
                return;
            }
            if (this.isReady()) {
                double x = (double) this.targetBed.getX() + 0.5 - mc.thePlayer.posX;
                double y = (double) this.targetBed.getY() + 0.5 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                double z = (double) this.targetBed.getZ() + 0.5 - mc.thePlayer.posZ;
                float[] rotations = RotationUtil.getRotationsTo(x, y, z, event.getYaw(), event.getPitch());
                float yaw = rotations[0];
                float pitch = rotations[1];
                float speed = this.rotationSpeed.getValue();
                if (speed < 20.0F) {
                    yaw = this.rotMove(rotations[0], event.getYaw(), speed);
                    pitch = this.rotMove(rotations[1], event.getPitch(), speed);
                }
                event.setRotation(yaw, pitch, 5);
                event.setPervRotation(this.moveFix.getValue() != 0 ? yaw : mc.thePlayer.rotationYaw, 5);
            }
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled()) {
            if (this.isBreaking()
                    && !OpenSkid.playerStateManager.attacking
                    && !OpenSkid.playerStateManager.digging
                    && !OpenSkid.playerStateManager.placing
                    && !OpenSkid.playerStateManager.swinging) {
                this.doSwing();
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 5.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && !event.isCancelled() && !(event.getY() <= 0.0)) {
            if (this.ignoreVelocity.getValue() == 1 && this.targetBed != null) {
                event.setCancelled(true);
                event.setX(mc.thePlayer.motionX);
                event.setY(mc.thePlayer.motionY);
                event.setZ(mc.thePlayer.motionZ);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.isEnabled()) {
            if (this.targetBed != null && (!this.isBed || !this.surroundings.getValue())) {
                if (this.showProgress.getValue() != 0) {
                    HUD hud = (HUD) OpenSkid.moduleManager.modules.get(HUD.class);
                    float scale = hud.scale.getValue();
                    String text = String.format("%d%%", (int) (this.calcProgress() * 100.0F));
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, 0.0F);
                    GlStateManager.disableDepth();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    int width = mc.fontRendererObj.getStringWidth(text);
                    mc.fontRendererObj
                            .drawString(
                                    text,
                                    (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / scale - (float) width / 2.0F,
                                    (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 2.0F / scale,
                                    this.getProgressColor(this.showProgress.getValue()).getRGB() & 16777215 | -1090519040,
                                    hud.shadow.getValue()
                            );
                    GlStateManager.disableBlend();
                    GlStateManager.enableDepth();
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && this.targetBed != null && !mc.theWorld.isAirBlock(this.targetBed)) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), this.targetBed, (int) (this.calcProgress() * 10.0F) - 1);
            if (this.showTarget.getValue() != 0) {
                BedESP bedESP = (BedESP) OpenSkid.moduleManager.modules.get(BedESP.class);
                int color = this.getProgressColor(this.showTarget.getValue()).getRGB();
                RenderUtil.enableRenderState();
                BlockPos target = this.targetBed;
                double newHeight = this.isBed ? bedESP.getHeight() : 1.0;
                int r = (color >> 16 & 0xFF);
                int g = (color & 0xFF);
                int b = (color >> 8 & 0xFF);
                RenderUtil.drawBlockBox(target, newHeight, r, b, g);
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.waitingForStart = false;
        this.waitingForRespawn = false;
        this.respawnMessageTime = 0L;
        this.whitelistScanAt = -1L;
        this.ownBedAnchor = null;
        this.bedWhitelist.clear();
        this.resetBreaking();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isCancelled()) {
            if (event.getPacket() instanceof S02PacketChat) {
                String text = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
                if (text.contains("§e§lProtect your bed and destroy the enemy bed") || text.contains("§e§lDestroy the enemy bed and then eliminate them")) {
                    this.waitingForStart = true;
                }
                // Adapted from donor BedAura respawn anchor recapture.
                if (this.spawnAnchor.getValue()) {
                    if (text.contains("You will respawn because you still have a bed!")) {
                        this.waitingForRespawn = true;
                        this.respawnMessageTime = System.currentTimeMillis();
                    } else if (text.contains("You have respawned!") && this.waitingForRespawn
                            && System.currentTimeMillis() - this.respawnMessageTime <= 12000L) {
                        this.waitingForRespawn = false;
                        if (mc.thePlayer != null) {
                            this.ownBedAnchor = new BlockPos(
                                    MathHelper.floor_double(mc.thePlayer.posX),
                                    MathHelper.floor_double(mc.thePlayer.posY),
                                    MathHelper.floor_double(mc.thePlayer.posZ));
                        }
                        this.scheduleWhitelistScan();
                    }
                }
            }
            if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waitingForStart) {
                this.waitingForStart = false;
                // Adapted from donor BedAura spawn anchor capture: pin spawn now.
                if (this.spawnAnchor.getValue() && mc.thePlayer != null) {
                    this.ownBedAnchor = new BlockPos(
                            MathHelper.floor_double(mc.thePlayer.posX),
                            MathHelper.floor_double(mc.thePlayer.posY),
                            MathHelper.floor_double(mc.thePlayer.posZ));
                }
                this.bedWhitelist.clear();
                this.scheduleWhitelistScan();
            }
            if (this.isEnabled() && this.targetBed != null && this.ignoreVelocity.getValue() == 2 && OpenSkid.delayManager.getDelayModule() != DelayModules.BED_NUKER) {
                if (event.getPacket() instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                    if (packet.getEntityID() == mc.thePlayer.getEntityId() && packet.getMotionY() > 0) {
                        OpenSkid.delayManager.delay(DelayModules.BED_NUKER);
                        OpenSkid.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                    }
                }
                if (event.getPacket() instanceof S27PacketExplosion) {
                    S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
                    if (explosion.func_149149_c() != 0.0F || explosion.func_149144_d() != 0.0F || explosion.func_149147_e() != 0.0F) {
                        OpenSkid.delayManager.delay(DelayModules.BED_NUKER);
                        OpenSkid.delayManager.delayedPacket.offer(explosion);
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            if (this.isReady() || this.targetBed != null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (this.isReady()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            if (this.isReady() || this.targetBed != null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            if (this.savedSlot != -1) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onDisabled() {
        this.restoreSlot();
        this.resetBreaking();
        this.waitingForStart = false;
        this.waitingForRespawn = false;
        this.respawnMessageTime = 0L;
        this.whitelistScanAt = -1L;
        this.ownBedAnchor = null;
        this.bedWhitelist.clear();
        OpenSkid.delayManager.setDelayState(false, DelayModules.BED_NUKER);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
