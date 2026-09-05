package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PacketEvent;
import openskid.events.UpdateEvent;
import openskid.management.LagCore;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

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
                PacketUtil.sendPacket(packetQueue.poll().packet);
            }
            this.isDispatching = false;
        } else {
            packetQueue.clear();
            this.isDispatching = false;
        }
        this.pulseStartMs = 0L;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if(!this.enabled) return;
        if (event.getType() == EventType.SEND) {
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
                packetQueue.add(new PacketData(packet, System.currentTimeMillis()));
            } else if (core == null) {
                event.setCancelled(true);
                packetQueue.add(new PacketData(packet, System.currentTimeMillis()));
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (mc.thePlayer == null) return;

            LagCore core = OpenSkid.lagCore;
            if (mode.getValue() == 1) {
                if (core != null) {
                    int held = core.countHeld(this, LagCore.Direction.OUTBOUND);
                    long elapsed = System.currentTimeMillis() - pulseStartMs;
                    if (held >= pulseTicks.getValue() || elapsed >= this.delay.getValue()) {
                        core.release(this);
                        packetQueue.clear();
                        pulseStartMs = System.currentTimeMillis();
                    }
                } else if (!packetQueue.isEmpty() && System.currentTimeMillis() - pulseStartMs >= this.delay.getValue()) {
                    this.isDispatching = true;
                    while (!packetQueue.isEmpty()) PacketUtil.sendPacket(packetQueue.poll().packet);
                    this.isDispatching = false;
                    pulseStartMs = System.currentTimeMillis();
                }
                return;
            }

            long delayTime = this.delay.getValue();
            if (packetQueue.isEmpty()) return;

            if (core != null) {
                int tickDelay = (int) Math.max(0, delayTime / 50L);
                int released = core.releaseOlderThan(LagCore.Direction.OUTBOUND, this, tickDelay);
                while (released-- > 0 && !packetQueue.isEmpty()) packetQueue.poll();
                return;
            }

            long currentTime = System.currentTimeMillis();
            while (!packetQueue.isEmpty()) {
                PacketData data = packetQueue.peek();

                if (currentTime - data.timestamp >= delayTime) {
                    packetQueue.poll();

                    this.isDispatching = true;
                    PacketUtil.sendPacket(data.packet);
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

        public PacketData(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ this.mode.getModeString() + " " + this.delay.getValue() + "ms" };
    }
}