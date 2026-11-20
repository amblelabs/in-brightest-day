package dev.amble.core.sculpt;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.blocks.HardLightBlock;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.walls.WallManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SculptManager {
    private static final Map<UUID, SculptShape> SHAPES = new HashMap<>();
    private static final Map<ServerPlayer, Session> SESSIONS = new HashMap<>();
    private static final List<Sculpture> SCULPTURES = new ArrayList<>();
    private static int nextId;

    private static final class Sculpture {
        final int id;
        final UUID caster;
        final long createdAt;
        final List<Integer> walls = new ArrayList<>();
        int blocks;

        Sculpture(int id, UUID caster, long createdAt) {
            this.id = id;
            this.caster = caster;
            this.createdAt = createdAt;
        }
    }

    private static final class Session {
        final SculptShape shape;
        final int width;
        final int color;
        final Set<BlockPos> interior = new HashSet<>();
        final List<Vec3> ring = new ArrayList<>();
        @Nullable Sculpture sculpture;
        @Nullable Vec3 start;
        @Nullable Vec3 last;
        Vec3 direction;
        double depth = Double.NaN;
        @Nullable Vec3 stairAnchor;
        int stairY;
        int floorY = Integer.MIN_VALUE;
        boolean full;

        Session(SculptShape shape, int width, int color, Vec3 direction) {
            this.shape = shape;
            this.width = width;
            this.color = color;
            this.direction = direction;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SculptManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SESSIONS.remove(handler.player);
            SHAPES.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SHAPES.clear();
            SESSIONS.clear();
            SCULPTURES.clear();
        });
    }

    public static boolean isSculpting(ServerPlayer player) {
        return SESSIONS.containsKey(player);
    }

    public static void setShape(ServerPlayer player, SculptShape shape) {
        SHAPES.put(player.getUUID(), shape);
    }

    public static void start(ServerPlayer player, int width, int color) {
        if (SESSIONS.containsKey(player)) return;
        SculptShape shape = SHAPES.getOrDefault(player.getUUID(), SculptShape.FREEFORM);
        SESSIONS.put(player, new Session(shape, width, color, SculptGeometry.flatDirection(player.getYRot())));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public static void stop(ServerPlayer player, boolean commit) {
        Session session = SESSIONS.get(player);
        if (session == null) return;

        if (commit && session.shape == SculptShape.CAGE) {
            SculptGeometry.Cage cage = SculptGeometry.cage(player.level(), session.ring);
            if (cage == null) {
                player.sendOverlayMessage(Component.translatable("message.brightestday.sculpt_cage_too_small"));
            } else {
                place(player, session, new LinkedHashSet<>(SculptGeometry.dome(player.level(), cage)));
                Vec3 center = cage.center();
                player.level().playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.8F);
            }
        }
        if (commit && session.shape == SculptShape.TUBE && session.start != null && session.last != null && session.start.distanceToSqr(session.last) >= 1.0) {
            Set<BlockPos> shell = new LinkedHashSet<>();
            SculptGeometry.tube(session.start, session.last, session.width, shell, session.interior);
            place(player, session, shell);
            player.level().playSound(null, session.last.x, session.last.y, session.last.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.0F);
        }
        SESSIONS.remove(player);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    public static long latestCreatedAt(UUID caster) {
        return SCULPTURES.stream().filter(sculpture -> sculpture.caster.equals(caster)).mapToLong(sculpture -> sculpture.createdAt).max().orElse(Long.MIN_VALUE);
    }

    public static boolean dismissLatest(UUID caster) {
        Sculpture newest = newest(caster);
        if (newest == null) return false;
        dissolve(newest);
        return true;
    }

    public static void dismissAll(UUID caster) {
        for (Sculpture sculpture : List.copyOf(SCULPTURES)) {
            if (sculpture.caster.equals(caster)) dissolve(sculpture);
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(SESSIONS.keySet())) {
            boolean selected = ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.SCULPT;
            if (player.isRemoved() || !player.isAlive() || !ArmedRingPower.isArmed(player) || !selected || !PowerRingItem.hasCharge(player)) {
                stop(player, false);
                continue;
            }
            draw(player, SESSIONS.get(player));
        }
        if (server.getTickCount() % 20 == 0) upkeep(server);
    }

    private static void draw(ServerPlayer player, Session session) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 point = session.shape == SculptShape.FLOOR && session.floorY != Integer.MIN_VALUE
                ? SculptGeometry.onPlane(eye, player.getLookAngle(), session.floorY)
                : SculptGeometry.trace(level, player, eye, player.getLookAngle(), session.depth);
        if (point == null) return;
        if (Double.isNaN(session.depth)) session.depth = SculptGeometry.lockDepth(eye, point);

        if (session.shape == SculptShape.CAGE) {
            if (session.ring.isEmpty() || session.ring.getLast().distanceToSqr(point) > SculptGeometry.STEP * SculptGeometry.STEP) session.ring.add(point);
            return;
        }
        if (session.shape == SculptShape.TUBE) {
            if (session.start == null) session.start = point;
            session.last = point;
            return;
        }
        if (session.full) return;

        Vec3 from = session.last == null ? point : session.last;
        session.last = point;
        Vec3 flat = new Vec3(point.x - from.x, 0.0, point.z - from.z);
        if (flat.lengthSqr() > 1.0E-4) session.direction = flat.normalize();

        Set<BlockPos> cells = new LinkedHashSet<>();
        int steps = Math.max(Mth.ceil(from.distanceTo(point) / SculptGeometry.STEP), 1);
        for (int step = 1; step <= steps; step++) {
            cross(session, from.lerp(point, (double) step / steps), cells);
        }
        place(player, session, cells);
    }

    private static void cross(Session session, Vec3 point, Set<BlockPos> cells) {
        switch (session.shape) {
            case FREEFORM -> SculptGeometry.ribbon(point, Mth.floor(point.y), session.direction, session.width, cells);
            case STAIRS -> {
                int target = Mth.floor(point.y);
                if (session.stairAnchor == null) {
                    session.stairAnchor = point;
                    session.stairY = target;
                }
                double run = Math.hypot(point.x - session.stairAnchor.x, point.z - session.stairAnchor.z);
                if (target != session.stairY && run >= 1.0) {
                    session.stairY += Integer.signum(target - session.stairY);
                    session.stairAnchor = point;
                }
                SculptGeometry.ribbon(point, session.stairY, session.direction, session.width, cells);
            }
            case FLOOR -> {
                if (session.floorY == Integer.MIN_VALUE) session.floorY = Mth.floor(point.y);
                SculptGeometry.patch(point, session.floorY, session.width, cells);
            }
            case TUBE, CAGE -> {}
        }
    }

    private static void place(ServerPlayer player, Session session, Set<BlockPos> cells) {
        ServerLevel level = player.level();
        cells.removeIf(pos -> session.interior.contains(pos)
                || !HardLightBlock.canReplace(level.getBlockState(pos))
                || !level.mayInteract(player, pos)
                || !level.getEntities((Entity) null, new AABB(pos), entity -> !entity.isSpectator()).isEmpty());
        if (cells.isEmpty()) return;

        List<BlockPos> batch = new ArrayList<>(cells);
        int used = session.sculpture == null ? 0 : session.sculpture.blocks;
        int maxBlocks = BrightestDayConfig.get().sculptMaxBlocks;
        if (used + batch.size() > maxBlocks) {
            batch = batch.subList(0, Math.max(0, maxBlocks - used));
            session.full = true;
            player.sendOverlayMessage(Component.translatable("message.brightestday.sculpt_limit"));
        }

        int affordable = afford(player, batch.size());
        if (affordable < batch.size()) {
            batch = batch.subList(0, affordable);
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            SESSIONS.remove(player);
        }
        if (batch.isEmpty()) return;

        Sculpture sculpture = session.sculpture;
        if (sculpture == null) {
            sculpture = new Sculpture(nextId++, player.getUUID(), level.getGameTime());
            session.sculpture = sculpture;
            SCULPTURES.add(sculpture);
        }
        makeRoom(player.getUUID(), sculpture, batch.size());

        int wall = WallManager.raiseSustained(level, batch, session.color, player);
        if (wall < 0) return;
        sculpture.walls.add(wall);
        sculpture.blocks += batch.size();
        if (level.getGameTime() % 3 == 0) {
            BlockPos pos = batch.getFirst();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.4F + level.getRandom().nextFloat() * 0.3F);
        }
    }

    private static int afford(ServerPlayer player, int count) {
        int cost = BrightestDayConfig.get().sculptBlockCost;
        if (player.hasInfiniteMaterials() || PowerRingItem.consumeCharge(player, cost * count)) return count;
        int paid = 0;
        while (paid < count && PowerRingItem.consumeCharge(player, cost)) paid++;
        return paid;
    }

    private static void makeRoom(UUID caster, Sculpture keep, int incoming) {
        while (total(caster) + incoming > BrightestDayConfig.get().sculptMaxTotalBlocks) {
            Sculpture oldest = null;
            for (Sculpture sculpture : SCULPTURES) {
                if (sculpture != keep && sculpture.caster.equals(caster) && (oldest == null || sculpture.id < oldest.id)) oldest = sculpture;
            }
            if (oldest == null) return;
            dissolve(oldest);
        }
    }

    private static void upkeep(MinecraftServer server) {
        Map<UUID, Integer> totals = new HashMap<>();
        for (Sculpture sculpture : SCULPTURES) totals.merge(sculpture.caster, sculpture.blocks, Integer::sum);

        for (Map.Entry<UUID, Integer> entry : totals.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasInfiniteMaterials()) continue;

            int drain = Mth.ceil((float) entry.getValue() / Math.max(1, BrightestDayConfig.get().sculptBlocksPerUpkeep));
            if (PowerRingItem.hasCharge(player) && PowerRingItem.drainWorn(player, drain)) continue;

            Sculpture newest = newest(entry.getKey());
            if (newest == null) continue;
            dissolve(newest);
            player.sendOverlayMessage(Component.translatable("message.brightestday.sculpt_fading"));
        }
    }

    private static @Nullable Sculpture newest(UUID caster) {
        Sculpture newest = null;
        for (Sculpture sculpture : SCULPTURES) {
            if (sculpture.caster.equals(caster) && (newest == null || sculpture.id > newest.id)) newest = sculpture;
        }
        return newest;
    }

    private static int total(UUID caster) {
        int total = 0;
        for (Sculpture sculpture : SCULPTURES) {
            if (sculpture.caster.equals(caster)) total += sculpture.blocks;
        }
        return total;
    }

    private static void dissolve(Sculpture sculpture) {
        SCULPTURES.remove(sculpture);
        for (Session session : SESSIONS.values()) {
            if (session.sculpture == sculpture) {
                session.sculpture = null;
                session.full = false;
            }
        }
        for (int i = 0; i < sculpture.walls.size(); i++) {
            WallManager.remove(sculpture.walls.get(i), i == 0);
        }
    }

    private SculptManager() {}
}
