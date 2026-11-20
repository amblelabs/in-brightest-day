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
    private static final float[] FACE_SHADE = {
            Mth.lerp(0.6F, 0.6F, 1.0F), Mth.lerp(0.6F, 0.6F, 1.0F),
            Mth.lerp(0.6F, 0.5F, 1.0F), Mth.lerp(0.6F, 1.0F, 1.0F),
            Mth.lerp(0.6F, 0.8F, 1.0F), Mth.lerp(0.6F, 0.8F, 1.0F)};
    private static final float DENSITY = 0.3F;
    private static final float BACK_FADE = 0.28F;
    private static final float FRESNEL_WHITE = 0.35F;
    public static final float FRESNEL_GLASS = 0.45F;
    public static final float FRESNEL_FROST = 0.8F;

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
        glass(pose, buffer, center, ax, ay, az, fill, rim, rimWidth, faces, drawFill, false, FRESNEL_GLASS);
    }

    public static void glass(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 ax, Vec3 ay, Vec3 az, int fill, int rim, float rimWidth, int faces, boolean drawFill,
                             boolean frontOnly, float fresnelMin) {
        Vec3[] axes = {ax, ay, az};
        for (int k = 0; k < 3; k++) {
            for (int s = -1; s <= 1; s += 2) {
                if ((faces & bit(k, s)) == 0) continue;
                Vec3 normal = axes[k].scale(s);
                Vec3 u = axes[(k + 1) % 3];
                Vec3 v = axes[(k + 2) % 3];
                Vec3 face = center.add(normal);
                double faceLength = face.length();
                float facing = faceLength < 1.0E-4 ? 1.0F : (float) (-normal.normalize().dot(face) / faceLength);
                if (facing <= 0.0F && frontOnly) continue;
                float grazing = facing <= 0.0F ? 0.0F : (1.0F - facing) * (1.0F - facing);
                float strength = facing <= 0.0F ? BACK_FADE : Mth.lerp(grazing, fresnelMin, 1.0F);
                int faceRim = withAlpha(rim, strength);
                if (drawFill) quad(pose, buffer, face.subtract(u).subtract(v), face.add(u).subtract(v), face.add(u).add(v), face.subtract(u).add(v),
                        withAlpha(whiten(soft(normal, fill), FRESNEL_WHITE * grazing), strength));
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
                        quad(pose, buffer, edge.subtract(across), edge.add(across), inner.add(across), inner.subtract(across), faceRim);
                    }
                }
            }
        }
    }

    public static int glassBox(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float half, int fill, int rim, float rimWidth, int faces, int drawn, boolean drawFill) {
        return glassBox(pose, buffer, x, y, z, half, fill, rim, rimWidth, faces, drawn, drawFill, false, FRESNEL_GLASS);
    }

    public static int glassBox(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float half, int fill, int rim, float rimWidth, int faces, int drawn, boolean drawFill,
                               boolean frontOnly, float fresnelMin) {
        int quads = 0;
        float inset = half - Math.min(rimWidth, half * 0.5F);
        for (int k = 0; k < 3; k++) {
            int a = (k + 1) % 3;
            int b = (k + 2) % 3;
            float ck = k == 0 ? x : k == 1 ? y : z;
            float ca = a == 0 ? x : a == 1 ? y : z;
            float cb = b == 0 ? x : b == 1 ? y : z;
            for (int s = -1; s <= 1; s += 2) {
                if ((drawn & bit(k, s)) == 0) continue;
                float plane = ck + s * half;
                float facing = facing(k, s, plane, ca, cb);
                if (facing <= 0.0F && frontOnly) continue;
                float grazing = facing <= 0.0F ? 0.0F : (1.0F - facing) * (1.0F - facing);
                float strength = facing <= 0.0F ? BACK_FADE : Mth.lerp(grazing, fresnelMin, 1.0F);
                int faceRim = withAlpha(rim, strength);
                if (drawFill) {
                    int shaded = withAlpha(whiten(shade(fill, FACE_SHADE[k * 2 + (s > 0 ? 1 : 0)]), FRESNEL_WHITE * grazing), strength);
                    vertex(pose, buffer, k, plane, a, ca - half, cb - half, shaded);
                    vertex(pose, buffer, k, plane, a, ca + half, cb - half, shaded);
                    vertex(pose, buffer, k, plane, a, ca + half, cb + half, shaded);
                    vertex(pose, buffer, k, plane, a, ca - half, cb + half, shaded);
                    quads++;
                }
                float lifted = plane + s * (float) RIM_LIFT;
                for (int r = -1; r <= 1; r += 2) {
                    if ((faces & bit(a, r)) != 0) {
                        float edge = ca + r * half;
                        float inner = ca + r * inset;
                        vertex(pose, buffer, k, lifted, a, edge, cb - half, faceRim);
                        vertex(pose, buffer, k, lifted, a, edge, cb + half, faceRim);
                        vertex(pose, buffer, k, lifted, a, inner, cb + half, faceRim);
                        vertex(pose, buffer, k, lifted, a, inner, cb - half, faceRim);
                        quads++;
                    }
                    if ((faces & bit(b, r)) != 0) {
                        float edge = cb + r * half;
                        float inner = cb + r * inset;
                        vertex(pose, buffer, k, lifted, a, ca - half, edge, faceRim);
                        vertex(pose, buffer, k, lifted, a, ca + half, edge, faceRim);
                        vertex(pose, buffer, k, lifted, a, ca + half, inner, faceRim);
                        vertex(pose, buffer, k, lifted, a, ca - half, inner, faceRim);
                        quads++;
                    }
                }
            }
        }
        return quads;
    }

    private static float facing(int k, int s, float plane, float ca, float cb) {
        float length = Mth.sqrt(plane * plane + ca * ca + cb * cb);
        return length < 1.0E-4F ? 1.0F : -s * plane / length;
    }

    private static int withAlpha(int color, float strength) {
        return ARGB.color(Math.round(Mth.clamp(ARGB.alpha(color) * strength, 0.0F, 255.0F)), color);
    }

    private static int whiten(int color, float amount) {
        if (amount <= 0.0F) return color;
        return ARGB.color(ARGB.alpha(color), ARGB.srgbLerp(Math.clamp(amount, 0.0F, 1.0F), color, 0xFFFFFFFF));
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int k, float kv, int a, float av, float bv, int color) {
        float x = k == 0 ? kv : a == 0 ? av : bv;
        float y = k == 1 ? kv : a == 1 ? av : bv;
        float z = k == 2 ? kv : a == 2 ? av : bv;
        buffer.addVertex(pose, x, y, z).setColor(color);
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

    public static int nearFade(double distance, int color) {
        float t = (float) Math.clamp((distance - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0.0, 1.0);
        if (t >= 1.0F) return color;
        float fade = t * t * (3.0F - 2.0F * t);
        return ARGB.color(Math.round(ARGB.alpha(color) * fade), color);
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
