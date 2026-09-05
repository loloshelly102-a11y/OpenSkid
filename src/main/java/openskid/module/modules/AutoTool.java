package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.TickEvent;
import openskid.mixin.IAccessorPlayerControllerMP;
import openskid.module.Module;
import openskid.util.ItemUtil;
import openskid.util.KeyBindUtil;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;
    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
    public final BooleanProperty hoverCheck = new BooleanProperty("hover-check", false);
    public final BooleanProperty disableOnRight = new BooleanProperty("disable-on-right", false);
    public final ModeProperty swapMode = new ModeProperty("swap-mode", 0, new String[]{"CLIENT", "SPOOF"});

    public AutoTool() {
        super("AutoTool", false, false, "Automatically switches to the best tool for mining blocks.");
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) OpenSkid.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.gameSettings.keyBindAttack.isKeyDown()
                    && !mc.thePlayer.isUsingItem()
                    && !isKillAura()) {
                if (this.hoverCheck.getValue() && mc.objectMouseOver.getBlockPos() == null) {
                    this.tickDelayCounter++;
                    return;
                }
                if (this.disableOnRight.getValue() && mc.gameSettings.keyBindUseItem.isKeyDown()) {
                    if (this.switchBack.getValue() && this.previousSlot != -1) {
                        mc.thePlayer.inventory.currentItem = this.previousSlot;
                    }
                    this.currentToolSlot = -1;
                    this.previousSlot = -1;
                    this.tickDelayCounter = 0;
                    return;
                }
                if (this.tickDelayCounter >= this.switchDelay.getValue()
                        && (!(Boolean) this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                    int slot = ItemUtil.findInventorySlot(
                            mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                    );
                    if (mc.thePlayer.inventory.currentItem != slot) {
                        if (this.previousSlot == -1) {
                            this.previousSlot = mc.thePlayer.inventory.currentItem;
                        }
                        mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                        if (this.swapMode.getValue() == 1) {
                            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                        }
                    }
                }
                this.tickDelayCounter++;
            } else {
                if (this.switchBack.getValue() && this.previousSlot != -1) {
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                }
                this.currentToolSlot = -1;
                this.previousSlot = -1;
                this.tickDelayCounter = 0;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
    }
}
