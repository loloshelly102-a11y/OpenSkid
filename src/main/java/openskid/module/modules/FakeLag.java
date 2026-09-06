package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.AttackEvent;
import openskid.events.PacketEvent;
import openskid.events.Render3DEvent;
import openskid.events.UpdateEvent;
import openskid.management.LagCore;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.PacketUtil;
import openskid.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.concurrent.ConcurrentLinkedQueue;

public class FakeLag extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // The amount of latency to simulate in milliseconds
    public final IntProperty delay = new IntProperty("delay-ms", 200, 50, 5000);
    // PULSE appended at end, DELAY keeps legacy sliding-window behavior.
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DELAY", "PULSE"});
    public final IntProperty pulseTicks = new IntProperty("pulse-ticks", 20, 1, 200, () -> this.mode.getValue() == 1);

    private final ConcurrentLinkedQueue<PacketData> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean isDispatching = false;
    private long pulseStartMs = 0L;
    private long enableMs = 0L;
    private Vec3 startPos = null;

    public FakeLag() {
        super("FakeLag", false, false, "Delays outbound packets to simulate lag spikes.");
    }

    @Override
    public void onEnabled() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        packetQueue.clear();
        this.isDispatching = false;
        this.pulseStartMs = System.currentTimeMillis();
        this.enableMs = System.currentTimeMillis();
        if (mc.thePlayer != null) {
            this.startPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        } else {
            this.startPos = null;
        }
    }

    @Override
    public void onDisabled() {
        // Release hub holds so no packets leak on toggle. Adapted from BlinkManager release.
        LagCore core = OpenSkid.lagCore;
        if (core != null) {
            core.release(this);
            packetQueue.clear();
        } else if (mc.getNetHandler() != null) {
            this.isDispatching = true;
            while (!packetQueue.isEmpty()) {
                this.dispatch(packetQueue.poll());
            }
            this.isDispatching = false;
        } else {
            packetQueue.clear();
            this.isDispatching = false;
        }
        this.pulseStartMs = 0L;
        this.enableMs = 0L;
        this.startPos = null;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if(!this.enabled) return;
        if (event.getType() == EventType.RECEIVE) {
            this.holdInbound(event);
            return;
        }
        if (event.getType() == EventType.SEND) {
            if (!this.holdsOutbound()) return;
            LagCore core = OpenSkid.lagCore;
            if (core != null && core.isReleasing()) return;
            if (this.isDispatching) {
                return;
            }

            if (mc.thePlayer == null || mc.theWorld == null) {
                return;
            }

            Packet<?> packet = event.getPacket();

            if (core != null && core.isHeld(packet)) {
                event.setCancelled(true);
                return;
            }
            if (core != null && core.hold(packet, LagCore.Direction.OUTBOUND, this)) {
                event.setCancelled(true);
                packetQueue.add(new PacketData(packet, System.currentTimeMillis(), LagCore.Direction.OUTBOUND));
            } else if (core == null) {
                event.setCancelled(true);
                packetQueue.add(new PacketData(packet, System.currentTimeMillis(), LagCore.Direction.OUTBOUND));
            }
        }
    }

    private boolean holdsOutbound() {
        return this.direction.getValue() == 1 || this.direction.getValue() == 2;
    }

    private boolean holdsInbound() {
        return this.direction.getValue() == 0 || this.direction.getValue() == 2;
    }

    private void holdInbound(PacketEvent event) {
        if (!this.holdsInbound()) return;
        LagCore core = OpenSkid.lagCore;
        if (core != null && core.isReleasing()) return;
        if (this.isDispatching) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (event.getPacket() instanceof S40PacketDisconnect) return;
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            if (core != null && core.holdsFrom(this)) core.release(this);
            return;
        }
        if (!event.getPacket().getClass().getSimpleName().startsWith("S")) return;

        Packet<?> packet = event.getPacket();

        if (core != null && core.isHeld(packet)) {
            event.setCancelled(true);
            return;
        }
        if (core != null && core.hold(packet, LagCore.Direction.INBOUND, this)) {
            event.setCancelled(true);
            packetQueue.add(new PacketData(packet, System.currentTimeMillis(), LagCore.Direction.INBOUND));
        } else if (core == null) {
            event.setCancelled(true);
            packetQueue.add(new PacketData(packet, System.currentTimeMillis(), LagCore.Direction.INBOUND));
        }
    }

    private int countHeld(LagCore core) {
        int held = 0;
        if (this.holdsOutbound()) held += core.countHeld(this, LagCore.Direction.OUTBOUND);
        if (this.holdsInbound()) held += core.countHeld(this, LagCore.Direction.INBOUND);
        return held;
    }

    private int releaseHeld(LagCore core, int minTicks) {
        int released = 0;
        if (this.holdsOutbound()) released += core.releaseOlderThan(LagCore.Direction.OUTBOUND, this, minTicks);
        if (this.holdsInbound()) released += core.releaseOlderThan(LagCore.Direction.INBOUND, this, minTicks);
        return released;
    }

    private void dispatch(PacketData data) {
        if (data.dir == LagCore.Direction.INBOUND && mc.getNetHandler() != null) {
            dispatchInbound(data.packet);
        } else {
            PacketUtil.sendPacket(data.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private static void dispatchInbound(Packet<?> packet) {
        ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.enabled && this.disableOnAttack.getValue()) {
            this.setEnabled(false);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled || !this.showStartPos.getValue() || this.startPos == null || mc.thePlayer == null) return;
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

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (mc.thePlayer == null) return;
            if (this.disableAfterMs.getValue() > 0 && this.enableMs > 0L
                    && System.currentTimeMillis() - this.enableMs >= (long) this.disableAfterMs.getValue()) {
                this.setEnabled(false);
                return;
            }

            LagCore core = OpenSkid.lagCore;
            if (mode.getValue() == 1) {
                if (core != null) {
                    int held = this.countHeld(core);
                    long elapsed = System.currentTimeMillis() - pulseStartMs;
                    if (held >= pulseTicks.getValue() || elapsed >= this.delay.getValue()) {
                        core.release(this);
                        packetQueue.clear();
                        pulseStartMs = System.currentTimeMillis();
                    }
                } else if (!packetQueue.isEmpty() && System.currentTimeMillis() - pulseStartMs >= this.delay.getValue()) {
                    this.isDispatching = true;
                    while (!packetQueue.isEmpty()) this.dispatch(packetQueue.poll());
                    this.isDispatching = false;
                    pulseStartMs = System.currentTimeMillis();
                }
                return;
            }

            long delayTime = this.delay.getValue();
            if (packetQueue.isEmpty()) return;

            if (core != null) {
                int tickDelay = (int) Math.max(0, delayTime / 50L);
                int released = this.releaseHeld(core, tickDelay);
                while (released-- > 0 && !packetQueue.isEmpty()) packetQueue.poll();
                return;
            }

            long currentTime = System.currentTimeMillis();
            while (!packetQueue.isEmpty()) {
                PacketData data = packetQueue.peek();

                if (currentTime - data.timestamp >= delayTime) {
                    packetQueue.poll();

                    this.isDispatching = true;
                    this.dispatch(data);
                    this.isDispatching = false;
                } else {
                    break;
                }
            }
        }
    }

    private static class PacketData {
        private final Packet<?> packet;
        private final long timestamp;
        private final LagCore.Direction dir;

        public PacketData(Packet<?> packet, long timestamp, LagCore.Direction dir) {
            this.packet = packet;
            this.timestamp = timestamp;
            this.dir = dir;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ this.mode.getModeString() + " " + this.delay.getValue() + "ms" };
    }

    public final ModeProperty direction = new ModeProperty("direction", 1, new String[]{"Inbound", "Outbound", "Both"});
    public final IntProperty disableAfterMs = new IntProperty("disable-after-ms", 0, 0, 10000);
    public final BooleanProperty disableOnAttack = new BooleanProperty("disable-on-attack", false);
    public final BooleanProperty showStartPos = new BooleanProperty("show-start-pos", false);
}