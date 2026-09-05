package openskid.module.modules;

// Adapted from MiauMinus misc/Panic (disable-all plus self-off). Allowlist and GUI hide added.
import java.util.ArrayList;
import java.util.List;
import openskid.OpenSkid;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.TextProperty;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;

public class Panic extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty hideGui = new BooleanProperty("hide-gui", true);
    public final TextProperty keep = new TextProperty("keep", "");

    public Panic() {
        super("Panic", false, false, "Disables all active modules at once for a quick panic.");
    }

    @Override
    public String[] getSuffix() {
        return new String[0];
    }

    @Override
    public void onEnabled() {
        List<Module> toDisable = new ArrayList<>();
        for (Module module : OpenSkid.moduleManager.allModules()) {
            if (module != this && module.isEnabled() && !isKept(module.getName())) {
                toDisable.add(module);
            }
        }
        for (Module module : toDisable) {
            module.setEnabled(false);
        }
        if (this.hideGui.getValue() && mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        ChatUtil.sendFormatted(String.format("%s%s: &cdisabled %d modules&r", OpenSkid.clientName, this.getName(), toDisable.size()));
        this.setEnabled(false);
    }

    private boolean isKept(String name) {
        for (String entry : this.keep.getValue().split(",")) {
            if (!entry.trim().isEmpty() && entry.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
