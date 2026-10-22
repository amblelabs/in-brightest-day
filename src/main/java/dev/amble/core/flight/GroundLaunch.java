package dev.amble.core.flight;

import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.GroundLaunchS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GroundLaunch {
    public static final int IDLE = 0;
    public static final int CHARGING = 1;
    public static final int LAUNCHED = 2;
    public static final int MIN_CHARGE = 8;
    public static final int FULL_CHARGE = 40;
    private static final int BASE_COST = 20;
    private static final int EXTRA_COST = 60;
    public static final int COOLDOWN_TICKS = 60;
    private static final double MIN_SPEED = 1.0;
    private static final double MAX_SPEED = 3.0;
    private static final double MIN_LIFT = 0.45;
    private static final int RING_POINTS = 28;

    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    public static float power(int chargeTicks) {
        return Mth.clamp((chargeTicks - MIN_CHARGE) / (float) (FULL_CHARGE - MIN_CHARGE), 0.0F, 1.0F);
    }

    public static void charging(ServerPlayer player, boolean charging) {
        if (charging && !FlightRingPower.hasFlight(player)) return;
        broadcast(player, charging ? CHARGING : IDLE);
    }

    public static Vec3 velocity(Vec3 look, float power) {
        return new Vec3(look.x, Math.max(look.y, MIN_LIFT), look.z).normalize().scale(Mth.lerp(power, MIN_SPEED, MAX_SPEED));
    }

    public static boolean launch(ServerPlayer player, int chargeTicks) {
        if (chargeTicks < FULL_CHARGE || !player.onGround() || player.isPassenger() || player.isSpectator()) return false;
        if (!FlightRingPower.hasFlight(player) || !PowerRingItem.hasCharge(player)) return false;

        long now = player.level().getGameTime();
        long ready = READY_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < ready) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.boost.recharging", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
            return false;
        }

        float power = power(chargeTicks);
        if (!player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, BASE_COST + Math.round(EXTRA_COST * power))) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return false;
        }
        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);

        player.resetFallDistance();
        FlightRingPower.setEnabled(player, true);
        FlightBoost.burst(player);
        effects(player.level(), player, power);
        broadcast(player, LAUNCHED);
        return true;
    }

    private static void broadcast(ServerPlayer player, int state) {
        GroundLaunchS2CPayload payload = new GroundLaunchS2CPayload(player.getId(), state);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private static void effects(ServerLevel level, ServerPlayer player, float power) {
        Vec3 feet = player.position();
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F + power, 0.8F - 0.2F * power);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 0.6F + 0.6F * power, 1.1F - 0.3F * power);

        DustParticleOptions dust = new DustParticleOptions(CorpsColors.of(player), 1.6F);
        double radius = 0.6 + 0.8 * power;
        for (int i = 0; i < RING_POINTS; i++) {
            float angle = i * Mth.TWO_PI / RING_POINTS;
            double cos = Mth.cos(angle), sin = Mth.sin(angle);
            level.sendParticles(dust, feet.x + cos * radius, feet.y + 0.1, feet.z + sin * radius, 0, cos, 0.05, sin, 0.4 + 0.4 * power);
        }

        BlockState ground = level.getBlockState(player.getOnPos());
        if (ground.getRenderShape() != RenderShape.INVISIBLE) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x, feet.y + 0.1, feet.z, 30 + Math.round(40 * power), radius * 0.5, 0.1, radius * 0.5, 0.25);
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, feet.x, feet.y + 0.2, feet.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (power > 0.6F) level.sendParticles(ParticleTypes.SONIC_BOOM, feet.x, feet.y + 0.3, feet.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private GroundLaunch() {}
}
