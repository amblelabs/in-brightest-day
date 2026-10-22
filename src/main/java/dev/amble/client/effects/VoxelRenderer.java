package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class VoxelRenderer {
    public static final float PIXEL = 1.0F / 16.0F;
    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int NORTH = 4;
    public static final int SOUTH = 8;
    public static final int WEST = 16;
    public static final int EAST = 32;
    public static final int ALL_FACES = 63;
    private static final double NEAR_FADE_START = 0.1;
    private static final double NEAR_FADE_END = 2.0;

    private static final float SHADE_TOP = 1.0F;
    private static final float SHADE_BOTTOM = 0.5F;
    private static final float SHADE_NORTH_SOUTH = 0.8F;
    private static final float SHADE_EAST_WEST = 0.6F;
    private static final float SOFTEN = 0.6F;
    private static final double RIM_LIFT = 0.002;
    private static final float DENSITY = 0.05F;

    public static void cube(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, float half, int color, boolean shaded) {
        cube(pose, buffer, center, half, color, shaded, ALL_FACES);
    }

    public static void cube(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, float half, int color, boolean shaded, int faces) {
        float x0 = (float) center.x - half, x1 = (float) center.x + half;
        float y0 = (float) center.y - half, y1 = (float) center.y + half;
        float z0 = (float) center.z - half, z1 = (float) center.z + half;

        int top = shaded ? shade(color, SHADE_TOP) : color;
        int bottom = shaded ? shade(color, SHADE_BOTTOM) : color;
        int northSouth = shaded ? shade(color, SHADE_NORTH_SOUTH) : color;
        int eastWest = shaded ? shade(color, SHADE_EAST_WEST) : color;

        if ((faces & UP) != 0) face(pose, buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top);
        if ((faces & DOWN) != 0) face(pose, buffer, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, bottom);
        if ((faces & NORTH) != 0) face(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, northSouth);
        if ((faces & SOUTH) != 0) face(pose, buffer, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, northSouth);
        if ((faces & WEST) != 0) face(pose, buffer, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, eastWest);
        if ((faces & EAST) != 0) face(pose, buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, eastWest);
    }

    public static void glass(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 ax, Vec3 ay, Vec3 az, int fill, int rim, float rimWidth, int faces, boolean drawFill) {
        Vec3[] axes = {ax, ay, az};
        for (int k = 0; k < 3; k++) {
            for (int s = -1; s <= 1; s += 2) {
                if ((faces & bit(k, s)) == 0) continue;
                Vec3 normal = axes[k].scale(s);
                Vec3 u = axes[(k + 1) % 3];
                Vec3 v = axes[(k + 2) % 3];
                Vec3 face = center.add(normal);
                if (drawFill) quad(pose, buffer, face.subtract(u).subtract(v), face.add(u).subtract(v), face.add(u).add(v), face.subtract(u).add(v), soft(normal, fill));
                Vec3 lifted = face.add(normal.normalize().scale(RIM_LIFT));
                for (int j = 1; j <= 2; j++) {
                    int axis = (k + j) % 3;
                    Vec3 along = axes[axis];
                    Vec3 across = axes[(k + 3 - j) % 3];
                    double length = along.length();
                    if (length < 1.0E-6) continue;
                    double inset = 1.0 - Math.min(rimWidth / length, 0.5);
                    for (int r = -1; r <= 1; r += 2) {
                        if ((faces & bit(axis, r)) == 0) continue;
                        Vec3 edge = lifted.add(along.scale(r));
                        Vec3 inner = lifted.add(along.scale(r * inset));
                        quad(pose, buffer, edge.subtract(across), edge.add(across), inner.add(across), inner.subtract(across), rim);
                    }
                }
            }
        }
    }

    private static int bit(int axis, int sign) {
        return switch (axis) {
            case 0 -> sign > 0 ? EAST : WEST;
            case 1 -> sign > 0 ? UP : DOWN;
            default -> sign > 0 ? SOUTH : NORTH;
        };
    }

    private static int soft(Vec3 normal, int color) {
        Vec3 n = normal.normalize();
        float across = (float) (Math.abs(n.x) / (Math.abs(n.x) + Math.abs(n.z) + 1.0E-4));
        float sideShade = Mth.lerp(across, SHADE_NORTH_SOUTH, SHADE_EAST_WEST);
        float factor = n.y >= 0.0 ? Mth.lerp((float) n.y, sideShade, SHADE_TOP) : Mth.lerp((float) -n.y, sideShade, SHADE_BOTTOM);
        return shade(color, Mth.lerp(SOFTEN, factor, 1.0F));
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        face(pose, buffer, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) c.x, (float) c.y, (float) c.z, (float) d.x, (float) d.y, (float) d.z, color);
    }

    public static int nearFade(Vec3 relative, int color) {
        double distance = relative.length();
        float t = (float) Math.clamp((distance - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0.0, 1.0);
        float fade = t * t * (3.0F - 2.0F * t);
        return ARGB.color(Math.round(ARGB.alpha(color) * fade), color);
    }

    public static Vec3 snap(Vec3 pos) {
        return new Vec3(snap(pos.x), snap(pos.y), snap(pos.z));
    }

    public static float snapSize(float half) {
        return Math.max(Math.round(half * 2.0F / PIXEL), 1) * PIXEL * 0.5F;
    }

    public static float denser(float alpha) {
        return alpha + (1.0F - alpha) * DENSITY;
    }

    public static int toWhite(int color, float amount) {
        return ARGB.srgbLerp(Math.clamp(amount, 0.0F, 1.0F), color, 0xFFFFFFFF);
    }

    private static double snap(double value) {
        return Math.round(value / PIXEL) * (double) PIXEL;
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        buffer.addVertex(pose, ax, ay, az).setColor(color);
        buffer.addVertex(pose, bx, by, bz).setColor(color);
        buffer.addVertex(pose, cx, cy, cz).setColor(color);
        buffer.addVertex(pose, dx, dy, dz).setColor(color);
    }

    private static int shade(int color, float factor) {
        return ARGB.color(ARGB.alpha(color),
                Math.round(ARGB.red(color) * factor),
                Math.round(ARGB.green(color) * factor),
                Math.round(ARGB.blue(color) * factor));
    }

    private VoxelRenderer() {}
}
