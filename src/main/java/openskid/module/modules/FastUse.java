package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.TickEvent;
import openskid.mixin.IAccessorEntityPlayer;
import openskid.mixin.IAccessorMinecraft;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;

public class FastUse extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Instant", "NCP", "Matrix"});
    public final IntProperty packets = new IntProperty("packets", 20, 3, 35);

    public FastUse() {
        super("FastUse", false, false, "Speeds up eating and blocking by finishing use faster.");
    }

    private boolean using() {
        return mc.thePlayer.isEating() || mc.thePlayer.isUsingItem();
    }

    private void resetTimer() {
        try {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        } catch (Exception ignored) {
        }
    }

    private void finishUse() {
        ItemStack held = mc.thePlayer.getCurrentEquippedItem();
        if (held != null) {
            ((IAccessorEntityPlayer) mc.thePlayer).setItemInUseCount(held.getMaxItemUseDuration() - 1);
        }
        mc.playerController.onStoppedUsingItem(mc.thePlayer);
    }

    private void spamGround(int count) {
        boolean ground = mc.thePlayer.onGround;
        for (int i = 0; i < count; i++) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer(ground));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!using()) {
            if (this.mode.getValue() == 2) {
                resetTimer();
            }
            return;
        }
        switch (this.mode.getValue()) {
            case 0:
                spamGround(this.packets.getValue());
                finishUse();
                break;
            case 1:
                if (mc.thePlayer.getItemInUseDuration() > 14) {
                    spamGround(this.packets.getValue());
                    finishUse();
                }
                break;
            default:
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.18F;
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(mc.thePlayer.onGround));
                break;
        }
    }

    @Override
    public void onDisabled() {
        resetTimer();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
