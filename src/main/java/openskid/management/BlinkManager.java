package openskid.management;

import openskid.OpenSkid;
import openskid.enums.BlinkModules;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BlinkManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    public BlinkModules blinkModule = BlinkModules.NONE;
    public boolean blinking = false;
    public Deque<Packet<?>> blinkedPackets = new ConcurrentLinkedDeque<>();

    public boolean offerPacket(Packet<?> packet) {
        if (this.blinkModule == BlinkModules.NONE || packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if (this.blinkedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction) {
            return false;
        } else {
            LagCore core = OpenSkid.lagCore;
            if (core != null && !core.hold(packet, LagCore.Direction.OUTBOUND, this)) {
                return false;
            }
            this.blinkedPackets.offer(packet);
            return true;
        }
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        if (module == BlinkModules.NONE) {
            return false;
        }
        if (state) {
            this.blinkModule = module;
            this.blinking = true;
        } else {
            if(blinkModule != module){
                return false;
            }
            this.blinking = false;
            if (Minecraft.getMinecraft().getNetHandler() != null && this.blinkedPackets.isEmpty()) {
                return true;
            }
            LagCore core = OpenSkid.lagCore;
            if (core != null) {
                core.release(this);
            } else {
                for (Packet<?> blinkedPacket : blinkedPackets) {
                    openskid.util.PacketUtil.sendPacketNoEvent(blinkedPacket);
                }
            }
            this.blinkedPackets.clear();
            this.blinkModule = BlinkModules.NONE;
        }
        return true;
    }

    public BlinkModules getBlinkingModule() {
        return this.blinkModule;
    }

    public long countMovement() {
        return this.blinkedPackets.stream().filter(packet -> packet instanceof C03PacketPlayer).count();
    }

    public boolean isBlinking() {
        return blinking;
    }

    private void releaseAll() {
        this.blinking = false;
        LagCore core = OpenSkid.lagCore;
        if (core != null) {
            core.release(this);
        } else if (Minecraft.getMinecraft().getNetHandler() != null) {
            for (Packet<?> blinkedPacket : blinkedPackets) {
                openskid.util.PacketUtil.sendPacketNoEvent(blinkedPacket);
            }
        }
        this.blinkedPackets.clear();
        this.blinkModule = BlinkModules.NONE;
    }

    @EventTarget
    public void onDisconnect(PacketEvent event) {
        if (event.getPacket() instanceof S40PacketDisconnect) {
            releaseAll();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        releaseAll();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setBlinkState(false, this.blinkModule);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer.isDead) {
                this.setBlinkState(false, this.blinkModule);
            }
        }
    }
}
