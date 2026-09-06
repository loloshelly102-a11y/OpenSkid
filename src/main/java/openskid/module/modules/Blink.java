package openskid.module.modules;

import openskid.OpenSkid;
import openskid.enums.BlinkModules;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.AttackEvent;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.Render3DEvent;
import openskid.events.TickEvent;
import openskid.management.LagCore;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class Blink extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private long enableMs = 0L;
    private Vec3 startPos = null;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DEFAULT", "PULSE", "HOLD"});
    public final IntProperty ticks = new IntProperty("ticks", 20, 0, 1200);
    public final BooleanProperty c03Only = new BooleanProperty("c03-only", false, () -> this.mode.getValue() == 2);

    public Blink() {
        super("Blink", false, false, "Holds outgoing packets to fake position then releases them.");
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            if (this.disableAfterMs.getValue() > 0 && this.enableMs > 0L
                    && System.currentTimeMillis() - this.enableMs >= (long) this.disableAfterMs.getValue()) {
                this.setEnabled(false);
                return;
            }
            if (this.mode.getValue() == 2) {
                tickHold();
                return;
            }
            if (!OpenSkid.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
                this.setEnabled(false);
            } else {
                if (this.ticks.getValue() > 0 && OpenSkid.blinkManager.countMovement() > (long) this.ticks.getValue()) {
                    switch (this.mode.getValue()) {
                        case 0:
                            this.setEnabled(false);
                            break;
                        case 1:
                            OpenSkid.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                            OpenSkid.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() == EventType.RECEIVE) {
            this.holdInbound(event);
            return;
        }
        if (event.getType() != EventType.SEND || this.mode.getValue() != 2) return;
        if (!this.holdsOutbound()) return;
        if (event.getPacket() instanceof C00PacketKeepAlive) return;
        if (event.getPacket() instanceof C01PacketChatMessage) return;
        if (this.c03Only.getValue() && !(event.getPacket() instanceof C03PacketPlayer)) return;
        LagCore core = OpenSkid.lagCore;
        if (core == null || core.isReleasing() || core.isHeld(event.getPacket())) {
            if (core != null && core.isHeld(event.getPacket())) event.setCancelled(true);
            return;
        }
        if (core.hold(event.getPacket(), LagCore.Direction.OUTBOUND, this)) event.setCancelled(true);
    }

    private boolean holdsOutbound() {
        return this.direction.getValue() == 1 || this.direction.getValue() == 2;
    }

    private boolean holdsInbound() {
        return this.direction.getValue() == 0 || this.direction.getValue() == 2;
    }

    private void holdInbound(PacketEvent event) {
        if (!this.holdsInbound()) return;
        if (event.getPacket() instanceof S40PacketDisconnect) return;
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            LagCore core = OpenSkid.lagCore;
            if (core != null && core.holdsFrom(this)) core.release(this);
            return;
        }
        if (!event.getPacket().getClass().getSimpleName().startsWith("S")) return;
        LagCore core = OpenSkid.lagCore;
        if (core == null || core.isReleasing() || core.isHeld(event.getPacket())) {
            if (core != null && core.isHeld(event.getPacket())) event.setCancelled(true);
            return;
        }
        if (core.hold(event.getPacket(), LagCore.Direction.INBOUND, this)) event.setCancelled(true);
    }

    private int countHeld(LagCore core) {
        int held = 0;
        if (this.holdsOutbound()) held += core.countHeld(this, LagCore.Direction.OUTBOUND);
        if (this.holdsInbound()) held += core.countHeld(this, LagCore.Direction.INBOUND);
        return held;
    }

    private void tickHold() {
        LagCore core = OpenSkid.lagCore;
        if (core == null) return;
        if (this.ticks.getValue() > 0 && this.countHeld(core) > this.ticks.getValue()) {
            core.release(this);
        }
    }

    @Override
    public void onEnabled() {
        this.enableMs = System.currentTimeMillis();
        if (mc.thePlayer != null) {
            this.startPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        } else {
            this.startPos = null;
        }
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        if (this.mode.getValue() == 2) return;
        OpenSkid.blinkManager.setBlinkState(false, OpenSkid.blinkManager.getBlinkingModule());
        OpenSkid.blinkManager.setBlinkState(true, BlinkModules.BLINK);
    }

    @Override
    public void onDisabled() {
        this.enableMs = 0L;
        this.startPos = null;
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        OpenSkid.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    }

    @Override
    public String[] getSuffix() {
        LagCore core = OpenSkid.lagCore;
        int held = core != null ? this.countHeld(core) : 0;
        if (this.mode.getValue() == 2 && held > 0) return new String[]{ this.mode.getModeString() + " " + held };
        return new String[]{ this.mode.getModeString() };
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && this.disableOnAttack.getValue()) {
            this.setEnabled(false);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.showStartPos.getValue() || this.startPos == null || mc.thePlayer == null) return;
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        double x = this.startPos.xCoord - rm.getRenderPosX();
        double y = this.startPos.yCoord - rm.getRenderPosY();
        double z = this.startPos.zCoord - rm.getRenderPosZ();
        AxisAlignedBB aabb = new AxisAlignedBB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(aabb, 255, 255, 255);
        RenderUtil.drawBoundingBox(aabb, 255, 255, 255, 200, 2.0F);
        RenderUtil.disableRenderState();
    }

    public final ModeProperty direction = new ModeProperty("direction", 1, new String[]{"Inbound", "Outbound", "Both"});
    public final IntProperty disableAfterMs = new IntProperty("disable-after-ms", 0, 0, 10000);
    public final BooleanProperty disableOnAttack = new BooleanProperty("disable-on-attack", false);
    public final BooleanProperty showStartPos = new BooleanProperty("show-start-pos", false);
}
