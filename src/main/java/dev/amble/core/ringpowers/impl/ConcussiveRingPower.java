package dev.amble.core.ringpowers.impl;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.ringpowers.CorpsCombat;
import com.mojang.serialization.MapCodec;
import dev.amble.BrightestDay;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.ConcussiveS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import dev.amble.core.ringpowers.RingPowerRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

public class ConcussiveRingPower extends RingPower<Unit> {
    private static final double LIFT = 0.3;
    private static final double MIN_STRENGTH = 0.35;

    private static final Map<ServerPlayer, Long> LAST_FIRED = new WeakHashMap<>();

    public ConcussiveRingPower() {
        super(BrightestDay.id("concussive_blast"), EnumSet.complementOf(EnumSet.of(LanternCorps.BLUE)), MapCodec.unitCodec(Unit.INSTANCE));
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.UTILITY;
    }

    @Override
    public Unit createData() {
        return Unit.INSTANCE;
    }

    public static void fire(ServerPlayer player) {
        if (player.isSpectator() || BrightestDayAttachments.get(player, RingPowerRegistry.CONCUSSIVE).isEmpty()) return;

        BrightestDayConfig config = BrightestDayConfig.get();
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_FIRED.get(player);
        if (last != null && now - last < config.concussiveCooldownTicks) return;

        if (!player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, CorpsCombat.utilityCost(player, config.concussiveCost))) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }
        LAST_FIRED.put(player, now);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = config.concussiveRange;
        double coneCos = Math.cos(Math.toRadians(config.concussiveConeDegrees / 2.0));

        for (Entity entity : level.getEntities(player, new AABB(eye, eye).inflate(range), entity -> !Mannequins.isHologram(entity) && !entity.isSpectator() && !RingTargets.allied(player, entity) && entity.isPickable())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 1.0E-3) continue;

            double cos = to.dot(look) / distance;
            if (cos < coneCos || !player.hasLineOfSight(entity)) continue;

            double centered = coneCos >= 1.0 ? 1.0 : (cos - coneCos) / (1.0 - coneCos);
            double strength = config.concussiveKnockback * Math.max(MIN_STRENGTH, (1.0 - distance / range) * (0.5 + 0.5 * centered));
            RingTargets.hurt(level, entity, RingDamage.source(level, player), config.concussiveDamage);
            entity.push(to.normalize().add(look).normalize().scale(strength).add(0.0, LIFT * strength, 0.0));
            entity.needsSync = true;
        }

        ArmedRingPower.raise(player);
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.6F);

        ConcussiveS2CPayload payload = new ConcussiveS2CPayload(player.getId(), look, CorpsColors.of(player));
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }
}
