package openskid.ui.impl.clickgui.normal.component;

import lombok.Getter;
import openskid.OpenSkid;
import openskid.module.Module;
import openskid.property.Property;
import openskid.property.properties.*;
import openskid.ui.impl.clickgui.normal.MaterialTheme;
import openskid.util.AnimationUtil;
import openskid.util.RenderUtil;
import openskid.util.font.FontManager;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ModuleEntry extends Component {
    @Getter
    private final Module module;
    private final List<Component> propertiesComponents;
    private boolean expanded;
    private float hoverOpacity = 0f;
    private float currentSettingsHeight = 0f;
    private int currentColor;
    private long headerHoverStart = 0L;

    public ModuleEntry(Module module, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.module = module;
        this.expanded = false;
        this.propertiesComponents = new ArrayList<>();
        this.currentColor = MaterialTheme.getRGB(MaterialTheme.TEXT_COLOR);
        initializePropertiesComponents();
    }

    private void initializePropertiesComponents() {
        int currentY = y + height;

        KeybindComponent keybindComp = new KeybindComponent(module, x, currentY, width, 20);
        propertiesComponents.add(keybindComp);

        if (OpenSkid.propertyManager != null) {
            List<Property<?>> properties = OpenSkid.propertyManager.properties.get(module);
            if (properties != null) {
                for (Property<?> property : properties) {
                    Component comp = null;
                    int compHeight = 20;

                    if (property instanceof BooleanProperty) {
                        comp = new Switch((BooleanProperty) property, x, currentY, width, compHeight);
                    } else if (property instanceof IntProperty || property instanceof FloatProperty || property instanceof PercentProperty || property instanceof LongProperty) {
                        comp = new Slider(property, x, currentY, width, compHeight);
                    } else if (property instanceof ModeProperty) {
                        comp = new Dropdown((ModeProperty) property, x, currentY, width, compHeight);
                    } else if (property instanceof ColorProperty) {
                        comp = new ColorPicker((ColorProperty) property, x, currentY, width, 60);
                    } else if (property instanceof TextProperty) {
                        comp = new TextField((TextProperty) property, x, currentY, width, compHeight);
                    } else if (property instanceof DragProperty) {
                        comp = new DragLabel((DragProperty) property, x, currentY, width, compHeight);
                    }

                    if (comp != null) {
                        propertiesComponents.add(comp);
                    }
                }
            }
        }
    }

    private boolean isComponentVisible(Component comp) {
        if (comp instanceof Switch) return ((Switch) comp).getProperty().isVisible();
        if (comp instanceof Slider) return ((Slider) comp).getProperty().isVisible();
        if (comp instanceof Dropdown) return ((Dropdown) comp).getProperty().isVisible();
        if (comp instanceof ColorPicker) return ((ColorPicker) comp).getProperty().isVisible();
        if (comp instanceof TextField) return ((TextField) comp).getProperty().isVisible();
        if (comp instanceof DragLabel) return ((DragLabel) comp).getProperty().isVisible();
        return true;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float animationProgress, boolean isLast, int scrollOffset, float deltaTime) {
        int scrolledY = y - scrollOffset;
        boolean hovered = isMouseOverHeader(mouseX, mouseY, scrollOffset);
        int alpha = (int) (255 * animationProgress);

        if (hovered) {
            if (headerHoverStart == 0L) headerHoverStart = System.currentTimeMillis();
        } else {
            headerHoverStart = 0L;
        }

        float targetHover = hovered ? 1.0f : 0.0f;
        this.hoverOpacity = AnimationUtil.animateSmooth(targetHover, this.hoverOpacity, 10.0f, deltaTime);
        if (hoverOpacity > 0.01f) {
            int hoverColor = MaterialTheme.getRGBWithAlpha(MaterialTheme.SURFACE_CONTAINER_HIGH, (int) (alpha * hoverOpacity));
            RenderUtil.drawRoundedRect(x + 2, scrolledY, width - 4, height, 4, hoverColor, true, true, true, true);
        }

        openskid.module.modules.ClickGUIModule clickGUI = (openskid.module.modules.ClickGUIModule) OpenSkid.moduleManager.getModule("ClickGUI");
        java.awt.Color accent = (clickGUI != null) ? clickGUI.getAccentColor() : MaterialTheme.PRIMARY_COLOR;
        int targetColor = module.isEnabled() ? MaterialTheme.getRGB(accent) : MaterialTheme.getRGB(MaterialTheme.TEXT_COLOR);
        this.currentColor = AnimationUtil.interpolateColor(this.currentColor, targetColor, 10.0f * deltaTime);
        int finalTextColor = (this.currentColor & 0x00FFFFFF) | (alpha << 24);

        if (alpha > 5) {
            if (FontManager.productSans16 != null) {
                float textY = (float) (scrolledY + (height - FontManager.productSans16.getHeight()) / 2f + 1);
                FontManager.productSans16.drawString(module.getName(), x + 10, textY, finalTextColor);
                if (!propertiesComponents.isEmpty()) {
                    String icon = expanded ? "..." : ":";
                    float iconW = (float) FontManager.productSans16.getStringWidth(icon);
                    FontManager.productSans16.drawString(icon, x + width - iconW - 8, textY, MaterialTheme.getRGBWithAlpha(MaterialTheme.TEXT_COLOR_SECONDARY, alpha));
                }
            } else {
                mc.fontRendererObj.drawStringWithShadow(module.getName(), x + 8, scrolledY + 6, finalTextColor);
            }
            if (module.isEnabled()) {
                RenderUtil.drawRoundedRect(x + 4, scrolledY + height / 2f - 1.5f, 3, 3, 1.5f, finalTextColor, true, true, true, true);
            }
        }

        float visibleHeightSum = 0;
        if (expanded) {
            for (Component comp : propertiesComponents) {
                if (isComponentVisible(comp)) {
                    visibleHeightSum += comp.getHeight();
                }
            }
        }

        this.currentSettingsHeight = AnimationUtil.animateSmooth(visibleHeightSum, this.currentSettingsHeight, 12.0f, deltaTime);

        if (currentSettingsHeight > 1.0f) {
            float bgLeft = x + 2;
            float bgTop = scrolledY + height;
            float bgRight = x + width - 2;
            float bgBottom = bgTop + currentSettingsHeight;

            RenderUtil.drawRect(bgLeft, bgTop, bgRight, bgBottom, new Color(10, 10, 12, (int) (100 * (alpha / 255f))).getRGB());

            RenderUtil.scissor(x, scrolledY + height, width, currentSettingsHeight);

            float dynamicY = y + height;

            for (int i = 0; i < propertiesComponents.size(); i++) {
                Component comp = propertiesComponents.get(i);

                if (!isComponentVisible(comp)) continue;

                float relativeY = dynamicY - (y + height);
                if (relativeY < currentSettingsHeight) {
                    comp.setX(x + 4);
                    comp.setY((int) dynamicY);
                    comp.setWidth(width - 8);

                    comp.render(mouseX, mouseY, partialTicks, animationProgress, isLast && (i == propertiesComponents.size() - 1), scrollOffset, deltaTime);
                }

                dynamicY += comp.getHeight();
            }
            RenderUtil.releaseScissor();
        }
    }

    public void renderTooltip(int mouseX, int mouseY, int scrollOffset, float animationProgress) {
        if (!isMouseOverHeader(mouseX, mouseY, scrollOffset)) return;
        if (headerHoverStart == 0L) return;
        if (System.currentTimeMillis() - headerHoverStart < 400) return;
        String desc = module.getDescription();
        if (desc == null || desc.trim().isEmpty()) return;
        int alpha = (int) (255 * animationProgress);
        if (alpha < 5) return;

        double textW;
        double textH;
        if (FontManager.productSans16 != null) {
            textW = FontManager.productSans16.getStringWidth(desc);
            textH = FontManager.productSans16.getHeight();
        } else {
            textW = mc.fontRendererObj.getStringWidth(desc);
            textH = 8;
        }
        float pad = 6.0f;
        float bw = (float) textW + pad * 2;
        float bh = (float) textH + pad * 2;
        float bx = mouseX + 12;
        float by = mouseY + 12;
        ScaledResolution sr = new ScaledResolution(mc);
        if (bx + bw > sr.getScaledWidth() - 2) bx = mouseX - bw - 8;
        if (by + bh > sr.getScaledHeight() - 2) by = mouseY - bh - 8;
        if (bx < 2) bx = 2;
        if (by < 2) by = 2;

        int bgAlpha = (int) (230 * animationProgress);
        int bg = new Color(15, 15, 18, Math.max(0, Math.min(255, bgAlpha))).getRGB();
        RenderUtil.drawRoundedRect(bx, by, bw, bh, 4.0f, bg, true, true, true, true);
        int outline = MaterialTheme.getRGBWithAlpha(MaterialTheme.OUTLINE_COLOR, (int) (100 * animationProgress));
        RenderUtil.drawRoundedRectOutline(bx, by, bw, bh, 4.0f, 1.0f, outline, true, true, true, true);

        int textColor = MaterialTheme.getRGBWithAlpha(MaterialTheme.TEXT_COLOR, alpha);
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(desc, bx + pad, by + pad, textColor);
        } else {
            mc.fontRendererObj.drawStringWithShadow(desc, bx + pad, by + pad, textColor);
        }
    }

    public float getCurrentHeight() {
        float heightSum = height;
        if (expanded || currentSettingsHeight > 0) {
            heightSum += currentSettingsHeight;
        }
        return heightSum;
    }

    private boolean isMouseOverHeader(int mouseX, int mouseY, int scrollOffset) {
        int actualY = this.y - scrollOffset;
        return mouseX >= x && mouseX <= x + width && mouseY >= actualY && mouseY <= actualY + height;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, int scrollOffset) {
        if (isMouseOverHeader(mouseX, mouseY, scrollOffset)) {
            if (mouseButton == 0) {
                module.toggle();
                return true;
            } else if (mouseButton == 1) {
                if (!propertiesComponents.isEmpty()) {
                    expanded = !expanded;
                }
                return true;
            }
        }

        if (expanded) {
            if (currentSettingsHeight < 10) return false;

            for (Component comp : propertiesComponents) {
                if (!isComponentVisible(comp)) continue;

                if (comp.mouseClicked(mouseX, mouseY, mouseButton, scrollOffset)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isBinding() {
        if (expanded) {
            for (Component comp : propertiesComponents) {
                if (!isComponentVisible(comp)) continue;
                if (comp instanceof KeybindComponent && ((KeybindComponent) comp).isBinding()) return true;
            }
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (expanded) {
            for (Component comp : propertiesComponents) {
                if (isComponentVisible(comp)) {
                    comp.keyTyped(typedChar, keyCode);
                }
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton, int scrollOffset) {
        if (expanded) {
            for (Component comp : propertiesComponents) {
                if (isComponentVisible(comp)) {
                    comp.mouseReleased(mouseX, mouseY, mouseButton, scrollOffset);
                }
            }
        }
    }

    private static class DragLabel extends Component {
        private final DragProperty property;

        DragLabel(DragProperty property, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.property = property;
        }

        DragProperty getProperty() {
            return property;
        }

        @Override
        public void render(int mouseX, int mouseY, float partialTicks, float animationProgress, boolean isLast, int scrollOffset, float deltaTime) {
            if (!property.isVisible()) {
                return;
            }
            int scrolledY = y - scrollOffset;
            int alpha = (int) (255 * animationProgress);
            if (alpha < 5) return;
            String text = property.getName() + ": " + Math.round(property.position.x) + ", " + Math.round(property.position.y);
            int textColor = MaterialTheme.getRGBWithAlpha(MaterialTheme.TEXT_COLOR, alpha);
            if (FontManager.productSans16 != null) {
                FontManager.productSans16.drawString(text, x + 2, scrolledY + 6, textColor);
            } else {
                mc.fontRendererObj.drawStringWithShadow(text, x + 2, scrolledY + 6, textColor);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
        }
    }
}
