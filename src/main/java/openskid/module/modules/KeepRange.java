package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.events.MoveInputEvent;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.RotationUtil;
import openskid.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

// Standalone spacing keeper mirroring the KillAura KeepRange option.
// Adapted from MiauMinus KeepRange behavior and KillAura keepRange block, rewritten here.
public class KeepRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Back", "Stop"});
    public final FloatProperty range = new FloatProperty("range", 2.8F, 1.0F, 4.0F);
    public final BooleanProperty auraOnly = new BooleanProperty("aura-only", true);
    public final FloatProperty backHysteresis = new FloatProperty("back-hysteresis", 0.05F, 0.0F, 0.5F, () -> this.mode.getValue() == 0);
    public final BooleanProperty stopStrafe = new BooleanProperty("stop-strafe", true, () -> this.mode.getValue() == 1);

    private EntityLivingBase lastTarget;

    public KeepRange() {
        super("KeepRange", false, false, "Keeps a set distance from your combat target.");
    }

    @Override
    public void onEnabled() {
        this.lastTarget = null;
    }

    @Override
    public void onDisabled() {
        this.lastTarget = null;
    }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) OpenSkid.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            EntityLivingBase aura = killAura.getTarget();
            if (aura != null && !aura.isDead && TeamUtil.isEntityLoaded(aura)) {
                return aura;
            }
        }
        if (this.auraOnly.getValue()) {
            return null;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        EntityLivingBase best = null;
        double bestDist = 6.0D;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }
            if (entity == mc.thePlayer) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entity;
            if (player.isDead) {
                continue;
            }
            if (TeamUtil.isFriend(player) || TeamUtil.isSameTeam(player) || TeamUtil.isBot(player)) {
                continue;
            }
            double dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        this.lastTarget = this.resolveTarget();
        if (this.lastTarget == null) {
            return;
        }
        double gate = this.mode.getValue() == 0
                ? (double) this.backHysteresis.getValue()
                : 0.05D;
        double dist = RotationUtil.distanceToEntity(this.lastTarget);
        if (dist >= (double) this.range.getValue() - gate) {
            return;
        }
        if (this.mode.getValue() == 1) {
            mc.thePlayer.movementInput.moveForward = 0.0F;
            if (this.stopStrafe.getValue()) {
                mc.thePlayer.movementInput.moveStrafe = 0.0F;
            }
        } else if (mc.thePlayer.movementInput.moveForward > 0.0F) {
            mc.thePlayer.movementInput.moveForward = -1.0F;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
