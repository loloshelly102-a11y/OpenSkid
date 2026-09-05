package openskid.module.modules;

import openskid.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

public class FreeLook extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private float cameraYaw;
    private float cameraPitch;
    private boolean active;
    private int prevThirdPersonView;

    public FreeLook() {
        super("FreeLook", false, false, "Lets you look around freely without turning your player.");
    }

    @Override
    public void onEnabled() {
        this.prevThirdPersonView = mc.gameSettings.thirdPersonView;
        EntityPlayerSP player = mc.thePlayer;
        if (player != null) {
            this.cameraYaw = player.rotationYaw;
            this.cameraPitch = player.rotationPitch;
            this.active = true;
            if (mc.gameSettings.thirdPersonView == 0) {
                mc.gameSettings.thirdPersonView = 1;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.active = false;
        mc.gameSettings.thirdPersonView = this.prevThirdPersonView;
    }

    public boolean isFreeLooking() {
        return this.isEnabled() && this.active && mc.thePlayer != null;
    }

    public void updateCamera(float yawDelta, float pitchDelta) {
        this.cameraYaw += yawDelta * 0.15F;
        this.cameraPitch = MathHelper.clamp_float(this.cameraPitch - pitchDelta * 0.15F, -90.0F, 90.0F);
    }

    public float getCameraYaw() {
        return this.cameraYaw;
    }

    public float getCameraPitch() {
        return this.cameraPitch;
    }
}