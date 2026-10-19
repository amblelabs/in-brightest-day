package dev.amble.client.effects;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.networking.payloads.s2c.ConcussiveS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ConcussiveEffects {
    private static final int LIFETIME = 10;
    private static final int RINGS = 3;
    private static final int RING_DELAY = 2;
    private static final int VOXELS_PER_RING = 28;
    private static final float VOXEL_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float START_RADIUS = 0.3F;

    private static final List<Wave> WAVES = new ArrayList<>();

    private static final class Wave {
        final Vec3 origin;
        final Vec3 direction;
        final int color;
        final long seed;
        int age;

        Wave(Vec3 origin, Vec3 direction, int color, long seed) {
            this.origin = origin;
            this.direction = direction;
            this.color = color;
            this.seed = seed;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ConcussiveS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WAVES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(ConcussiveEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(ConcussiveEffects::render);
    }

    private static void spawn(Minecraft client, ConcussiveS2CPayload payload) {
        if (client.level == null) return;
        Entity shooter = client.level.getEntity(payload.playerId());
        if (shooter == null) return;

        Vec3 origin = shooter instanceof Player player ? BlastEffects.hand(player, 1.0F) : shooter.getEyePosition();
        WAVES.add(new Wave(origin, payload.direction().normalize(), ARGB.opaque(payload.color()), client.level.getRandom().nextLong()));
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        WAVES.removeIf(wave -> ++wave.age > LIFETIME + RINGS * RING_DELAY);
    }

    private static void render(LevelRenderContext context) {
        if (WAVES.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        BrightestDayConfig config = BrightestDayConfig.get();
        double spread = Math.tan(Math.toRadians(Math.min(config.concussiveConeDegrees, 170.0) / 2.0));

        for (Wave wave : WAVES) {
            Vec3 side = wave.direction.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 up = side.cross(wave.direction).normalize();
            RandomSource random = RandomSource.create(wave.seed);

            for (int ring = 0; ring < RINGS; ring++) {
                float t = (wave.age + partialTicks - ring * RING_DELAY) / LIFETIME;
                if (t <= 0.0F || t > 1.0F) continue;

                float eased = 1.0F - (1.0F - t) * (1.0F - t);
                double distance = config.concussiveRange * eased;
                double radius = START_RADIUS + distance * spread;
                Vec3 center = wave.origin.add(wave.direction.scale(distance));
                int tint = VoxelRenderer.toWhite(wave.color, 0.6F * (1.0F - t));
                float half = VoxelRenderer.snapSize(VOXEL_SIZE * (1.0F - 0.6F * t) * 0.5F);

                List<ShieldEffects.Voxel> voxels = new ArrayList<>(VisualQuality.count(VOXELS_PER_RING));
                for (int i = 0; i < VisualQuality.count(VOXELS_PER_RING); i++) {
                    double angle = (i + random.nextFloat() * 0.5) / VisualQuality.count(VOXELS_PER_RING) * Mth.TWO_PI;
                    double wobble = radius * (0.9 + random.nextFloat() * 0.2);
                    Vec3 point = center.add(side.scale(Math.cos(angle) * wobble)).add(up.scale(Math.sin(angle) * wobble));
                    voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, tint));
                }
                ShieldEffects.submit(context, camera, voxels, 1.0F - t * t);
            }
        }
    }

    private ConcussiveEffects() {}
}
