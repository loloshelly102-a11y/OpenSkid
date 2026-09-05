package openskid.module.modules;

import openskid.OpenSkid;
import openskid.enums.BlinkModules;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.management.LagCore;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Blink extends Module {
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DEFAULT", "PULSE", "HOLD"});
    public final IntProperty ticks = new IntProperty("ticks", 20, 0, 1200);
    public final BooleanProperty c03Only = new BooleanProperty("c03-only", false, () -> this.mode.getValue() == 2);

    public Blink() {
        super("Blink", false, false, "Holds outgoing packets to fake position then releases them.");
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
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
        if (!this.isEnabled() || this.mode.getValue() != 2) return;
        if (event.getType() != EventType.SEND) return;
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

    private void tickHold() {
        LagCore core = OpenSkid.lagCore;
        if (core == null) return;
        if (this.ticks.getValue() > 0 && core.countHeld(this, LagCore.Direction.OUTBOUND) > this.ticks.getValue()) {
            core.release(this);
        }
    }

    @Override
    public void onEnabled() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        if (this.mode.getValue() == 2) return;
        OpenSkid.blinkManager.setBlinkState(false, OpenSkid.blinkManager.getBlinkingModule());
        OpenSkid.blinkManager.setBlinkState(true, BlinkModules.BLINK);
    }

    @Override
    public void onDisabled() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        OpenSkid.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    }

    @Override
    public String[] getSuffix() {
        LagCore core = OpenSkid.lagCore;
        int held = core != null ? core.countHeld(this, LagCore.Direction.OUTBOUND) : 0;
        if (this.mode.getValue() == 2 && held > 0) return new String[]{ this.mode.getModeString() + " " + held };
        return new String[]{ this.mode.getModeString() };
    }
}
