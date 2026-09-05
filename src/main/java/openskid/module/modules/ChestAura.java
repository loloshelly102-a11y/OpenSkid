package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.*;
import openskid.management.RotationState;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.MoveUtil;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// 为了防止某个叫秋窈的狗来狗叫，我特定说明这个ChestAura是来自某个魔水的，而不是你那坨狗屎Eternity里面还是我搞上去的ChestAura
// 你那个端里的ChestAura还是我弄上去的，你有什么资格来狗叫我
public class ChestAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("0.0");

    public final FloatProperty range = new FloatProperty("Range", 4.0f, 1.0f, 6.0f);
    public final BooleanProperty throughWalls = new BooleanProperty("Through Walls", true);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"});
    public final FloatProperty playerRange = new FloatProperty("player-range", 0.0f, 0.0f, 8.0f);

    private final List<BlockPos> openedChests = new ArrayList<>();
    private TileEntityChest targetChest;
    private float[] rotations;
    private boolean isRotating;
    private boolean scaffoldWasEnabled = false;

    public ChestAura() {
        super("ChestAura", false, false, "Automatically faces and opens nearby unopened chests.");
    }

    @Override
    public void onEnabled() {
        Scaffold scaffold = (Scaffold) OpenSkid.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            scaffoldWasEnabled = true;
            scaffold.setEnabled(false);
        }
        openedChests.clear();
    }

    @Override
    public void onDisabled() {
        if (scaffoldWasEnabled) {
            Scaffold scaffold = (Scaffold) OpenSkid.moduleManager.getModule(Scaffold.class);
            if (scaffold != null) {
                scaffold.setEnabled(true);
            }
            scaffoldWasEnabled = false;
        }
        targetChest = null;
        isRotating = false;
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        openedChests.clear();
        scaffoldWasEnabled = false;
    }

    private void addOpenedChest(BlockPos pos) {
        if (!openedChests.contains(pos)) {
            openedChests.add(pos);
        }
        net.minecraft.block.Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block instanceof BlockChest) {
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos neighbor = pos.offset(facing);
                if (mc.theWorld.getBlockState(neighbor).getBlock() == block) {
                    if (!openedChests.contains(neighbor)) {
                        openedChests.add(neighbor);
                    }
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(range.getValue())};
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof S24PacketBlockAction) {
            S24PacketBlockAction packet = (S24PacketBlockAction) event.getPacket();
            if (packet.getData2() == 1) {
                addOpenedChest(packet.getBlockPosition());
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() != EventType.PRE) return;

        KillAura killAura = (KillAura) OpenSkid.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            targetChest = null;
            isRotating = false;
            return;
        }

        if (shouldYieldToWorldModules()) {
            targetChest = null;
            isRotating = false;
            return;
        }

        if (isPlayerInRange()) {
            targetChest = null;
            isRotating = false;
            return;
        }

        if (mc.currentScreen instanceof GuiContainer || mc.currentScreen instanceof GuiInventory) {
            targetChest = null;
            isRotating = false;
            return;
        }

        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) tileEntity;
                if (chest.numPlayersUsing > 0) {
                    addOpenedChest(chest.getPos());
                }
            }
        }

        targetChest = getClosestChest();
        isRotating = false;

        if (targetChest != null) {
            double x = targetChest.getPos().getX() + 0.5 - mc.thePlayer.posX;
            double y = targetChest.getPos().getY() + 0.5 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            double z = targetChest.getPos().getZ() + 0.5 - mc.thePlayer.posZ;
            double dist = Math.sqrt(x * x + z * z);

            float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float) -(Math.atan2(y, dist) * 180.0 / Math.PI);

            rotations = new float[]{yaw, pitch};

            event.setRotation(rotations[0], rotations[1], 1);
            mc.thePlayer.rotationYawHead = rotations[0];
            mc.thePlayer.renderYawOffset = rotations[0];
            isRotating = true;

            if (this.moveFix.getValue() != 0) {
                event.setPervRotation(rotations[0], 1);
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (targetChest == null || !isRotating || rotations == null) return;
        if (shouldYieldToWorldModules() || isPlayerInRange()) {
            targetChest = null;
            isRotating = false;
            return;
        }
        if (!(RotationState.isActived() && RotationState.getPriority() == 1)) return;
        float useYaw = RotationState.getSmoothedYaw();
        float usePitch = RotationState.getRotationPitch();
        MovingObjectPosition mop = rayTrace(useYaw, usePitch, range.getValue().doubleValue());
        if (mop == null
                || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(targetChest.getPos())) return;
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(),
                targetChest.getPos(), mop.sideHit, mop.hitVec)) {
            mc.thePlayer.swingItem();
            addOpenedChest(targetChest.getPos());
        }
    }

    private boolean shouldYieldToWorldModules() {
        Scaffold scaffold = (Scaffold) OpenSkid.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) return true;
        BedNuker bedNuker = (BedNuker) OpenSkid.moduleManager.getModule(BedNuker.class);
        if (bedNuker != null && bedNuker.isEnabled() && (bedNuker.isReady() || bedNuker.isBreaking())) return true;
        BedDefender bedDefender = (BedDefender) OpenSkid.moduleManager.getModule(BedDefender.class);
        if (bedDefender != null && bedDefender.isEnabled()) return true;
        AutoBedDef autoBedDef = (AutoBedDef) OpenSkid.moduleManager.getModule(AutoBedDef.class);
        if (autoBedDef != null && autoBedDef.isEnabled()) return true;
        AutoBlockIn autoBlockIn = (AutoBlockIn) OpenSkid.moduleManager.getModule(AutoBlockIn.class);
        return autoBlockIn != null && autoBlockIn.isEnabled();
    }

    private boolean isPlayerInRange() {
        float gate = playerRange.getValue();
        if (gate <= 0.0f || mc.theWorld == null || mc.thePlayer == null) return false;
        double rangeSq = (double) gate * (double) gate;
        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) o;
            if (player == mc.thePlayer) continue;
            if (mc.thePlayer.getDistanceSqToEntity(player) <= rangeSq) return true;
        }
        return false;
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch, double distance) {
        float yr = (float) Math.toRadians(yaw);
        float pr = (float) Math.toRadians(pitch);
        double lx = -Math.sin(yr) * Math.cos(pr);
        double ly = -Math.sin(pr);
        double lz = Math.cos(yr) * Math.cos(pr);
        Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 end = start.addVector(lx * distance, ly * distance, lz * distance);
        return mc.theWorld.rayTraceBlocks(start, end);
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled()) return;

        KillAura killAura = (KillAura) OpenSkid.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) return;

        if (isRotating && targetChest != null) {
            if (this.moveFix.getValue() == 1 && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(rotations[0]);
            }
        }
    }

    private TileEntityChest getClosestChest() {
        List<TileEntityChest> chests = mc.theWorld.loadedTileEntityList.stream()
                .filter(e -> e instanceof TileEntityChest)
                .map(e -> (TileEntityChest) e)
                .filter(e -> !openedChests.contains(e.getPos()))
                .filter(e -> mc.thePlayer.getDistanceSq(e.getPos()) <= range.getValue() * range.getValue())
                .filter(e -> throughWalls.getValue() || mc.thePlayer.canEntityBeSeen(
                        new net.minecraft.entity.item.EntityItem(mc.theWorld, e.getPos().getX(), e.getPos().getY(), e.getPos().getZ())))
                .sorted(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceSq(e.getPos())))
                .collect(Collectors.toList());

        return chests.isEmpty() ? null : chests.get(0);
    }
}