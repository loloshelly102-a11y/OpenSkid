package openskid.module.modules;

// Taunt-on-kill rebuilt from the MiauMinus misc/KillSults attack-track plus death-check loop.
// Fixed language packs replaced with a comma word list plus a chance gate.
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.AttackEvent;
import openskid.events.PacketEvent;
import openskid.events.TickEvent;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.property.properties.PercentProperty;
import openskid.property.properties.TextProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S02PacketChat;

public class KillSults extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long TARGET_TIMEOUT_MS = 10000L;
    private static final long KILL_CHAT_WINDOW_MS = 8000L;
    private static final String[] KILL_TOKENS = {
        "was slain", "was killed", "killed", "eliminat", "final kill",
        "died", "fell", "burned", "drowned", "blew up", "was shot", "knocked"
    };
    private final Random random = new Random();

    public final TextProperty words = new TextProperty("words", "");
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 50);

    private EntityPlayer target;
    private long lastAttackAt;
    private int ticks;
    private int sent;
    private String killChatVictim;
    private long killChatAt;

    public KillSults() {
        super("KillSults", false, false, "Sends a random taunt message after your kills.");
    }

    @Override
    public void onEnabled() {
        clearTarget();
    }

    @Override
    public void onDisabled() {
        clearTarget();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.sent)};
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || !(event.getTarget() instanceof EntityPlayer)) {
            return;
        }
        this.target = (EntityPlayer) event.getTarget();
        this.lastAttackAt = System.currentTimeMillis();
        this.ticks = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || this.target == null) {
            return;
        }
        if (this.target.isDead || this.target.getHealth() <= 0.0F) {
            if (this.ticks >= this.delay.getValue() && isChatConfirmed(this.target.getName())) {
                sendSult(this.target.getName());
            }
            this.target = null;
            return;
        }
        if (System.currentTimeMillis() - this.lastAttackAt > TARGET_TIMEOUT_MS) {
            this.target = null;
            return;
        }
        this.ticks++;
    }

    @EventTarget
    public void onChat(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE
                || !(event.getPacket() instanceof S02PacketChat) || this.target == null) {
            return;
        }
        String message;
        try {
            message = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        } catch (Exception ignored) {
            return;
        }
        if (message == null) {
            return;
        }
        String name = this.target.getName();
        if (name == null || name.isEmpty()) {
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.contains(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        for (String token : KILL_TOKENS) {
            if (lower.contains(token)) {
                this.killChatVictim = name;
                this.killChatAt = System.currentTimeMillis();
                return;
            }
        }
    }

    private boolean isChatConfirmed(String victim) {
        return victim != null && victim.equalsIgnoreCase(this.killChatVictim)
                && System.currentTimeMillis() - this.killChatAt <= KILL_CHAT_WINDOW_MS;
    }

    private void sendSult(String victim) {
        if (mc.thePlayer == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        for (String word : this.words.getValue().split(",")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                options.add(trimmed);
            }
        }
        if (options.isEmpty() || this.random.nextInt(100) >= this.chance.getValue()) {
            return;
        }
        String line = options.get(this.random.nextInt(options.size())).replace("{victim}", victim);
        mc.thePlayer.sendChatMessage(line);
        this.sent++;
    }

    private void clearTarget() {
        this.target = null;
        this.lastAttackAt = 0L;
        this.ticks = 0;
        this.killChatVictim = null;
        this.killChatAt = 0L;
    }
}
