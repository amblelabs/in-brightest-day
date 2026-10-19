package dev.amble.core.ringpowers.constructs;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.BrightestDay;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.area.AreaAttacks;
import dev.amble.core.networking.payloads.s2c.NovaS2CPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class NovaBurstConstruct extends ConstructRingPower {
    private static final double LIFT = 0.55;
    private static final double MIN_FALLOFF = 0.25;

    public NovaBurstConstruct() {
        super(BrightestDay.id("nova_burst"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().novaCost;
    }

    @Override
    public int chargeTicks() {
        return BrightestDayConfig.get().novaChargeTicks;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ServerLevel level = player.level();
        BrightestDayConfig config = BrightestDayConfig.get();
        double radius = config.novaRadius;
        Vec3 center = player.getBoundingBox().getCenter();
        DamageSource source = RingDamage.source(level, player);

        for (Entity entity : level.getEntities(player, new AABB(center, center).inflate(radius), entity -> !Mannequins.isHologram(entity) && !entity.isSpectator() && !RingTargets.allied(player, entity) && entity.isPickable())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(center);
            double distance = to.length();
            if (distance > radius || !player.hasLineOfSight(entity)) continue;

            double falloff = Math.max(MIN_FALLOFF, 1.0 - distance / radius);
            RingTargets.hurt(level, entity, source, (float) (config.novaDamage * falloff));

            Vec3 away = to.horizontalDistanceSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : to.multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            entity.push(away.scale(config.novaKnockback * falloff).add(0.0, LIFT + LIFT * falloff, 0.0));
            entity.needsSync = true;
        }

        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.4F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 1.5F);

        AreaAttacks.broadcast(player, new NovaS2CPayload(center, (float) radius, color));
    }
}
