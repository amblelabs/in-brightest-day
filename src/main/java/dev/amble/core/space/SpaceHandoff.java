package dev.amble.core.space;

import com.mojang.brigadier.Command;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpaceHandoff {
    private static final double WORMHOLE_RADIUS = Cosmos.WORMHOLE_SIZE * 0.8;
    private static final double HINT_RADIUS = 800.0;
    private static final double FLOOR_BOUNCE = 0.6;
    private static final double EDGE = 0.95;
    private static final double EXIT_SPEED = 0.6;
    private static final long REENTRY_GRACE_TICKS = 60;
    private static final int HINT_INTERVAL = 40;
    private static final int MOGO_RANK = 4;

    private static final Map<UUID, Long> LAST_SWITCH = new HashMap<>();
    private static final Map<UUID, Trace> TRACES = new HashMap<>();
    private static final Map<UUID, Grace> GRACE = new HashMap<>();

    private record Grace(String planet, long until) {}

    private record Trace(ResourceKey<Level> dimension, Vec3 position) {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SpaceHandoff::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            TRACES.remove(handler.player.getUUID());
            GRACE.remove(handler.player.getUUID());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("mogo")
                .then(Commands.literal("summon").executes(context -> summonMogo(context.getSource())))));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("planets").executes(context -> listPlanets(context.getSource()))));
    }

    private static void tick(MinecraftServer server) {
        CosmosState.tick(server);
        ResourceKey<Level> mogo = CosmosState.mogoSector(server);
        long now = server.getTickCount();
        LAST_SWITCH.values().removeIf(tick -> now - tick >= SpaceTravel.COOLDOWN_TICKS);

        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Trace trace = TRACES.put(player.getUUID(), new Trace(player.level().dimension(), player.position()));
            if (player.isSpectator() || player.isPassenger() || player.isChangingDimension() || !player.isAlive()) continue;
            if (LAST_SWITCH.containsKey(player.getUUID())) continue;
            Vec3 from = trace != null && trace.dimension() == player.level().dimension() ? trace.position() : player.position();
            if (travel(server, player, mogo, from)) {
                LAST_SWITCH.put(player.getUUID(), now);
                TRACES.put(player.getUUID(), new Trace(player.level().dimension(), player.position()));
            }
        }
    }

    private static boolean travel(MinecraftServer server, ServerPlayer player, ResourceKey<Level> mogo, Vec3 from) {
        ResourceKey<Level> dimension = player.level().dimension();

        if (Cosmos.isSurface(dimension)) {
            if (player.getY() < SpaceTravel.EXIT_ATMOSPHERE_Y) return false;
            Cosmos.Planet surface = Cosmos.planetAt(dimension, player.getX());
            ResourceKey<Level> home = surface == null ? null : Cosmos.homeSector(surface, mogo);
            if (home == null) return false;
            return depart(server, player, surface, home);
        }

        if (!Cosmos.isSector(dimension)) return false;

        Vec3 to = player.position();
        for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) {
            if (wormhole.sector() != dimension || wormhole.antimatter() && !CosmosState.antimatterOpen()) continue;
            if (sweep(from, to, new Vec3(wormhole.x(), wormhole.y(), wormhole.z())) > WORMHOLE_RADIUS) continue;
            boolean moved = hop(server, player, wormhole.destination(), new Vec3(wormhole.toX(), wormhole.toY(), wormhole.toZ()),
                    player.getDeltaMovement(), player.getYRot(), player.getXRot());
            if (moved) player.sendSystemMessage(Component.translatable("message.brightestday.wormhole.arrived", sectorName(wormhole.destination())).withColor(wormhole.color()));
            return moved;
        }
        hint(player, dimension);

        long ticks = player.level().getGameTime();
        Grace grace = GRACE.get(player.getUUID());
        if (grace != null && ticks >= grace.until()) {
            GRACE.remove(player.getUUID());
            grace = null;
        }
        for (Cosmos.Planet planet : Cosmos.planetsIn(dimension, mogo)) {
            if (grace != null && grace.planet().equals(planet.id())) continue;
            PlanetBody.Entry entry = PlanetBody.entry(planet, ticks, from, to);
            if (entry != null) return land(server, player, planet, entry, ticks);
        }

        if (player.getY() > SpaceTravel.SPACE_FLOOR_Y) return false;
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, FLOOR_BOUNCE, velocity.z);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.sendOverlayMessage(Component.translatable("message.brightestday.space.nothing_below"));
        return false;
    }

    private static boolean land(MinecraftServer server, ServerPlayer player, Cosmos.Planet planet, PlanetBody.Entry entry, long ticks) {
        Quaternionf orientation = PlanetBody.orientation(planet, ticks);
        double half = planet.surfaceHalf();
        double x = planet.regionX() + Mth.clamp(entry.u(), -EDGE, EDGE) * half;
        double z = -Mth.clamp(entry.v(), -EDGE, EDGE) * half;
        Vec3 velocity = vec(PlanetBody.toSurface(entry.face(), PlanetBody.toLocal(orientation, player.getDeltaMovement())));
        Vec3 look = vec(PlanetBody.toSurface(entry.face(), PlanetBody.toLocal(orientation, player.getLookAngle())));
        boolean landed = hop(server, player, planet.surface(), new Vec3(x, SpaceTravel.REENTRY_Y, z), velocity, yaw(look), pitch(look));
        if (!landed) return false;
        if (!planet.name().isEmpty()) player.sendSystemMessage(Component.translatable("message.brightestday.space.landing", planet.name()).withColor(0xC8D8FF));
        return true;
    }

    private static boolean depart(MinecraftServer server, ServerPlayer player, Cosmos.Planet planet, ResourceKey<Level> sector) {
        long ticks = player.level().getGameTime();
        Quaternionf orientation = PlanetBody.orientation(planet, ticks);
        int face = PlanetBody.SURFACE;
        double half = planet.surfaceHalf();
        float u = (float) Mth.clamp((player.getX() - planet.regionX()) / half, -EDGE, EDGE);
        float w = (float) Mth.clamp(player.getZ() / half, -EDGE, EDGE);
        float lift = (float) (1.0 + PlanetBody.DEPARTURE_MARGIN / planet.footprint());
        Vector3f local = PlanetBody.fromSurface(face, new Vector3f(u, lift, w)).mul((float) planet.footprint());
        Vec3 position = PlanetBody.center(planet).add(PlanetBody.toWorld(orientation, local));
        Vec3 outward = PlanetBody.toWorld(orientation, new Vector3f(PlanetBody.FACES[face][0]));
        Vec3 velocity = PlanetBody.toWorld(orientation, PlanetBody.fromSurface(face, vector(player.getDeltaMovement())));
        velocity = velocity.add(outward.scale(Math.max(0.0, EXIT_SPEED - velocity.dot(outward))));
        Vec3 look = PlanetBody.toWorld(orientation, PlanetBody.fromSurface(face, vector(player.getLookAngle())));
        boolean moved = hop(server, player, sector, position, velocity, yaw(look), pitch(look));
        if (moved) GRACE.put(player.getUUID(), new Grace(planet.id(), ticks + REENTRY_GRACE_TICKS));
        return moved;
    }

    private static double sweep(Vec3 from, Vec3 to, Vec3 point) {
        Vec3 path = to.subtract(from);
        double length = path.lengthSqr();
        double t = length < 1.0E-6 ? 0.0 : Mth.clamp(point.subtract(from).dot(path) / length, 0.0, 1.0);
        return from.add(path.scale(t)).distanceTo(point);
    }

    private static Vec3 vec(Vector3f vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }

    private static Vector3f vector(Vec3 vec) {
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    private static float yaw(Vec3 look) {
        return (float) Math.toDegrees(Math.atan2(-look.x, look.z));
    }

    private static float pitch(Vec3 look) {
        double length = look.length();
        return length < 1.0E-6 ? 0.0F : (float) Math.toDegrees(Math.asin(Mth.clamp(-look.y / length, -1.0, 1.0)));
    }

    private static void hint(ServerPlayer player, ResourceKey<Level> sector) {
        if (player.tickCount % HINT_INTERVAL != 0) return;
        Vec3 position = player.position();
        for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) {
            if (wormhole.sector() != sector || wormhole.antimatter() && !CosmosState.antimatterOpen()) continue;
            if (position.distanceTo(new Vec3(wormhole.x(), wormhole.y(), wormhole.z())) > HINT_RADIUS) continue;
            String key = wormhole.antimatter() ? "message.brightestday.wormhole.antimatter" : "message.brightestday.wormhole.near";
            player.sendOverlayMessage(Component.translatable(key, sectorName(wormhole.destination())).withColor(wormhole.color()));
            return;
        }
    }

    public static Component sectorName(ResourceKey<Level> key) {
        Cosmos.Sector sector = Cosmos.sector(key);
        return Component.translatable("sector.brightestday." + (sector == null ? key.identifier().getPath() : sector.id()));
    }

    private static boolean hop(MinecraftServer server, ServerPlayer player, @Nullable ResourceKey<Level> destination, Vec3 position, Vec3 velocity, float yaw, float pitch) {
        ServerLevel to = destination == null ? null : server.getLevel(destination);
        if (to == null) return false;
        player.teleport(new TeleportTransition(to, position, velocity, yaw, pitch, TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        player.setDeltaMovement(velocity);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        return true;
    }

    private static int listPlanets(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        MinecraftServer server = source.getServer();
        ResourceKey<Level> mogo = CosmosState.mogoSector(server);
        for (Cosmos.Sector sector : Cosmos.SECTORS) {
            if (player != null && player.level().dimension() != sector.key()) continue;
            source.sendSystemMessage(sectorName(sector.key()).copy().withColor(sector.starColor()));
            for (Cosmos.Planet planet : Cosmos.planetsIn(sector.key(), mogo)) {
                Component name = planet.name().isEmpty() ? Component.translatable("dimension.brightestday." + planet.id()) : Component.literal(planet.name());
                source.sendSystemMessage(Component.translatable("message.brightestday.space.planet_entry", name, Math.round(planet.x()) + ", " + Math.round(planet.z()), Math.round(planet.footprint())));
            }
        }
        if (player != null && !Cosmos.isSector(player.level().dimension())) source.sendSystemMessage(Component.translatable("message.brightestday.space.not_in_space"));
        return Command.SINGLE_SUCCESS;
    }

    private static int summonMogo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = player.level().getServer();
        boolean green = PowerRingItem.getWornCorps(player).orElse(null) == LanternCorps.GREEN && RingRanks.rank(player, LanternCorps.GREEN) >= MOGO_RANK;
        if (!green && !Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
            player.sendSystemMessage(Component.translatable("message.brightestday.mogo.unworthy").withColor(LanternCorps.GREEN.color()));
            return 0;
        }
        Cosmos.Sector sector = Cosmos.sector(player.level().dimension());
        if (sector == null || sector.antimatter()) {
            player.sendSystemMessage(Component.translatable("message.brightestday.mogo.not_in_space").withColor(LanternCorps.GREEN.color()));
            return 0;
        }
        if (CosmosState.mogoSector(server) == sector.key()) {
            player.sendSystemMessage(Component.translatable("message.brightestday.mogo.already_here").withColor(LanternCorps.GREEN.color()));
            return 0;
        }
        CosmosState.moveMogo(server, sector.key());
        Component message = Component.translatable("message.brightestday.mogo.summoned", player.getDisplayName(), sectorName(sector.key())).withColor(LanternCorps.GREEN.color());
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player || PowerRingItem.getWornCorps(other).orElse(null) == LanternCorps.GREEN) other.sendSystemMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }

    private SpaceHandoff() {}
}
