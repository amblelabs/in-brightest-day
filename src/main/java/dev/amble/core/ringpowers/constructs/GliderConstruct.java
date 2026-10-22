package dev.amble.core.ringpowers.constructs;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.config.BrightestDayConfig;
import dev.amble.BrightestDay;
import dev.amble.core.attacks.projectile.ProjectileTargeting;
import dev.amble.core.glide.GlideManager;
import dev.amble.core.networking.payloads.s2c.GlideBoltS2CPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GliderConstruct extends ConstructRingPower {

    public GliderConstruct() {
        super(BrightestDay.id("glider"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().gliderCost;
    }

    @Override
    public int cooldownTicks() {
        return 15;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ServerLevel level = player.level();
        Aim aim = aim(player, BrightestDayConfig.get().gliderRange);
        LivingEntity target = aim.entity() instanceof LivingEntity living && living.isAlive() ? living : seek(player, aim.eye(), aim.look());
        if (target == null) target = player;

        GlideManager.apply(target, player, color, BrightestDayConfig.get().gliderDurationTicks);
        ProjectileTargeting.broadcast(player, new GlideBoltS2CPayload(player.getId(), target.getId(), color));
        if (target instanceof ServerPlayer glider) glider.sendOverlayMessage(Component.translatable("message.brightestday.glider_granted"));

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.8F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static @Nullable LivingEntity seek(ServerPlayer player, Vec3 eye, Vec3 look) {
        double cone = Math.cos(BrightestDayConfig.get().gliderAssistConeDegrees * Mth.DEG_TO_RAD);
        LivingEntity best = null;
        double bestCos = -1.0;
        for (Entity entity : player.level().getEntities(player, new AABB(eye, eye).inflate(BrightestDayConfig.get().gliderRange),
                entity -> !Mannequins.isHologram(entity) && entity instanceof LivingEntity && entity.isAlive() && entity.isPickable() && !entity.isSpectator())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > BrightestDayConfig.get().gliderRange || distance < 1.0E-3) continue;

            double cos = to.dot(look) / distance;
            if (cos < cone || cos <= bestCos) continue;
            if (!player.hasLineOfSight(entity)) continue;

            best = (LivingEntity) entity;
            bestCos = cos;
        }
        return best;
    }
}
