package dev.amble.core.space;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SpaceTravel {
    private static final int COOLDOWN_TICKS = 40;
    private static final double ORBIT_ALTITUDE = 80.0;
    private static final double CORNER_REACH = Math.sqrt(3.0);
    private static final double ORBIT_FOOTPRINT = 0.35;
    private static final double ENTRY_SPEED = 1.2;
    private static final float ENTRY_PITCH = 50.0F;

    private static final Map<ServerPlayer, Integer> LAST_JUMP = new WeakHashMap<>();

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, BrightestDay.id("oa_terrain"), OaTerrainFeature.CODEC);
        PowerBattery.init();
        ServerTickEvents.END_SERVER_TICK.register(SpaceTravel::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel oa = server.getLevel(Cosmos.OA);
            if (oa != null) PowerBattery.ensure(oa);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(source.is(DamageTypes.FELL_OUT_OF_WORLD) && entity instanceof ServerPlayer player
                        && player.level().dimension().equals(Cosmos.SPACE) && PowerRingItem.getWornCorps(player).isPresent()));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.isSpectator() || coolingDown(server, player)) continue;

            ServerLevel level = player.level();
            if (level.dimension().equals(Cosmos.SPACE)) {
                tickInSpace(server, player);
            } else {
                Cosmos.Celestial planet = Cosmos.planetFor(level.dimension());
                if (planet != null && FlightRingPower.isFlying(player) && player.getY() >= BrightestDayConfig.get().spaceEntryHeight) {
                    launch(server, player, planet);
                }
            }
        }
    }

    private static void tickInSpace(MinecraftServer server, ServerPlayer player) {
        Vec3 position = player.position();
        for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) {
            if (position.distanceTo(wormhole.center()) <= wormhole.radius()) {
                traverse(player, wormhole);
                return;
            }
        }
        for (Cosmos.Celestial body : Cosmos.BODIES) {
            if (body.enterable() && body.contains(position, player.level().getGameTime())) {
                land(server, player, body);
                return;
            }
        }
    }

    private static void launch(MinecraftServer server, ServerPlayer player, Cosmos.Celestial planet) {
        ServerLevel space = server.getLevel(Cosmos.SPACE);
        if (space == null) return;

        double footprint = planet.radius() * ORBIT_FOOTPRINT;
        double offsetX = Mth.clamp(player.getX() / planet.surfaceScale(), -footprint, footprint);
        double offsetZ = Mth.clamp(player.getZ() / planet.surfaceScale(), -footprint, footprint);
        Vec3 arrival = planet.center().add(offsetX, planet.radius() * CORNER_REACH + ORBIT_ALTITUDE, offsetZ);
        jump(server, player, space, arrival, player.getDeltaMovement(), player.getYRot(), player.getXRot());
    }

    private static void land(MinecraftServer server, ServerPlayer player, Cosmos.Celestial body) {
        ServerLevel destination = server.getLevel(body.destination());
        if (destination == null) return;

        Vec3 local = player.position().subtract(body.center());
        double x = local.x * body.surfaceScale();
        double z = local.z * body.surfaceScale();
        double offset = Math.hypot(x, z);
        if (offset > body.maxSurfaceOffset()) {
            x *= body.maxSurfaceOffset() / offset;
            z *= body.maxSurfaceOffset() / offset;
        }
        double border = destination.getWorldBorder().getSize() / 2.0 - 16.0;
        x = Mth.clamp(x, -border, border);
        z = Mth.clamp(z, -border, border);
        if (body.destination().equals(Cosmos.OA)) PowerBattery.ensure(destination);

        Vec3 arrival = new Vec3(x, BrightestDayConfig.get().spaceArrivalHeight, z);
        jump(server, player, destination, arrival, new Vec3(0.0, -ENTRY_SPEED, 0.0), player.getYRot(), ENTRY_PITCH);
    }

    private static void traverse(ServerPlayer player, Cosmos.Wormhole wormhole) {
        double speed = player.getDeltaMovement().length();
        Vec3 heading = wormhole.exitHeading().normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
        jump(player.level().getServer(), player, player.level(), wormhole.exit(), heading.scale(Math.max(speed, ENTRY_SPEED)), yaw, 0.0F);
    }

    private static void jump(MinecraftServer server, ServerPlayer player, ServerLevel level, Vec3 position, Vec3 velocity, float yaw, float pitch) {
        LAST_JUMP.put(player, server.getTickCount());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
        player.teleport(new TeleportTransition(level, position, velocity, yaw, pitch, TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        level.playSound(null, position.x, position.y, position.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    private static boolean coolingDown(MinecraftServer server, ServerPlayer player) {
        Integer last = LAST_JUMP.get(player);
        return last != null && server.getTickCount() - last < COOLDOWN_TICKS;
    }

    private SpaceTravel() {}
}
