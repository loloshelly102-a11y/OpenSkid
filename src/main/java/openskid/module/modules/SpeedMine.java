package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.TickEvent;
import openskid.mixin.IAccessorPlayerControllerMP;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class SpeedMine extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty speed = new PercentProperty("speed", 15);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 4);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "LEGIT"});
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100);

    public SpeedMine() {
        super("SpeedMine", false, false, "Speeds up block mining and reduces hit delay.");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (!mc.playerController.isInCreativeMode()) {
                if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                    if (this.delayChance.getValue() >= 100 || Math.random() * 100.0 < this.delayChance.getValue()) {
                        ((IAccessorPlayerControllerMP) mc.playerController)
                                .setBlockHitDelay(Math.min(((IAccessorPlayerControllerMP) mc.playerController).getBlockHitDelay(), this.delay.getValue() + 1));
                    }
                    if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
                        float curBlockDamageMP = ((IAccessorPlayerControllerMP) mc.playerController).getCurBlockDamageMP();
                        float damage = 0.3F * (this.speed.getValue().floatValue() / 100.0F);
                        if (this.mode.getValue() == 1 && curBlockDamageMP <= 0.0F) {
                            return;
                        }
                        if (curBlockDamageMP < damage) {
                            ((IAccessorPlayerControllerMP) mc.playerController).setCurBlockDamageMP(damage);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d%%", this.speed.getValue())};
    }
}
