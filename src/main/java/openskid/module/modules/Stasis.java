package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LivingUpdateEvent;
import openskid.events.MoveInputEvent;
import openskid.events.PacketEvent;
import openskid.events.StrafeEvent;
import openskid.events.UpdateEvent;
import openskid.management.LagCore;
import openskid.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Stasis extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;

    private int tickCounter;
    private int phase; // 0 = stasis, 1 = release
    private static final int STASIS_TICKS = 45;
    private static final int RELEASE_TICKS = 1;

    public Stasis() {
        super("Stasis", false, false, "Freezes motion and packets briefly to stall in air.");
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            savedMotionX = mc.thePlayer.motionX;
            savedMotionY = mc.thePlayer.motionY;
            savedMotionZ = mc.thePlayer.motionZ;
        }
        tickCounter = 0;
        phase = 0; // 立即进入冻结状态
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (mc.thePlayer == null) return;

        tickCounter++;

        if (phase == 0 && tickCounter >= STASIS_TICKS) {
            phase = 1;
            tickCounter = 0;
            // Release phase resends every held C03 so the server sees no gaps.
            LagCore core = OpenSkid.lagCore;
            if (core != null) {
                core.release(this);
            }
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionY = savedMotionY;
            mc.thePlayer.motionZ = savedMotionZ;
        } else if (phase == 1 && tickCounter >= RELEASE_TICKS) {
            phase = 0;
            tickCounter = 0;
            savedMotionX = mc.thePlayer.motionX;
            savedMotionY = mc.thePlayer.motionY;
            savedMotionZ = mc.thePlayer.motionZ;
        }

        if (phase == 0) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
        if (mc.thePlayer != null && mc.thePlayer.onGround) {
            this.setEnabled(false);
            return;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && phase == 0 && mc.thePlayer != null) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && phase == 0 && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && phase == 0) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        }
    }

    @EventTarget
    public void onSendPacket(PacketEvent e) {
        if (!this.isEnabled()) return;
        if (e.getType() != EventType.SEND) return;
        if (!(e.getPacket() instanceof C03PacketPlayer)) return;

        if (phase == 1) return;

        if (mc.thePlayer == null || mc.thePlayer.hurtTime != 0) {
            return;
        }

        if (!(e.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            LagCore core = OpenSkid.lagCore;
            if (core == null || core.isReleasing()) {
                return;
            }
            if (core.isHeld(e.getPacket())) {
                e.setCancelled(true);
                return;
            }
            // Hold through the hub instead of dropping, so the release phase
            // resends every C03 and the server never sees sequence gaps.
            if (core.hold(e.getPacket(), LagCore.Direction.OUTBOUND, this)) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void onDisabled() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) {
            core.release(this);
        }
        if (mc.thePlayer != null) {
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionY = savedMotionY;
            mc.thePlayer.motionZ = savedMotionZ;
        }
        tickCounter = 0;
        phase = 0;
    }
}