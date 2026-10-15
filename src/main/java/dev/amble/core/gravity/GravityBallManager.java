package dev.amble.core.gravity;

import dev.amble.BrightestDay;
import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.networking.payloads.s2c.GravityBallS2CPayload;
import dev.amble.core.progression.WorldProgress;
import dev.amble.core.team.LanternTeams;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GravityBallManager {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final int LIFETIME = 200;
    private static final double BASE_RADIUS = 10.0;
    private static final double RADIUS_PER_LEVEL = 3.0;
    private static final double BASE_PULL = 0.05;
    private static final double PULL_PER_LEVEL = 0.03;
    private static final double HOLD_SPRING = 0.5;
    private static final double BURST_SPEED = 0.9;
    private static final double BURST_PER_LEVEL = 0.25;
    private static final double BURST_LIFT = 0.4;
    private static final int TEAR_LEVEL = 3;
    private static final float[] TEAR_HARDNESS = {0.8F, 2.0F, 3.5F};
    private static final int[] TEAR_LIMIT = {10, 24, 48};
    private static final int[] TEAR_INTERVAL = {8, 6, 4};
    private static final int BATTERY_CLEARANCE = 4;
    private static final int BATTERY_BELOW = 6;
    private static final int BATTERY_ABOVE = 5;
    private static final int CONTACT_LEVEL = 4;
    private static final float CONTACT_DAMAGE = 1.0F;
    private static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK, BrightestDay.id("tractor_immune"));

    private static final List<Ball> BALLS = new ArrayList<>();
    private static int nextId;

    private static final class Ball {
        final int id;
        final UUID owner;
        final ServerLevel level;
        final Vec3 center;
        final int strength;
        final int color;
        final long created;
        final boolean repels;
        int age;
        int torn;
        final List<Entity> held = new ArrayList<>();

        Ball(int id, ServerPlayer owner, Vec3 center, int strength, int color, boolean repels) {
            this.id = id;
            this.owner = owner.getUUID();
            this.level = owner.level();
            this.center = center;
            this.strength = strength;
            this.color = color;
            this.created = owner.level().getGameTime();
            this.repels = repels;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GravityBallManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BALLS.clear());
    }

    public static double radius(int strength) {
        return BASE_RADIUS + RADIUS_PER_LEVEL * (Mth.clamp(strength, MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL);
    }

    public static float visualRadius(int strength) {
        return 1.2F + 0.15F * Mth.clamp(strength, MIN_LEVEL, MAX_LEVEL);
    }

    public static void spawn(ServerPlayer owner, Vec3 center, int strength, int color, boolean repels) {
        dismissAll(owner);
        Ball ball = new Ball(nextId++, owner, center, Mth.clamp(strength, MIN_LEVEL, MAX_LEVEL), color, repels);
        BALLS.add(ball);
        broadcast(ball, true);
        ball.level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    public static long latestCreatedAt(UUID owner) {
        long latest = Long.MIN_VALUE;
        for (Ball ball : BALLS) if (ball.owner.equals(owner)) latest = Math.max(latest, ball.created);
        return latest;
    }

    public static void dismissAll(ServerPlayer owner) {
        for (Ball ball : List.copyOf(BALLS)) {
            if (ball.owner.equals(owner.getUUID())) collapse(ball);
        }
    }

    private static void collapse(Ball ball) {
        BALLS.remove(ball);
        broadcast(ball, false);
        if (!ball.repels) burst(ball);
        ball.level.playSound(null, ball.center.x, ball.center.y, ball.center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    private static void tick(MinecraftServer server) {
        for (Ball ball : List.copyOf(BALLS)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ball.owner);
            if (++ball.age > LIFETIME || owner == null) {
                collapse(ball);
                continue;
            }
            pull(ball, owner);
            if (!ball.repels && ball.strength >= TEAR_LEVEL && ball.age % TEAR_INTERVAL[ball.strength - TEAR_LEVEL] == 0) tear(ball, owner);
            if (ball.age % 4 == 0) {
                ball.level.sendParticles(ball.repels ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, ball.center.x, ball.center.y, ball.center.z, 6, radius(ball.strength) * 0.4, radius(ball.strength) * 0.4, radius(ball.strength) * 0.4, 0.0);
            }
        }
    }

    private static boolean pullable(Entity entity, ServerPlayer owner) {
        if (entity == owner || entity.isSpectator() || !entity.isAlive() || Mannequins.isHologram(entity)) return false;
        if (entity instanceof Player player && (player.isCreative() || LanternTeams.areTeammates(owner, player))) return false;
        if (entity instanceof LivingEntity) return RingTargets.isTarget(owner, entity);
        return entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity || entity instanceof Projectile;
    }

    private static void pull(Ball ball, ServerPlayer owner) {
        double radius = radius(ball.strength);
        double core = visualRadius(ball.strength) + 0.5;
        double pull = BASE_PULL + PULL_PER_LEVEL * (ball.strength - MIN_LEVEL);
        boolean contact = ball.strength >= CONTACT_LEVEL && ball.age % 20 == 0;
        ball.held.removeIf(entity -> entity.isRemoved() || !entity.isAlive());

        for (Entity entity : ball.level.getEntities(owner, new AABB(ball.center, ball.center).inflate(radius), entity -> ball.held.contains(entity) || pullable(entity, owner))) {
            Vec3 to = ball.center.subtract(entity.getBoundingBox().getCenter());
            double distance = to.length();
            if (!ball.held.contains(entity) && (distance > radius || distance < 1.0E-3)) continue;
            Vec3 inward = distance < 1.0E-3 ? Vec3.ZERO : to.scale(1.0 / distance);

            if (ball.repels) {
                double falloff = 1.0 - distance / radius;
                entity.setDeltaMovement(entity.getDeltaMovement().add(inward.scale(-pull * (0.6 + falloff * 2.0))).add(0.0, falloff * pull * 0.5, 0.0));
                if (contact && distance <= core && entity instanceof LivingEntity) RingTargets.hurt(ball.level, entity, RingDamage.source(ball.level, owner), CONTACT_DAMAGE);
            } else if (ball.held.contains(entity) || distance <= core) {
                if (!ball.held.contains(entity)) ball.held.add(entity);
                Vec3 slot = ball.center.add(slot(ball.held.indexOf(entity), visualRadius(ball.strength) * 0.45));
                Vec3 offset = slot.subtract(entity.getBoundingBox().getCenter());
                entity.setDeltaMovement(offset.scale(HOLD_SPRING));
                if (contact && entity instanceof LivingEntity) RingTargets.hurt(ball.level, entity, RingDamage.source(ball.level, owner), CONTACT_DAMAGE);
            } else {
                double falloff = 1.0 - distance / radius;
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.92).add(inward.scale(pull * (0.4 + falloff * 1.6))));
            }
            entity.resetFallDistance();
            if (entity instanceof FallingBlockEntity block) block.time = 1;
            if (entity instanceof ItemEntity item) item.setPickUpDelay(10);
            entity.needsSync = true;
        }
    }

    private static Vec3 slot(int index, double spread) {
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        double y = 1.0 - (index % 24) / 11.5;
        double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double theta = golden * index;
        double scale = spread * (0.5 + 0.5 * ((index / 24) % 2));
        return new Vec3(Math.cos(theta) * ring * scale, y * scale, Math.sin(theta) * ring * scale);
    }

    private static void burst(Ball ball) {
        if (ball.held.isEmpty()) return;
        double speed = BURST_SPEED + BURST_PER_LEVEL * (ball.strength - MIN_LEVEL);
        RandomSource random = ball.level.getRandom();
        for (int i = 0; i < ball.held.size(); i++) {
            Entity entity = ball.held.get(i);
            if (entity.isRemoved()) continue;
            Vec3 direction = slot(i, 1.0);
            if (direction.lengthSqr() < 1.0E-4) direction = new Vec3(random.nextDouble() - 0.5, 0.5, random.nextDouble() - 0.5);
            entity.setDeltaMovement(direction.normalize().scale(speed * (0.8 + random.nextDouble() * 0.4)).add(0.0, BURST_LIFT, 0.0));
            if (entity instanceof ItemEntity item) item.setPickUpDelay(20);
            entity.needsSync = true;
        }
        ball.held.clear();
        ball.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ball.center.x, ball.center.y, ball.center.z, 1, 0.0, 0.0, 0.0, 0.0);
        ball.level.playSound(null, ball.center.x, ball.center.y, ball.center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.8F);
    }

    private static void tear(Ball ball, ServerPlayer owner) {
        int tier = ball.strength - TEAR_LEVEL;
        if (ball.torn >= TEAR_LIMIT[tier]) return;
        RandomSource random = ball.level.getRandom();
        double reach = radius(ball.strength) * 0.5;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = BlockPos.containing(ball.center.add((random.nextDouble() * 2.0 - 1.0) * reach, (random.nextDouble() * 2.0 - 1.0) * reach, (random.nextDouble() * 2.0 - 1.0) * reach));
            BlockState state = ball.level.getBlockState(pos);
            if (!tearable(ball.level, pos, state, TEAR_HARDNESS[tier]) || !ball.level.mayInteract(owner, pos) || nearBattery(ball.level, pos)
                    || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(ball.level, owner, pos, state, null)) continue;
            FallingBlockEntity.fall(ball.level, pos, state);
            ball.torn++;
            return;
        }
    }

    private static boolean tearable(ServerLevel level, BlockPos pos, BlockState state, float maxHardness) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(IMMUNE)) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness <= maxHardness;
    }

    private static boolean nearBattery(ServerLevel level, BlockPos pos) {
        for (WorldProgress.Battery battery : WorldProgress.get(level.getServer()).batteries()) {
            if (!battery.in(level)) continue;
            BlockPos core = battery.pos();
            int dy = pos.getY() - core.getY();
            if (Math.abs(pos.getX() - core.getX()) <= BATTERY_CLEARANCE && Math.abs(pos.getZ() - core.getZ()) <= BATTERY_CLEARANCE
                    && dy >= -BATTERY_BELOW && dy <= BATTERY_ABOVE) return true;
        }
        return false;
    }

    private static void broadcast(Ball ball, boolean active) {
        GravityBallS2CPayload payload = new GravityBallS2CPayload(ball.id, active, ball.center.x, ball.center.y, ball.center.z, ball.strength, ball.color);
        for (ServerPlayer watcher : PlayerLookup.tracking(ball.level, BlockPos.containing(ball.center))) ServerPlayNetworking.send(watcher, payload);
    }

    private GravityBallManager() {}
}
