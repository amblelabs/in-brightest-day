package dev.amble.client.effects.attacks.area;

import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.SlamS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import dev.amble.client.flight.FlightAnimator;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SlamEffects {
    private static final int RING_LIFETIME = 12;
    private static final int RINGS = 3;
    private static final int RING_DELAY = 3;
    private static final float RING_SPACING = 0.3F;
    private static final float RING_VOXEL_SIZE = 4.0F * VoxelRenderer.PIXEL;
    private static final float START_RADIUS = 0.5F;
    private static final float GROUND_OFFSET = 0.15F;
    private static final int DEBRIS = 36;
    private static final int DEBRIS_LIFETIME = 22;
    private static final float DEBRIS_VOXEL_SIZE = 5.0F * VoxelRenderer.PIXEL;
    private static final float DEBRIS_GRAVITY = 0.05F;
    private static final int PROBE_UP = 1;
    private static final int PROBE_DOWN = 3;

    private static final List<Slam> SLAMS = new ArrayList<>();

    private static final class Slam {
        final Vec3 center;
        final float radius;
        final int color;
        final long seed;
        final Map<Long, Double> ground = new HashMap<>();
        int age;

        Slam(Vec3 center, float radius, int color, long seed) {
            this.center = center;
            this.radius = radius;
            this.color = color;
            this.seed = seed;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SlamS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SLAMS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(SlamEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(SlamEffects::render);
    }

    private static void spawn(Minecraft client, SlamS2CPayload payload) {
        if (client.level == null) return;
        SLAMS.add(new Slam(payload.center(), payload.radius(), ARGB.opaque(payload.color()), client.level.getRandom().nextLong()));
        if (client.level.getEntity(payload.playerId()) instanceof Player player) FlightAnimator.slam(player);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        SLAMS.removeIf(slam -> ++slam.age > Math.max(RING_LIFETIME + RINGS * RING_DELAY, DEBRIS_LIFETIME));
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (SLAMS.isEmpty() || client.level == null) return;

        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Slam slam : SLAMS) {
            float time = slam.age + partialTicks;
            for (int ring = 0; ring < RINGS; ring++) {
                ring(context, camera, client.level, slam, (time - ring * RING_DELAY) / RING_LIFETIME, ring);
            }
            debris(context, camera, client.level, slam, time);
        }
    }

    private static void ring(LevelRenderContext context, Vec3 camera, ClientLevel level, Slam slam, float t, int index) {
        if (t <= 0.0F || t > 1.0F) return;

        float eased = 1.0F - (1.0F - t) * (1.0F - t);
        float radius = START_RADIUS + (slam.radius - START_RADIUS) * eased * (1.0F - 0.15F * index);
        int count = Math.max(Mth.ceil(Mth.TWO_PI * radius / RING_SPACING), 12);
        float half = VoxelRenderer.snapSize(RING_VOXEL_SIZE * (1.0F - 0.5F * t) * (1.0F - 0.2F * index) * 0.5F);
        int tint = VoxelRenderer.toWhite(slam.color, 0.7F * (1.0F - t));
        RandomSource random = RandomSource.create(slam.seed + index);

        List<ShieldEffects.Voxel> voxels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = (i + random.nextFloat() * 0.5) / count * Mth.TWO_PI;
            double wobble = radius * (0.94 + random.nextFloat() * 0.12);
            double x = slam.center.x + Math.cos(angle) * wobble;
            double z = slam.center.z + Math.sin(angle) * wobble;
            double y = ground(level, slam, x, z) + GROUND_OFFSET + random.nextFloat() * 0.25F * (1.0F - t);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(new Vec3(x, y, z)), half, tint));
        }
        ShieldEffects.submit(context, camera, voxels, 1.0F - t * t);
    }

    private static void debris(LevelRenderContext context, Vec3 camera, ClientLevel level, Slam slam, float time) {
        float t = time / DEBRIS_LIFETIME;
        if (t > 1.0F) return;

        float life = 1.0F - t;
        RandomSource random = RandomSource.create(slam.seed ^ 0x5DEECE66DL);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(DEBRIS);
        for (int i = 0; i < DEBRIS; i++) {
            double angle = random.nextFloat() * Mth.TWO_PI;
            double start = slam.radius * 0.5 * random.nextFloat();
            double outward = 0.08 + random.nextFloat() * 0.18;
            double up = 0.3 + random.nextFloat() * 0.35;
            float delay = random.nextFloat() * 4.0F;
            float half = VoxelRenderer.snapSize(DEBRIS_VOXEL_SIZE * (0.5F + random.nextFloat() * 0.5F) * 0.5F);
            float local = time - delay;
            if (local < 0.0F) continue;

            double x = slam.center.x + Math.cos(angle) * (start + outward * local);
            double z = slam.center.z + Math.sin(angle) * (start + outward * local);
            double floor = ground(level, slam, x, z);
            double y = floor + up * local - 0.5 * DEBRIS_GRAVITY * local * local;
            if (y < floor) continue;

            int tint = VoxelRenderer.toWhite(slam.color, 0.3F * life);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(new Vec3(x, y + half, z)), VoxelRenderer.snapSize(half * (0.4F + 0.6F * life)), tint));
        }
        if (!voxels.isEmpty()) ShieldEffects.submit(context, camera, voxels, life);
    }

    private static double ground(ClientLevel level, Slam slam, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        return slam.ground.computeIfAbsent(BlockPos.asLong(bx, 0, bz), key -> probe(level, bx, slam.center.y, bz));
    }

    private static double probe(ClientLevel level, int x, double y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(y) + PROBE_UP;
        int bottom = Mth.floor(y) - PROBE_DOWN;
        for (int by = top; by >= bottom; by--) {
            pos.set(x, by, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return by + shape.max(Direction.Axis.Y);
        }
        return y;
    }

    private SlamEffects() {}
}
