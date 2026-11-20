package dev.amble.core.walls;

import dev.amble.core.items.PowerRingItem;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.blocks.HardLightBlock;
import dev.amble.core.networking.payloads.s2c.WallRemoveS2CPayload;
import dev.amble.core.networking.payloads.s2c.WallSpawnS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WallManager {
    public static final int NO_EXPIRY = -1;
    private static final int WATCH_INTERVAL = 10;

    private static final List<Wall> WALLS = new ArrayList<>();
    private static final Map<ServerLevel, Map<BlockPos, Wall>> OWNED = new WeakHashMap<>();
    private static int nextId;

    private static final class Wall {
        final int id;
        final ServerLevel level;
        final List<BlockPos> cells;
        final Map<BlockPos, BlockState> covered;
        final Set<BlockPos> broken = new HashSet<>();
        final int size;
        final UUID caster;
        final int casterId;
        final int color;
        final boolean sustained;
        boolean hidden;
        final long createdAt;
        final Set<ServerPlayer> watchers = Collections.newSetFromMap(new WeakHashMap<>());
        int age;

        Wall(int id, ServerLevel level, List<BlockPos> cells, Map<BlockPos, BlockState> covered, ServerPlayer caster, int color, boolean sustained) {
            this.id = id;
            this.level = level;
            this.cells = cells;
            this.covered = covered;
            this.size = cells.size();
            this.caster = caster.getUUID();
            this.casterId = caster.getId();
            this.color = color;
            this.sustained = sustained;
            this.createdAt = level.getGameTime();
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WallManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WALLS.clear());
    }

    public static boolean raise(ServerLevel level, List<BlockPos> candidates, int color, ServerPlayer caster) {
        return place(level, candidates, color, caster, false) != null;
    }

    public static int raiseHidden(ServerLevel level, List<BlockPos> candidates, int color, ServerPlayer caster) {
        List<BlockPos> clear = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (level.getEntities((Entity) null, new AABB(pos), entity -> !entity.isSpectator()).isEmpty()) clear.add(pos);
        }
        Wall wall = place(level, clear, color, caster, true, true);
        return wall == null ? -1 : wall.id;
    }

    public static int raiseSustained(ServerLevel level, List<BlockPos> candidates, int color, ServerPlayer caster) {
        Wall wall = place(level, candidates, color, caster, true);
        return wall == null ? -1 : wall.id;
    }

    private static @Nullable Wall place(ServerLevel level, List<BlockPos> candidates, int color, ServerPlayer caster, boolean sustained) {
        return place(level, candidates, color, caster, sustained, false);
    }

    private static @Nullable Wall place(ServerLevel level, List<BlockPos> candidates, int color, ServerPlayer caster, boolean sustained, boolean hidden) {
        List<BlockPos> placed = new ArrayList<>();
        Map<BlockPos, BlockState> covered = new HashMap<>();
        for (BlockPos pos : candidates) {
            BlockState replaced = level.getBlockState(pos);
            if (!HardLightBlock.canReplace(replaced) || !level.mayInteract(caster, pos)) continue;
            BlockPos cell = pos.immutable();
            if (HardLightBlock.cover(replaced)) covered.put(cell, replaced);
            level.setBlock(cell, HardLightBlock.stateFor(BrightestDayBlocks.HARD_LIGHT, replaced), Block.UPDATE_ALL);
            placed.add(cell);
        }
        if (placed.isEmpty()) return null;

        Wall wall = new Wall(nextId++, level, placed, covered, caster, color, sustained);
        wall.hidden = hidden;
        WALLS.add(wall);
        Map<BlockPos, Wall> owned = OWNED.computeIfAbsent(level, key -> new HashMap<>());
        for (BlockPos cell : placed) owned.put(cell, wall);
        HardLightDamage.forget(level, placed);
        syncWatchers(wall);
        return wall;
    }

    public static boolean owns(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Wall> owned = OWNED.get(level);
        return owned != null && owned.containsKey(pos);
    }

    public static boolean any(ServerLevel level) {
        Map<BlockPos, Wall> owned = OWNED.get(level);
        return owned != null && !owned.isEmpty();
    }

    public static @Nullable UUID casterAt(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Wall> owned = OWNED.get(level);
        Wall wall = owned == null ? null : owned.get(pos);
        return wall == null ? null : wall.caster;
    }

    public static int colorAt(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Wall> owned = OWNED.get(level);
        Wall wall = owned == null ? null : owned.get(pos);
        return wall == null ? 0xFFFFFF : wall.color;
    }

    public static void shatter(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Wall> owned = OWNED.get(level);
        Wall wall = owned == null ? null : owned.remove(pos);
        if (wall == null) return;
        wall.cells.remove(pos);
        wall.broken.add(pos);
        restore(wall, pos);
        if (wall.cells.isEmpty()) {
            WALLS.remove(wall);
            collapse(wall, false);
        }
    }

    public static boolean breached(int id, Vec3 point, double reach) {
        double reachSq = reach * reach;
        for (Wall wall : WALLS) {
            if (wall.id != id) continue;
            for (BlockPos pos : wall.broken) {
                if (Vec3.atCenterOf(pos).distanceToSqr(point) <= reachSq) return true;
            }
            return false;
        }
        return false;
    }

    public static float brokenFraction(int id) {
        for (Wall wall : WALLS) {
            if (wall.id == id) return wall.size == 0 ? 0.0F : (float) wall.broken.size() / wall.size;
        }
        return 0.0F;
    }

    public static void sendCracks(int id, ServerPlayer player) {
        for (Wall wall : WALLS) {
            if (wall.id == id) {
                HardLightDamage.sendTo(player, wall.level, everyCell(wall));
                return;
            }
        }
    }

    private static List<BlockPos> everyCell(Wall wall) {
        List<BlockPos> all = new ArrayList<>(wall.cells.size() + wall.broken.size());
        all.addAll(wall.cells);
        all.addAll(wall.broken);
        return all;
    }

    private static void restore(Wall wall, BlockPos pos) {
        if (!wall.level.getBlockState(pos).is(BrightestDayBlocks.HARD_LIGHT)) return;
        BlockState original = wall.covered.get(pos);
        if (original != null && original.canSurvive(wall.level, pos)) wall.level.setBlock(pos, original, Block.UPDATE_ALL);
        else wall.level.removeBlock(pos, false);
    }

    public static long latestCreatedAt(UUID caster) {
        return WALLS.stream().filter(wall -> !wall.sustained && wall.caster.equals(caster)).mapToLong(wall -> wall.createdAt).max().orElse(Long.MIN_VALUE);
    }

    public static boolean dismissLatest(UUID caster) {
        Wall latest = null;
        for (Wall wall : WALLS) {
            if (!wall.sustained && wall.caster.equals(caster) && (latest == null || wall.id > latest.id)) latest = wall;
        }
        if (latest == null) return false;

        WALLS.remove(latest);
        collapse(latest, true);
        return true;
    }

    public static void dismissAll(UUID caster) {
        Iterator<Wall> iterator = WALLS.iterator();
        while (iterator.hasNext()) {
            Wall wall = iterator.next();
            if (wall.sustained || !wall.caster.equals(caster)) continue;
            iterator.remove();
            collapse(wall, true);
        }
    }

    public static void remove(int id, boolean sound) {
        Iterator<Wall> iterator = WALLS.iterator();
        while (iterator.hasNext()) {
            Wall wall = iterator.next();
            if (wall.id != id) continue;
            iterator.remove();
            collapse(wall, sound);
            return;
        }
    }

    private static void tick(MinecraftServer server) {
        for (Wall wall : WALLS) {
            if (++wall.age % WATCH_INTERVAL == 0) syncWatchers(wall);
        }
        if (server.getTickCount() % 20 != 0) return;

        Map<UUID, Integer> standing = new HashMap<>();
        for (Wall wall : WALLS) {
            if (!wall.sustained) standing.merge(wall.caster, 1, Integer::sum);
        }
        standing.forEach((caster, count) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(caster);
            if (player == null || player.hasInfiniteMaterials()) return;
            int drain = BrightestDayConfig.get().wallDrainPerSecond * count;
            if (drain > 0 && !PowerRingItem.drainWorn(player, drain)) dismissAll(caster);
        });
    }

    private static void collapse(Wall wall, boolean sound) {
        Map<BlockPos, Wall> owned = OWNED.get(wall.level);
        if (owned != null) {
            for (BlockPos pos : wall.cells) owned.remove(pos, wall);
        }
        for (BlockPos pos : wall.cells) restore(wall, pos);
        HardLightDamage.forget(wall.level, everyCell(wall));
        for (ServerPlayer player : wall.watchers) {
            if (!player.hasDisconnected()) ServerPlayNetworking.send(player, new WallRemoveS2CPayload(wall.id));
        }
        wall.watchers.clear();

        if (!sound || wall.cells.isEmpty()) return;
        BlockPos center = wall.cells.getFirst();
        wall.level.playSound(null, center, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    private static void syncWatchers(Wall wall) {
        if (wall.hidden || wall.cells.isEmpty()) return;
        Set<ServerPlayer> tracking = new HashSet<>(PlayerLookup.tracking(wall.level, wall.cells.getFirst()));
        for (ServerPlayer player : tracking) {
            if (wall.watchers.add(player)) {
                ServerPlayNetworking.send(player, new WallSpawnS2CPayload(wall.id, wall.casterId, wall.cells, wall.color, NO_EXPIRY, wall.age));
                HardLightDamage.sendTo(player, wall.level, wall.cells);
            }
        }
        wall.watchers.removeIf(player -> {
            boolean gone = !tracking.contains(player);
            if (gone && !player.hasDisconnected()) ServerPlayNetworking.send(player, new WallRemoveS2CPayload(wall.id));
            return gone;
        });
    }

    private WallManager() {}
}
