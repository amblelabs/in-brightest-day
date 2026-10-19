package dev.amble.core.ringpowers.constructs;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.core.sphere.ContainmentSphere;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.core.networking.payloads.s2c.BlastS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class BlastConstruct extends ConstructRingPower {
    private static final double LIFT = 0.35;
    private static final int MAX_SIZE = 5;
    private static final float BLAST_BATTERY_DAMAGE = 6.0F;
    private static final int EMPOWERED_MAX_SIZE = 6;
    private static final float SCALE_PER_SIZE = 0.4F;
    private static final float KNOCKBACK_PER_SIZE = 0.2F;
    private static final int[] COST_MULTIPLIERS = {1, 2, 5, 6, 8, 10};

    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    public BlastConstruct() {
        super(BrightestDay.id("blast"), CorpsArsenal.shared(LanternCorps.RED));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().blastCost;
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int maxSize() {
        return MAX_SIZE;
    }

    @Override
    public int empoweredMaxSize() {
        return EMPOWERED_MAX_SIZE;
    }

    @Override
    public int cost(int size) {
        int clamped = Math.clamp(this.costSize(size), 1, COST_MULTIPLIERS.length);
        return this.useCost() * COST_MULTIPLIERS[clamped - 1];
    }

    @Override
    public Component describeSize(int size) {
        return Component.translatable("construct.brightestday.blast.size", this.clampSize(size), this.cost(size));
    }

    public static float scale(int size) {
        return 1.0F + SCALE_PER_SIZE * (Math.max(size, 1) - 1);
    }

    @Override
    public int chargeTicks() {
        return BrightestDayConfig.get().blastChargeTicks;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {
        ServerLevel level = player.level();
        ContainmentSphere.breakOut(player);
        BrightestDayConfig config = BrightestDayConfig.get();
        double range = config.blastRange;
        float scale = scale(radius);
        double blastRadius = config.blastRadius * scale;
        Aim aim = aim(player, range);
        Vec3 impact = aim.end();
        Entity direct = aim.entity();
        if (direct == null) {
            direct = seek(player, aim.eye(), aim.look(), config);
            if (direct != null) impact = direct.getBoundingBox().getCenter();
        }
        boolean hit = direct != null || aim.eye().distanceTo(impact) < range - 1.0E-3;
        DamageSource source = RingDamage.source(level, player);

        for (Entity entity : level.getEntities(player, new AABB(impact, impact).inflate(blastRadius), entity -> !Mannequins.isHologram(entity) && !RingTargets.allied(player, entity))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            double distance = center.distanceTo(impact);
            if (distance > blastRadius && entity != direct) continue;

            double falloff = entity == direct ? 1.0 : 1.0 - distance / blastRadius;
            float damage = (float) (config.blastSplashDamage * scale * falloff) + (entity == direct ? config.blastDirectDamage : 0.0F);
            RingTargets.hurt(level, entity, source, damage);

            Vec3 away = center.subtract(impact);
            away = away.lengthSqr() < 1.0E-4 ? aim.look() : away.normalize();
            Vec3 push = away.add(aim.look()).normalize().scale(config.blastKnockback * (1.0F + KNOCKBACK_PER_SIZE * (Math.max(radius, 1) - 1)) * falloff).add(0.0, LIFT * falloff, 0.0);
            entity.push(push);
            entity.needsSync = true;
        }

        if (hit) CentralPowerBattery.blast(level, impact, blastRadius, Math.round(BLAST_BATTERY_DAMAGE * scale), player);
        if (hit) {
            level.explode(player, null, BLOCKS_ONLY, impact.x, impact.y, impact.z, config.blastExplosionPower * scale, false,
                    config.blastBreaksBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 0.8F);

        BlastS2CPayload payload = new BlastS2CPayload(player.getId(), impact, color, scale);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private static @Nullable Entity seek(ServerPlayer player, Vec3 eye, Vec3 look, BrightestDayConfig config) {
        double range = config.blastRange;
        double assistCone = Math.cos(config.blastAssistConeDegrees * Mth.DEG_TO_RAD);
        double homingCone = Math.cos(config.blastHomingConeDegrees * Mth.DEG_TO_RAD);
        ServerLevel level = player.level();
        Entity best = null;
        double bestCos = -1.0;
        for (Entity entity : level.getEntities(player, new AABB(eye, eye).inflate(range),
                entity -> entity.isPickable() && RingTargets.isTarget(player, entity))) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 1.0E-3) continue;

            double cos = to.dot(look) / distance;
            double cone = isAirborne(entity) ? homingCone : assistCone;
            if (cos < cone || cos <= bestCos) continue;
            if (!player.hasLineOfSight(entity)) continue;

            best = entity;
            bestCos = cos;
        }
        return best;
    }

    private static boolean isAirborne(Entity entity) {
        return !entity.onGround() && !entity.isInWater() && !entity.isPassenger();
    }
}
