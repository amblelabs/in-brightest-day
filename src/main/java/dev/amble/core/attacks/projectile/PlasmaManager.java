package dev.amble.core.attacks.projectile;

import dev.amble.core.walls.HardLightDamage;
import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class PlasmaManager {
    private static final double SPEED = 0.7;
    private static final double GRAVITY = 0.012;
    private static final int MAX_AGE = 100;
    private static final double HIT_PADDING = 0.5;
    private static final float MIN_DAMAGE = 8.0F;
    private static final float MAX_DAMAGE = 22.0F;
    private static final double MIN_RADIUS = 2.5;
    private static final double MAX_RADIUS = 5.0;
    private static final double MIN_FALLOFF = 0.4;
    private static final int BURN_TICKS = 100;
    private static final int FIRE_ATTEMPTS = 24;
    private static final double KNOCKBACK = 1.2;
    private static final float FULL_POWER = 0.99F;

    private static final List<Orb> ORBS = new ArrayList<>();

    private static final class Orb {
        final ServerPlayer owner;
        final ServerLevel level;
        final int color;
        final float power;
        Vec3 position;
        Vec3 velocity;
        int age;

        Orb(ServerPlayer owner, int color, float power, Vec3 position, Vec3 velocity) {
            this.owner = owner;
            this.level = owner.level();
            this.color = color;
            this.power = power;
            this.position = position;
            this.velocity = velocity;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PlasmaManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ORBS.clear());
    }

    public static void clear(ServerPlayer player) {
        ORBS.removeIf(orb -> orb.owner == player);
    }

    public static void launch(ServerPlayer player, int color, float power) {
        Vec3 look = player.getLookAngle();
        ORBS.add(new Orb(player, color, power, player.getEyePosition().add(look.scale(0.8)), look.scale(SPEED)));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    private static void tick(MinecraftServer server) {
        Iterator<Orb> iterator = ORBS.iterator();
        while (iterator.hasNext()) {
            Orb orb = iterator.next();
            if (orb.owner.isRemoved() || ++orb.age > MAX_AGE) {
                iterator.remove();
                detonate(orb, orb.position);
                continue;
            }

            Vec3 next = orb.position.add(orb.velocity);
            HitResult block = orb.level.clip(new ClipContext(orb.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, orb.owner));
            Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();

            AABB sweep = new AABB(orb.position, end).inflate(HIT_PADDING);
            boolean struck = !orb.level.getEntities(orb.owner, sweep, entity -> RingTargets.isTarget(orb.owner, entity)).isEmpty();
            if (struck || block.getType() != HitResult.Type.MISS) {
                iterator.remove();
                detonate(orb, end);
                continue;
            }

            trail(orb, next);
            orb.position = next;
            orb.velocity = orb.velocity.add(0.0, -GRAVITY, 0.0);
        }
    }

    private static void trail(Orb orb, Vec3 at) {
        float size = 1.5F + orb.power * 2.0F;
        orb.level.sendParticles(new DustParticleOptions(orb.color, Math.min(size, 4.0F)), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
        orb.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
        if (orb.age % 8 == 0) orb.level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private static void detonate(Orb orb, Vec3 impact) {
        ServerLevel level = orb.level;
        double radius = Mth.lerp(orb.power, MIN_RADIUS, MAX_RADIUS);
        float damage = Mth.lerp(orb.power, MIN_DAMAGE, MAX_DAMAGE);
        DamageSource source = RingDamage.source(level, orb.owner);

        for (Entity entity : level.getEntities(orb.owner, new AABB(impact, impact).inflate(radius), entity -> !Mannequins.isHologram(entity) && !RingTargets.allied(orb.owner, entity))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            double distance = center.distanceTo(impact);
            if (distance > radius) continue;

            double falloff = Math.max(MIN_FALLOFF, 1.0 - distance / radius);
            boolean hittable = RingTargets.isHittable(entity);
            RingTargets.hurt(level, entity, source, (float) (damage * falloff));
            if (hittable && RingTargets.root(entity) instanceof LivingEntity living) {
                if (orb.power >= FULL_POWER && !living.isAlive()) RingRanks.fire(orb.owner, Trigger.PLASMA_KILL, Milestone.Context.of(living));
                living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), BURN_TICKS));
            }
            Vec3 away = center.subtract(impact);
            if (away.lengthSqr() > 1.0E-4) {
                entity.push(away.normalize().scale(KNOCKBACK * falloff).add(0.0, 0.3 * falloff, 0.0));
                entity.needsSync = true;
            }
        }

        HardLightDamage.burst(level, impact, radius, damage, orb.owner);
        if (BrightestDayConfig.get().blastBreaksBlocks) ignite(level, impact, radius);

        level.sendParticles(new DustParticleOptions(orb.color, 4.0F), impact.x, impact.y, impact.z, 60, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
        level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 40, radius * 0.3, radius * 0.3, radius * 0.3, 0.08);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.7F);
    }

    private static void ignite(ServerLevel level, Vec3 impact, double radius) {
        BlockPos origin = BlockPos.containing(impact);
        int reach = Mth.ceil(radius);
        for (int i = 0; i < FIRE_ATTEMPTS; i++) {
            BlockPos pos = origin.offset(level.getRandom().nextInt(reach * 2 + 1) - reach, level.getRandom().nextInt(3) - 1,
                    level.getRandom().nextInt(reach * 2 + 1) - reach);
            if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        }
    }

    private PlasmaManager() {}
}
