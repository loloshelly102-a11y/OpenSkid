package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.event.types.Priority;
import openskid.events.TickEvent;
import openskid.events.UpdateEvent;
import openskid.mixin.IAccessorEntityLivingBase;
import openskid.module.Module;
import openskid.util.KeyBindUtil;
import openskid.util.MoveUtil;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

public class Sprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean wasSprinting = false;
    public final BooleanProperty foxFix = new BooleanProperty("fov-fix", true);
    public final ModeProperty sprintMode = new ModeProperty("mode", 0, new String[]{"Legit", "Omni"});
    public final ModeProperty bypassMode = new ModeProperty("bypass", 1, new String[]{"None", "Legit"}, () -> sprintMode.getValue() == 1);
    public static boolean omniActive = false;

    public Sprint() {
        super("Sprint", true, true, "Keeps you sprinting automatically in all directions.");
    }

    public boolean shouldApplyFovFix(IAttributeInstance attribute) {
        if (!this.foxFix.getValue()) {
            return false;
        } else {
            AttributeModifier attributeModifier = ((IAccessorEntityLivingBase) mc.thePlayer).getSprintingSpeedBoostModifier();
            return attribute.getModifier(attributeModifier.getID()) == null && this.wasSprinting;
        }
    }

    public boolean shouldKeepFov(boolean boolean2) {
        return this.foxFix.getValue() && !boolean2 && this.wasSprinting;
    }

    @Override
    public boolean shouldKeepSprint() {
        return this.isEnabled() && this.sprintMode.getValue() == 1;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.sprintMode.getModeString()};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    driveSprint();
                    break;
                case POST:
                    this.wasSprinting = mc.thePlayer != null && mc.thePlayer.isSprinting();
            }
        }
    }

    // Sprint owns the sprint key. Single drive path.
    private void driveSprint() {
        if (mc.thePlayer == null) {
            omniActive = false;
            return;
        }
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        if (this.sprintMode.getValue() == 1 && MoveUtil.isForwardPressed()) {
            mc.thePlayer.setSprinting(true);
            omniActive = true;
        } else {
            omniActive = false;
        }
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || !MoveUtil.isForwardPressed()) {
            omniActive = false;
        }
    }

    @Override
    public void onDisabled() {
        this.wasSprinting = false;
        omniActive = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
    }
}
