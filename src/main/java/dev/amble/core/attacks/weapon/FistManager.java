package dev.amble.core.attacks.weapon;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.core.networking.payloads.s2c.FistS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class FistManager {
    private static final double START_DISTANCE = 2.0;
    private static final double DROP = 0.3;
    private static final double HALF_SIZE = 1.35;
    private static final int WINDUP_TICKS = 2;
    private static final int TRAVEL_TICKS = 7;
    private static final double LIFT = 0.45;
    private static final double IMPACT_KNOCKBACK = 1.0;

    private static final List<Punch> PUNCHES = new ArrayList<>();

    private static final class Punch {
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 direction;
        final double reach;
        final boolean impact;
        final Set<Integer> hit = new HashSet<>();
        int age;

        Punch(ServerPlayer player, Vec3 origin, Vec3 direction, double reach, boolean impact) {
            this.player = player;
            this.level = player.level();
            this.origin = origin;
            this.direction = direction;
            this.reach = reach;
            this.impact = impact;
        }

        Vec3 at(int tick) {
            float t = Math.clamp((float) (tick - WINDUP_TICKS) / TRAVEL_TICKS, 0.0F, 1.0F);
            float eased = 1.0F - (1.0F - t) * (1.0F - t);
            return this.origin.add(this.direction.scale(this.reach * eased));
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FistManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PUNCHES.clear());
    }

    public static void punch(ServerPlayer player, int color) {
        ServerLevel level = player.level();
        Vec3 direction = player.getLookAngle();
        double travel = BrightestDayConfig.get().fistRange;
        Vec3 origin = player.getEyePosition().add(direction.scale(START_DISTANCE)).add(0.0, -DROP, 0.0);
        Vec3 end = origin.add(direction.scale(travel + HALF_SIZE));

        double reach = travel;
        boolean impact = false;
        HitResult blockHit = level.clip(new ClipContext(player.getEyePosition(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            reach = Math.clamp(blockHit.getLocation().distanceTo(origin) - HALF_SIZE * 0.6, 0.0, travel);
            impact = true;
        }

        PUNCHES.add(new Punch(player, origin, direction, reach, impact));
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.7F);

        FistS2CPayload payload = new FistS2CPayload(origin, direction, (float) reach, color, WINDUP_TICKS, TRAVEL_TICKS, impact);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Punch> iterator = PUNCHES.iterator();
        while (iterator.hasNext()) {
            Punch punch = iterator.next();
            if (punch.player.isRemoved() || punch.player.level() != punch.level) {
                iterator.remove();
                continue;
            }

            Vec3 from = punch.at(punch.age);
            punch.age++;
            Vec3 to = punch.at(punch.age);
            sweep(punch, new AABB(from, to).inflate(HALF_SIZE));

            if (punch.age >= WINDUP_TICKS + TRAVEL_TICKS) {
                if (punch.impact) burst(punch, to.add(punch.direction.scale(HALF_SIZE)));
                iterator.remove();
            }
        }
    }

    private static void sweep(Punch punch, AABB box) {
        ServerPlayer player = punch.player;
        BrightestDayConfig config = BrightestDayConfig.get();
        for (Entity entity : punch.level.getEntities(player, box,
                entity -> !Mannequins.isHologram(entity) && entity.isAlive() && entity.isPickable() && !entity.isSpectator() && !RingTargets.allied(player, entity) && entity != player.getVehicle())) {
            if (!punch.hit.add(RingTargets.rootId(entity))) continue;

            RingTargets.hurt(punch.level, entity, RingDamage.source(punch.level, player), config.fistDamage);
            entity.push(punch.direction.scale(config.fistKnockback).add(0.0, LIFT, 0.0));
            entity.needsSync = true;
            Vec3 center = entity.getBoundingBox().getCenter();
            punch.level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.6F);
        }
    }

    private static void burst(Punch punch, Vec3 impact) {
        ServerPlayer player = punch.player;
        BrightestDayConfig config = BrightestDayConfig.get();
        double radius = config.fistImpactRadius;
        for (Entity entity : punch.level.getEntities(player, new AABB(impact, impact).inflate(radius),
                entity -> !Mannequins.isHologram(entity) && entity.isAlive() && entity.isPickable() && !entity.isSpectator() && !RingTargets.allied(player, entity))) {
            Vec3 away = entity.getBoundingBox().getCenter().subtract(impact);
            double distance = away.length();
            if (distance > radius) continue;

            double falloff = 1.0 - distance / radius;
            if (!punch.hit.contains(RingTargets.rootId(entity))) {
                RingTargets.hurt(punch.level, entity, RingDamage.source(punch.level, player), (float) (config.fistImpactDamage * falloff));
            }
            away = distance < 1.0E-3 ? punch.direction.reverse() : away.normalize();
            entity.push(away.scale(IMPACT_KNOCKBACK * falloff).add(0.0, LIFT * falloff, 0.0));
            entity.needsSync = true;
        }
        punch.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
        punch.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private FistManager() {}
}
