package dev.amble.core.attacks.projectile;

import dev.amble.core.walls.HardLightDamage;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.networking.payloads.s2c.DiscS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class DiscManager {
    private static final double OUT_SPEED = 1.3;
    private static final double RETURN_SPEED = 1.5;
    private static final double TURN_RATE = 0.22;
    private static final double OUT_DRAG = 0.035;
    private static final double MIN_OUT_SPEED = 0.25;
    private static final double CATCH_DISTANCE = 1.5;
    private static final int MAX_AGE = 100;
    private static final double HIT_PADDING = 0.6;
    private static final double LIFT = 0.15;
    private static final int HUM_INTERVAL = 6;

    private static final List<Disc> DISCS = new ArrayList<>();
    private static int nextId;

    private static final class Disc {
        final int id;
        final ServerPlayer owner;
        final ServerLevel level;
        final int color;
        final Vec3 origin;
        Vec3 position;
        Vec3 velocity;
        boolean returning;
        final Set<Entity> hit = new HashSet<>();
        int age;

        Disc(int id, ServerPlayer owner, int color, Vec3 origin, Vec3 velocity) {
            this.id = id;
            this.owner = owner;
            this.level = owner.level();
            this.color = color;
            this.origin = origin;
            this.position = origin;
            this.velocity = velocity;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DiscManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DISCS.clear());
    }

    public static void clear(ServerPlayer player) {
        DISCS.removeIf(disc -> disc.owner == player);
    }

    public static void dismiss(ServerPlayer player) {
        Iterator<Disc> iterator = DISCS.iterator();
        while (iterator.hasNext()) {
            Disc disc = iterator.next();
            if (disc.owner != player) continue;
            iterator.remove();
            send(disc, false);
        }
    }

    public static void launch(ServerPlayer player, int color) {
        Vec3 origin = ProjectileTargeting.hand(player);
        Disc disc = new Disc(nextId++, player, color, origin, player.getLookAngle().scale(OUT_SPEED));
        DISCS.add(disc);
        send(disc, true);
    }

    private static void tick(MinecraftServer server) {
        Iterator<Disc> iterator = DISCS.iterator();
        while (iterator.hasNext()) {
            Disc disc = iterator.next();
            ServerPlayer owner = disc.owner;
            if (owner.isRemoved()) {
                iterator.remove();
                continue;
            }

            boolean done = ++disc.age >= MAX_AGE || !owner.isAlive() || owner.level() != disc.level || step(disc);
            send(disc, !done);
            if (done) iterator.remove();
        }
    }

    private static boolean step(Disc disc) {
        ServerPlayer owner = disc.owner;
        ServerLevel level = disc.level;
        BrightestDayConfig config = BrightestDayConfig.get();
        Vec3 hand = ProjectileTargeting.hand(owner);

        if (!disc.returning) {
            double speed = disc.velocity.length() - OUT_DRAG;
            if (speed < MIN_OUT_SPEED || disc.position.distanceTo(disc.origin) >= config.discRange) {
                turn(disc);
            } else {
                disc.velocity = disc.velocity.normalize().scale(speed);
            }
        }
        if (disc.returning) {
            Vec3 toHand = hand.subtract(disc.position);
            if (toHand.length() < CATCH_DISTANCE) {
                level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 1.4F);
                return true;
            }
            Vec3 desired = toHand.normalize().scale(RETURN_SPEED);
            disc.velocity = disc.velocity.lerp(desired, TURN_RATE);
            if (disc.velocity.length() > toHand.length()) disc.velocity = toHand;
        }

        Vec3 from = disc.position;
        Vec3 to = from.add(disc.velocity);
        if (!disc.returning) {
            BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                to = blockHit.getLocation();
                HardLightDamage.hit(level, blockHit, config.discDamage, owner);
                turn(disc);
                disc.velocity = disc.velocity.scale(-0.3);
                level.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
            }
        }

        for (Entity entity : ProjectileTargeting.along(owner, from, to, HIT_PADDING, disc.hit)) {
            disc.hit.add(entity);
            Vec3 push = disc.velocity.normalize().scale(config.discKnockback).add(0.0, LIFT, 0.0);
            ProjectileTargeting.strike(owner, entity, config.discDamage, push);
            level.playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.2F);
        }

        disc.position = to;
        if (disc.age % HUM_INTERVAL == 0) {
            level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.8F);
        }
        return false;
    }

    private static void turn(Disc disc) {
        if (disc.returning) return;
        disc.returning = true;
        disc.hit.clear();
    }

    private static void send(Disc disc, boolean alive) {
        Vec3 direction = disc.velocity.lengthSqr() < 1.0E-6 ? disc.owner.getLookAngle() : disc.velocity.normalize();
        ProjectileTargeting.broadcast(disc.owner, new DiscS2CPayload(disc.id, disc.color, disc.position, direction, alive));
    }

    private DiscManager() {}
}
