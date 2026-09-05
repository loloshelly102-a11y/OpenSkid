package openskid.ui.impl.clickgui.legacy.components;

import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import openskid.OpenSkid;
import openskid.module.Module;
import openskid.property.Property;
import openskid.property.properties.*;
import openskid.ui.impl.clickgui.legacy.Component;
import openskid.ui.impl.clickgui.legacy.dataset.impl.FloatSlider;
import openskid.ui.impl.clickgui.legacy.dataset.impl.IntSlider;
import openskid.ui.impl.clickgui.legacy.dataset.impl.PercentageSlider;
import openskid.util.RenderUtil;
import openskid.util.Timer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class ModuleComponent implements Component {
    public final ArrayList<Component> settings;
    private final int enabledColor = new Color(24, 154, 255).getRGB();
    private final int disabledColor = new Color(192, 192, 192).getRGB();
    private final int originalHoverAlpha = 120;
    private final int hoverColor = (new Color(0, 0, 0, originalHoverAlpha)).getRGB();
    public Module mod;
    public CategoryComponent category;
    public int yPos;
    public boolean isOpened;
    private boolean hovering;
    private Timer hoverTimer;
    private boolean hoverStarted;
    private long headerHoverStart = 0L;
    private Timer smoothTimer;
    private int smoothingY = 16;
    private int targetHeight = 16;
    private boolean isAnimatingHeight = false;

    public ModuleComponent(Module mod, CategoryComponent category, int yPos) {
        this.mod = mod;
        this.category = category;
        this.yPos = yPos;
        this.settings = new ArrayList<>();
        this.isOpened = false;
        int y = yPos + 12;

        if (!OpenSkid.propertyManager.properties.get(mod).isEmpty()) {
            for (Property<?> baseProperty : OpenSkid.propertyManager.properties.get(mod)) {
                if (baseProperty instanceof BooleanProperty) {
                    BooleanProperty property = (BooleanProperty) baseProperty;
                    CheckBoxComponent c = new CheckBoxComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof FloatProperty) {
                    FloatProperty property = (FloatProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new FloatSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof IntProperty) {
                    IntProperty property = (IntProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new IntSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof PercentProperty) {
                    PercentProperty property = (PercentProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new PercentageSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ModeProperty) {
                    ModeProperty property = (ModeProperty) baseProperty;
                    ModeComponent c = new ModeComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ColorProperty) {
                    ColorProperty property = (ColorProperty) baseProperty;
                    ColorSliderComponent c = new ColorSliderComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof TextProperty) {
                    TextProperty property = (TextProperty) baseProperty;
                    TextComponent c = new TextComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof LongProperty) {
                    final LongProperty property = (LongProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new openskid.ui.impl.clickgui.legacy.dataset.Slider() {
                        @Override
                        public double getInput() {
                            return property.getValue();
                        }

                        @Override
                        public double getMin() {
                            return property.getMinimum();
                        }

                        @Override
                        public double getMax() {
                            return property.getMaximum();
                        }

                        @Override
                        public void setValue(double value) {
                            property.setValue((long) Math.round(value));
                        }

                        @Override
                        public String getName() {
                            return property.getName().replace("-", " ");
                        }

                        @Override
                        public String getValueString() {
                            return property.getValue().toString();
                        }

                        @Override
                        public double getIncrement() {
                            return 1;
                        }

                        @Override
                        public boolean isVisible() {
                            return property.isVisible();
                        }
                    }, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof DragProperty) {
                    final DragProperty property = (DragProperty) baseProperty;
                    final ModuleComponent parent = this;
                    final int[] posY = new int[]{y};
                    Component c = new Component() {
                        @Override
                        public void draw(AtomicInteger offset) {
                            GL11.glPushMatrix();
                            GL11.glScaled(0.5D, 0.5D, 0.5D);
                            String text = property.getName().replace("-", " ") + ": " + Math.round(property.position.x) + ", " + Math.round(property.position.y);
                            OpenSkid.fontManagers.getFont(24).drawString(text, (float) ((parent.category.getX() + 4) * 2), (float) ((parent.category.getY() + posY[0] + 5) * 2), -1, false);
                            GL11.glPopMatrix();
                        }

                        @Override
                        public void render() {
                            draw(new AtomicInteger(0));
                        }

                        @Override
                        public void drawScreen(int x, int y) {
                        }

                        @Override
                        public void onClick(int x, int y, int mouse) {
                        }

                        @Override
                        public void setComponentStartAt(int newOffsetY) {
                            posY[0] = newOffsetY;
                        }

                        @Override
                        public int getHeight() {
                            return 12;
                        }

                        @Override
                        public void update(int mousePosX, int mousePosY) {
                        }

                        @Override
                        public void mouseDown(int x, int y, int button) {
                        }

                        @Override
                        public void mouseReleased(int x, int y, int button) {
                        }

                        @Override
                        public void keyTyped(char chatTyped, int keyCode) {
                        }

                        @Override
                        public boolean isVisible() {
                            return property.isVisible();
                        }

                        @Override
                        public void updateHeight(int y) {
                            posY[0] = y;
                        }

                        @Override
                        public void onScroll(int scroll) {
                        }

                        @Override
                        public void onGuiClosed() {
                        }
                    };
                    this.settings.add(c);
                    y += c.getHeight();
                }
            }
        }

        this.settings.add(new BindComponent(this, y));
    }

    public void updateHeight(int newY) {
        this.yPos = newY;
        int y = this.yPos + 16;
        Iterator var3 = this.settings.iterator();

        while (true) {
            while (var3.hasNext()) {
                Component co = (Component) var3.next();
                if (!isVisible(co)) {
                    continue;
                }
                co.updateHeight(y);
                y += co.getHeight();
            }

            return;
        }
    }

    public void render() {
        if (hovering || hoverTimer != null) {
            double hoverAlpha = (hovering && hoverTimer != null) ? hoverTimer.getValueFloat(0, originalHoverAlpha, 1) : (hoverTimer != null && !hovering) ? originalHoverAlpha - hoverTimer.getValueFloat(0, originalHoverAlpha, 1) : originalHoverAlpha;
            if (hoverAlpha == 0) {
                hoverTimer = null;
            }
            RenderUtil.drawRoundedRectangle(this.category.getX(), this.category.getY() + yPos, this.category.getX() + this.category.getWidth(), this.category.getY() + 16 + this.yPos, 8, mergeAlpha(hoverColor, (int) hoverAlpha));
        }
        int button_rgb = this.mod.isEnabled() ? enabledColor : disabledColor;

        if (smoothTimer != null && System.currentTimeMillis() - smoothTimer.last >= 300) {
            smoothTimer = null;
            isAnimatingHeight = false;
        }
        if (smoothTimer != null) {
            if (isAnimatingHeight) {
                // Height change animation (for mode switches)
                if (targetHeight > smoothingY) {
                    smoothingY = smoothTimer.getValueInt(smoothingY, targetHeight, 1);
                } else {
                    smoothingY = smoothTimer.getValueInt(targetHeight, smoothingY, 1);
                }
                if (smoothingY == targetHeight) {
                    smoothTimer = null;
                    isAnimatingHeight = false;
                }
            } else if (isOpened) {
                smoothingY = smoothTimer.getValueInt(16, getModuleHeight(), 1);
                if (smoothingY == getModuleHeight()) {
                    smoothTimer = null;
                }
            } else {
                smoothingY = smoothTimer.getValueInt(getModuleHeight(), 16, 1);
                if (smoothingY == 16) {
                    smoothTimer = null;
                }
            }
            this.category.updateHeight();
        }

        OpenSkid.fontManagers.getFont(20).drawString(this.mod.getName(), (float) (this.category.getX() + this.category.getWidth() / 2 - OpenSkid.fontManagers.getFont(20).getStringWidth(this.mod.getName()) / 2), (float) (this.category.getY() + this.yPos + 2), button_rgb);
        boolean scissorRequired = smoothTimer != null;
        if (scissorRequired) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtil.scissor(this.category.getX() - 2, this.category.getY() + this.yPos + 4, this.category.getWidth() + 4, smoothingY + 4);
        }

        if (this.isOpened || smoothTimer != null) {
            for (Component settingComponent : this.settings) {
                if (!isVisible(settingComponent)) {
                    continue;
                }
                settingComponent.render();
            }
        }

        if (scissorRequired) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glPopMatrix();
        }
    }

    public int getHeight() {
        if (smoothTimer != null) {
            return smoothingY;
        }
        if (!this.isOpened) {
            return 16;
        } else {
            int h = 16;
            Iterator var2 = this.settings.iterator();

            while (true) {
                while (var2.hasNext()) {
                    Component c = (Component) var2.next();
                    if (!isVisible(c)) {
                        continue;
                    }
                    h += c.getHeight();
                }

                return h;
            }
        }
    }

    public void startHeightAnimation(int fromHeight, int toHeight) {
        this.smoothingY = fromHeight;
        this.targetHeight = toHeight;
        this.isAnimatingHeight = true;
        (this.smoothTimer = new Timer(200)).start();
        this.category.updateHeight();
    }

    public void onSliderChange() {
        for (Component c : this.settings) {
            if (c instanceof SliderComponent) {
                ((SliderComponent) c).onSliderChange();
            }
        }
    }

    public int getModuleHeight() {
        int h = 16;
        Iterator var2 = this.settings.iterator();

        while (true) {
            while (var2.hasNext()) {
                Component c = (Component) var2.next();
                if (!isVisible(c)) {
                    continue;
                }
                h += c.getHeight();
            }

            return h;
        }
    }

    public void drawScreen(int x, int y) {
        for (Component c : this.settings) {
            c.drawScreen(x, y);
        }
        if (overModuleName(x, y) && this.category.opened) {
            hovering = true;
            if (headerHoverStart == 0L) headerHoverStart = System.currentTimeMillis();
            if (hoverTimer == null) {
                (hoverTimer = new Timer(75)).start();
                hoverStarted = true;
            }
        } else {
            headerHoverStart = 0L;
            if (hovering && hoverStarted) {
                (hoverTimer = new Timer(75)).start();
            }
            hoverStarted = false;
            hovering = false;
        }
    }

    public String getName() {
        return mod.getName();
    }

    public void onClick(int x, int y, int mouse) {
        if (this.overModuleName(x, y) && mouse == 0) {
            this.mod.toggle();
        }

        if (this.overModuleName(x, y) && mouse == 1) {
            this.isOpened = !this.isOpened;
            (this.smoothTimer = new Timer(200)).start();
            this.category.updateHeight();
        }

        for (Component settingComponent : this.settings) {
            settingComponent.onClick(x, y, mouse);
        }
    }

    public void mouseReleased(int x, int y, int m) {
        for (Component c : this.settings) {
            c.mouseReleased(x, y, m);
        }

    }

    public void keyTyped(char t, int k) {
        for (Component c : this.settings) {
            c.keyTyped(t, k);
        }
    }

    public void onScroll(int scroll) {
        for (Component component : this.settings) {
            component.onScroll(scroll);
        }
    }

    public void onGuiClosed() {
        for (Component c : this.settings) {
            c.onGuiClosed();
        }
        smoothTimer = null;
        hoverTimer = null;
        smoothingY = getHeight();
    }

    public void renderTooltip(int x, int y) {
        if (!this.category.opened || !overModuleName(x, y)) return;
        if (headerHoverStart == 0L) return;
        if (System.currentTimeMillis() - headerHoverStart < 400) return;
        String desc = mod.getDescription();
        if (desc == null || desc.trim().isEmpty()) return;
        int pad = 4;
        int tw = OpenSkid.fontManagers.getFont(20).getStringWidth(desc);
        int th = OpenSkid.fontManagers.getFont(20).getHeight();
        int bw = tw + pad * 2;
        int bh = th + pad * 2;
        int bx = x + 12;
        int by = y + 12;
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        if (bx + bw > sr.getScaledWidth() - 2) bx = x - bw - 8;
        if (by + bh > sr.getScaledHeight() - 2) by = y - bh - 8;
        if (bx < 2) bx = 2;
        if (by < 2) by = 2;
        RenderUtil.drawRoundedRectangle(bx, by, bx + bw, by + bh, 4, new Color(10, 10, 12, 230).getRGB());
        OpenSkid.fontManagers.getFont(20).drawString(desc, (float) (bx + pad), (float) (by + pad), new Color(200, 200, 200).getRGB());
    }

    public boolean overModuleName(int x, int y) {
        return x > this.category.getX() && x < this.category.getX() + this.category.getWidth() && y > this.category.getModuleY() + this.yPos && y < this.category.getModuleY() + 16 + this.yPos;
    }

    public boolean isVisible(Component component) {
        return component.isVisible();
    }

    private int mergeAlpha(int color, int alpha) {
        int newAlpha = (alpha & 0xFF) << 24;
        return (color & 0x00FFFFFF) | newAlpha;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.yPos = newOffsetY;
        int y = this.yPos + 16;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    @Override
    public void draw(AtomicInteger offset) {
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
    }

    @Override
    public void mouseDown(int x, int y, int button) {
    }
}
