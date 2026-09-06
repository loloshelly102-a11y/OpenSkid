package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.UpdateEvent;
import openskid.events.WindowClickEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.ItemUtil;
import openskid.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int actionDelay = 0;
    private int oDelay = 0;
    private boolean inventoryOpen = false;
    private int cleanPickup = -1;
    private int cleanPlace = -1;
    private boolean cleanReturning = false;
    private ItemStack cleanPicked = null;
    private final TimerUtil autoArmorTime = new TimerUtil();
    public final IntProperty minDelay = new IntProperty("min-delay", 1, 0, 20);
    public final IntProperty maxDelay = new IntProperty("max-delay", 2, 0, 20);
    public final IntProperty openDelay = new IntProperty("open-delay", 1, 0, 20);
    public final BooleanProperty humanize = new BooleanProperty("humanize", false);
    public final IntProperty humanizeTicks = new IntProperty("humanize-ticks", 2, 0, 10, this.humanize::getValue);
    public final BooleanProperty autoArmor = new BooleanProperty("auto-armor", true);
    public final IntProperty autoArmorInterval = new IntProperty("auto-armor-interval", 0, 0, 100, this.autoArmor::getValue);
    public final BooleanProperty dropTrash = new BooleanProperty("drop-trash", false);
    public final BooleanProperty checkDurability = new BooleanProperty("check-durability", true);
    public final IntProperty swordSlot = new IntProperty("sword-slot", 1, 0, 9);
    public final IntProperty pickaxeSlot = new IntProperty("pickaxe-slot", 3, 0, 9);
    public final IntProperty shovelSlot = new IntProperty("shovel-slot", 4, 0, 9);
    public final IntProperty axeSlot = new IntProperty("axe-slot", 5, 0, 9);
    public final IntProperty blocksSlot = new IntProperty("blocks-slot", 2, 0, 9);
    public final IntProperty blocks = new IntProperty("blocks", 128, 64, 2304);
    public final IntProperty projectileSlot = new IntProperty("projectile-slot", 7, 0, 9);
    public final IntProperty projectiles = new IntProperty("projectiles", 64, 16, 2304);
    public final IntProperty goldAppleSlot = new IntProperty("gold-apple-slot", 9, 0, 9);
    public final IntProperty arrow = new IntProperty("arrow", 256, 0, 2304);
    public final IntProperty bowSlot = new IntProperty("bow-slot", 8, 0, 9);

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) {
            return 8 - (slot - 36);
        } else {
            return slot <= 8 ? slot + 36 : slot;
        }
    }

    private void clickSlot(int windowId, int slotId, int mouseButtonClicked, int mode) {
        mc.playerController.windowClick(windowId, slotId, mouseButtonClicked, mode, mc.thePlayer);
        if (this.invMode.getValue() == 2 && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    private int getStackSize(int slot) {
        if (slot == -1) {
            return 0;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null ? stack.stackSize : 0;
        }
    }

    public InvManager() {
        super("InvManager", false, false, "Automatically organizes armor and hotbar items.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.actionDelay > 0) {
                this.actionDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }
            boolean guiOpen = mc.currentScreen instanceof GuiInventory
                    && ((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer;
            boolean canManage = guiOpen || (this.invMode.getValue() == 2
                    && (mc.currentScreen == null || guiOpen));
            if (!canManage) {
                this.inventoryOpen = false;
                this.cleanPickup = -1;
                this.cleanPlace = -1;
                this.cleanReturning = false;
                this.cleanPicked = null;
            } else {
                if (!this.inventoryOpen) {
                    this.inventoryOpen = true;
                    this.oDelay = this.openDelay.getValue() + 1;
                    if (!guiOpen) {
                        this.oDelay = 0;
                    }
                    this.autoArmorTime.reset();
                }
                if (this.oDelay <= 0 && this.actionDelay <= 0) {
                    if (this.isEnabled() && this.isValidGameMode()) {
                        ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        for (int i = 0; i < 4; i++) {
                            equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
                            inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
                        }
                        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
                        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, this.checkDurability.getValue());
                        if (inventorySwordSlot == -1)
                            inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);
                        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
                        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, this.checkDurability.getValue());
                        if (inventoryPickaxeSlot == -1)
                            inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);
                        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
                        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, this.checkDurability.getValue());
                        if (inventoryShovelSlot == -1)
                            inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);
                        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
                        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, this.checkDurability.getValue());
                        if (inventoryAxeSlot == -1)
                            inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);
                        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
                        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot, ItemUtil.ItemType.Block);
                        int preferredProjectileHotbarSlot = this.projectileSlot.getValue() - 1;
                        int inventoryProjectileSlot = ItemUtil.findInventorySlot(preferredProjectileHotbarSlot, ItemUtil.ItemType.Projectile);
                        if (inventoryProjectileSlot == -1)
                            inventoryProjectileSlot = ItemUtil.findInventorySlot(preferredProjectileHotbarSlot, ItemUtil.ItemType.FishRod);
                        int preferredGoldAppleHotbarSlot = this.goldAppleSlot.getValue() - 1;
                        int inventoryGoldAppleSlot = ItemUtil.findInventorySlot(preferredGoldAppleHotbarSlot, ItemUtil.ItemType.GoldApple);
                        int preferredBowHotbarSlot = this.bowSlot.getValue() - 1;
                        int inventoryBowSlot = ItemUtil.findBowInventorySlot(preferredBowHotbarSlot, this.checkDurability.getValue());
                        if (inventoryBowSlot == -1)
                            inventoryBowSlot = ItemUtil.findBowInventorySlot(preferredBowHotbarSlot, false);
                        if (this.autoArmor.getValue() && this.autoArmorTime.hasTimeElapsed(this.autoArmorInterval.getValue() * 50L)) {
                            for (int i = 0; i < 4; i++) {
                                int equippedSlot = equippedArmorSlots.get(i);
                                int inventorySlot = inventoryArmorSlots.get(i);
                                if (equippedSlot != -1 || inventorySlot != -1) {
                                    int playerArmorSlot = 39 - i;
                                    if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                                        if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                                            if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 0, 1);
                                            } else {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 1, 4);
                                            }
                                        } else {
                                            int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(armorToEquipSlot), 0, 1);
                                            this.autoArmorTime.reset();
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                        LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                        if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && inventorySwordSlot != -1) {
                            usedHotbarSlots.add(preferredSwordHotbarSlot);
                            if (inventorySwordSlot != preferredSwordHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventorySwordSlot), preferredSwordHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot) && inventoryPickaxeSlot != -1) {
                            usedHotbarSlots.add(preferredPickaxeHotbarSlot);
                            if (inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryPickaxeSlot), preferredPickaxeHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredShovelHotbarSlot) && inventoryShovelSlot != -1) {
                            usedHotbarSlots.add(preferredShovelHotbarSlot);
                            if (inventoryShovelSlot != preferredShovelHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryShovelSlot), preferredShovelHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredAxeHotbarSlot) && inventoryAxeSlot != -1) {
                            usedHotbarSlots.add(preferredAxeHotbarSlot);
                            if (inventoryAxeSlot != preferredAxeHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryAxeSlot), preferredAxeHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBlocksHotbarSlot) && inventoryBlocksSlot != -1) {
                            usedHotbarSlots.add(preferredBlocksHotbarSlot);
                            if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBlocksSlot), preferredBlocksHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredProjectileHotbarSlot >= 0 && preferredProjectileHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredProjectileHotbarSlot) && inventoryProjectileSlot != -1) {
                            usedHotbarSlots.add(preferredProjectileHotbarSlot);
                            if (inventoryProjectileSlot != preferredProjectileHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryProjectileSlot), preferredProjectileHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredGoldAppleHotbarSlot >= 0 && preferredGoldAppleHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredGoldAppleHotbarSlot) && inventoryGoldAppleSlot != -1) {
                            usedHotbarSlots.add(preferredGoldAppleHotbarSlot);
                            if (inventoryGoldAppleSlot != preferredGoldAppleHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryGoldAppleSlot), preferredGoldAppleHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredBowHotbarSlot >= 0 && preferredBowHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBowHotbarSlot) && inventoryBowSlot != -1) {
                            usedHotbarSlots.add(preferredBowHotbarSlot);
                            if (inventoryBowSlot != preferredBowHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBowSlot), preferredBowHotbarSlot, 2);
                                return;
                            }
                        }
                        if (this.cleanSort.getValue() && this.doCleanSortStep()) {
                            return;
                        }
                        if (this.dropTrash.getValue()) {
                            int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
                            int currentProjectileCount = this.getStackSize(inventoryProjectileSlot);
                            for (int i = 0; i < 36; i++) {
                                if (!equippedArmorSlots.contains(i)
                                        && !inventoryArmorSlots.contains(i)
                                        && inventorySwordSlot != i
                                        && inventoryPickaxeSlot != i
                                        && inventoryShovelSlot != i
                                        && inventoryAxeSlot != i
                                        && inventoryBlocksSlot != i
                                        && inventoryProjectileSlot != i
                                        && inventoryGoldAppleSlot != i
                                        && inventoryBowSlot != i) {
                                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                    if (stack != null) {
                                        boolean isBlock = ItemUtil.isBlock(stack);
                                        boolean isProjectile = ItemUtil.isProjectile(stack);
                                        if (isBlock) {
                                            currentBlockCount += stack.stackSize;
                                        }
                                        if (isProjectile) {
                                            currentProjectileCount += stack.stackSize;
                                        }
                                        if (isBlock ? currentBlockCount > this.blocks.getValue() :
                                                isProjectile ? currentProjectileCount > this.projectiles.getValue() :
                                                        ItemUtil.isNotSpecialItem(stack)) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        this.actionDelay = RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
        if (this.invMode.getValue() == 2) {
            this.actionDelay += RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
        }
        if (this.humanize.getValue() && this.humanizeTicks.getValue() > 0) {
            this.actionDelay += RandomUtils.nextInt(0, this.humanizeTicks.getValue() + 1);
            if (Math.random() < 0.1) {
                this.actionDelay += this.humanizeTicks.getValue() * 2;
            }
        }
    }

    @Override
    public void verifyValue(String mode) {
        switch (mode) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public void onDisabled() {
        this.cleanPickup = -1;
        this.cleanPlace = -1;
        this.cleanReturning = false;
        this.cleanPicked = null;
    }

    private boolean samePicked(ItemStack cursor) {
        if (cursor == null || this.cleanPicked == null) return false;
        if (cursor.getItem() == null || cursor.getItem() != this.cleanPicked.getItem()) return false;
        return cursor.getItemDamage() == this.cleanPicked.getItemDamage();
    }

    private boolean areMergeable(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() == null || a.getItem() != b.getItem()) return false;
        if (!a.isStackable() || !b.isStackable()) return false;
        if (b.stackSize >= b.getMaxStackSize()) return false;
        if (a.getItemDamage() != b.getItemDamage()) return false;
        return ItemStack.areItemStackTagsEqual(a, b);
    }

    private boolean doCleanSortStep() {
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        int windowId = mc.thePlayer.inventoryContainer.windowId;
        if (cursor != null) {
            if (this.cleanPlace == -1) {
                return true;
            }
            if (!this.cleanReturning) {
                ItemStack target = mc.thePlayer.inventory.getStackInSlot(this.cleanPlace);
                boolean swappable = target != null && target.getItem() instanceof ItemArmor
                        && cursor.getItem() instanceof ItemArmor;
                if (!swappable && (target == null || !this.areMergeable(cursor, target))) {
                    if (this.samePicked(cursor) && this.cleanPickup != -1) {
                        this.clickSlot(windowId, this.convertSlotIndex(this.cleanPickup), 0, 0);
                    }
                    this.cleanPickup = -1;
                    this.cleanPlace = -1;
                    this.cleanReturning = false;
                    this.cleanPicked = null;
                    return true;
                }
                this.clickSlot(windowId, this.convertSlotIndex(this.cleanPlace), 0, 0);
                this.cleanReturning = true;
                return true;
            }
            this.clickSlot(windowId, this.convertSlotIndex(this.cleanPickup), 0, 0);
            this.cleanPickup = -1;
            this.cleanPlace = -1;
            this.cleanReturning = false;
            this.cleanPicked = null;
            return true;
        }
        if (this.cleanReturning || this.cleanPlace != -1) {
            this.cleanPickup = -1;
            this.cleanPlace = -1;
            this.cleanReturning = false;
            this.cleanPicked = null;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack a = mc.thePlayer.inventory.getStackInSlot(i);
            if (a == null) continue;
            for (int j = i + 1; j < 36; j++) {
                ItemStack b = mc.thePlayer.inventory.getStackInSlot(j);
                if (b == null) continue;
                if (this.areMergeable(a, b) || this.areMergeable(b, a)) {
                    this.clickSlot(windowId, this.convertSlotIndex(i), 0, 0);
                    this.cleanPickup = i;
                    this.cleanPlace = j;
                    this.cleanReturning = false;
                    this.cleanPicked = a;
                    return true;
                }
                if (a.getItem() instanceof ItemArmor && b.getItem() instanceof ItemArmor
                        && ItemUtil.getArmorProtection(b) > ItemUtil.getArmorProtection(a)) {
                    this.clickSlot(windowId, this.convertSlotIndex(i), 0, 0);
                    this.cleanPickup = i;
                    this.cleanPlace = j;
                    this.cleanReturning = false;
                    this.cleanPicked = a;
                    return true;
                }
            }
        }
        return false;
    }

    public final ModeProperty invMode = new ModeProperty("mode", 0, new String[]{"Basic", "OpenInv", "Hypixel"});
    public final BooleanProperty cleanSort = new BooleanProperty("clean-sort", false);
}