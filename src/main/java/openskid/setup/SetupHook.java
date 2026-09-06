package openskid.setup;

import net.minecraft.client.Minecraft;
import openskid.ui.impl.mainmenu.OpenSkidMainMenu;

public final class SetupHook {
    private SetupHook() {
    }

    public static void onMainMenu(OpenSkidMainMenu menu) {
        try {
            if (!SetupState.isDone()) {
                menu.addSetupButton();
            }
        } catch (Exception ignored) {
        }
    }

    public static void open() {
        try {
            Minecraft.getMinecraft().displayGuiScreen(new SetupScreen());
        } catch (Exception ignored) {
        }
    }
}
