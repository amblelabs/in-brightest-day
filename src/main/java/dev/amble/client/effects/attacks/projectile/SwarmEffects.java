package dev.amble.client.effects.attacks.projectile;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.SwarmS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class SwarmEffects {
    private static final int TRAIL_LENGTH = 6;
    private static final int STALE_TICKS = 20;
    private static final float HEAD_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float TRAIL_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final float TRAIL_SPACING = 2.0F * VoxelRenderer.PIXEL;
    private static final int BURST_VOXELS = 14;
    private static final float BURST_SPEED = 0.3F;
    private static final float BURST_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final int BURST_LIFETIME = 8;

    private static final Map<Integer, Volley> VOLLEYS = new HashMap<>();
    private static @Nullable ClientLevel trackedLevel;

    private static final class Dart {
        Vec3 previous;
        Vec3 position;
        final LinkedList<Vec3> trail = new LinkedList<>();
        boolean alive = true;

        Dart(Vec3 position) {
            this.previous = position;
            this.position = position;
        }
    }

    private static final class Volley {
        final int color;
        final List<Dart> darts = new ArrayList<>();
        int idle;

        Volley(int color) {
            this.color = color;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SwarmS2CPayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VOLLEYS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(SwarmEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(SwarmEffects::render);
    }

    private static void receive(SwarmS2CPayload payload) {
        Volley volley = VOLLEYS.get(payload.volleyId());
        List<Vec3> positions = payload.positions();
        if (volley == null) {
            if (payload.alive() == 0) return;
            volley = new Volley(ARGB.opaque(payload.color()));
            for (Vec3 position : positions) volley.darts.add(new Dart(position));
            VOLLEYS.put(payload.volleyId(), volley);
        }
        volley.idle = 0;

        for (int i = 0; i < Math.min(positions.size(), volley.darts.size()); i++) {
            Dart dart = volley.darts.get(i);
            if (!dart.alive) continue;
            dart.trail.addFirst(dart.position);
            if (dart.trail.size() > TRAIL_LENGTH) dart.trail.removeLast();
            dart.previous = dart.position;
            dart.position = positions.get(i);
            if ((payload.burst() & 1 << i) != 0) {
                dart.alive = false;
                ProjectileBursts.spawn(dart.position, volley.color, VisualQuality.count(BURST_VOXELS), BURST_SPEED, BURST_SIZE, BURST_LIFETIME);
            } else if ((payload.alive() & 1 << i) == 0) {
                dart.alive = false;
            }
        }
        if (payload.alive() == 0) VOLLEYS.remove(payload.volleyId());
    }

    private static void tick(Minecraft client) {
        if (client.level != trackedLevel) {
            VOLLEYS.clear();
            trackedLevel = client.level;
        }
        if (client.isPaused()) return;
        VOLLEYS.values().removeIf(volley -> ++volley.idle > STALE_TICKS);
    }

    private static void render(LevelRenderContext context) {
        if (VOLLEYS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        List<ShieldEffects.Voxel> voxels = new ArrayList<>();
        for (Volley volley : VOLLEYS.values()) {
            int core = VoxelRenderer.toWhite(volley.color, 0.7F);
            for (Dart dart : volley.darts) {
                if (!dart.alive) continue;
                Vec3 head = dart.previous.lerp(dart.position, partialTicks);
                voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(head), VoxelRenderer.snapSize(HEAD_SIZE * 0.5F), core));

                List<Vec3> path = new ArrayList<>(dart.trail.size() + 1);
                path.add(head);
                path.addAll(dart.trail);
                float total = 0.0F;
                for (int i = 1; i < path.size(); i++) total += (float) path.get(i - 1).distanceTo(path.get(i));
                if (total < 1.0E-3F) continue;

                float walked = 0.0F;
                for (int i = 1; i < path.size(); i++) {
                    Vec3 from = path.get(i - 1);
                    Vec3 to = path.get(i);
                    float length = (float) from.distanceTo(to);
                    int steps = Math.max(Mth.floor(length / TRAIL_SPACING), 1);
                    for (int step = 1; step <= steps; step++) {
                        float along = walked + length * step / steps;
                        float fade = 1.0F - along / total;
                        float half = TRAIL_SIZE * fade * 0.5F;
                        if (half < VoxelRenderer.PIXEL * 0.25F) continue;
                        Vec3 point = from.lerp(to, (double) step / steps);
                        voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), VoxelRenderer.snapSize(half), VoxelRenderer.toWhite(volley.color, 0.4F * fade)));
                    }
                    walked += length;
                }
            }
        }
        if (!voxels.isEmpty()) ShieldEffects.submit(context, camera, voxels, 1.0F);
    }

    private SwarmEffects() {}
}
