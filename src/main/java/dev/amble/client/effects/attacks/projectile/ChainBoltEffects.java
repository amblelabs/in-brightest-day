package dev.amble.client.effects.attacks.projectile;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.ChainBoltS2CPayload;
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

public final class ChainBoltEffects {
    private static final int LIFETIME = 10;
    private static final int HOP_DELAY = 1;
    private static final int FLICKER_TICKS = 2;
    private static final float KINK_LENGTH = 0.7F;
    private static final float JAG = 0.3F;
    private static final float SPACING = 1.5F * VoxelRenderer.PIXEL;
    private static final float VOXEL_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final int HIT_VOXELS = 12;
    private static final float HIT_SPEED = 0.25F;
    private static final float HIT_SIZE = 2.5F * VoxelRenderer.PIXEL;
    private static final int HIT_LIFETIME = 8;

    private static final List<Bolt> BOLTS = new ArrayList<>();

    private static final class Bolt {
        final List<Vec3> points;
        final int color;
        final long seed;
        int age;

        Bolt(List<Vec3> points, int color, long seed) {
            this.points = points;
            this.color = color;
            this.seed = seed;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ChainBoltS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BOLTS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(ChainBoltEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(ChainBoltEffects::render);
    }

    private static void spawn(Minecraft client, ChainBoltS2CPayload payload) {
        if (client.level == null || payload.points().isEmpty()) return;
        Entity shooter = client.level.getEntity(payload.shooterId());
        List<Vec3> points = new ArrayList<>(payload.points().size() + 1);
        points.add(shooter instanceof Player player ? BlastEffects.hand(player, 1.0F) : payload.points().getFirst());
        points.addAll(payload.points());
        int color = ARGB.opaque(payload.color());
        BOLTS.add(new Bolt(points, color, client.level.getRandom().nextLong()));
        for (int i = 1; i < points.size(); i++) {
            int count = payload.struck() ? VisualQuality.count(HIT_VOXELS) : VisualQuality.count(HIT_VOXELS) / 2;
            ProjectileBursts.spawn(points.get(i), color, count, HIT_SPEED, HIT_SIZE, HIT_LIFETIME, (i - 1) * HOP_DELAY);
        }
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        BOLTS.removeIf(bolt -> ++bolt.age >= LIFETIME + bolt.points.size() * HOP_DELAY);
    }

    private static void render(LevelRenderContext context) {
        if (BOLTS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Bolt bolt : BOLTS) {
            for (int hop = 1; hop < bolt.points.size(); hop++) {
                float time = bolt.age + partialTicks - (hop - 1) * HOP_DELAY;
                if (time < 0.0F) continue;
                float life = 1.0F - Mth.clamp(time / LIFETIME, 0.0F, 1.0F);
                if (life <= 0.0F) continue;

                RandomSource random = RandomSource.create(bolt.seed + hop * 7919L + (bolt.age / FLICKER_TICKS) * 31L);
                List<Vec3> path = jag(bolt.points.get(hop - 1), bolt.points.get(hop), random);
                int tint = VoxelRenderer.toWhite(bolt.color, 0.3F + 0.6F * life);
                float half = VoxelRenderer.snapSize(VOXEL_SIZE * (0.4F + 0.6F * life) * 0.5F);

                List<ShieldEffects.Voxel> voxels = new ArrayList<>();
                for (int i = 1; i < path.size(); i++) {
                    Vec3 from = path.get(i - 1);
                    Vec3 to = path.get(i);
                    int steps = Math.max(Mth.floor((float) from.distanceTo(to) / SPACING), 1);
                    for (int step = 0; step < steps; step++) {
                        voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(from.lerp(to, (double) step / steps)), half, tint));
                    }
                }
                voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(path.getLast()), half, tint));
                ShieldEffects.submit(context, camera, voxels, life);
            }
        }
    }

    private static List<Vec3> jag(Vec3 from, Vec3 to, RandomSource random) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        List<Vec3> path = new ArrayList<>();
        path.add(from);
        if (length < 1.0E-3) return path;

        Vec3 direction = axis.scale(1.0 / length);
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 up = side.cross(direction).normalize();
        int kinks = Math.max(Mth.floor((float) length / KINK_LENGTH), 2);
        for (int i = 1; i < kinks; i++) {
            double t = (double) i / kinks;
            double taper = Math.sin(t * Math.PI);
            Vec3 offset = side.scale(random.nextGaussian() * JAG * taper).add(up.scale(random.nextGaussian() * JAG * taper));
            path.add(from.add(axis.scale(t)).add(offset));
        }
        path.add(to);
        return path;
    }

    private ChainBoltEffects() {}
}
