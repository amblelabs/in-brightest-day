package dev.amble.core.attacks.area;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.core.networking.payloads.s2c.SlamS2CPayload;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class SlamManager {
    private static final double SLAM_SPEED = 2.8;
    private static final int FORCE_TICKS = 3;
    private static final int MAX_TICKS = 100;
    private static final double OUTWARD = 0.6;
    private static final double MIN_FALLOFF = 0.3;
    private static final double VERTICAL_REACH = 2.5;
    private static final int DUST_POINTS = 28;
    private static final int PROBE_UP = 1;
    private static final int PROBE_DOWN = 3;

    private static final Map<ServerPlayer, Slam> SLAMS = new HashMap<>();

    private static final class Slam {
        final double startY;
        final int color;
        int age;

        Slam(double startY, int color) {
            this.startY = startY;
            this.color = color;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SlamManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SLAMS.remove(handler.player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && SLAMS.containsKey(player)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SLAMS.clear());
    }

    public static boolean isSlamming(ServerPlayer player) {
        return SLAMS.containsKey(player);
    }

    public static boolean canSlam(ServerPlayer player) {
        return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger() && !player.isSpectator();
    }

    public static void start(ServerPlayer player, int color) {
        if (SLAMS.containsKey(player)) return;
        if (FlightRingPower.canFly(player)) FlightRingPower.setEnabled(player, false);
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (player.isFallFlying()) player.stopFallFlying();

        SLAMS.put(player, new Slam(player.getY(), color));
        drive(player);
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    public static void stop(ServerPlayer player) {
        SLAMS.remove(player);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : new ArrayList<>(SLAMS.keySet())) {
            Slam slam = SLAMS.get(player);
            player.resetFallDistance();
            if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.isPassenger() || player.isInWater() || player.isInLava() || ++slam.age > MAX_TICKS) {
                SLAMS.remove(player);
                continue;
            }
            if (player.onGround()) {
                SLAMS.remove(player);
                impact(player, slam);
                continue;
            }
            if (slam.age <= FORCE_TICKS) drive(player);
        }
    }

    private static void drive(ServerPlayer player) {
        player.setDeltaMovement(0.0, -SLAM_SPEED, 0.0);
        player.syncVelocity = true;
    }

    private static void impact(ServerPlayer player, Slam slam) {
        ServerLevel level = player.level();
        Vec3 center = player.position();
        BrightestDayConfig config = BrightestDayConfig.get();
        double fall = Math.max(slam.startY - center.y, 0.0);
        double minRadius = config.slamMinRadius;
        double maxRadius = Math.max(config.slamMaxRadius, minRadius);
        double radius = Mth.clamp(minRadius + fall * config.slamRadiusPerBlock, minRadius, maxRadius);
        float damage = Math.min(config.slamBaseDamage + (float) fall * config.slamDamagePerBlock, config.slamMaxDamage);
        DamageSource source = RingDamage.source(level, player);

        for (Entity entity : level.getEntities(player, new AABB(center, center).inflate(radius, VERTICAL_REACH, radius), entity -> !Mannequins.isHologram(entity) && !entity.isSpectator() && !RingTargets.allied(player, entity) && entity.isPickable())) {
            Vec3 to = entity.position().subtract(center);
            double distance = to.horizontalDistance();
            if (distance > radius || Math.abs(to.y) > VERTICAL_REACH) continue;

            double falloff = Math.max(MIN_FALLOFF, 1.0 - distance / radius);
            RingTargets.hurt(level, entity, source, (float) (damage * falloff));
            Vec3 away = distance < 1.0E-3 ? Vec3.ZERO : new Vec3(to.x / distance, 0.0, to.z / distance);
            entity.push(away.scale(OUTWARD * falloff).add(0.0, config.slamLaunch * falloff, 0.0));
            entity.needsSync = true;
        }

        dust(level, center, radius);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.7F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.6F);

        AreaAttacks.broadcast(player, new SlamS2CPayload(center, (float) radius, slam.color));
    }

    private static void dust(ServerLevel level, Vec3 center, double radius) {
        puff(level, center.x, center.y, center.z, 40, 0.8, 0.2);
        for (int i = 0; i < DUST_POINTS; i++) {
            double angle = (double) i / DUST_POINTS * Mth.TWO_PI;
            double distance = radius * (0.35 + 0.65 * level.getRandom().nextDouble());
            puff(level, center.x + Math.cos(angle) * distance, center.y, center.z + Math.sin(angle) * distance, 8, 0.4, 0.15);
        }
    }

    private static void puff(ServerLevel level, double x, double y, double z, int count, double spread, double speed) {
        BlockPos pos = ground(level, x, y, z);
        if (pos == null) return;
        BlockState state = level.getBlockState(pos);
        double top = pos.getY() + state.getCollisionShape(level, pos).max(Direction.Axis.Y);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, top + 0.1, z, count, spread, 0.1, spread, speed);
    }

    private static @Nullable BlockPos ground(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(y) + PROBE_UP;
        int bottom = Mth.floor(y) - PROBE_DOWN;
        for (int by = top; by >= bottom; by--) {
            pos.set(Mth.floor(x), by, Mth.floor(z));
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) return pos.immutable();
        }
        return null;
    }

    private SlamManager() {}
}
