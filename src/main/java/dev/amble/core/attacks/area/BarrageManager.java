package dev.amble.core.attacks.area;

import dev.amble.core.walls.HardLightDamage;
import dev.amble.core.mannequin.Mannequins;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.BarrageBoltS2CPayload;
import dev.amble.core.networking.payloads.s2c.BarrageS2CPayload;
import dev.amble.core.ringpowers.constructs.RapidBarrageConstruct;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class BarrageManager {
    private static final double ASSIST_DEGREES = 5.0;
    private static final double PUSH = 0.12;

    private static final Map<ServerPlayer, Barrage> BARRAGES = new HashMap<>();

    private static final class Barrage {
        final int color;
        int age;

        Barrage(int color) {
            this.color = color;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BarrageManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BARRAGES.clear());
    }

    public static void start(ServerPlayer player, int color) {
        if (BARRAGES.containsKey(player)) return;
        Barrage barrage = new Barrage(color);
        BARRAGES.put(player, barrage);
        AreaAttacks.broadcast(player, new BarrageS2CPayload(player.getId(), color, true));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.8F);
        shoot(player, barrage);
    }

    public static void stop(ServerPlayer player) {
        Barrage barrage = BARRAGES.remove(player);
        if (barrage == null) return;
        AreaAttacks.broadcast(player, new BarrageS2CPayload(player.getId(), barrage.color, false));
    }

    public static boolean isFiring(Entity entity) {
        return entity instanceof ServerPlayer player && BARRAGES.containsKey(player);
    }

    private static void tick(MinecraftServer server) {
        boolean drainTick = server.getTickCount() % 20 == 0;
        BrightestDayConfig config = BrightestDayConfig.get();

        for (ServerPlayer player : new ArrayList<>(BARRAGES.keySet())) {
            Barrage barrage = BARRAGES.get(player);
            boolean selected = ArmedRingPower.selectedConstruct(player).orElse(null) instanceof RapidBarrageConstruct;
            boolean outOfCharge = !PowerRingItem.hasCharge(player)
                    || drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, config.barrageDrainPerSecond);
            if (++barrage.age > config.barrageMaxTicks || player.isRemoved() || !player.isAlive() || player.isSpectator() || !ArmedRingPower.isArmed(player) || !selected || outOfCharge) {
                stop(player);
                continue;
            }

            if (barrage.age % Math.max(config.barrageFireInterval, 1) == 0) shoot(player, barrage);
        }
    }

    private static void shoot(ServerPlayer player, Barrage barrage) {
        ServerLevel level = player.level();
        BrightestDayConfig config = BrightestDayConfig.get();
        double range = config.barrageRange;
        RandomSource random = player.getRandom();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Entity assisted = seek(player, eye, look, range);
        Vec3 direction = assisted != null ? assisted.getBoundingBox().getCenter().subtract(eye).normalize() : look;
        double spread = Math.tan(config.barrageSpreadDegrees * Mth.DEG_TO_RAD);
        direction = direction.add(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(spread)).normalize();

        Vec3 end = eye.add(direction.scale(range));
        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean hit = blockHit.getType() != HitResult.Type.MISS;
        if (hit) end = blockHit.getLocation();

        AABB searchArea = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, end, searchArea,
                entity -> !Mannequins.isHologram(entity) && entity != player && !entity.isSpectator() && entity.isPickable(), 0.3F);
        if (entityHit != null) {
            end = entityHit.getLocation();
            hit = true;
            Entity target = entityHit.getEntity();
            RingTargets.strike(level, target, RingDamage.source(level, player), config.barrageDamage);
            target.push(direction.scale(PUSH));
            target.needsSync = true;
            level.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.6F + random.nextFloat() * 0.3F);
        } else if (hit) {
            HardLightDamage.hit(level, blockHit, config.barrageDamage, player);
        }

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.7F, 1.7F + random.nextFloat() * 0.3F);
        AreaAttacks.broadcast(player, new BarrageBoltS2CPayload(player.getId(), end, barrage.color, hit));
    }

    private static @Nullable Entity seek(ServerPlayer player, Vec3 eye, Vec3 look, double range) {
        double cone = Math.cos(ASSIST_DEGREES * Mth.DEG_TO_RAD);
        Entity best = null;
        double bestCos = -1.0;
        for (Entity entity : player.level().getEntities(player, new AABB(eye, eye).inflate(range),
                entity -> entity.isPickable() && RingTargets.isTarget(player, entity))) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 1.0E-3) continue;

            double cos = to.dot(look) / distance;
            if (cos < cone || cos <= bestCos || !player.hasLineOfSight(entity)) continue;

            best = entity;
            bestCos = cos;
        }
        return best;
    }

    private BarrageManager() {}
}
