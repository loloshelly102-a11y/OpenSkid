package openskid.module.modules;

import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.ui.ClickGui;
import openskid.ui.impl.clickgui.normal.ClickGuiScreen;
import openskid.ui.impl.clickgui.modern.ModernClickGui;
import openskid.ui.impl.clickgui.legacy.LegacyClickGui;
import openskid.ui.impl.clickgui.nova.NovaClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class ClickGUIModule extends Module {
    private boolean switchingGuiStyle;

    // ── Color palette (same as TargetESP) ────────────────────────────────────
    public static final int[] COLORS = {
            0xFF4FC3F7, // Sky Blue
            0xFF81C784, // Green
            0xFFFF8A65, // Orange
            0xFFBA68C8, // Purple
            0xFFFFD54F, // Yellow
            0xFFFF6B6B, // Red
            0xFF4DB6AC, // Teal
            0xFFFFFFFF, // White
    };
    public static final String[] COLOR_NAMES = {
            "Sky Blue", "Green", "Orange", "Purple", "Yellow", "Red", "Teal", "White"
    };

    public ModeProperty accentColor = new ModeProperty("Color", 0, COLOR_NAMES);
    public ModeProperty style = new ModeProperty("Style", 4, new String[]{"Normal", "Legacy", "Legacy+", "Nova", "Modern", "Cards"});
    public BooleanProperty saveGuiState = new BooleanProperty("Save GUI State", true);
    public BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public BooleanProperty glass = new BooleanProperty("Glass", true);

    public IntProperty windowWidth = new IntProperty("Window Width", 600, 300, 1200);
    public IntProperty windowHeight = new IntProperty("Window Height", 400, 200, 800);
    public FloatProperty cornerRadius = new FloatProperty("Corner Radius", 8.0f, 0.0f, 20.0f);
    public IntProperty fpsLimit = new IntProperty("fps-limit", 60, 10, 240);
    public BooleanProperty lowercaseToggle = new BooleanProperty("lowercase-toggle", false);
    public BooleanProperty teamTheme = new BooleanProperty("team-theme", false);

    public Color getAccentColor() {
        int idx = accentColor.getValue();
        if (idx < 0 || idx >= COLORS.length) idx = 0;
        return new Color(COLORS[idx], true);
    }

    public ClickGUIModule() {
        super("ClickGUI", false, false, "Opens the configurable click GUI menu for modules.");
        setKey(Keyboard.KEY_RSHIFT);
    }

    public void openSelectedGui() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = getSelectedGui();
        this.switchingGuiStyle = mc.currentScreen instanceof ClickGui
                || mc.currentScreen instanceof ClickGuiScreen
                || mc.currentScreen instanceof LegacyClickGui
                || mc.currentScreen instanceof NovaClickGui
                || mc.currentScreen instanceof openskid.ui.impl.clickgui.modern.ModernClickGui;
        try {
            mc.displayGuiScreen(screen);
        } finally {
            this.switchingGuiStyle = false;
        }
    }

    public boolean isSwitchingGuiStyle() {
        return switchingGuiStyle;
    }

    public GuiScreen getSelectedGui() {
        if (style.getValue() == 1) {
            return ClickGui.getInstance();
        }
        if (style.getValue() == 2) {
            LegacyClickGui raven = LegacyClickGui.getInstance();
            return raven != null ? raven : new LegacyClickGui();
        }
        if (style.getValue() == 3) {
            NovaClickGui nova = NovaClickGui.getInstance();
            return nova != null ? nova : new NovaClickGui();
        }
        if (style.getValue() == 4) {
            openskid.ui.impl.clickgui.modern.ModernClickGui modern = openskid.ui.impl.clickgui.modern.ModernClickGui.getInstance();
            return modern != null ? modern : new openskid.ui.impl.clickgui.modern.ModernClickGui();
        }
        if (style.getValue() == 5) {
            return openskid.ui.impl.clickgui.card.CardClickGUI.getInstance();
        }
        return ClickGuiScreen.getInstance();
    }

    @Override
    public void verifyValue(String name) {
        if ("Style".equalsIgnoreCase(name)) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof ClickGuiScreen
                    || mc.currentScreen instanceof LegacyClickGui || mc.currentScreen instanceof NovaClickGui
                    || mc.currentScreen instanceof ModernClickGui || mc.currentScreen instanceof openskid.ui.impl.clickgui.card.CardClickGUI) {
                openSelectedGui();
            }
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (Minecraft.getMinecraft().theWorld == null) {
            this.setEnabled(false);
            return;
        }
        openSelectedGui();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        Minecraft.getMinecraft().displayGuiScreen(null);
        if (Minecraft.getMinecraft().currentScreen == null) {
            Minecraft.getMinecraft().setIngameFocus();
        }
    }
}
