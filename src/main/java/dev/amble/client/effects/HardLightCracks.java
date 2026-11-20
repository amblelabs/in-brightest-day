package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.core.networking.payloads.s2c.HardLightCracksS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class HardLightCracks {
    public static final int BROKEN = HardLightCracksS2CPayload.BROKEN;
    private static final float LIFT = 0.004F;

    private static final Map<BlockPos, Integer> STAGE = new HashMap<>();
    private static int version;

    // camera-relative cell centre, so walls and dome shells can share the submit
    public record Crack(float x, float y, float z, float half, int drawn, int stage) {}

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

    // one camera-relative quad spanning centre ± ax ± ay, facing normal; uv picks the slice of the crack texture it shows
    public record Panel(Vec3 center, Vec3 ax, Vec3 ay, Vec3 normal, float u0, float v0, float u1, float v1, int stage) {}

    public static void submit(LevelRenderContext context, List<Crack> cracks) {
        submit(context, cracks, Crack::stage, HardLightCracks::faces);
    }

    public static void submitPanels(LevelRenderContext context, List<Panel> panels) {
        submit(context, panels, Panel::stage, HardLightCracks::panel);
    }

    private static <T> void submit(LevelRenderContext context, List<T> items, ToIntFunction<T> stage, Emitter<T> emitter) {
        if (items.isEmpty()) return;
        for (int index = 0; index < ModelBakery.DESTROY_TYPES.size(); index++) {
            int texture = index;
            if (items.stream().noneMatch(item -> texture(stage.applyAsInt(item)) == texture)) continue;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), ModelBakery.DESTROY_TYPES.get(index), (pose, buffer) -> {
                for (T item : items) {
                    if (texture(stage.applyAsInt(item)) == texture) emitter.emit(pose, buffer, item);
                }
            });
        }
    }

    private interface Emitter<T> {
        void emit(PoseStack.Pose pose, VertexConsumer buffer, T item);
    }

    private static void panel(PoseStack.Pose pose, VertexConsumer buffer, Panel panel) {
        Vec3 c = panel.center().add(panel.normal().scale(LIFT));
        Vec3 ax = panel.ax();
        Vec3 ay = panel.ay();
        float nx = (float) panel.normal().x;
        float ny = (float) panel.normal().y;
        float nz = (float) panel.normal().z;
        Vec3 p0 = c.subtract(ax).subtract(ay);
        Vec3 p1 = c.add(ax).subtract(ay);
        Vec3 p2 = c.add(ax).add(ay);
        Vec3 p3 = c.subtract(ax).add(ay);
        // crumbling culls back faces, so wind counter-clockwise around the requested normal
        if (ax.cross(ay).dot(panel.normal()) >= 0.0) {
            vertex(pose, buffer, p0, panel.u0(), panel.v1(), nx, ny, nz);
            vertex(pose, buffer, p1, panel.u1(), panel.v1(), nx, ny, nz);
            vertex(pose, buffer, p2, panel.u1(), panel.v0(), nx, ny, nz);
            vertex(pose, buffer, p3, panel.u0(), panel.v0(), nx, ny, nz);
        } else {
            vertex(pose, buffer, p3, panel.u0(), panel.v0(), nx, ny, nz);
            vertex(pose, buffer, p2, panel.u1(), panel.v0(), nx, ny, nz);
            vertex(pose, buffer, p1, panel.u1(), panel.v1(), nx, ny, nz);
            vertex(pose, buffer, p0, panel.u0(), panel.v1(), nx, ny, nz);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, float u, float v, float nx, float ny, float nz) {
        vertex(pose, buffer, (float) p.x, (float) p.y, (float) p.z, u, v, nx, ny, nz);
    }

    // stages 1..BROKEN-1 spread across vanilla's destroy_stage_0..9
    private static int texture(int stage) {
        int last = ModelBakery.DESTROY_TYPES.size() - 1;
        return Mth.clamp(Math.round((stage - 1) * (float) last / (BROKEN - 2)), 0, last);
    }

    private static void faces(PoseStack.Pose pose, VertexConsumer buffer, Crack crack) {
        float h = crack.half() + LIFT;
        float x = crack.x();
        float y = crack.y();
        float z = crack.z();
        int drawn = crack.drawn();
        if ((drawn & VoxelRenderer.UP) != 0) quad(pose, buffer, 0.0F, 1.0F, 0.0F, x - h, y + h, z - h, x - h, y + h, z + h, x + h, y + h, z + h, x + h, y + h, z - h);
        if ((drawn & VoxelRenderer.DOWN) != 0) quad(pose, buffer, 0.0F, -1.0F, 0.0F, x - h, y - h, z + h, x - h, y - h, z - h, x + h, y - h, z - h, x + h, y - h, z + h);
        if ((drawn & VoxelRenderer.NORTH) != 0) quad(pose, buffer, 0.0F, 0.0F, -1.0F, x + h, y + h, z - h, x + h, y - h, z - h, x - h, y - h, z - h, x - h, y + h, z - h);
        if ((drawn & VoxelRenderer.SOUTH) != 0) quad(pose, buffer, 0.0F, 0.0F, 1.0F, x - h, y + h, z + h, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y + h, z + h);
        if ((drawn & VoxelRenderer.WEST) != 0) quad(pose, buffer, -1.0F, 0.0F, 0.0F, x - h, y + h, z - h, x - h, y - h, z - h, x - h, y - h, z + h, x - h, y + h, z + h);
        if ((drawn & VoxelRenderer.EAST) != 0) quad(pose, buffer, 1.0F, 0.0F, 0.0F, x + h, y + h, z + h, x + h, y - h, z + h, x + h, y - h, z - h, x + h, y + h, z - h);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float nx, float ny, float nz, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(pose, buffer, x0, y0, z0, 0.0F, 0.0F, nx, ny, nz);
        vertex(pose, buffer, x1, y1, z1, 0.0F, 1.0F, nx, ny, nz);
        vertex(pose, buffer, x2, y2, z2, 1.0F, 1.0F, nx, ny, nz);
        vertex(pose, buffer, x3, y3, z3, 1.0F, 0.0F, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        // shader packs swap BLOCK for a format with Normal, which BufferBuilder then requires on every vertex
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    private HardLightCracks() {}
}
