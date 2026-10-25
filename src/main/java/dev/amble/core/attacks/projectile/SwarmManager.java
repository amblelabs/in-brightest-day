package dev.amble.core.attacks.projectile;

import dev.amble.core.walls.HardLightDamage;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.networking.payloads.s2c.SwarmS2CPayload;
import dev.amble.core.team.RingTargets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class SwarmManager {
    private static final double RETARGET_RANGE = 10.0;
    private static final int MAX_AGE = 40;
    private static final int FAN_TICKS = 4;
    private static final double LAUNCH_SPEED = 0.45;
    private static final double FAN_SPEED = 0.4;
    private static final double MAX_SPEED = 1.6;
    private static final double ACCELERATION = 0.12;
    private static final double MIN_TURN = 0.18;
    private static final double MAX_TURN = 0.6;
    private static final double HIT_PADDING = 0.3;
    private static final double LIFT = 0.1;

    private static final List<Volley> VOLLEYS = new ArrayList<>();
    private static int nextId;

    private static final class Dart {
        Vec3 position;
        Vec3 velocity;
        @Nullable Entity target;
        boolean alive = true;

        Dart(Vec3 position, Vec3 velocity, @Nullable Entity target) {
            this.position = position;
            this.velocity = velocity;
            this.target = target;
        }
    }

    private static final class Volley {
        final int id;
        final ServerPlayer owner;
        final int color;
        final ServerLevel level;
        final List<Dart> darts;
        final Vec3 origin;
        final Vec3 forward;
        int age;

        Volley(int id, ServerPlayer owner, int color, List<Dart> darts, Vec3 origin, Vec3 forward) {
            this.id = id;
            this.owner = owner;
            this.color = color;
            this.level = owner.level();
            this.darts = darts;
            this.origin = origin;
            this.forward = forward;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SwarmManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VOLLEYS.clear());
    }

    public static void clear(ServerPlayer player) {
        VOLLEYS.removeIf(volley -> volley.owner == player);
    }

    public static void launch(ServerPlayer player, int color) {
        BrightestDayConfig config = BrightestDayConfig.get();
        int count = Math.clamp(config.swarmDarts, 1, 16);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        Vec3 origin = ProjectileTargeting.hand(player);
        List<Entity> targets = ProjectileTargeting.inCone(player, eye, look, config.swarmRange, config.swarmConeDegrees);

        List<Dart> darts = new ArrayList<>(count);
        float offset = player.getRandom().nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            double angle = offset + (double) i / count * Mth.TWO_PI;
            Vec3 fan = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle) * 0.7 + 0.3));
            Vec3 velocity = look.scale(LAUNCH_SPEED).add(fan.normalize().scale(FAN_SPEED));
            Entity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            darts.add(new Dart(origin, velocity, target));
        }

        Volley volley = new Volley(nextId++, player, color, darts, origin, look);
        VOLLEYS.add(volley);
        ProjectileTargeting.broadcast(player, new SwarmS2CPayload(volley.id, color, positions(volley), alive(volley), 0));
    }

    private static void tick(MinecraftServer server) {
        Iterator<Volley> iterator = VOLLEYS.iterator();
        while (iterator.hasNext()) {
            Volley volley = iterator.next();
            ServerPlayer owner = volley.owner;
            int burst = 0;
            volley.age++;
            if (owner.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (owner.level() != volley.level) {
                iterator.remove();
                ProjectileTargeting.broadcast(owner, new SwarmS2CPayload(volley.id, volley.color, positions(volley), 0, alive(volley)));
                continue;
            }

            for (int i = 0; i < volley.darts.size(); i++) {
                Dart dart = volley.darts.get(i);
                if (!dart.alive) continue;
                if (step(volley, dart)) {
                    dart.alive = false;
                    burst |= 1 << i;
                }
            }

            int alive = alive(volley);
            ProjectileTargeting.broadcast(owner, new SwarmS2CPayload(volley.id, volley.color, positions(volley), alive, burst));
            if (alive == 0) iterator.remove();
        }
    }

    private static boolean step(Volley volley, Dart dart) {
        ServerPlayer owner = volley.owner;
        ServerLevel level = volley.level;
        BrightestDayConfig config = BrightestDayConfig.get();
        if (dart.target != null && (!RingTargets.isHittable(dart.target) || dart.target.level() != level)) {
            dart.target = ProjectileTargeting.nearest(owner, dart.position, RETARGET_RANGE, Set.of());
        }

        double speed = Math.min(dart.velocity.length() + ACCELERATION, MAX_SPEED);
        if (volley.age > FAN_TICKS) {
            Vec3 goal = dart.target != null ? dart.target.getBoundingBox().getCenter().subtract(dart.position) : volley.forward;
            double turn = Mth.lerp(Math.min((volley.age - FAN_TICKS) / 10.0, 1.0), MIN_TURN, MAX_TURN);
            Vec3 desired = goal.lengthSqr() < 1.0E-6 ? dart.velocity : goal.normalize();
            dart.velocity = dart.velocity.normalize().lerp(desired, turn).normalize().scale(speed);
        } else {
            dart.velocity = dart.velocity.scale(0.92);
        }

        Vec3 from = dart.position;
        Vec3 to = from.add(dart.velocity);
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (blockHit.getType() == HitResult.Type.BLOCK) to = blockHit.getLocation();

        List<Entity> hits = ProjectileTargeting.along(owner, from, to, HIT_PADDING, Set.of());
        if (!hits.isEmpty()) {
            Entity hit = hits.getFirst();
            dart.position = hit.getBoundingBox().getCenter();
            Vec3 push = dart.velocity.normalize().scale(config.swarmKnockback).add(0.0, LIFT, 0.0);
            ProjectileTargeting.strike(owner, hit, config.swarmDamage, push);
            pop(level, dart.position);
            return true;
        }

        dart.position = to;
        boolean spent = dart.target == null && dart.position.distanceTo(volley.origin) > config.swarmRange;
        if (blockHit.getType() == HitResult.Type.BLOCK) HardLightDamage.hit(level, blockHit, config.swarmDamage, owner);
        if (blockHit.getType() == HitResult.Type.BLOCK || spent || volley.age >= MAX_AGE) {
            pop(level, dart.position);
            return true;
        }
        return false;
    }

    private static void pop(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.5F, 1.6F + level.getRandom().nextFloat() * 0.3F);
    }

    private static List<Vec3> positions(Volley volley) {
        List<Vec3> positions = new ArrayList<>(volley.darts.size());
        for (Dart dart : volley.darts) positions.add(dart.position);
        return positions;
    }

    private static int alive(Volley volley) {
        int mask = 0;
        for (int i = 0; i < volley.darts.size(); i++) {
            if (volley.darts.get(i).alive) mask |= 1 << i;
        }
        return mask;
    }

    private SwarmManager() {}
}
