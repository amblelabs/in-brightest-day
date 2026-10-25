package dev.amble.core.ringpowers.constructs;

import dev.amble.core.walls.HardLightDamage;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.projectile.ProjectileTargeting;
import dev.amble.core.networking.payloads.s2c.LanceS2CPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PiercingLanceConstruct extends ConstructRingPower {
    private static final double HIT_PADDING = 0.3;

    public PiercingLanceConstruct() {
        super(BrightestDay.id("piercing_lance"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().lanceCost;
    }

    @Override
    public int chargeTicks() {
        return BrightestDayConfig.get().lanceChargeTicks;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ServerLevel level = player.level();
        BrightestDayConfig config = BrightestDayConfig.get();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(config.lanceRange));
        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();
        HardLightDamage.hit(level, blockHit, config.lanceDamage, player);

        List<Vec3> hits = new ArrayList<>();
        for (Entity entity : ProjectileTargeting.along(player, eye, end, HIT_PADDING, Set.of())) {
            ProjectileTargeting.strike(player, entity, config.lanceDamage, look.scale(config.lanceKnockback));
            hits.add(entity.getBoundingBox().getCenter());
        }

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 0.7F);
        ProjectileTargeting.broadcast(player, new LanceS2CPayload(player.getId(), end, hits, color));
    }
}
