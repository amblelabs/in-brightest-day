package dev.amble.core.attacks.weapon;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.core.networking.payloads.s2c.WhipS2CPayload;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class WhipManager {
    private static final int SWEEP_TICKS = 5;
    private static final double THICKNESS = 1.0;
    private static final double OUTWARD = 0.4;
    private static final double LIFT = 0.3;
    private static final int LASH_TICKS = 4;
    private static final double YANK_PER_BLOCK = 0.2;
    private static final double YANK_MAX = 2.2;
    private static final double YANK_LIFT = 0.35;

    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final List<Crack> CRACKS = new ArrayList<>();

    private static final class Crack {
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 forward;
        final Vec3 side;
        final Vec3 up;
        final float sweepSide;
        final float arc;
        final boolean lash;
        final double length;
        final @Nullable Entity target;
        final Set<Integer> hit = new HashSet<>();
        int age;

        Crack(ServerPlayer player, Vec3 forward, float sweepSide, float arc, boolean lash, double length, @Nullable Entity target) {
            this.player = player;
            this.level = player.level();
            this.forward = forward;
            this.side = side(forward);
            this.up = this.side.cross(forward).normalize();
            this.sweepSide = sweepSide;
            this.arc = arc;
            this.lash = lash;
            this.length = length;
            this.target = target;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WhipManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CRACKS.clear());
    }

    public static Vec3 side(Vec3 forward) {
        Vec3 side = forward.cross(UP);
        return side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
    }

    public static float sweepAngle(float progress, float arcDegrees, float sweepSide) {
        float t = Math.clamp(progress, 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        return sweepSide * arcDegrees * 0.5F * Mth.DEG_TO_RAD * (1.0F - 2.0F * eased);
    }

    public static void crack(ServerPlayer player, int color) {
        ServerLevel level = player.level();
        Vec3 forward = player.getLookAngle();
        float sweepSide = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        boolean lash = player.isShiftKeyDown();
        BrightestDayConfig config = BrightestDayConfig.get();
        float arc = config.whipArcDegrees;

        double length = config.whipLength;
        Entity target = null;
        if (lash) {
            ConstructRingPower.Aim aim = ConstructRingPower.aim(player, config.whipLashLength);
            length = Math.max(aim.eye().distanceTo(aim.end()), 1.0);
            target = aim.entity();
        }

        CRACKS.add(new Crack(player, forward, sweepSide, arc, lash, length, target));
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);

        WhipS2CPayload payload = new WhipS2CPayload(player.getId(), forward, color, lash, (float) length, arc, sweepSide, lash ? LASH_TICKS : SWEEP_TICKS);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Crack> iterator = CRACKS.iterator();
        while (iterator.hasNext()) {
            Crack crack = iterator.next();
            if (crack.player.isRemoved() || crack.player.level() != crack.level) {
                iterator.remove();
                continue;
            }

            crack.age++;
            int ticks = crack.lash ? LASH_TICKS : SWEEP_TICKS;
            if (crack.lash) {
                lash(crack, (float) crack.age / ticks);
            } else {
                sweep(crack, (float) (crack.age - 1) / ticks, (float) crack.age / ticks);
            }

            if (crack.age >= ticks) {
                Vec3 tip = tip(crack);
                crack.level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.9F);
                crack.level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.6F);
                iterator.remove();
            }
        }
    }

    private static Vec3 tip(Crack crack) {
        Vec3 eye = crack.player.getEyePosition();
        if (crack.lash) return eye.add(crack.forward.scale(crack.length));
        float angle = sweepAngle(1.0F, crack.arc, crack.sweepSide);
        return eye.add(crack.forward.scale(Mth.cos(angle) * crack.length)).add(crack.side.scale(Mth.sin(angle) * crack.length));
    }

    private static void sweep(Crack crack, float from, float to) {
        ServerPlayer player = crack.player;
        BrightestDayConfig config = BrightestDayConfig.get();
        Vec3 eye = player.getEyePosition();
        float start = sweepAngle(from, crack.arc, crack.sweepSide);
        float end = sweepAngle(to, crack.arc, crack.sweepSide);
        float low = Math.min(start, end);
        float high = Math.max(start, end);
        if (from <= 0.0F) {
            low -= 0.05F;
            high += 0.05F;
        }

        for (Entity entity : crack.level.getEntities(player, new AABB(eye, eye).inflate(crack.length + 1.0),
                entity -> !Mannequins.isHologram(entity) && entity.isAlive() && entity.isPickable() && !entity.isSpectator() && !RingTargets.allied(player, entity) && entity != player.getVehicle())) {
            if (crack.hit.contains(RingTargets.rootId(entity))) continue;

            Vec3 to3 = entity.getBoundingBox().getCenter().subtract(eye);
            double along = to3.dot(crack.forward);
            double across = to3.dot(crack.side);
            double vertical = to3.dot(crack.up);
            double planar = Math.sqrt(along * along + across * across);
            double reach = entity.getBbWidth() * 0.5;
            if (planar > crack.length + reach || Math.abs(vertical) > THICKNESS + entity.getBbHeight() * 0.5) continue;

            float angle = (float) Mth.atan2(across, along);
            float slack = planar < 1.0E-3 ? Mth.PI : (float) Math.atan2(reach, planar);
            if (angle < low - slack || angle > high + slack) continue;
            if (!player.hasLineOfSight(entity)) continue;

            crack.hit.add(RingTargets.rootId(entity));
            Vec3 radial = crack.forward.scale(Mth.cos(angle)).add(crack.side.scale(Mth.sin(angle)));
            Vec3 tangent = crack.forward.scale(Mth.sin(angle)).subtract(crack.side.scale(Mth.cos(angle))).scale(crack.sweepSide);
            RingTargets.hurt(crack.level, entity, RingDamage.source(crack.level, player), config.whipDamage);
            entity.push(tangent.scale(config.whipKnockback).add(radial.scale(OUTWARD)).add(0.0, LIFT, 0.0));
            entity.needsSync = true;
        }
    }

    private static void lash(Crack crack, float progress) {
        Entity target = crack.target;
        if (target == null || !target.isAlive() || crack.hit.contains(RingTargets.rootId(target))) return;

        ServerPlayer player = crack.player;
        Vec3 eye = player.getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        if (eye.distanceTo(center) > crack.length * progress + target.getBbWidth() + 0.5) return;

        crack.hit.add(RingTargets.rootId(target));
        RingTargets.hurt(crack.level, target, RingDamage.source(crack.level, player), BrightestDayConfig.get().whipLashDamage);
        Vec3 pull = player.position().subtract(target.position());
        double distance = pull.length();
        if (distance > 1.0E-3) {
            Vec3 yank = pull.scale(Math.min(distance * YANK_PER_BLOCK, YANK_MAX) / distance).add(0.0, YANK_LIFT, 0.0);
            target.push(yank);
            target.needsSync = true;
        }
        crack.level.playSound(null, center.x, center.y, center.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private WhipManager() {}
}
