package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.flight.FlightRenderTypes;
import dev.amble.core.networking.payloads.s2c.HardLightCracksS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HardLightCracks {
    public static final int BROKEN = HardLightCracksS2CPayload.BROKEN;
    private static final float STAGES = 9.0F;
    private static final float MAX_DARKEN = 0.6F;
    private static final float LINE_WIDTH = 0.045F;
    private static final float LIFT = 0.004F;
    private static final int CRACK_COLOR = 0x0A0A12;

    private static final Map<BlockPos, Integer> STAGE = new HashMap<>();
    private static int version;

    public record Crack(Vec3 center, float half, int drawn, int stage, long seed) {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(HardLightCracksS2CPayload.TYPE, (payload, context) -> {
            List<BlockPos> cells = payload.cells();
            byte[] stages = payload.stages();
            for (int i = 0; i < cells.size() && i < stages.length; i++) {
                if (stages[i] <= 0) STAGE.remove(cells.get(i));
                else STAGE.put(cells.get(i).immutable(), (int) stages[i]);
            }
            version++;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STAGE.clear();
            version++;
        });
    }

    public static boolean any() {
        return !STAGE.isEmpty();
    }

    public static int version() {
        return version;
    }

    public static int stage(BlockPos pos) {
        if (STAGE.isEmpty()) return 0;
        Integer stage = STAGE.get(pos);
        return stage == null ? 0 : stage;
    }

    public static boolean broken(BlockPos pos) {
        return stage(pos) >= BROKEN;
    }

    public static int darken(int color, int stage) {
        if (stage <= 0) return color;
        float amount = Math.min(stage, STAGES) / STAGES * MAX_DARKEN;
        return ARGB.color(ARGB.alpha(color), ARGB.srgbLerp(amount, color, 0xFF000000));
    }

    public static long seed(BlockPos pos) {
        return pos.asLong() * 0x9E3779B97F4A7C15L;
    }

    public static void submit(LevelRenderContext context, Vec3 camera, List<Crack> cracks, float alpha) {
        if (cracks.isEmpty()) return;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(), (pose, buffer) -> {
            for (Crack crack : cracks) {
                float x = (float) (crack.center().x - camera.x);
                float y = (float) (crack.center().y - camera.y);
                float z = (float) (crack.center().z - camera.z);
                draw(pose, buffer, x, y, z, crack.half(), crack.drawn(), crack.stage(), crack.seed(), lineColor(crack.stage(), alpha));
            }
        });
    }

    public static int lineColor(int stage, float alpha) {
        float strength = Math.min(stage, STAGES) / STAGES;
        return ARGB.color(Math.round(Mth.clamp((0.45F + 0.5F * strength) * alpha, 0.0F, 1.0F) * 255), CRACK_COLOR);
    }

    public static void draw(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float half, int drawn, int stage, long seed, int color) {
        int branches = 1 + stage / 2;
        int steps = 2 + stage / 3;
        float width = LINE_WIDTH * half * 2.0F;
        for (int k = 0; k < 3; k++) {
            int a = (k + 1) % 3;
            int b = (k + 2) % 3;
            float ck = k == 0 ? x : k == 1 ? y : z;
            float ca = a == 0 ? x : a == 1 ? y : z;
            float cb = b == 0 ? x : b == 1 ? y : z;
            for (int s = -1; s <= 1; s += 2) {
                if ((drawn & bit(k, s)) == 0) continue;
                float plane = ck + s * (half + LIFT);
                long random = seed ^ (k * 2L + (s > 0 ? 1 : 0)) * 0xBF58476D1CE4E5B9L;
                random = next(random);
                float originA = (unit(random) - 0.5F) * half;
                random = next(random);
                float originB = (unit(random) - 0.5F) * half;
                for (int branch = 0; branch < branches; branch++) {
                    random = next(random);
                    float angle = unit(random) * Mth.TWO_PI;
                    float pa = originA;
                    float pb = originB;
                    for (int step = 0; step < steps; step++) {
                        random = next(random);
                        angle += (unit(random) - 0.5F) * 1.4F;
                        random = next(random);
                        float length = half * (0.25F + 0.3F * unit(random));
                        float na = Mth.clamp(pa + Mth.cos(angle) * length, -half, half);
                        float nb = Mth.clamp(pb + Mth.sin(angle) * length, -half, half);
                        segment(pose, buffer, k, plane, a, ca + pa, cb + pb, ca + na, cb + nb, width * (1.0F - step * 0.15F), color);
                        if (Math.abs(na) >= half || Math.abs(nb) >= half) break;
                        pa = na;
                        pb = nb;
                    }
                }
            }
        }
    }

    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, int k, float plane, int a, float a0, float b0, float a1, float b1, float width, int color) {
        float da = a1 - a0;
        float db = b1 - b0;
        float length = Mth.sqrt(da * da + db * db);
        if (length < 1.0E-4F) return;
        float oa = -db / length * width * 0.5F;
        float ob = da / length * width * 0.5F;
        vertex(pose, buffer, k, plane, a, a0 + oa, b0 + ob, color);
        vertex(pose, buffer, k, plane, a, a1 + oa, b1 + ob, color);
        vertex(pose, buffer, k, plane, a, a1 - oa, b1 - ob, color);
        vertex(pose, buffer, k, plane, a, a0 - oa, b0 - ob, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int k, float kv, int a, float av, float bv, int color) {
        float x = k == 0 ? kv : a == 0 ? av : bv;
        float y = k == 1 ? kv : a == 1 ? av : bv;
        float z = k == 2 ? kv : a == 2 ? av : bv;
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    private static int bit(int axis, int sign) {
        return switch (axis) {
            case 0 -> sign > 0 ? VoxelRenderer.EAST : VoxelRenderer.WEST;
            case 1 -> sign > 0 ? VoxelRenderer.UP : VoxelRenderer.DOWN;
            default -> sign > 0 ? VoxelRenderer.SOUTH : VoxelRenderer.NORTH;
        };
    }

    private static long next(long state) {
        long z = state + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long value) {
        return (value >>> 40) / (float) (1L << 24);
    }

    private HardLightCracks() {}
}
