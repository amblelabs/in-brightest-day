package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

public final class VoxelRenderer {
    public static final float PIXEL = 1.0F / 16.0F;
    private static final double NEAR_FADE_START = 0.1;
    private static final double NEAR_FADE_END = 2.0;

    private static final float SHADE_TOP = 1.0F;
    private static final float SHADE_BOTTOM = 0.5F;
    private static final float SHADE_NORTH_SOUTH = 0.8F;
    private static final float SHADE_EAST_WEST = 0.6F;

    public static void cube(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, float half, int color, boolean shaded) {
        float x0 = (float) center.x - half, x1 = (float) center.x + half;
        float y0 = (float) center.y - half, y1 = (float) center.y + half;
        float z0 = (float) center.z - half, z1 = (float) center.z + half;

        int top = shaded ? shade(color, SHADE_TOP) : color;
        int bottom = shaded ? shade(color, SHADE_BOTTOM) : color;
        int northSouth = shaded ? shade(color, SHADE_NORTH_SOUTH) : color;
        int eastWest = shaded ? shade(color, SHADE_EAST_WEST) : color;

        face(pose, buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top);
        face(pose, buffer, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, bottom);
        face(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, northSouth);
        face(pose, buffer, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, northSouth);
        face(pose, buffer, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, eastWest);
        face(pose, buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, eastWest);
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
