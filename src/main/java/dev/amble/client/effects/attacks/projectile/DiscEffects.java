package dev.amble.client.effects.attacks.projectile;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.DiscS2CPayload;
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
import java.util.List;
import java.util.Map;

public final class DiscEffects {
    private static final int STALE_TICKS = 20;
    private static final float RADIUS = 0.55F;
    private static final float INNER_RADIUS = 0.3F;
    private static final int RIM_VOXELS = 21;
    private static final int INNER_VOXELS = 10;
    private static final int BLADES = 3;
    private static final float SPIN_SPEED = 0.9F;
    private static final float TILT = 0.25F;
    private static final float VOXEL_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final float BLADE_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float HUB_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final int BURST_VOXELS = 24;
    private static final float BURST_SPEED = 0.25F;
    private static final float BURST_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final int BURST_LIFETIME = 10;

    private static final Map<Integer, Disc> DISCS = new HashMap<>();
    private static @Nullable ClientLevel trackedLevel;

    private static final class Disc {
        final int color;
        Vec3 previous;
        Vec3 position;
        Vec3 direction;
        int age;
        int idle;

        Disc(int color, Vec3 position, Vec3 direction) {
            this.color = color;
            this.previous = position;
            this.position = position;
            this.direction = direction;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DiscS2CPayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DISCS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(DiscEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(DiscEffects::render);
    }

    private static void receive(DiscS2CPayload payload) {
        Disc disc = DISCS.get(payload.discId());
        if (!payload.alive()) {
            DISCS.remove(payload.discId());
            int color = disc != null ? disc.color : ARGB.opaque(payload.color());
            ProjectileBursts.spawn(payload.position(), color, VisualQuality.count(BURST_VOXELS), BURST_SPEED, BURST_SIZE, BURST_LIFETIME);
            return;
        }
        if (disc == null) {
            DISCS.put(payload.discId(), new Disc(ARGB.opaque(payload.color()), payload.position(), payload.direction()));
            return;
        }
        disc.previous = disc.position;
        disc.position = payload.position();
        disc.direction = payload.direction();
        disc.idle = 0;
    }

    private static void tick(Minecraft client) {
        if (client.level != trackedLevel) {
            DISCS.clear();
            trackedLevel = client.level;
        }
        if (client.isPaused()) return;
        DISCS.values().removeIf(disc -> {
            disc.age++;
            return ++disc.idle > STALE_TICKS;
        });
    }

    private static void render(LevelRenderContext context) {
        if (DISCS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        List<ShieldEffects.Voxel> voxels = new ArrayList<>();
        for (Disc disc : DISCS.values()) {
            Vec3 center = disc.previous.lerp(disc.position, partialTicks);
            Vec3 flat = new Vec3(disc.direction.x, 0.0, disc.direction.z);
            Vec3 forward = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
            Vec3 side = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 axisA = forward.add(0.0, disc.direction.y * TILT, 0.0).normalize();
            Vec3 axisB = side;
            float spin = (disc.age + partialTicks) * SPIN_SPEED;

            int rim = disc.color;
            int bright = VoxelRenderer.toWhite(disc.color, 0.6F);
            float rimHalf = VoxelRenderer.snapSize(VOXEL_SIZE * 0.5F);
            float bladeHalf = VoxelRenderer.snapSize(BLADE_SIZE * 0.5F);
            for (int i = 0; i < RIM_VOXELS; i++) {
                float angle = spin + (float) i / RIM_VOXELS * Mth.TWO_PI;
                boolean blade = i % (RIM_VOXELS / BLADES) == 0;
                Vec3 point = center.add(axisA.scale(Mth.cos(angle) * RADIUS)).add(axisB.scale(Mth.sin(angle) * RADIUS));
                voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), blade ? bladeHalf : rimHalf, blade ? bright : rim));
            }
            for (int i = 0; i < INNER_VOXELS; i++) {
                float angle = -spin * 0.5F + (float) i / INNER_VOXELS * Mth.TWO_PI;
                Vec3 point = center.add(axisA.scale(Mth.cos(angle) * INNER_RADIUS)).add(axisB.scale(Mth.sin(angle) * INNER_RADIUS));
                voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), rimHalf, VoxelRenderer.toWhite(disc.color, 0.25F)));
            }
            for (int blade = 0; blade < BLADES; blade++) {
                float angle = spin + (float) blade / BLADES * Mth.TWO_PI;
                for (float r = INNER_RADIUS + VoxelRenderer.PIXEL * 2.0F; r < RADIUS; r += VoxelRenderer.PIXEL * 2.0F) {
                    Vec3 point = center.add(axisA.scale(Mth.cos(angle) * r)).add(axisB.scale(Mth.sin(angle) * r));
                    voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), rimHalf, bright));
                }
            }
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center), VoxelRenderer.snapSize(HUB_SIZE * 0.5F), VoxelRenderer.toWhite(disc.color, 0.8F)));
        }
        ShieldEffects.submit(context, camera, voxels, 1.0F);
    }

    private DiscEffects() {}
}
