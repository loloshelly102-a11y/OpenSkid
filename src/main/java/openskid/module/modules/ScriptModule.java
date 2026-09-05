package openskid.module.modules;

import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import openskid.module.Module;
import openskid.property.Property;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.ColorProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.ModeProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts a loaded script (a {@code keystrokesmod.module.Module} backed by a
 * {@code keystrokesmod.script.Script}) so it can be enabled, toggled, bound, and configured
 * through OpenSkid's own module/property system exactly like any built-in module - chat commands,
 * keybinds, config profiles, and every ClickGui skin all go through this instead of the script engine's own
 * clickgui.
 */
public class ScriptModule extends Module {
    private final keystrokesmod.module.Module rslModule;
    private final List<Property<?>> properties = new ArrayList<>();
    private final Map<String, Runnable> pushHandlers = new LinkedHashMap<>();

    public ScriptModule(keystrokesmod.module.Module rslModule) {
        super(rslModule.getName(), rslModule.isEnabled(), false, "Runs a custom script module through the client.");
        this.rslModule = rslModule;
        buildProperties();
    }

    public keystrokesmod.module.Module getRslModule() {
        return this.rslModule;
    }

    public List<Property<?>> getScriptProperties() {
        return this.properties;
    }

    private void buildProperties() {
        for (Setting setting : rslModule.getSettings()) {
            if (setting instanceof ButtonSetting) {
                mapButton((ButtonSetting) setting);
            } else if (setting instanceof SliderSetting) {
                mapSlider((SliderSetting) setting);
            } else if (setting instanceof ColorSetting) {
                mapColor((ColorSetting) setting);
            }
            // GroupSetting/DescriptionSetting are layout-only in the script engine's own UI and have no
            // value to mirror. KeySetting has no OpenSkid equivalent (OpenSkid binds per-module, not
            // per-setting), so it's left script-local rather than stubbed.
        }
    }

    private void mapButton(final ButtonSetting setting) {
        if (setting.isMethodButton) {
            // An action trigger, not a persistent value - OpenSkid has no property type for that.
            return;
        }
        BooleanProperty property = new BooleanProperty(setting.getName(), setting.isToggled());
        properties.add(property);
        pushHandlers.put(property.getName(), () -> setting.setEnabled(property.getValue()));
    }

    private void mapSlider(final SliderSetting setting) {
        if (setting.isString) {
            String[] options = setting.getOptions();
            int index = clampIndex((int) Math.round(setting.getInput()), options.length);
            ModeProperty property = new ModeProperty(setting.getName(), index, options);
            properties.add(property);
            pushHandlers.put(property.getName(), () -> setting.setValueWithEvent(property.getValue()));
        } else {
            FloatProperty property = new FloatProperty(
                    setting.getName(),
                    (float) setting.getInput(),
                    (float) setting.getMin(),
                    (float) setting.getMax()
            );
            properties.add(property);
            pushHandlers.put(property.getName(), () -> setting.setValueWithEvent(property.getValue()));
        }
    }

    private void mapColor(final ColorSetting setting) {
        ColorProperty property = new ColorProperty(setting.getName(), setting.getRGB());
        properties.add(property);
        pushHandlers.put(property.getName(), () -> {
            int rgb = property.getValue();
            setting.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, setting.getAlpha());
        });
    }

    private static int clampIndex(int index, int length) {
        if (length <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(length - 1, index));
    }

    /**
     * The script engine disables scripts directly (bypassing this wrapper) on every reload cycle. Called
     * afterward to pull OpenSkid's enabled flag back in sync without re-triggering onDisabled().
     */
    public void syncEnabledState() {
        this.enabled = rslModule.isEnabled();
    }

    @Override
    public void verifyValue(String name) {
        Runnable handler = pushHandlers.get(name);
        if (handler != null) {
            handler.run();
        }
    }

    @Override
    public void onEnabled() {
        rslModule.enable();
    }

    @Override
    public void onDisabled() {
        rslModule.disable();
    }
}
