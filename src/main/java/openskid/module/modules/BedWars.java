package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.ChatUtil;
import openskid.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Concept adapted from raven-bS BedWars (held-item and armor alerts).
// Rebuilt for OpenSkid as passive chat alerts plus trap and upgrade timers. No pasted code.
public class BedWars extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty diamond = new BooleanProperty("diamond-armor", true);
    public final BooleanProperty fireball = new BooleanProperty("fireball", true);
    public final BooleanProperty pearl = new BooleanProperty("pearl", true);
    public final BooleanProperty obsidian = new BooleanProperty("obsidian", true);
    public final ModeProperty timers = new ModeProperty("timers", 0, new String[]{"OFF", "CHAT", "HUD"});
    public final IntProperty trapWindow = new IntProperty("trap-window", 30, 5, 120, () -> timers.getValue() == 1);
    public final IntProperty upgradeWindow = new IntProperty("upgrade-window", 6, 1, 12, () -> timers.getValue() == 2);
    public final BooleanProperty chatAlert = new BooleanProperty("chat-alert", true);
    public final BooleanProperty sound = new BooleanProperty("sound", true);

    private final Set<String> diamondNotified = new HashSet<>();
    private final Map<String, String> heldNotified = new HashMap<>();
    private int alerts;
    private int tick;
    private long lastTrapAt;
    private long lastUpgradeAt;

    public BedWars() {
        super("BedWars", false, false, "Alerts for enemy gear and tracks BedWars upgrades.");
    }

    @Override
    public void onEnabled() {
        reset();
    }

    @Override
    public void onDisabled() {
        reset();
    }

    @Override
    public String[] getSuffix() {
        if (alerts > 0) {
            return new String[]{String.valueOf(alerts)};
        }
        return new String[]{timers.getModeString()};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        checkTimerExpiry();
        if (++tick % 10 != 0) {
            return;
        }
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer) {
                continue;
            }
            String name = player.getName();
            if (name == null) {
                continue;
            }
            if (diamond.getValue() && !diamondNotified.contains(name) && hasDiamondLeggings(player)) {
                diamondNotified.add(name);
                notify(name + " &fhas &bDiamond Armor&r");
            }
            String key = itemKey(player.getHeldItem());
            if (key == null) {
                heldNotified.remove(name);
                continue;
            }
            String prev = heldNotified.get(name);
            if (!key.equals(prev)) {
                heldNotified.put(name, key);
                int distance = 0;
                try {
                    distance = (int) mc.thePlayer.getDistanceToEntity(player);
                } catch (Exception ignored) {
                }
                notify(name + " &fis holding " + key + " &7(" + distance + "m)&r");
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || !(event.getPacket() instanceof S02PacketChat)) {
            return;
        }
        String raw;
        try {
            raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        } catch (Exception ignored) {
            return;
        }
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (lower.contains("trap")) {
            if (lower.contains("triggered") || lower.contains("set off")) {
                notify("&cTrap triggered!&r");
            } else if (lower.contains("purchased") && timers.getValue() != 0) {
                lastTrapAt = now;
            }
        }
        if (lower.contains("upgrade") || lower.contains("sharpened") || lower.contains("reinforced") || lower.contains("forge")) {
            if (lower.contains("purchased") && timers.getValue() != 0) {
                lastUpgradeAt = now;
            }
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        reset();
    }

    private void checkTimerExpiry() {
        long now = System.currentTimeMillis();
        if (timers.getValue() == 1) {
            if (lastTrapAt > 0 && now - lastTrapAt >= (long) trapWindow.getValue() * 1000L) {
                lastTrapAt = 0L;
                notify("&fTrap window passed&r");
            }
            return;
        }
        if (timers.getValue() == 2) {
            if (lastUpgradeAt > 0 && now - lastUpgradeAt >= (long) upgradeWindow.getValue() * 60L * 1000L) {
                lastUpgradeAt = 0L;
                notify("&fUpgrade window passed&r");
            }
        }
    }

    private boolean hasDiamondLeggings(EntityPlayer player) {
        try {
            if (player.inventory == null || player.inventory.armorInventory == null || player.inventory.armorInventory.length < 2) {
                return false;
            }
            ItemStack leggings = player.inventory.armorInventory[1];
            return leggings != null && leggings.getItem() == Items.diamond_leggings;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String itemKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String unlocalized;
        try {
            unlocalized = stack.getItem().getUnlocalizedName();
        } catch (Exception ignored) {
            return null;
        }
        if (unlocalized == null) {
            return null;
        }
        String lower = unlocalized.toLowerCase(Locale.ROOT);
        if (pearl.getValue() && lower.contains("enderpearl")) {
            return "&3Ender Pearl&r";
        }
        if (obsidian.getValue() && lower.contains("obsidian")) {
            return "&dObsidian&r";
        }
        if (fireball.getValue() && (lower.contains("fireball") || lower.contains("fire_charge"))) {
            return "&6Fireball&r";
        }
        return null;
    }

    private void notify(String detail) {
        alerts++;
        if (chatAlert.getValue()) {
            ChatUtil.sendFormatted(String.format("%s%s: &eAlert: &r%s", OpenSkid.clientName, getName(), detail));
        }
        if (sound.getValue()) {
            try {
                SoundUtil.playSound("note.pling");
            } catch (Exception ignored) {
            }
        }
    }

    private void reset() {
        diamondNotified.clear();
        heldNotified.clear();
        alerts = 0;
        tick = 0;
        lastTrapAt = 0L;
        lastUpgradeAt = 0L;
    }
}
