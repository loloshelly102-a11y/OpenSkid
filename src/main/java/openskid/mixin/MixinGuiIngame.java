package openskid.mixin;

import openskid.OpenSkid;
import openskid.module.modules.AutoBlockIn;
import openskid.module.modules.RenderFixes;
import openskid.module.modules.Scaffold;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngame.class}, priority = 9999)
public abstract class MixinGuiIngame {
    @Inject(method = {"renderScoreboard"}, at = @At("HEAD"), cancellable = true)
    private void openskid$renderModernScoreboard(ScoreObjective objective, ScaledResolution scaledRes, CallbackInfo callbackInfo) {
        if (RenderFixes.renderScoreboard(objective, scaledRes)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void openskid$renderTooltip(ScaledResolution sr, float partialTicks, CallbackInfo callbackInfo) {
        if (OpenSkid.moduleManager != null) {
            openskid.module.modules.Hotbar hotbar = (openskid.module.modules.Hotbar) OpenSkid.moduleManager.modules.get(openskid.module.modules.Hotbar.class);
            if (hotbar != null && hotbar.isEnabled()) {
                callbackInfo.cancel();
            }
        }
    }

    @Redirect(
            method = {"updateTick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack updateTick(InventoryPlayer inventoryPlayer) {
        Scaffold scaffold = (Scaffold) OpenSkid.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
            int slot = scaffold.getSlot();
            if (slot >= 0) {
                return inventoryPlayer.getStackInSlot(slot);
            }
        }
        AutoBlockIn autoBlockIn = (AutoBlockIn) OpenSkid.moduleManager.modules.get(AutoBlockIn.class);
        if(autoBlockIn.itemSpoof.getValue() && autoBlockIn.isEnabled()){
            int slot = autoBlockIn.getSlot();
            if (slot >= 0) {
                return inventoryPlayer.getStackInSlot(slot);
            }
        }
        return inventoryPlayer.getCurrentItem();
    }
}
