package dev.amble.core.beams;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.poses.Poses;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.BeamS2CPayload;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import dev.amble.core.walls.HardLightDamage;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class BeamManager {
    private static final int DAMAGE_INTERVAL = 5;
    private static final double PUSH = 0.08;

    private static final Map<ServerPlayer, Beam> BEAMS = new HashMap<>();

    private static final class Beam {
        final int color;
        int age;

        Beam(int color) {
            this.color = color;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BeamManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BEAMS.clear());
    }

    public static void start(ServerPlayer player, int color) {
        if (BEAMS.containsKey(player)) return;
        BEAMS.put(player, new Beam(color));
        broadcast(player, color, true);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
    }

    public static void stop(ServerPlayer player) {
        Beam beam = BEAMS.remove(player);
        if (beam == null) return;
        broadcast(player, beam.color, false);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    private static void tick(MinecraftServer server) {
        boolean drainTick = server.getTickCount() % 20 == 0;

        for (ServerPlayer player : new ArrayList<>(BEAMS.keySet())) {
            Beam beam = BEAMS.get(player);
            boolean selected = ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.BEAM;
            boolean outOfCharge = !PowerRingItem.hasCharge(player)
                    || drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, BrightestDayConfig.get().beamDrainPerSecond);
            if (++beam.age > BrightestDayConfig.get().beamMaxTicks || player.isRemoved() || !player.isAlive() || !ArmedRingPower.isArmed(player) && !Poses.channeling(player) || !selected || outOfCharge) {
                stop(player);
                continue;
            }

            ConstructRingPower.Aim aim = ConstructRingPower.aim(player, range());
            Entity target = aim.entity();
            if (target == null) {
                if (beam.age % DAMAGE_INTERVAL == 0) {
                    ServerLevel level = player.level();
                    HitResult block = level.clip(new ClipContext(aim.eye(), aim.eye().add(aim.look().scale(range())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    HardLightDamage.hit(level, block, BrightestDayConfig.get().beamDamage, player);
                }
                continue;
            }
            if (!RingTargets.isHittable(target)) continue;

            ServerLevel level = player.level();
            if (beam.age % DAMAGE_INTERVAL == 0) {
                RingTargets.strike(level, target, RingDamage.source(level, player), BrightestDayConfig.get().beamDamage);
            }
            Vec3 push = aim.look().scale(PUSH);
            target.push(push);
            if (beam.age % 4 == 0) target.needsSync = true;
        }
    }

    private static void broadcast(ServerPlayer player, int color, boolean active) {
        BeamS2CPayload payload = new BeamS2CPayload(player.getId(), color, active);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    public static double range() {
        return BrightestDayConfig.get().beamRange;
    }

    public static boolean isBeaming(Entity entity) {
        return entity instanceof ServerPlayer player && BEAMS.containsKey(player);
    }

    private BeamManager() {}
}
