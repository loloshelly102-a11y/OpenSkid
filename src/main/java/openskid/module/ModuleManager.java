package openskid.module;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.KeyEvent;
import openskid.events.TickEvent;
import openskid.module.modules.ClickGUIModule;
import openskid.module.modules.HUD;
import openskid.util.ChatUtil;
import openskid.util.SoundPlayer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;

public class ModuleManager {
    private boolean sound = false;
    private boolean soundEnabled = true;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();
    // Modules that aren't backed by a fixed class (one instance per loaded script), keyed by
    // unique name instead. Kept separate from `modules` because that map is Class-keyed and
    // can only ever hold one instance per runtime type.
    public final LinkedHashMap<String, Module> dynamicModules = new LinkedHashMap<>();

    public Module getModule(String string) {
        Module module = this.modules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
        if (module != null) {
            return module;
        }
        return this.dynamicModules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz){
        return this.modules.get(clazz);
    }

    public Collection<Module> allModules() {
        ArrayList<Module> all = new ArrayList<>(this.modules.values());
        all.addAll(this.dynamicModules.values());
        return all;
    }

    public void registerDynamicModule(Module module) {
        if (this.dynamicModules.containsKey(module.getName())) {
            this.unregisterDynamicModule(module.getName());
        }
        this.dynamicModules.put(module.getName(), module);
        // Only claim the `.<name>` chat command if it isn't already a built-in module's -
        // a script sharing a real module's name shouldn't be able to shadow it.
        if (this.modules.values().stream().noneMatch(m -> m.getName().equalsIgnoreCase(module.getName()))) {
            setModuleCommandName(module.getName(), true);
        }
    }

    public void unregisterDynamicModule(String name) {
        Module module = this.dynamicModules.remove(name);
        if (module != null && module.isEnabled()) {
            module.setEnabled(false);
        }
        OpenSkid.propertyManager.properties.remove(module);
        setModuleCommandName(name, false);
    }

    private void setModuleCommandName(String name, boolean add) {
        if (OpenSkid.commandManager == null) {
            return;
        }
        for (openskid.command.Command command : OpenSkid.commandManager.commands) {
            if (command instanceof openskid.command.commands.ModuleCommand) {
                if (add) {
                    if (!command.names.contains(name)) {
                        command.names.add(name);
                    }
                } else {
                    command.names.remove(name);
                }
                return;
            }
        }
    }

    public void playSound(boolean enabled) {
        this.sound = true;
        this.soundEnabled = enabled;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.allModules()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if(module instanceof ClickGUIModule){
                shouldNotify = false;
            }
            if (shouldNotify) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", OpenSkid.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.sound) {
                this.sound = false;
                try {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc == null || mc.theWorld == null) {
                        return;
                    }
                    HUD hud = (HUD) this.modules.get(HUD.class);
                    openskid.module.modules.Notifications notifications =
                            (openskid.module.modules.Notifications) this.modules.get(openskid.module.modules.Notifications.class);
                    if (hud == null || !hud.toggleSound.getValue() || notifications == null) {
                        return;
                    }
                    String set = notifications.toggleSet.getModeString().toLowerCase(Locale.ROOT);
                    int volume = notifications.toggleVolume.getValue();
                    String path = "/assets/openskid/sounds/toggle-" + set + "-" + (this.soundEnabled ? "enable" : "disable") + ".wav";
                    SoundPlayer.play(path, volume);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
