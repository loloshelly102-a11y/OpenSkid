
package openskid.ui.components;

import openskid.OpenSkid;
import openskid.module.Module;
import openskid.module.modules.HUD;
import openskid.property.Property;
import openskid.property.properties.*;
import openskid.ui.Component;
import openskid.ui.dataset.impl.FloatSlider;
import openskid.ui.dataset.impl.IntSlider;
import openskid.ui.dataset.impl.PercentageSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ModuleComponent implements Component {
    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    private final ArrayList<Component> settings;
    public boolean panelExpand;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.panelExpand = false;
        int y = offsetY + 12;
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
                    SliderComponent c = new SliderComponent(new openskid.ui.dataset.Slider() {
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
                        public void setValueString(String value) {
                            try {
                                property.setValue(Long.parseLong(value.trim()));
                            } catch (Exception ignore) {
                            }
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
                        public String getValueColorString() {
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

                        @Override
                        public void stepping(boolean increment) {
                            long current = property.getValue();
                            if (increment) {
                                if (current >= property.getMaximum()) return;
                                property.setValue(current + 1);
                            } else {
                                if (current <= property.getMinimum()) return;
                                property.setValue(current - 1);
                            }
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
                            Minecraft.getMinecraft().fontRendererObj.drawString(text, (float) ((parent.category.getX() + 4) * 2), (float) ((parent.category.getY() + posY[0] + 5) * 2), -1, false);
                            GL11.glPopMatrix();
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
                    };
                    this.settings.add(c);
                    y += c.getHeight();
                }
            }
        }

        this.settings.add(new BindComponent(this, y));
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
        int y = this.offsetY + 16;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    public void draw(AtomicInteger offset) {
        int textColor;
        if (this.mod.isEnabled()) {
            textColor = ((HUD) OpenSkid.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get());
        } else {
            textColor = new Color(102, 102, 102).getRGB();
        }
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(this.mod.getName(), (float) (this.category.getX() + this.category.getWidth() / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.mod.getName()) / 2), (float) (this.category.getY() + this.offsetY + 4), textColor);
        if (this.panelExpand && !this.settings.isEmpty()) {
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    c.draw(offset);
                    offset.incrementAndGet();
                }
            }
        }


    }

    public int getHeight() {
        if (!this.panelExpand) {
            return 16;
        } else {
            int h = 16;
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    h += c.getHeight();
                }
            }
            return h;
        }
    }

    public void update(int mousePosX, int mousePosY) {
        if(!panelExpand) return;
        if (!this.settings.isEmpty()) {
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    c.update(mousePosX, mousePosY);
                }
            }
        }

    }

    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0) {
            this.mod.toggle();
        }

        if (this.isHovered(x, y) && button == 1) {
            this.panelExpand = !this.panelExpand;
        }

        if(!panelExpand) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseDown(x, y, button);
            }
        }

    }

    public void mouseReleased(int x, int y, int button) {
        if(!panelExpand) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseReleased(x, y, button);
            }
        }

    }

    public void keyTyped(char chatTyped, int keyCode) {
        if(!panelExpand) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.keyTyped(chatTyped, keyCode);
            }
        }

    }

    public boolean isHovered(int x, int y) {
        return x > this.category.getX() && x < this.category.getX() + this.category.getWidth() && y > this.category.getY() + this.offsetY && y < this.category.getY() + 16 + this.offsetY;
    }


    @Override
    public boolean isVisible() {
        return true;
    }
}
