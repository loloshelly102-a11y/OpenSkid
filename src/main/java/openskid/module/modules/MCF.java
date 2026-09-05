package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.events.KeyEvent;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class MCF extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty keyCode = new IntProperty("key-code", -98, -100, 255);

    public MCF() {
        super("MCF", false, true, "Adds or removes friends with a middle click.");
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        int trigger = this.getKey() != 0 ? this.getKey() : this.keyCode.getValue();
        if (this.isEnabled() && event.getKey() == trigger) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
                String hitName = mc.objectMouseOver.entityHit.getName();
                if (!OpenSkid.friendManager.isFriend(hitName)) {
                    OpenSkid.friendManager.add(hitName);
                    ChatUtil.sendFormatted(String.format("%sAdded &o%s&r to your friend list&r", OpenSkid.clientName, hitName));
                } else {
                    OpenSkid.friendManager.remove(hitName);
                    ChatUtil.sendFormatted(String.format("%sRemoved &o%s&r from your friend list&r", OpenSkid.clientName, hitName));
                }
            }
        }
    }
}
