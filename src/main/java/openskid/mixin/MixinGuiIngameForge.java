package openskid.mixin;

import openskid.OpenSkid;
import openskid.event.EventManager;
import openskid.events.Render2DEvent;
import openskid.module.modules.NickHider;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngameForge.class}, priority = 9999)
public abstract class MixinGuiIngameForge {
    @Inject(
            method = {"renderGameOverlay"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/GuiIngameForge;renderTitle(IIF)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )}
    )
    private void renderGameOverlay(float float1, CallbackInfo callbackInfo) {
        EventManager.call(new Render2DEvent(float1));
    }

    @Inject(method = "renderExperience", at = @At("HEAD"), cancellable = true, remap = false)
    private void openskid$cancelExperienceBar(int width, int height, CallbackInfo callbackInfo) {
        if (OpenSkid.moduleManager != null) {
            openskid.module.modules.Hotbar hotbar = (openskid.module.modules.Hotbar) OpenSkid.moduleManager.modules.get(openskid.module.modules.Hotbar.class);
            if (hotbar != null && hotbar.isEnabled() && hotbar.xpBar.getValue()) {
                callbackInfo.cancel();
            }
        }
    }

    @Inject(method = "renderHealth", at = @At("HEAD"), remap = false)
    private void openskid$adjustHealthBar(int width, int height, CallbackInfo callbackInfo) {
        if (OpenSkid.moduleManager != null) {
            openskid.module.modules.Hotbar hotbar = (openskid.module.modules.Hotbar) OpenSkid.moduleManager.modules.get(openskid.module.modules.Hotbar.class);
            if (hotbar != null && hotbar.isEnabled() && hotbar.healthYOffset.getValue() != 0) {
                GuiIngameForge.left_height += hotbar.healthYOffset.getValue();
            }
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), remap = false)
    private void openskid$adjustFoodBar(int width, int height, CallbackInfo callbackInfo) {
        if (OpenSkid.moduleManager != null) {
            openskid.module.modules.Hotbar hotbar = (openskid.module.modules.Hotbar) OpenSkid.moduleManager.modules.get(openskid.module.modules.Hotbar.class);
            if (hotbar != null && hotbar.isEnabled() && hotbar.healthYOffset.getValue() != 0) {
                GuiIngameForge.right_height += hotbar.healthYOffset.getValue();
            }
        }
    }

    @Redirect(
            method = {"renderExperience"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;experience:F"
            )
    )
    private float renderExperience(EntityPlayerSP entityPlayerSP) {
        if (OpenSkid.moduleManager == null) {
            return entityPlayerSP.experience;
        } else {
            NickHider event = (NickHider) OpenSkid.moduleManager.modules.get(NickHider.class);
            return event.isEnabled() && event.level.getValue() ? 0.0F : entityPlayerSP.experience;
        }
    }

    @Redirect(
            method = {"renderExperience"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;experienceLevel:I"
            )
    )
    private int renderExperienceLevel(EntityPlayerSP entityPlayerSP) {
        if (OpenSkid.moduleManager == null) {
            return entityPlayerSP.experienceLevel;
        } else {
            NickHider event = (NickHider) OpenSkid.moduleManager.modules.get(NickHider.class);
            return event.isEnabled() && event.level.getValue() ? 0 : entityPlayerSP.experienceLevel;
        }
    }
}
