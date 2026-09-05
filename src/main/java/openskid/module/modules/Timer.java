package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.UpdateEvent;
import openskid.mixin.IAccessorMinecraft;
import openskid.module.Module;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty timerMode = new ModeProperty("mode", 0, new String[]{"Normal", "TimerHop", "TimerBalance"});
    public final FloatProperty speed = new FloatProperty("speed", 1.0F, 0.01F, 10.0F);
    public final FloatProperty hopSpeed = new FloatProperty("hop-speed", 1.5F, 0.1F, 3.0F, () -> timerMode.getValue() == 1);
    public final FloatProperty balanceSpeed = new FloatProperty("balance-speed", 0.5F, 0.1F, 1.0F, () -> timerMode.getValue() == 2);
    public final IntProperty balanceInterval = new IntProperty("balance-interval", 5, 1, 20, () -> timerMode.getValue() == 2);

    private int timerTicks = 0;

    public Timer() {
        super("Timer", false, false, "Changes client timer speed to move faster.");
    }

    @Override
    public void onEnabled() {
        this.timerTicks = 0;
    }

    @Override
    public void onDisabled() {
        this.timerTicks = 0;
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            timer.timerSpeed = 1.0F;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            // Ported from Raven GrimBoatLongJump timer-balance idea: hop and balance variants.
            if (this.timerMode.getValue() == 1 && mc.thePlayer != null) {
                timer.timerSpeed = mc.thePlayer.onGround ? this.speed.getValue() : this.hopSpeed.getValue();
            } else if (this.timerMode.getValue() == 2) {
                this.timerTicks++;
                int interval = this.balanceInterval.getValue();
                timer.timerSpeed = (this.timerTicks % (interval * 2)) < interval
                        ? this.balanceSpeed.getValue() : 1.0F;
            } else {
                timer.timerSpeed = this.speed.getValue();
            }
        }
    }

    @Override
    public String[] getSuffix() {
        if (this.timerMode.getValue() == 0) {
            return new String[]{String.format("%.1fx", this.speed.getValue())};
        }
        return new String[]{this.timerMode.getModeString()};
    }
}