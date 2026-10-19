package dev.amble.client.effects.attacks.area;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.NovaS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class NovaEffects {
    private static final int LIFETIME = 14;
    private static final int ECHO_DELAY = 3;
    private static final float START_RADIUS = 0.6F;
    private static final float LAYOUT_SCALE = 0.6F;
    private static final float ECHO_SCALE = 0.75F;
    private static final int FLASH_TICKS = 6;
    private static final int FLASH_VOXELS = 36;
    private static final float FLASH_CORE_SIZE = 14.0F * VoxelRenderer.PIXEL;
    private static final float FLASH_VOXEL_SIZE = 4.0F * VoxelRenderer.PIXEL;
    private static final float FLASH_SPEED = 0.6F;
    private static final float RING_SPACING = 0.3F;
    private static final float RING_VOXEL_SIZE = 4.0F * VoxelRenderer.PIXEL;

    private static final List<Nova> NOVAS = new ArrayList<>();

    private static final class Nova {
        final Vec3 center;
        final float radius;
        final int color;
        final long seed;
        int age;

        Nova(Vec3 center, float radius, int color, long seed) {
            this.center = center;
            this.radius = radius;
            this.color = color;
            this.seed = seed;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(NovaS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NOVAS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(NovaEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(NovaEffects::render);
    }

    private static void spawn(Minecraft client, NovaS2CPayload payload) {
        if (client.level == null) return;
        NOVAS.add(new Nova(payload.center(), payload.radius(), ARGB.opaque(payload.color()), client.level.getRandom().nextLong()));
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        NOVAS.removeIf(nova -> ++nova.age > LIFETIME + ECHO_DELAY);
    }

    private static void render(LevelRenderContext context) {
        if (NOVAS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Nova nova : NOVAS) {
            float time = nova.age + partialTicks;
            shell(context, camera, nova, time / LIFETIME, 1.0F, time);
            shell(context, camera, nova, (time - ECHO_DELAY) / LIFETIME, ECHO_SCALE, time);
            ring(context, camera, nova, time / LIFETIME);
            flash(context, camera, nova, time);
        }
    }

    private static void shell(LevelRenderContext context, Vec3 camera, Nova nova, float t, float scale, float time) {
        if (t <= 0.0F || t > 1.0F) return;

        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        float radius = (START_RADIUS + (nova.radius - START_RADIUS) * eased) * scale;
        int tint = VoxelRenderer.toWhite(nova.color, 0.5F * (1.0F - t));
        List<ShieldEffects.Voxel> voxels = ShieldEffects.sphere(nova.center, radius, nova.radius * LAYOUT_SCALE * scale, time, tint);
        ShieldEffects.submit(context, camera, voxels, (1.0F - t) * (1.0F - t) * scale);
    }

    private static void ring(LevelRenderContext context, Vec3 camera, Nova nova, float t) {
        if (t <= 0.0F || t > 1.0F) return;

        float eased = 1.0F - (1.0F - t) * (1.0F - t);
        float radius = START_RADIUS + (nova.radius * 1.1F - START_RADIUS) * eased;
        int count = Math.max(Mth.ceil(Mth.TWO_PI * radius / RING_SPACING), 12);
        float half = VoxelRenderer.snapSize(RING_VOXEL_SIZE * (1.0F - 0.5F * t) * 0.5F);
        int tint = VoxelRenderer.toWhite(nova.color, 0.8F * (1.0F - t));
        RandomSource random = RandomSource.create(nova.seed);

        List<ShieldEffects.Voxel> voxels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = (i + random.nextFloat() * 0.4) / count * Mth.TWO_PI;
            double wobble = radius * (0.96 + random.nextFloat() * 0.08);
            Vec3 point = nova.center.add(Math.cos(angle) * wobble, (random.nextFloat() - 0.5F) * 0.2F, Math.sin(angle) * wobble);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, tint));
        }
        ShieldEffects.submit(context, camera, voxels, 1.0F - t * t);
    }

    private static void flash(LevelRenderContext context, Vec3 camera, Nova nova, float time) {
        float t = time / FLASH_TICKS;
        if (t > 1.0F) return;

        float life = 1.0F - t;
        RandomSource random = RandomSource.create(nova.seed ^ 0x5DEECE66DL);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(VisualQuality.count(FLASH_VOXELS) + 1);
        voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(nova.center), VoxelRenderer.snapSize(FLASH_CORE_SIZE * (0.4F + life) * 0.5F), 0xFFFFFFFF));
        for (int i = 0; i < VisualQuality.count(FLASH_VOXELS); i++) {
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            float distance = FLASH_SPEED * time * (0.5F + random.nextFloat() * 0.5F);
            float half = VoxelRenderer.snapSize(FLASH_VOXEL_SIZE * life * (0.6F + random.nextFloat() * 0.4F) * 0.5F);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(nova.center.add(direction.scale(distance))), half, VoxelRenderer.toWhite(nova.color, 0.4F + 0.6F * life)));
        }
        ShieldEffects.submit(context, camera, voxels, life);
    }

    private NovaEffects() {}
}
