package openskid.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;

// Single packet-hold hub for the lag managers. Adapted from donor's UnifiedLagHandler.
public class LagCore {
    public enum Direction {
        OUTBOUND,
        INBOUND
    }

    public static final class HeldPacket {
        public final Packet<?> packet;
        public final Direction direction;
        public final Object owner;
        public final long queuedAtMs;
        private volatile int ticksHeld;

        HeldPacket(Packet<?> packet, Direction direction, Object owner) {
            this.packet = packet;
            this.direction = direction;
            this.owner = owner;
            this.queuedAtMs = System.currentTimeMillis();
            this.ticksHeld = 0;
        }

        public int getTicksHeld() {
            return this.ticksHeld;
        }
    }

    private static final Minecraft mc = Minecraft.getMinecraft();

    // Watchdog + cap so no queue grows unbounded when a holder never releases.
    public static final int MAX_HELD_PACKETS = 1000;
    public static final long MAX_HELD_AGE_MS = 30000L;

    private final Deque<HeldPacket> outbound = new ConcurrentLinkedDeque<>();
    private final Deque<HeldPacket> inbound = new ConcurrentLinkedDeque<>();
    // Inbound packets arrive on Netty threads, so the identity sets must be synchronized.
    private final Set<Packet<?>> fastTrack = Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>()));
    private final Set<Packet<?>> held = Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>()));
    private volatile boolean releasing;
    private volatile Vec3 lastServerPosition = new Vec3(0.0, 0.0, 0.0);

    public boolean hold(Packet<?> packet, Direction direction, Object owner) {
        if (packet == null || direction == null || owner == null || this.releasing) {
            return false;
        }
        if (this.fastTrack.remove(packet)) {
            if (direction == Direction.OUTBOUND) {
                this.track(packet);
            }
            return false;
        }
        // Guard double-hold callers here so every owner gets the same answer.
        if (this.isHeld(packet)) {
            return true;
        }
        if (!this.held.add(packet)) {
            return true;
        }
        HeldPacket node = new HeldPacket(packet, direction, owner);
        if (direction == Direction.OUTBOUND) {
            this.outbound.offer(node);
        } else {
            this.inbound.offer(node);
        }
        this.enforceCaps();
        return true;
    }

    public void release(Object owner) {
        if (owner == null) {
            return;
        }
        if (mc.getNetHandler() == null) {
            this.discard(owner);
            return;
        }
        this.releasing = true;
        try {
            this.drain(this.outbound, owner, -1, -1L);
            this.drain(this.inbound, owner, -1, -1L);
        } finally {
            this.releasing = false;
        }
    }

    public void releaseAll() {
        if (mc.getNetHandler() == null) {
            this.clear();
            return;
        }
        this.releasing = true;
        try {
            this.drain(this.outbound, null, -1, -1L);
            this.drain(this.inbound, null, -1, -1L);
        } finally {
            this.releasing = false;
        }
    }

    public void discard(Object owner) {
        if (owner == null) {
            return;
        }
        this.drop(this.outbound, owner);
        this.drop(this.inbound, owner);
    }

    public void fastTrack(Packet<?> packet) {
        if (packet != null) {
            this.fastTrack.add(packet);
        }
    }

    public boolean isHeld(Packet<?> packet) {
        return packet != null && this.held.contains(packet);
    }

    public boolean holdsFrom(Object owner) {
        if (owner == null) {
            return false;
        }
        for (HeldPacket node : this.outbound) {
            if (node.owner == owner) {
                return true;
            }
        }
        for (HeldPacket node : this.inbound) {
            if (node.owner == owner) {
                return true;
            }
        }
        return false;
    }

    public int countHeld(Object owner, Direction direction) {
        if (owner == null || direction == null) {
            return 0;
        }
        int count = 0;
        Deque<HeldPacket> queue = direction == Direction.OUTBOUND ? this.outbound : this.inbound;
        for (HeldPacket node : queue) {
            if (node.owner == owner) {
                count++;
            }
        }
        return count;
    }

    public int releaseOlderThan(Direction direction, Object owner, int minTicks) {
        if (direction == null || owner == null) {
            return 0;
        }
        if (mc.getNetHandler() == null) {
            return this.drop(direction == Direction.OUTBOUND ? this.outbound : this.inbound, owner);
        }
        this.releasing = true;
        try {
            return this.drain(direction == Direction.OUTBOUND ? this.outbound : this.inbound, owner, minTicks, -1L);
        } finally {
            this.releasing = false;
        }
    }

    public int releaseExpired(Direction direction, long maxAgeMs) {
        if (direction == null) {
            return 0;
        }
        if (mc.getNetHandler() == null) {
            Deque<HeldPacket> queue = direction == Direction.OUTBOUND ? this.outbound : this.inbound;
            return this.drop(queue, null);
        }
        this.releasing = true;
        try {
            return this.drain(direction == Direction.OUTBOUND ? this.outbound : this.inbound, null, -1, maxAgeMs);
        } finally {
            this.releasing = false;
        }
    }

    public int releaseExpired(long maxAgeMs) {
        return this.releaseExpired(Direction.OUTBOUND, maxAgeMs) + this.releaseExpired(Direction.INBOUND, maxAgeMs);
    }

    public Vec3 getLastServerPosition() {
        return this.lastServerPosition;
    }

    public void track(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer c03 = (C03PacketPlayer) packet;
            if (c03.isMoving()) {
                this.lastServerPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
            }
        }
    }

    public boolean isReleasing() {
        return this.releasing;
    }

    public void clear() {
        this.outbound.clear();
        this.inbound.clear();
        this.held.clear();
        this.fastTrack.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            this.clear();
            this.lastServerPosition = new Vec3(0.0, 0.0, 0.0);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST) {
            return;
        }
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            this.clear();
            this.lastServerPosition = new Vec3(0.0, 0.0, 0.0);
            return;
        }
        for (HeldPacket node : this.outbound) {
            node.ticksHeld++;
        }
        for (HeldPacket node : this.inbound) {
            node.ticksHeld++;
        }
        // Max-age watchdog: force-release packets whose owner stopped ticking.
        this.releaseExpired(MAX_HELD_AGE_MS);
    }

    // Size cap: force-release the oldest packets first until back under the cap.
    private void enforceCaps() {
        int total = this.outbound.size() + this.inbound.size();
        if (total <= MAX_HELD_PACKETS) {
            return;
        }
        int overflow = total - MAX_HELD_PACKETS;
        this.releasing = true;
        try {
            for (int i = 0; i < overflow; i++) {
                if (!this.releaseOldest()) {
                    break;
                }
            }
        } finally {
            this.releasing = false;
        }
    }

    private boolean releaseOldest() {
        HeldPacket outHead = this.outbound.peek();
        HeldPacket inHead = this.inbound.peek();
        if (outHead == null && inHead == null) {
            return false;
        }
        Deque<HeldPacket> queue;
        HeldPacket oldest;
        if (outHead != null && (inHead == null || outHead.queuedAtMs <= inHead.queuedAtMs)) {
            queue = this.outbound;
            oldest = outHead;
        } else {
            queue = this.inbound;
            oldest = inHead;
        }
        if (queue.remove(oldest)) {
            this.held.remove(oldest.packet);
            this.dispatch(oldest);
            return true;
        }
        return false;
    }

    private int drain(Deque<HeldPacket> queue, Object owner, int minTicks, long maxAgeMs) {
        List<HeldPacket> owned = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (HeldPacket node : queue) {
            if (owner != null && node.owner != owner) {
                continue;
            }
            if (minTicks >= 0 && node.ticksHeld <= minTicks) {
                continue;
            }
            if (maxAgeMs >= 0 && now - node.queuedAtMs <= maxAgeMs) {
                continue;
            }
            owned.add(node);
        }
        for (HeldPacket node : owned) {
            if (queue.remove(node)) {
                this.held.remove(node.packet);
                this.dispatch(node);
            }
        }
        return owned.size();
    }

    private int drop(Deque<HeldPacket> queue, Object owner) {
        List<HeldPacket> owned = new ArrayList<>();
        for (HeldPacket node : queue) {
            if (owner == null || node.owner == owner) {
                owned.add(node);
            }
        }
        for (HeldPacket node : owned) {
            if (queue.remove(node)) {
                this.held.remove(node.packet);
            }
        }
        return owned.size();
    }

    private void dispatch(HeldPacket node) {
        if (node.direction == Direction.OUTBOUND) {
            PacketUtil.sendPacketNoEvent(node.packet);
            this.track(node.packet);
        } else {
            this.handleInbound(node.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleInbound(Packet<?> packet) {
        if (mc.getNetHandler() == null) {
            return;
        }
        ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
    }
}
