package openskid.module.modules;

import java.util.concurrent.ConcurrentLinkedQueue;
import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PacketEvent;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.ChatUtil;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

// Adapted from Raven S+ PingSpoof. Donor reschedules held packets on an
// executor. Here it is rebuilt as a tick driven queue with no LagCore link.
public class PingSpoof extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MAX_QUEUE = 200;

    public final IntProperty delayMs = new IntProperty("delay-ms", 250, 0, 2000);
    public final BooleanProperty keepAlive = new BooleanProperty("keep-alive", true);
    public final BooleanProperty transactions = new BooleanProperty("transactions", true);
    public final ModeProperty release = new ModeProperty("release", 0, new String[]{"Delay", "Pulse"});
    public final IntProperty pulseSize = new IntProperty("pulse-size", 20, 1, 100, () -> release.getValue() == 1);

    private final ConcurrentLinkedQueue<PacketData> queue = new ConcurrentLinkedQueue<>();
    private boolean stackWarned = false;

    public PingSpoof() {
        super("PingSpoof", false, false, "Delays keep alive packets to spoof your ping.");
    }

    @Override
    public void onEnabled() {
        queue.clear();
        stackWarned = false;
        warnIfStacked();
    }

    @Override
    public void onDisabled() {
        flushAll();
        queue.clear();
        stackWarned = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        boolean hold = (event.getPacket() instanceof C00PacketKeepAlive && keepAlive.getValue())
                || (event.getPacket() instanceof C0FPacketConfirmTransaction && transactions.getValue());
        if (!hold) return;

        warnIfStacked();
        event.setCancelled(true);
        queue.add(new PacketData(event.getPacket(), System.currentTimeMillis()));
        while (queue.size() > MAX_QUEUE) {
            PacketData dropped = queue.poll();
            if (dropped != null) PacketUtil.sendPacketNoEvent(dropped.packet);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (queue.isEmpty()) return;
        if (mc.getNetHandler() == null) {
            queue.clear();
            return;
        }

        if (release.getValue() == 1) {
            PacketData oldest = queue.peek();
            long age = oldest == null ? 0 : System.currentTimeMillis() - oldest.timestamp;
            if (queue.size() >= pulseSize.getValue() || age >= delayMs.getValue()) {
                flushAll();
            }
            return;
        }

        long now = System.currentTimeMillis();
        while (true) {
            PacketData data = queue.peek();
            if (data == null || now - data.timestamp < delayMs.getValue()) break;
            queue.poll();
            PacketUtil.sendPacketNoEvent(data.packet);
        }
    }

    private void flushAll() {
        if (mc.getNetHandler() == null) {
            queue.clear();
            return;
        }
        PacketData data;
        while ((data = queue.poll()) != null) {
            PacketUtil.sendPacketNoEvent(data.packet);
        }
    }

    private boolean isMatrixPingSpoofActive() {
        try {
            if (OpenSkid.moduleManager == null) return false;
            Module m = OpenSkid.moduleManager.getModule(Disabler.class);
            if (!(m instanceof Disabler)) return false;
            Disabler disabler = (Disabler) m;
            return disabler.isEnabled() && disabler.matrixDisabler.getValue() && disabler.matrixT.getValue() == 1;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void warnIfStacked() {
        if (stackWarned) return;
        if (!(keepAlive.getValue() || transactions.getValue())) return;
        if (!isMatrixPingSpoofActive()) return;
        stackWarned = true;
        ChatUtil.sendFormatted(OpenSkid.clientName + "PingSpoof: &eDisabler matrix PingSpoof also delays C00/C0F. Stacking both may flag. Disable one.");
    }

    private static class PacketData {
        private final Packet<?> packet;
        private final long timestamp;

        private PacketData(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{delayMs.getValue() + "ms"};
    }
}
