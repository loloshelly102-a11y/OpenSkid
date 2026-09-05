package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PickEvent;
import openskid.events.RaytraceEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

public class Reach extends Module {
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Random theRandom = new Random();
    private boolean expanding = true;
    public final FloatProperty range = new FloatProperty("range", 3.1F, 3.0F, 6.0F);
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final BooleanProperty legit = new BooleanProperty("legit", false);
    public final BooleanProperty jitter = new BooleanProperty("jitter", false);
    public final FloatProperty minRange = new FloatProperty("min-range", 3.0F, 3.0F, 6.0F, () -> jitter.getValue());
    public final FloatProperty maxRange = new FloatProperty("max-range", 3.4F, 3.0F, 6.0F, () -> jitter.getValue());
    public final BooleanProperty sprintOnly = new BooleanProperty("sprint-only", false);
    public final BooleanProperty verticalCheck = new BooleanProperty("vertical-check", false);
    public final FloatProperty maxVertical = new FloatProperty("max-vertical", 1.5F, 0.0F, 5.0F, () -> verticalCheck.getValue());

    public Reach() {
        super("Reach", false, false, "Extends your melee attack reach distance.");
    }

    public double getEffectiveRange() {
        double base = this.range.getValue().doubleValue();
        if (this.jitter.getValue()) {
            float lo = Math.min(this.minRange.getValue(), this.maxRange.getValue());
            float hi = Math.max(this.minRange.getValue(), this.maxRange.getValue());
            base = lo + this.theRandom.nextDouble() * Math.max(0.0F, hi - lo);
        }
        if (this.legit.getValue()) {
            base = Math.min(base, 3.5D);
        }
        return base;
    }

    private boolean passesGates() {
        if (!this.expanding) return false;
        if (this.sprintOnly.getValue() && (mc.thePlayer == null || !mc.thePlayer.isSprinting())) return false;
        if (this.verticalCheck.getValue() && mc.thePlayer != null) {
            float pitch = Math.abs(mc.thePlayer.rotationPitch);
            if (pitch > 30.0F + this.maxVertical.getValue() * 10.0F) return false;
        }
        return true;
    }

    @EventTarget
    public void onPick(PickEvent event) {
        if (this.isEnabled() && passesGates()) {
            event.setRange(getEffectiveRange());
        }
    }

    @EventTarget
    public void onRaytrace(RaytraceEvent event) {
        if (this.isEnabled() && passesGates()) {
            event.setRange(Math.max(event.getRange(), getEffectiveRange() + 0.5));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.expanding = this.theRandom.nextDouble() <= (double) this.chance.getValue() / 100.0;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.range.getValue())};
    }
}
