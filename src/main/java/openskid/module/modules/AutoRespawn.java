package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;

public class AutoRespawn extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty delay = new IntProperty("delay", 100, 0, 1000);

    private long deathAt = 0L;

    public AutoRespawn() {
        super("AutoRespawn", false, false, "Instantly respawns after death.");
    }

    @Override
    public void onEnabled() {
        this.deathAt = 0L;
    }

    @Override
    public void onDisabled() {
        this.deathAt = 0L;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiGameOver)) {
            this.deathAt = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (this.deathAt == 0L) {
            this.deathAt = now;
        }
        if (now - this.deathAt < (long) this.delay.getValue()) {
            return;
        }
        this.deathAt = now;
        try {
            mc.thePlayer.respawnPlayer();
        } catch (Exception ignored) {
        }
    }
}
