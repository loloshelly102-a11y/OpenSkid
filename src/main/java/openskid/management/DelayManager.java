package openskid.management;

import openskid.OpenSkid;
import openskid.enums.DelayModules;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DelayManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    public DelayModules delayModule = DelayModules.NONE;
    public long delay = 0L;
    public Deque<Packet<INetHandlerPlayClient>> delayedPacket = new ConcurrentLinkedDeque<>();

    private void adoptInbox() {
        LagCore core = OpenSkid.lagCore;
        if (core == null || this.delayModule == DelayModules.NONE) {
            return;
        }
        for (Packet<INetHandlerPlayClient> packet : new ArrayList<>(this.delayedPacket)) {
            if (!core.isHeld(packet)) {
                core.hold(packet, LagCore.Direction.INBOUND, this);
            }
        }
    }

    public boolean shouldDelay(Packet<INetHandlerPlayClient> packet) {
        this.adoptInbox();
        if (this.delayModule == DelayModules.NONE) {
            return false;
        } else if (packet instanceof S00PacketKeepAlive) {
            return false;
        } else if (PacketUtil.isWorldRenderPacket(packet)) {
            return false;
        } else if (!(packet instanceof S01PacketJoinGame) && !(packet instanceof S07PacketRespawn) && !(packet instanceof S08PacketPlayerPosLook)) {
            if (packet instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus s19 = (S19PacketEntityStatus) packet;
                Entity entity = s19.getEntity(mc.theWorld);
                if (entity != null && (!entity.equals(mc.thePlayer) || s19.getOpCode() != 2)) {
                    return false;
                }
            }
            LagCore core = OpenSkid.lagCore;
            if (core != null && !core.hold(packet, LagCore.Direction.INBOUND, this)) {
                return false;
            }
            this.delayedPacket.offer(packet);
            return true;
        } else {
            this.clearDelayState();
            return false;
        }
    }

    public boolean setDelayState(boolean state, DelayModules delayModule) {
        if (state) {
            this.delay = 0;
            this.delayModule = delayModule;
        } else {
            this.adoptInbox();
            this.delayModule = DelayModules.NONE;
            LagCore core = OpenSkid.lagCore;
            if (core != null) {
                core.release(this);
            } else if (Minecraft.getMinecraft().getNetHandler() != null && this.delayedPacket.isEmpty()) {
                return true;
            } else {
                while (true) {
                    Packet<INetHandlerPlayClient> packet = this.delayedPacket.poll();
                    if (packet == null) {
                        this.delayedPacket.clear();
                        break;
                    }
                    packet.processPacket(Minecraft.getMinecraft().getNetHandler());
                }
            }
            this.delayedPacket.clear();
        }
        return this.delayModule != DelayModules.NONE;
    }

    public void clearDelayState() {
        this.delayModule = DelayModules.NONE;
        this.delay = 0L;
        LagCore core = OpenSkid.lagCore;
        if (core != null) {
            core.release(this);
        } else if (Minecraft.getMinecraft().getNetHandler() != null) {
            Packet<INetHandlerPlayClient> packet;
            while ((packet = this.delayedPacket.poll()) != null) {
                packet.processPacket(Minecraft.getMinecraft().getNetHandler());
            }
        }
        this.delayedPacket.clear();
    }

    public DelayModules getDelayModule() {
        return this.delayModule;
    }

    public void delay(DelayModules modules) {
        this.delayModule = modules;
    }

    public long getDelay() {
        return this.delay;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
            || event.getPacket() instanceof C00PacketServerQuery
            || event.getPacket() instanceof C01PacketPing
            || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.clearDelayState();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.clearDelayState();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer == null || mc.thePlayer.isDead) {
                this.setDelayState(false, this.delayModule);
            }
            if (this.delayModule != DelayModules.NONE) {
                this.delay++;
            }
        }
    }
}
