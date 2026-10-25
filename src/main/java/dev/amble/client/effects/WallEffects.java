package dev.amble.client.effects;

import dev.amble.core.networking.payloads.s2c.WallRemoveS2CPayload;
import dev.amble.core.networking.payloads.s2c.WallSpawnS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.CorpsColors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class WallEffects {
    private static final int POP_TICKS = 8;
    private static final int FADE_TICKS = 6;
    private static final float CELL_HALF = 0.49F;
    private static final float CELL_ALPHA = 0.85F;

    private static final Map<Integer, ClientWall> WALLS = new HashMap<>();
    private static boolean fusionDirty;
    private static int crackVersion;
    private static final int TERRAIN_REFRESH = 20;

    private static final class ClientWall {
        final List<BlockPos> cells;
        final Set<BlockPos> members;
        final int casterId;
        final int color;
        final int duration;
        final @Nullable ClientLevel level;
        int age;
        int fade = -1;
        boolean settled;
        int @Nullable [] faces;
        int @Nullable [] drawn;

        ClientWall(WallSpawnS2CPayload payload, @Nullable ClientLevel level) {
            this.cells = payload.cells();
            this.members = new HashSet<>(this.cells);
            this.casterId = payload.casterId();
            this.color = ARGB.opaque(payload.color());
            this.duration = payload.duration();
            this.age = payload.age();
            this.level = level;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(WallSpawnS2CPayload.TYPE, (payload, context) -> {
            WALLS.put(payload.id(), new ClientWall(payload, context.client().level));
            fusionDirty = true;
        });
        ClientPlayNetworking.registerGlobalReceiver(WallRemoveS2CPayload.TYPE, (payload, context) -> {
            ClientWall wall = WALLS.get(payload.id());
            if (wall != null && wall.fade < 0) {
                wall.fade = 0;
                fusionDirty = true;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WALLS.clear();
            fusionDirty = true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(WallEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(WallEffects::render);
    }

    public static void snapshot(Consumer<CustomPacketPayload> out) {
        WALLS.forEach((id, wall) -> {
            if (wall.fade < 0) out.accept(new WallSpawnS2CPayload(id, wall.casterId, wall.cells, wall.color, wall.duration, wall.age));
        });
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        if (client.level != null && client.level.getGameTime() % TERRAIN_REFRESH == 0 && !WALLS.isEmpty()) fusionDirty = true;
        Iterator<ClientWall> iterator = WALLS.values().iterator();
        while (iterator.hasNext()) {
            ClientWall wall = iterator.next();
            if (wall.level != client.level) {
                iterator.remove();
                fusionDirty = true;
                continue;
            }
            wall.age++;
            if (wall.fade < 0 && wall.duration >= 0 && wall.age > wall.duration + 20) wall.fade = 0;
            if (wall.fade >= 0 && ++wall.fade > FADE_TICKS) {
                iterator.remove();
                fusionDirty = true;
                continue;
            }
            boolean settled = wall.age > POP_TICKS * 1.5F && wall.fade < 0;
            if (settled != wall.settled) {
                wall.settled = settled;
                fusionDirty = true;
            }
        }
    }

    private static void render(LevelRenderContext context) {
        if (WALLS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        if (crackVersion != HardLightCracks.version()) {
            crackVersion = HardLightCracks.version();
            fusionDirty = true;
        }
        if (fusionDirty) fuse();
        List<HardLightCracks.Crack> cracks = new ArrayList<>();

        for (ClientWall wall : WALLS.values()) {
            float time = wall.age + partialTicks;
            float fade = wall.fade < 0 ? 1.0F : 1.0F - Mth.clamp((wall.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (fade <= 0.01F) continue;

            int color = liveColor(client.level, wall);
            List<ShieldEffects.Hard> cells = new ArrayList<>(wall.cells.size());
            int count = wall.cells.size();
            boolean settled = wall.settled && wall.faces != null && wall.drawn != null;
            for (int i = 0; i < count; i++) {
                float delay = (float) i / count * POP_TICKS;
                float pop = Mth.clamp((time - delay) / POP_TICKS * 2.0F, 0.0F, 1.0F);
                float scale = pop * fade;
                if (scale <= 0.01F) continue;

                BlockPos pos = wall.cells.get(i);
                int stage = HardLightCracks.stage(pos);
                if (stage >= HardLightCracks.BROKEN) continue;
                int tint = VoxelRenderer.toWhite(color, 0.08F + 0.1F * Mth.sin(time * 0.1F + (pos.getX() + pos.getY() + pos.getZ()) * 0.45F));
                int faces = settled ? wall.faces[i] : VoxelRenderer.ALL_FACES;
                int drawn = settled ? wall.drawn[i] : VoxelRenderer.ALL_FACES;
                if (drawn == 0) continue;
                float half = settled ? 0.5F : CELL_HALF * scale;
                cells.add(new ShieldEffects.Hard(Vec3.atCenterOf(pos), half, HardLightCracks.darken(tint, stage), faces, drawn));
                if (stage > 0) cracks.add(new HardLightCracks.Crack(Vec3.atCenterOf(pos), half, drawn, stage, HardLightCracks.seed(pos)));
            }
            ShieldEffects.submitHardLight(context, camera, cells, CELL_ALPHA, VoxelRenderer.PIXEL, ShieldEffects.HARD_LIGHT_FILL, ShieldEffects.opacityOf(client.level.getEntity(wall.casterId)));
        }
        HardLightCracks.submit(context, camera, cracks, 1.0F);
    }

    private static void fuse() {
        fusionDirty = false;
        Map<Integer, Set<BlockPos>> byCaster = new HashMap<>();
        for (ClientWall wall : WALLS.values()) {
            if (!wall.settled) continue;
            Set<BlockPos> fused = byCaster.computeIfAbsent(wall.casterId, id -> new HashSet<>());
            for (BlockPos pos : wall.members) {
                if (!HardLightCracks.broken(pos)) fused.add(pos);
            }
        }
        ClientLevel level = Minecraft.getInstance().level;
        for (ClientWall wall : WALLS.values()) {
            if (!wall.settled) {
                wall.faces = null;
                wall.drawn = null;
                continue;
            }
            Set<BlockPos> fused = byCaster.get(wall.casterId);
            int[] faces = new int[wall.cells.size()];
            int[] drawn = new int[faces.length];
            for (int i = 0; i < faces.length; i++) {
                BlockPos pos = wall.cells.get(i);
                int stage = HardLightCracks.stage(pos);
                if (stage >= HardLightCracks.BROKEN) continue;
                faces[i] = openFaces(fused, pos);
                drawn[i] = level == null ? faces[i] : faces[i] & ~buried(level, pos);
            }
            wall.faces = faces;
            wall.drawn = drawn;
        }
    }

    private static int liveColor(ClientLevel level, ClientWall wall) {
        if (level.getEntity(wall.casterId) instanceof Player caster && PowerRingItem.getWornCorps(caster).isPresent()) {
            return ARGB.opaque(CorpsColors.of(caster));
        }
        return wall.color;
    }

    public static void submitPreview(LevelRenderContext context, Vec3 camera, Iterable<BlockPos> cells, int color, float alpha) {
        int opaque = ARGB.opaque(color);
        List<ShieldEffects.Hard> voxels = new ArrayList<>();
        Set<BlockPos> members = cells instanceof Set<BlockPos> set ? set : toSet(cells);
        ClientLevel level = Minecraft.getInstance().level;
        for (BlockPos pos : cells) {
            int faces = openFaces(members, pos);
            int drawn = level == null ? faces : faces & ~buried(level, pos);
            if (drawn != 0) voxels.add(new ShieldEffects.Hard(Vec3.atCenterOf(pos), 0.5F, opaque, faces, drawn));
        }
        ShieldEffects.submitHardLight(context, camera, voxels, alpha, VoxelRenderer.PIXEL, ShieldEffects.HARD_LIGHT_FILL, ShieldEffects.opacityOf(Minecraft.getInstance().player));
    }

    private static int buried(ClientLevel level, BlockPos pos) {
        int faces = 0;
        if (level.getBlockState(pos.above()).isSolidRender()) faces |= VoxelRenderer.UP;
        if (level.getBlockState(pos.below()).isSolidRender()) faces |= VoxelRenderer.DOWN;
        if (level.getBlockState(pos.north()).isSolidRender()) faces |= VoxelRenderer.NORTH;
        if (level.getBlockState(pos.south()).isSolidRender()) faces |= VoxelRenderer.SOUTH;
        if (level.getBlockState(pos.west()).isSolidRender()) faces |= VoxelRenderer.WEST;
        if (level.getBlockState(pos.east()).isSolidRender()) faces |= VoxelRenderer.EAST;
        return faces;
    }

    public static int openFaces(Set<BlockPos> cells, BlockPos pos) {
        int faces = 0;
        if (!cells.contains(pos.above())) faces |= VoxelRenderer.UP;
        if (!cells.contains(pos.below())) faces |= VoxelRenderer.DOWN;
        if (!cells.contains(pos.north())) faces |= VoxelRenderer.NORTH;
        if (!cells.contains(pos.south())) faces |= VoxelRenderer.SOUTH;
        if (!cells.contains(pos.west())) faces |= VoxelRenderer.WEST;
        if (!cells.contains(pos.east())) faces |= VoxelRenderer.EAST;
        return faces;
    }

    private static Set<BlockPos> toSet(Iterable<BlockPos> cells) {
        Set<BlockPos> set = new HashSet<>();
        for (BlockPos pos : cells) set.add(pos);
        return set;
    }

    private WallEffects() {}
}
