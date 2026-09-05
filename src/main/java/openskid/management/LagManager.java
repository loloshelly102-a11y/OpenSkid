package openskid.management;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.Vec3;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LagManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final Deque<LagPacket> packetQueue;
    private int tickDelay;

    public LagManager() {
        this.packetQueue = new ConcurrentLinkedDeque<>();
        this.tickDelay = 0;
    }

    private void flushQueue() {
        LagCore core = OpenSkid.lagCore;
        if (core == null) {
            return;
        }
        if (this.tickDelay <= 0) {
            core.release(this);
            this.packetQueue.clear();
        } else {
            int released = core.releaseOlderThan(LagCore.Direction.OUTBOUND, this, this.tickDelay);
            while (released-- > 0 && !this.packetQueue.isEmpty()) {
                this.packetQueue.poll();
            }
        }
    }

    public boolean handlePacket(Packet<?> packet) {
        this.flushQueue();
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if ((long) this.tickDelay > 0L) {
            LagCore core = OpenSkid.lagCore;
            if (core != null && core.hold(packet, LagCore.Direction.OUTBOUND, this)) {
                this.packetQueue.offer(new LagPacket(packet));
                return true;
            }
            return false;
        } else {
            LagCore core = OpenSkid.lagCore;
            if (core != null) {
                core.track(packet);
            }
            return false;
        }
    }

    public void setDelay(int delay) {
        this.tickDelay = delay;
    }

    public Vec3 getLastPosition() {
        LagCore core = OpenSkid.lagCore;
        return core == null ? new Vec3(0.0, 0.0, 0.0) : core.getLastServerPosition();
    }

    public boolean isFlushing() {
        LagCore core = OpenSkid.lagCore;
        return core != null && core.isReleasing();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer.isDead) {
                this.setDelay(0);
            }
            this.flushQueue();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setDelay(0);
            // Handshake must flush everything, not just stop future holds.
            LagCore core = OpenSkid.lagCore;
            if (core != null) {
                core.releaseAll();
            }
            this.packetQueue.clear();
        }
    }

    public static class LagPacket {
        public final Packet<?> packet;
        public int delay;

        public LagPacket(Packet<?> packet) {
            this.packet = packet;
            this.delay = 0;
        }
    }
}
