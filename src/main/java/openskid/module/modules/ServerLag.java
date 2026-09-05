package openskid.module.modules;

import java.util.concurrent.ConcurrentLinkedQueue;
import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.management.LagCore;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S40PacketDisconnect;

public class ServerLag extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    // Mirror of this module's LagCore holds. Dispatch always goes through the
    // hub on the client thread, never direct processPacket here.
    private final ConcurrentLinkedQueue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private final IntProperty maxBlinkTime = new IntProperty("Lag ms", 1000, 500, 30000);
    private int currentLatency = 0;

    public ServerLag() {
        super("ServerLag", false, false, "Holds server packets to simulate lag and delay updates.");
    }

    @Override
    public void onEnabled() {
        currentLatency = maxBlinkTime.getValue();
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (currentLatency == 0) return;
        Packet<?> packet = event.getPacket();
        if (PacketUtil.isWorldRenderPacket(packet)) return;
        if (packet instanceof S19PacketEntityStatus || packet instanceof S02PacketChat || packet instanceof S0BPacketAnimation || packet instanceof S06PacketUpdateHealth) return;
        if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
            this.releaseAllPackets();
            return;
        }

        LagCore core = OpenSkid.lagCore;
        if (core == null) {
            return;
        }
        if (core.isReleasing() || core.isHeld(packet)) {
            event.setCancelled(true);
            return;
        }
        // Hold through the hub; timed release happens in onTick on the client thread.
        if (core.hold(packet, LagCore.Direction.INBOUND, this)) {
            event.setCancelled(true);
            packetQueue.add(packet);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;
        if (currentLatency <= 0) return;
        LagCore core = OpenSkid.lagCore;
        if (core == null) return;
        int released = core.releaseExpired(LagCore.Direction.INBOUND, currentLatency);
        while (released-- > 0 && !packetQueue.isEmpty()) {
            packetQueue.poll();
        }
        packetQueue.removeIf(p -> !core.isHeld(p));
        if (!core.holdsFrom(this)) {
            packetQueue.clear();
            currentLatency = 0;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.releaseAllPackets();
    }

    private void releaseAllPackets() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) {
            core.release(this);
        }
        packetQueue.clear();
        currentLatency = 0;
    }

    @Override
    public void onDisabled() {
        this.releaseAllPackets();
    }
}
