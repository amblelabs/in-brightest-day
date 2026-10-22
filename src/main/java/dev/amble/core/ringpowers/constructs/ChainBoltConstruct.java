package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.projectile.ProjectileTargeting;
import dev.amble.core.networking.payloads.s2c.ChainBoltS2CPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChainBoltConstruct extends ConstructRingPower {
    private static final double ASSIST_CONE_DEGREES = 12.0;
    private static final double LIFT = 0.15;

    public ChainBoltConstruct() {
        super(BrightestDay.id("chain_bolt"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().chainCost;
    }

    @Override
    public int cooldownTicks() {
        return 20;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ServerLevel level = player.level();
        BrightestDayConfig config = BrightestDayConfig.get();
        double range = config.chainRange;
        Aim aim = aim(player, range);
        Entity current = aim.entity() != null && ProjectileTargeting.isTarget(player, aim.entity())
                ? aim.entity()
                : ProjectileTargeting.seek(player, aim.eye(), aim.look(), range, ASSIST_CONE_DEGREES);

        List<Vec3> points = new ArrayList<>();
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
        if (current == null) {
            points.add(aim.end());
            ProjectileTargeting.broadcast(player, new ChainBoltS2CPayload(player.getId(), points, false, color));
            return;
        }

        Set<Entity> struck = new HashSet<>();
        Vec3 from = aim.eye();
        float damage = config.chainDamage;
        int maxJumps = Math.clamp(config.chainMaxJumps, 0, 14);
        for (int jump = 0; jump <= maxJumps && current != null; jump++) {
            Vec3 center = current.getBoundingBox().getCenter();
            Vec3 direction = center.subtract(from);
            direction = direction.lengthSqr() < 1.0E-4 ? aim.look() : direction.normalize();
            ProjectileTargeting.strike(player, current, damage, direction.scale(config.chainKnockback).add(0.0, LIFT, 0.0));
            level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F + jump * 0.05F);
            struck.add(current);
            points.add(center);

            from = center;
            damage *= config.chainFalloff;
            current = next(player, current, struck, config.chainJumpRange);
        }
        ProjectileTargeting.broadcast(player, new ChainBoltS2CPayload(player.getId(), points, true, color));
    }

    private static @Nullable Entity next(ServerPlayer player, Entity from, Set<Entity> struck, double jumpRange) {
        Set<Entity> skip = new HashSet<>(struck);
        while (true) {
            Entity candidate = ProjectileTargeting.nearest(player, from.getBoundingBox().getCenter(), jumpRange, skip);
            if (candidate == null) return null;
            if (!(from instanceof LivingEntity living) || living.hasLineOfSight(candidate)) return candidate;
            skip.add(candidate);
        }
    }
}
