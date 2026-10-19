package dev.amble.core.beams;

import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.poses.Poses;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.HealBeamS2CPayload;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class HealBeamManager {
    public static final double RANGE = 24.0;
    private static final double BREAK_DISTANCE = RANGE + 4.0;
    private static final int HEAL_INTERVAL = 10;

    private static final Map<ServerPlayer, HealBeam> BEAMS = new HashMap<>();

    private static final class HealBeam {
        final LivingEntity target;
        final int color;
        int age;

        HealBeam(LivingEntity target, int color) {
            this.target = target;
            this.color = color;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(HealBeamManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BEAMS.clear());
    }

    public static void start(ServerPlayer player, LivingEntity target, int color) {
        stop(player);
        BEAMS.put(player, new HealBeam(target, color));
        broadcast(player, target.getId(), color);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    public static boolean isHealing(ServerPlayer player) {
        return BEAMS.containsKey(player);
    }

    public static void stop(ServerPlayer player) {
        HealBeam beam = BEAMS.remove(player);
        if (beam == null) return;
        broadcast(player, HealBeamS2CPayload.NO_TARGET, beam.color);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    private static void tick(MinecraftServer server) {
        boolean drainTick = server.getTickCount() % 20 == 0;

        for (ServerPlayer player : new ArrayList<>(BEAMS.keySet())) {
            HealBeam beam = BEAMS.get(player);
            LivingEntity target = beam.target;
            boolean selected = ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.HEAL_BEAM;
            boolean outOfCharge = !PowerRingItem.hasCharge(player)
                    || drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, BrightestDayConfig.get().healBeamDrainPerSecond);
            boolean targetLost = !target.isAlive() || target.isRemoved() || target.level() != player.level()
                    || target.distanceTo(player) > BREAK_DISTANCE;
            if (++beam.age > BrightestDayConfig.get().healBeamMaxTicks || !player.isAlive() || !ArmedRingPower.isArmed(player) && !Poses.channeling(player) || !selected || outOfCharge || targetLost) {
                stop(player);
                continue;
            }

            if (beam.age % HEAL_INTERVAL == 0) {
                float before = target.getHealth();
                BrightestDayConfig config = BrightestDayConfig.get();
                target.heal(Math.min(config.healBeamMaxPulse, config.healBeamAmount * (float) Math.pow(config.healBeamGrowth, beam.age / HEAL_INTERVAL - 1)));
                RingRanks.fire(player, Trigger.HEAL, Milestone.Context.of(target), Math.round(target.getHealth() - before));
                if (target.isOnFire()) target.clearFire();
            }
        }
    }

    private static void broadcast(ServerPlayer player, int targetId, int color) {
        HealBeamS2CPayload payload = new HealBeamS2CPayload(player.getId(), targetId, color);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private HealBeamManager() {}
}
