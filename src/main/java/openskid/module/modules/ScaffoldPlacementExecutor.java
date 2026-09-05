package openskid.module.modules;

import openskid.util.ItemUtil;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;

// Slot swap plus single-place split adapted from donor ScaffoldPlacementExecutor (rewritten, not copied).
public class ScaffoldPlacementExecutor {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Scaffold scaffold;
    private final ScaffoldSessionState session;
    private long lastPlaceMs = 0L;

    public ScaffoldPlacementExecutor(Scaffold scaffold, ScaffoldSessionState session) {
        this.scaffold = scaffold;
        this.session = session;
    }

    public boolean ensureBlocks() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
        this.session.blockCount = Math.min(this.session.blockCount, count);
        if (this.session.blockCount <= 0) {
            this.session.lastSlot = ScaffoldSessionState.saveSlotOnce(this.session.lastSlot, mc.thePlayer.inventory.currentItem);
            int slot = mc.thePlayer.inventory.currentItem;
            if (this.session.blockCount == 0) {
                slot--;
            }
            for (int i = slot; i > slot - 9; i--) {
                int hotbarSlot = (i % 9 + 9) % 9;
                ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                if (ItemUtil.isBlock(candidate)) {
                    mc.thePlayer.inventory.currentItem = hotbarSlot;
                    this.session.blockCount = candidate.stackSize;
                    break;
                }
            }
        }
        return ItemUtil.isHoldingBlock() && this.session.blockCount > 0;
    }

    public boolean canThreeFmcPlaceNow() {
        if (!this.scaffold.isThreeFmcMode()) {
            return true;
        }
        if (mc.thePlayer == null || this.session.placedThisTick || this.session.threeFmcPlaceCooldown > 0) {
            return false;
        }
        if ((!this.scaffold.isThreeFmcTellyMode() && mc.thePlayer.isSprinting()) || mc.thePlayer.isCollidedHorizontally || mc.thePlayer.hurtTime > 0) {
            return false;
        }
        if (mc.thePlayer.onGround) {
            return Math.abs(mc.thePlayer.motionY) < 1.0E-4 && this.session.threeFmcGroundTicks > 0;
        }
        return this.scaffold.isThreeFmcTellyMode() ? this.session.threeFmcAirTicks > 1 : this.session.threeFmcAirTicks > 2;
    }

    public boolean placeSingle(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (!this.canThreeFmcPlaceNow()) {
            return false;
        }
        if (this.scaffold.placeJitter.getValue() && this.scaffold.placeJitterMs.getValue() > 0) {
            long now = System.currentTimeMillis();
            if (now - this.lastPlaceMs < (long) (Math.random() * this.scaffold.placeJitterMs.getValue())) {
                return false;
            }
            this.lastPlaceMs = now;
        }
        if (ItemUtil.isHoldingBlock() && this.session.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.session.blockCount--;
                }
                this.session.placedThisTick = true;
                if (this.scaffold.isThreeFmcMode()) {
                    this.session.threeFmcPlaceCooldown = 1;
                }
                this.scaffold.markPlaced(blockPos.offset(enumFacing));
                this.session.eagleBlocksPlaced++;
                if (this.scaffold.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }
                return true;
            }
        }
        return false;
    }
}
