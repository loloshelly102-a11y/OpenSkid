package openskid.module.modules;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

// Session-state split adapted from donor ScaffoldSessionState (rewritten, not copied).
public class ScaffoldSessionState {
    // Shared hotbar slot save/restore helper. Save once so nested swaps keep
    // the true origin instead of overwriting it with an already-swapped slot.
    public static int saveSlotOnce(int savedSlot, int currentSlot) {
        return savedSlot != -1 ? savedSlot : currentSlot;
    }

    public static boolean hasSavedSlot(int savedSlot) {
        return savedSlot != -1;
    }

    public int rotationTick = 0;
    public int lastSlot = -1;
    public int blockCount = -1;
    public float yaw = -180.0F;
    public float pitch = 0.0F;
    public boolean canRotate = false;
    public int towerTick = 0;
    public int towerDelay = 0;
    public int stage = 0;
    public int startY = 256;
    public boolean shouldKeepY = false;
    public boolean towering = false;
    public EnumFacing targetFacing = null;
    public int safeStuckTicks = 0;
    public int safeStuckDelayTicks = 0;
    public double safePrevMotionY = 0.0;
    public double savedMotionX;
    public double savedMotionY;
    public double savedMotionZ;
    public boolean safeStuckActive = false;
    public boolean snapRotating = false;
    public boolean placedThisTick = false;
    public int threeFmcAirTicks = 0;
    public int threeFmcGroundTicks = 0;
    public int threeFmcPlaceCooldown = 0;
    public float lastSnapPlaceYaw = Float.NaN;
    public float lastSnapPlacePitch = Float.NaN;
    public boolean eagleSneaking = false;
    public int eagleSneakTicks = 0;
    public long eagleLastSneakTime = 0L;
    public int eagleBlocksPlaced = 0;
    public final Map<BlockPos, Long> espHighlight = new HashMap<BlockPos, Long>();

    public void resetOnEnable() {
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
        this.safeStuckTicks = 0;
        this.safeStuckDelayTicks = 0;
        this.safePrevMotionY = 0.0;
        this.safeStuckActive = false;
        this.eagleSneaking = false;
        this.eagleSneakTicks = 0;
        this.eagleBlocksPlaced = 0;
        this.eagleLastSneakTime = 0L;
        this.snapRotating = false;
        this.threeFmcAirTicks = 0;
        this.threeFmcGroundTicks = 0;
        this.threeFmcPlaceCooldown = 0;
        this.lastSnapPlaceYaw = Float.NaN;
        this.lastSnapPlacePitch = Float.NaN;
        this.espHighlight.clear();
    }

    public void resetOnDisable() {
        this.safeStuckTicks = 0;
        this.safeStuckDelayTicks = 0;
        this.safePrevMotionY = 0.0;
        this.safeStuckActive = false;
        this.eagleSneaking = false;
        this.eagleSneakTicks = 0;
        this.threeFmcAirTicks = 0;
        this.threeFmcGroundTicks = 0;
        this.threeFmcPlaceCooldown = 0;
    }
}
