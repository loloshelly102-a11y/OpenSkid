package openskid.module.modules;

import openskid.event.EventTarget;
import openskid.event.types.EventType;
import openskid.events.PacketEvent;
import openskid.events.UpdateEvent;
import openskid.module.Module;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.property.properties.TextProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

// Adapted from Raven S+ GhostBlock. Donor cancels placements to keep client
// side blocks. Here it is rebuilt with a timed auto clear for scaffold tricks.
public class GhostBlock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty placeMode = new ModeProperty("place-mode", 0, new String[]{"Ghost", "Timed"});
    public final IntProperty timeoutMs = new IntProperty("timeout-ms", 3000, 500, 10000, () -> placeMode.getValue() == 1);
    public final TextProperty holdKey = new TextProperty("hold-key", "");

    private BlockPos ghostPos;
    private long ghostTime;

    public GhostBlock() {
        super("GhostBlock", false, false, "Creates client side blocks by cancelling placements.");
    }

    @Override
    public void onEnabled() {
        ghostPos = null;
        ghostTime = 0L;
    }

    @Override
    public void onDisabled() {
        clearGhost();
        ghostPos = null;
        ghostTime = 0L;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!isArmed()) return;

        BlockPos pos = ((C08PacketPlayerBlockPlacement) event.getPacket()).getPosition();
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return;

        event.setCancelled(true);
        ghostPos = pos;
        ghostTime = System.currentTimeMillis();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (placeMode.getValue() != 1 || ghostPos == null) return;
        if (System.currentTimeMillis() - ghostTime >= timeoutMs.getValue()) {
            clearGhost();
            ghostPos = null;
        }
    }

    private void clearGhost() {
        if (ghostPos != null && mc.theWorld != null) {
            mc.theWorld.setBlockToAir(ghostPos);
        }
    }

    private boolean isArmed() {
        String keyName = this.holdKey.getValue();
        if (keyName == null || keyName.trim().isEmpty()) {
            return true;
        }
        int code = Keyboard.getKeyIndex(keyName.trim().toUpperCase());
        return code != 0 && Keyboard.isKeyDown(code);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{placeMode.getModeString()};
    }
}
