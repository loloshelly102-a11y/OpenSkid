package openskid.module.modules;

import openskid.OpenSkid;
import openskid.event.EventTarget;
import openskid.event.types.EventType;
import java.util.function.Supplier;
import openskid.event.types.Priority;
import openskid.events.AttackEvent;
import openskid.events.LoadWorldEvent;
import openskid.events.PacketEvent;
import openskid.events.Render3DEvent;
import openskid.events.TickEvent;
import openskid.management.LagCore;
import openskid.mixin.IAccessorRenderManager;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.FloatProperty;
import openskid.property.properties.IntProperty;
import openskid.property.properties.ModeProperty;
import openskid.util.RenderUtil;
import openskid.util.RotationUtil;
import openskid.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty range;
    public final BooleanProperty adaptive;
    public final IntProperty normalDelay;
    public final IntProperty adaptiveDelay;
    public final BooleanProperty releaseOnHit;
    public final BooleanProperty interruptLagRange;
    public final BooleanProperty players;
    public final BooleanProperty teams;
    public final BooleanProperty botCheck;
    public final BooleanProperty esp;
    // Hub modes appended at end, NORMAL keeps legacy behavior. Adapted from MiauMinus BackTrack BUFFER.
    public final ModeProperty mode;
    public final FloatProperty packetDistance;
    public final IntProperty packetTimer;
    public final ModeProperty packetMode;
    public final IntProperty packetPingSize;
    public final BooleanProperty packetPlayerModel;

    private final ConcurrentLinkedQueue<Packet<?>> incomingQueue = new ConcurrentLinkedQueue<>();

    private Vec3 trackedPosition = null;
    private EntityLivingBase target;
    private EntityLivingBase lastAttacked;
    private long lastAttackTime = 0L;
    private static final long COMBAT_LOCK_MS = 3000L;
    private AxisAlignedBB realAABB;
    private long backtrackStartTime = 0L;
    private boolean lagRangeInterrupted = false;
    private static BackTrack instance;
    private EntityPlayer packetTarget;
    private double packetRealX;
    private double packetRealY;
    private double packetRealZ;
    private long bufferStartMs = 0L;

    public BackTrack() {
        super("Backtrack", false, false, "Delays packets to hit enemies at older positions.");
        instance = this;
        this.range = new FloatProperty("range", 3.5F, 1.0F, 8.0F);
        this.adaptive = new BooleanProperty("adaptive", true);
        this.normalDelay = new IntProperty("normal-delay", 100, 50, 1000, () -> !this.adaptive.getValue());
        this.adaptiveDelay = new IntProperty("adaptive-delay", 100, 50, 1000, this.adaptive::getValue);
        this.releaseOnHit = new BooleanProperty("release-on-hit", true);
        this.interruptLagRange = new BooleanProperty("interrupt-lagrange", true);
        this.players = new BooleanProperty("players", true);
        this.teams = new BooleanProperty("teams", true);
        this.botCheck = new BooleanProperty("bot-check", true);
        this.esp = new BooleanProperty("esp", true);
        this.mode = new ModeProperty("mode", 0, new String[]{"NORMAL", "BUFFER"});
        this.packetDistance = new FloatProperty("packet-distance", 4.0F, 0.0F, 7.0F, () -> this.mode.getValue() == 1);
        this.packetTimer = new IntProperty("packet-timer", 200, 0, 2000, () -> this.mode.getValue() == 1);
        this.packetMode = new ModeProperty("packet-mode", 0, new String[]{"PING", "DELAY"}, () -> this.mode.getValue() == 1);
        this.packetPingSize = new IntProperty("packet-ping-size", 0, 0, 2000, () -> this.mode.getValue() == 1 && this.packetMode.getValue() == 0);
        this.packetPlayerModel = new BooleanProperty("packet-player-model", true, () -> this.mode.getValue() == 1);
    }

    @Override
    public void onEnabled() {
        if (OpenSkid.lagCore != null) OpenSkid.lagCore.release(this);
        incomingQueue.clear();
        trackedPosition = null;
        realAABB = null;
        backtrackStartTime = 0L;
        target = null;
        lastAttacked = null;
        lastAttackTime = 0L;
        lagRangeInterrupted = false;
        packetTarget = null;
        bufferStartMs = 0L;
    }

    @Override
    public void onDisabled() {
        setLagRangeEnabled(true);
        if (OpenSkid.lagCore != null) OpenSkid.lagCore.release(this);
        incomingQueue.clear();
        trackedPosition = null;
        realAABB = null;
        target = null;
        lastAttacked = null;
        lastAttackTime = 0L;
        packetTarget = null;
        bufferStartMs = 0L;
    }

    private int currentMaxDelay() {
        return adaptive.getValue() ? adaptiveDelay.getValue() : normalDelay.getValue();
    }

    private LagRange getLagRange() {
        Module m = OpenSkid.moduleManager.getModule(LagRange.class);
        return (m instanceof LagRange) ? (LagRange) m : null;
    }

    private void setLagRangeEnabled(boolean enabled) {
        if (!interruptLagRange.getValue()) return;
        LagRange lr = getLagRange();
        if (lr == null) return;
        if (enabled && lagRangeInterrupted) {
            lagRangeInterrupted = false;
            lr.setEnabled(true);
        } else if (!enabled && !lagRangeInterrupted && lr.isEnabled()) {
            lagRangeInterrupted = true;
            lr.setEnabled(false);
        }
    }

    private boolean isInCombat() {
        if (lastAttacked == null || lastAttacked != target || lastAttacked.isDead) return false;
        return System.currentTimeMillis() - lastAttackTime <= COMBAT_LOCK_MS;
    }

    private boolean canBacktrack() {
        if (target == null || target.isDead) return false;
        if (trackedPosition == null) return false;
        if (distanceTo(trackedPosition) > range.getValue()) return false;
        if (backtrackStartTime > 0 && System.currentTimeMillis() - backtrackStartTime > currentMaxDelay()) return false;
        double distReal = distanceTo(trackedPosition);
        double distCurrent = mc.thePlayer.getDistanceToEntity(target);
        return adaptive.getValue() ? distReal > distCurrent : distReal > distCurrent + 0.1;
    }

    private boolean shouldQueueIncoming(Packet<?> p) {
        if (p instanceof S12PacketEntityVelocity) return false;
        if (p instanceof S27PacketExplosion) return false;
        if (p instanceof S0FPacketSpawnMob) return false;
        if (p instanceof S14PacketEntity) {
            Entity e = ((S14PacketEntity) p).getEntity(mc.theWorld);
            return e != null && e == target;
        }
        if (p instanceof S18PacketEntityTeleport) return ((S18PacketEntityTeleport) p).getEntityId() == target.getEntityId();
        if (p instanceof S19PacketEntityHeadLook) return ((S19PacketEntityHeadLook) p).getEntity(mc.theWorld) == target;
        return false;
    }

    private void releaseIncoming() {
        LagCore core = OpenSkid.lagCore;
        if (core != null) core.release(this);
        else if (mc.getNetHandler() != null) {
            Packet<?> p;
            while ((p = incomingQueue.poll()) != null) processPacketUnchecked(p);
        } else incomingQueue.clear();
        incomingQueue.clear();
        backtrackStartTime = 0L;
        bufferStartMs = 0L;
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.network.INetHandler> void processPacketUnchecked(Packet<T> packet) {
        packet.processPacket((T) Minecraft.getMinecraft().getNetHandler());
    }

    private void releaseAll() {
        releaseIncoming();
    }

    private boolean holdInbound(Packet<?> packet) {
        LagCore core = OpenSkid.lagCore;
        if (core == null || core.isReleasing() || core.isHeld(packet)) return core != null && core.isHeld(packet);
        return core.hold(packet, LagCore.Direction.INBOUND, this);
    }

    private void updateRealPosition(Packet<?> packet) {
        if (target == null) return;

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            Entity e = p.getEntity(mc.theWorld);
            if (e == null || e != target) return;

            Vec3 base = (trackedPosition != null) ? trackedPosition
                    : new Vec3(target.posX, target.posY, target.posZ);

            double dx = p.func_149062_c() / 32.0;
            double dy = p.func_149061_d() / 32.0;
            double dz = p.func_149064_e() / 32.0;
            trackedPosition = base.addVector(dx, dy, dz);

        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            if (p.getEntityId() != target.getEntityId()) return;

            trackedPosition = new Vec3(p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0);
        }

        if (trackedPosition != null) {
            double hw = target.width / 2.0;
            realAABB = new AxisAlignedBB(
                    trackedPosition.xCoord - hw, trackedPosition.yCoord,
                    trackedPosition.zCoord - hw,
                    trackedPosition.xCoord + hw, trackedPosition.yCoord + target.height,
                    trackedPosition.zCoord + hw
            );
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (event.getTarget() instanceof EntityLivingBase) {
            lastAttacked = (EntityLivingBase) event.getTarget();
            lastAttackTime = System.currentTimeMillis();
            if (mode.getValue() == 1 && event.getTarget() instanceof EntityPlayer) {
                EntityPlayer attacked = (EntityPlayer) event.getTarget();
                if (packetTarget != attacked) {
                    releaseIncoming();
                    packetTarget = attacked;
                    packetRealX = attacked.posX;
                    packetRealY = attacked.posY;
                    packetRealZ = attacked.posZ;
                    bufferStartMs = System.currentTimeMillis();
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        Module scaffold = OpenSkid.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            setLagRangeEnabled(true);
            releaseAll();
            incomingQueue.clear();
            return;
        }
        if (event.getType() == EventType.RECEIVE) {
            handleIncoming(event);
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        releaseAll();
        packetTarget = null;
        target = null;
        trackedPosition = null;
        realAABB = null;
    }

    private void handleIncoming(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S40PacketDisconnect) {
            releaseAll();
            packetTarget = null;
            return;
        }
        if (mode.getValue() == 1) {
            handleBufferIncoming(event, packet);
            return;
        }
        updateRealPosition(packet);
        if (target == null) return;
        if (canBacktrack() && shouldQueueIncoming(packet)) {
            if (backtrackStartTime == 0L) backtrackStartTime = System.currentTimeMillis();
            LagCore core = OpenSkid.lagCore;
            if (core != null && core.isHeld(packet)) {
                event.setCancelled(true);
                return;
            }
            if (holdInbound(packet)) {
                incomingQueue.add(packet);
                event.setCancelled(true);
            }
        } else if (!canBacktrack() && !incomingQueue.isEmpty()) {
            releaseIncoming();
        }
    }

    private boolean isBufferFlushPacket(Packet<?> packet) {
        return packet instanceof S40PacketDisconnect || packet instanceof S02PacketChat
                || packet instanceof S08PacketPlayerPosLook;
    }

    private void trackBufferRealPos(Packet<?> packet) {
        if (packetTarget == null) return;
        if (packet instanceof S14PacketEntity) {
            Entity e = ((S14PacketEntity) packet).getEntity(mc.theWorld);
            if (e == null || e.getEntityId() != packetTarget.getEntityId()) return;
            S14PacketEntity p = (S14PacketEntity) packet;
            packetRealX += p.func_149062_c() / 32.0;
            packetRealY += p.func_149061_d() / 32.0;
            packetRealZ += p.func_149064_e() / 32.0;
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            if (p.getEntityId() != packetTarget.getEntityId()) return;
            packetRealX = p.getX() / 32.0;
            packetRealY = p.getY() / 32.0;
            packetRealZ = p.getZ() / 32.0;
        }
    }

    private boolean shouldBufferHold(Packet<?> packet) {
        if (packetTarget == null) return false;
        String name = packet.getClass().getSimpleName();
        return name.startsWith("S");
    }

    private boolean shouldPacketBacktrack() {
        if (packetTarget == null || packetTarget.isDead) return false;
        if (mc.thePlayer == null) return false;
        double dist = mc.thePlayer.getDistanceToEntity(packetTarget);
        return dist >= packetDistance.getValue();
    }

    private void handleBufferIncoming(PacketEvent event, Packet<?> packet) {
        if (packetTarget == null) return;
        if (isBufferFlushPacket(packet)) {
            releaseIncoming();
            packetTarget = null;
            return;
        }
        trackBufferRealPos(packet);
        if (!shouldBufferHold(packet)) return;
        if (bufferStartMs == 0L) bufferStartMs = System.currentTimeMillis();
        LagCore core = OpenSkid.lagCore;
        if (core != null && core.isHeld(packet)) {
            event.setCancelled(true);
            return;
        }
        if (holdInbound(packet)) {
            incomingQueue.add(packet);
            event.setCancelled(true);
        }
    }

    private void updateBufferTick() {
        if (packetTarget == null) {
            if (!incomingQueue.isEmpty()) releaseIncoming();
            return;
        }
        if (!shouldPacketBacktrack()) {
            releaseIncoming();
            packetTarget = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (packetMode.getValue() == 0) {
            int pingSize = packetPingSize.getValue();
            if (pingSize <= 0) {
                releaseIncoming();
                bufferStartMs = now;
            } else if (bufferStartMs > 0 && now - bufferStartMs >= pingSize) {
                releaseIncoming();
                bufferStartMs = now;
            }
        } else if (bufferStartMs > 0 && now - bufferStartMs >= packetTimer.getValue()) {
            releaseIncoming();
            bufferStartMs = now;
        }
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() == EventType.PRE) tickPre();
    }

    private void tickPre() {
        if (mode.getValue() == 1) {
            updateBufferTick();
            return;
        }
        EntityLivingBase newTarget = resolveTarget();
        if (newTarget != target) {
            setLagRangeEnabled(true);
            releaseAll();
            realAABB = null;
            trackedPosition = null;
        }
        target = newTarget;

        if (target == null) {
            setLagRangeEnabled(true);
            return;
        }

        if (trackedPosition == null) {
            trackedPosition = new Vec3(
                    MathHelper.floor_double(target.posX * 32.0) / 32.0,
                    MathHelper.floor_double(target.posY * 32.0) / 32.0,
                    MathHelper.floor_double(target.posZ * 32.0) / 32.0
            );
            double hw = target.width / 2.0;
            realAABB = new AxisAlignedBB(
                    trackedPosition.xCoord - hw, trackedPosition.yCoord,
                    trackedPosition.zCoord - hw,
                    trackedPosition.xCoord + hw, trackedPosition.yCoord + target.height,
                    trackedPosition.zCoord + hw
            );
        }

        boolean shouldRelease = false;
        if (mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime && mc.thePlayer.maxHurtTime > 0) shouldRelease = true;
        if (distanceTo(trackedPosition) > range.getValue()) shouldRelease = true;
        if (backtrackStartTime > 0 && System.currentTimeMillis() - backtrackStartTime > currentMaxDelay()) shouldRelease = true;
        if (releaseOnHit.getValue() && target.hurtTime == 1) shouldRelease = true;

        if (shouldRelease) {
            setLagRangeEnabled(true);
            releaseAll();
            return;
        }

        if (isInCombat() && !incomingQueue.isEmpty()) {
            setLagRangeEnabled(false);
        } else if (!isInCombat() || incomingQueue.isEmpty()) {
            setLagRangeEnabled(true);
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !esp.getValue()) return;
        if (mode.getValue() == 1) {
            renderBufferBox();
            return;
        }
        if (target == null || realAABB == null) return;
        AxisAlignedBB visual = target.getEntityBoundingBox();
        double dx = realAABB.minX - visual.minX;
        double dy = realAABB.minY - visual.minY;
        double dz = realAABB.minZ - visual.minZ;
        if (Math.abs(dx) < 0.01 && Math.abs(dy) < 0.01 && Math.abs(dz) < 0.01) return;
        Color color = (target instanceof EntityPlayer)
                ? TeamUtil.getTeamColor((EntityPlayer) target, 1.0F)
                : new Color(255, 60, 60);
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        AxisAlignedBB aabb = new AxisAlignedBB(
                realAABB.minX - rm.getRenderPosX(),
                realAABB.minY - rm.getRenderPosY(),
                realAABB.minZ - rm.getRenderPosZ(),
                realAABB.maxX - rm.getRenderPosX(),
                realAABB.maxY - rm.getRenderPosY(),
                realAABB.maxZ - rm.getRenderPosZ()
        );
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    private void renderBufferBox() {
        if (packetTarget == null || !packetPlayerModel.getValue()) return;
        double hw = packetTarget.width / 2.0;
        AxisAlignedBB box = new AxisAlignedBB(
                packetRealX - hw, packetRealY, packetRealZ - hw,
                packetRealX + hw, packetRealY + packetTarget.height, packetRealZ + hw
        );
        AxisAlignedBB visual = packetTarget.getEntityBoundingBox();
        if (Math.abs(box.minX - visual.minX) < 0.01 && Math.abs(box.minY - visual.minY) < 0.01
                && Math.abs(box.minZ - visual.minZ) < 0.01) return;
        Color color = TeamUtil.getTeamColor(packetTarget, 1.0F);
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        AxisAlignedBB aabb = new AxisAlignedBB(
                box.minX - rm.getRenderPosX(), box.minY - rm.getRenderPosY(), box.minZ - rm.getRenderPosZ(),
                box.maxX - rm.getRenderPosX(), box.maxY - rm.getRenderPosY(), box.maxZ - rm.getRenderPosZ()
        );
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    private EntityLivingBase resolveTarget() {
        KillAura ka = (KillAura) OpenSkid.moduleManager.modules.get(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) return ka.getTarget();
        if (lastAttacked != null && !lastAttacked.isDead
                && System.currentTimeMillis() - lastAttackTime <= COMBAT_LOCK_MS
                && mc.thePlayer.getDistanceToEntity(lastAttacked) <= range.getValue() * 2.0F) {
            return lastAttacked;
        }
        ArrayList<EntityLivingBase> candidates = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) entity;
            if (isValidTarget(e) && mc.thePlayer.getDistanceToEntity(e) <= range.getValue()) candidates.add(e);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Float.compare(RotationUtil.angleToEntity(a), RotationUtil.angleToEntity(b)));
        return candidates.get(0);
    }

    private boolean isValidTarget(EntityLivingBase e) {
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;
        if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) return false;
        if (e == mc.getRenderViewEntity() || e == mc.getRenderViewEntity().ridingEntity) return false;
        if (e.deathTime > 0) return false;
        if (e instanceof EntityPlayer) {
            if (!players.getValue()) return false;
            EntityPlayer p = (EntityPlayer) e;
            if (TeamUtil.isFriend(p)) return false;
            if (teams.getValue() && TeamUtil.isSameTeam(p)) return false;
            if (botCheck.getValue() && TeamUtil.isBot(p)) return false;
            return true;
        }
        return false;
    }

    private double distanceTo(Vec3 v) {
        return mc.thePlayer.getDistance(v.xCoord, v.yCoord, v.zCoord);
    }

    public static boolean runWithNearestTrackedDistance(net.minecraft.entity.Entity entity, Supplier<Boolean> action) {
        if (instance == null || !instance.isEnabled()) {
            return action.get();
        }
        return action.get();
    }

    @Override
    public String[] getSuffix() {
        if (mode.getValue() == 1) return new String[]{ "BUFFER", packetMode.getModeString() };
        return new String[]{ currentMaxDelay() + "ms" };
    }
}