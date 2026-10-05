package dev.amble.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.core.space.Cosmos;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

public final class SpaceRenderer {
    public static final float DEPTH_FAR = 1_000_000.0F;
    private static final double SKY_DISTANCE = 500_000.0;
    private static final int STAR_COUNT = 3000;
    private static final long STAR_SEED = 0x0A0A0A0AL;
    private static final double STAR_PIXEL = 0.0011;
    private static final float BRIGHT_STAR_CHANCE = 0.08F;
    private static final double MIN_ANGULAR_SIZE = 0.0004;
    private static final float AMBIENT = 0.14F;
    private static final double ATMOSPHERE_SCALE = 1.06;
    private static final double CLOUD_SCALE = 1.02;
    private static final int GLOW_LEVELS = 4;
    private static final int ATMOSPHERE_DEPTH = 5;
    private static final int VORTEX_PIXELS = 40;
    private static final int CORONA_PIXELS = 48;
    private static final int RING_PIXELS = 72;
    private static final int GALAXY_PIXELS = 128;

    private record Galaxy(Vec3 direction, Vec3 normal, double radius, int arms, double twist, int core, int arm, int rim, long seed) {}

    private static final List<Galaxy> GALAXIES = List.of(
            new Galaxy(new Vec3(-0.55, 0.42, 0.72).normalize(), new Vec3(0.3, 0.85, -0.42).normalize(), 80_000.0, 2, 4.2,
                    0xFFF2D8, 0x8C9CFF, 0xFF6FC8, 11L),
            new Galaxy(new Vec3(0.62, 0.28, -0.73).normalize(), new Vec3(-0.6, 0.35, 0.7).normalize(), 32_000.0, 3, 3.1,
                    0xFFE6C0, 0x5FD6FF, 0xB07CFF, 29L)
    );

    private static final Vec3[] STAR_DIRECTIONS = new Vec3[STAR_COUNT];
    private static final int[] STAR_SIZES = new int[STAR_COUNT];
    private static final int[] STAR_COLORS = new int[STAR_COUNT];
    private static final float[] STAR_PHASES = new float[STAR_COUNT];
    private static final boolean[] STAR_GLINTS = new boolean[STAR_COUNT];

    static {
        RandomSource random = RandomSource.create(STAR_SEED);
        int[] palette = {0xFFFFFF, 0xCFE0FF, 0xFFF1D0, 0xFFD9A8, 0xB8CCFF, 0xFFC4E6};
        for (int i = 0; i < STAR_COUNT; i++) {
            Vec3 direction;
            do {
                direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            } while (direction.lengthSqr() < 1.0E-6);
            STAR_DIRECTIONS[i] = direction.normalize();
            float magnitude = random.nextFloat();
            STAR_SIZES[i] = magnitude > 0.97F ? 3 : magnitude > 0.8F ? 2 : 1;
            STAR_COLORS[i] = palette[random.nextInt(palette.length)];
            STAR_PHASES[i] = random.nextFloat() * Mth.TWO_PI;
            STAR_GLINTS[i] = random.nextFloat() < BRIGHT_STAR_CHANCE;
        }
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(SpaceRenderer::render);
    }

    public static boolean inSpace() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Cosmos.SPACE);
    }

    public static boolean onOa() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Cosmos.OA);
    }

    public static boolean extendsView() {
        return inSpace() || onOa();
    }

    private static void render(LevelRenderContext context) {
        boolean space = inSpace();
        if (!space && !onOa()) return;

        Minecraft client = Minecraft.getInstance();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double gameTime = client.level.getGameTime() + partialTicks;
        float time = (float) (gameTime % 24000.0);
        PoseStack poseStack = context.poseStack();

        context.submitNodeCollector().submitCustomGeometry(poseStack, SpaceRenderTypes.GLOW, (pose, buffer) -> {
            for (Galaxy galaxy : GALAXIES) galaxy(pose, buffer, galaxy);
            if (!space) return;
            stars(pose, buffer, time);
            for (Cosmos.Celestial body : Cosmos.BODIES) glow(pose, buffer, camera, body, gameTime);
            for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) vortex(pose, buffer, camera, wormhole, time, false);
        });
        if (!space) return;

        context.submitNodeCollector().submitCustomGeometry(poseStack, SpaceRenderTypes.BODY, (pose, buffer) -> {
            for (Cosmos.Celestial body : Cosmos.BODIES) cube(pose, buffer, camera, body, gameTime);
            for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) vortex(pose, buffer, camera, wormhole, time, true);
        });
        context.submitNodeCollector().submitCustomGeometry(poseStack, SpaceRenderTypes.HAZE, (pose, buffer) -> {
            for (Cosmos.Celestial body : Cosmos.BODIES) {
                if (body.ringed()) rings(pose, buffer, camera, body, gameTime);
                if (body.style() == Cosmos.Style.OCEANIC) clouds(pose, buffer, camera, body, gameTime);
            }
        });
    }

    private static void stars(PoseStack.Pose pose, VertexConsumer buffer, float time) {
        double pixel = STAR_PIXEL * SKY_DISTANCE;
        for (int i = 0; i < STAR_COUNT; i++) {
            Vec3 center = STAR_DIRECTIONS[i].scale(SKY_DISTANCE);
            Vec3[] axes = facing(center);
            float twinkle = 0.7F + 0.3F * Mth.sin(time * (0.05F + (i % 7) * 0.012F) + STAR_PHASES[i]);
            int alpha = quantize(twinkle * (0.65F + 0.15F * STAR_SIZES[i]));
            double half = STAR_SIZES[i] * pixel * 0.5;
            square(pose, buffer, center, axes[0], axes[1], -half, -half, half * 2.0, ARGB.color(alpha, STAR_COLORS[i]));
            if (STAR_GLINTS[i]) {
                int glint = ARGB.color(alpha / 2, STAR_COLORS[i]);
                double arm = pixel * 0.5;
                square(pose, buffer, center, axes[0], axes[1], -arm, half, pixel, glint);
                square(pose, buffer, center, axes[0], axes[1], -arm, -half - pixel, pixel, glint);
                square(pose, buffer, center, axes[0], axes[1], half, -arm, pixel, glint);
                square(pose, buffer, center, axes[0], axes[1], -half - pixel, -arm, pixel, glint);
            }
        }
    }

    private static void galaxy(PoseStack.Pose pose, VertexConsumer buffer, Galaxy galaxy) {
        Vec3 center = galaxy.direction().scale(SKY_DISTANCE);
        Vec3 reference = Math.abs(galaxy.normal().y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = galaxy.normal().cross(reference).normalize();
        Vec3 v = galaxy.normal().cross(u);
        double cell = galaxy.radius() * 2.0 / GALAXY_PIXELS;
        for (int i = 0; i < GALAXY_PIXELS; i++) {
            for (int j = 0; j < GALAXY_PIXELS; j++) {
                double x = (i + 0.5) / GALAXY_PIXELS * 2.0 - 1.0;
                double y = (j + 0.5) / GALAXY_PIXELS * 2.0 - 1.0;
                double r = Math.hypot(x, y);
                if (r > 1.0) continue;

                float speckle = PlanetTextures.hash(i, j, 0, (int) galaxy.seed());
                double theta = Math.atan2(y, x);
                double spiral = theta * galaxy.arms() - Math.log(r + 0.04) * galaxy.twist();
                float arm = (float) Math.pow(0.5 + 0.5 * Math.cos(spiral), 2.5);
                float core = (float) Math.exp(-r * 7.0);
                float density = core * 1.6F + arm * (float) Math.exp(-r * 1.8) * (0.7F + 0.6F * speckle) + (speckle > 0.98F ? 0.6F : 0.0F);
                int alpha = quantize(Mth.clamp(density, 0.0F, 1.0F));
                if (alpha == 0) continue;

                int tint = r < 0.22 ? ARGB.srgbLerp((float) (r / 0.22), ARGB.opaque(galaxy.core()), ARGB.opaque(galaxy.arm()))
                        : ARGB.srgbLerp((float) ((r - 0.22) / 0.78), ARGB.opaque(galaxy.arm()), ARGB.opaque(galaxy.rim()));
                square(pose, buffer, center, u, v, x * galaxy.radius() - cell * 0.5, y * galaxy.radius() - cell * 0.5, cell, ARGB.color(alpha, tint));
            }
        }
    }

    private static Vec3[][] rotatedFaces(Quaterniond orientation) {
        Vec3[][] faces = new Vec3[6][3];
        for (int face = 0; face < 6; face++) {
            for (int axis = 0; axis < 3; axis++) faces[face][axis] = rotate(orientation, PlanetTextures.FACES[face][axis]);
        }
        return faces;
    }

    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Cosmos.Celestial body, double gameTime) {
        Vec3 relative = body.center().subtract(camera);
        double distance = relative.length();
        if (distance > body.radius() * 2.0 && body.radius() / distance < MIN_ANGULAR_SIZE) return;

        PlanetTextures.Texture texture = PlanetTextures.of(body);
        int pixels = texture.pixels();
        Vec3[][] faces = rotatedFaces(body.orientation(gameTime));
        int stride = stride(body.radius(), distance);
        Vec3 light = Cosmos.SUN.center().subtract(body.center()).normalize();
        double cell = body.radius() * 2.0 / pixels;
        for (int face = 0; face < 6; face++) {
            Vec3 normal = faces[face][0];
            float lit = AMBIENT + (1.0F - AMBIENT) * (float) Math.max(normal.dot(light), 0.0);
            lit = Math.round(lit * 8.0F) / 8.0F;
            Vec3 origin = relative.add(normal.scale(body.radius()));
            int[] colors = texture.colors()[face];
            boolean[] emissive = texture.emissive()[face];
            for (int i = 0; i < pixels; i += stride) {
                for (int j = 0; j < pixels; j += stride) {
                    int index = i * pixels + j;
                    int color = emissive[index] ? colors[index] : ARGB.opaque(ARGB.scaleRGB(colors[index], lit));
                    square(pose, buffer, origin, faces[face][1], faces[face][2], -body.radius() + i * cell, -body.radius() + j * cell, cell * stride, color);
                }
            }
        }
    }

    private static void clouds(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Cosmos.Celestial body, double gameTime) {
        PlanetTextures.Texture texture = PlanetTextures.of(body);
        if (texture.clouds() == null) return;
        Vec3 relative = body.center().subtract(camera);
        double half = body.radius() * CLOUD_SCALE;
        int pixels = texture.pixels();
        double cell = half * 2.0 / pixels;
        Vec3[][] faces = rotatedFaces(body.orientation(gameTime * 1.15));
        Vec3 light = Cosmos.SUN.center().subtract(body.center()).normalize();
        for (int face = 0; face < 6; face++) {
            float lit = 0.25F + 0.75F * (float) Math.max(faces[face][0].dot(light), 0.0);
            Vec3 origin = relative.add(faces[face][0].scale(half));
            int[] clouds = texture.clouds()[face];
            for (int i = 0; i < pixels; i++) {
                for (int j = 0; j < pixels; j++) {
                    int cloud = clouds[i * pixels + j];
                    if (cloud == 0) continue;
                    int color = ARGB.color(ARGB.alpha(cloud), ARGB.scaleRGB(ARGB.opaque(cloud), lit));
                    square(pose, buffer, origin, faces[face][1], faces[face][2], -half + i * cell, -half + j * cell, cell, color);
                }
            }
        }
    }

    private static void glow(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Cosmos.Celestial body, double gameTime) {
        Vec3 relative = body.center().subtract(camera);
        switch (body.style()) {
            case STAR -> corona(pose, buffer, relative, body.radius() * 4.2, body.accent(), body.color());
            case OCEANIC -> atmosphere(pose, buffer, relative, body, 0x7FB6FF, gameTime);
            case LANTERN -> atmosphere(pose, buffer, relative, body, body.accent(), gameTime);
            default -> {}
        }
    }

    private static void atmosphere(PoseStack.Pose pose, VertexConsumer buffer, Vec3 relative, Cosmos.Celestial body, int color, double gameTime) {
        double half = body.radius() * ATMOSPHERE_SCALE;
        int pixels = PlanetTextures.pixels(body);
        double cell = half * 2.0 / pixels;
        Vec3[][] faces = rotatedFaces(body.orientation(gameTime));
        for (int face = 0; face < 6; face++) {
            Vec3 origin = relative.add(faces[face][0].scale(half));
            for (int i = 0; i < pixels; i++) {
                for (int j = 0; j < pixels; j++) {
                    int edge = Math.min(Math.min(i, j), Math.min(pixels - 1 - i, pixels - 1 - j));
                    if (edge >= ATMOSPHERE_DEPTH) continue;
                    int alpha = quantize(0.6F * (1.0F - edge / (float) ATMOSPHERE_DEPTH));
                    square(pose, buffer, origin, faces[face][1], faces[face][2], -half + i * cell, -half + j * cell, cell, ARGB.color(alpha, color));
                }
            }
        }
    }

    private static void corona(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, double extent, int inner, int outer) {
        Vec3[] axes = facing(center);
        double cell = extent * 2.0 / CORONA_PIXELS;
        for (int i = 0; i < CORONA_PIXELS; i++) {
            for (int j = 0; j < CORONA_PIXELS; j++) {
                double x = (i + 0.5) / CORONA_PIXELS * 2.0 - 1.0;
                double y = (j + 0.5) / CORONA_PIXELS * 2.0 - 1.0;
                double r = Math.max(Math.abs(x), Math.abs(y));
                if (r < 0.2 || r > 1.0) continue;
                float falloff = (float) Math.pow(1.0 - (r - 0.2) / 0.8, 2.2);
                int alpha = quantize(falloff * 0.9F);
                if (alpha == 0) continue;
                int tint = ARGB.srgbLerp((float) r, ARGB.opaque(inner), ARGB.opaque(outer));
                square(pose, buffer, center, axes[0], axes[1], x * extent - cell * 0.5, y * extent - cell * 0.5, cell, ARGB.color(alpha, tint));
            }
        }
    }

    private static void vortex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Cosmos.Wormhole wormhole, float time, boolean horizon) {
        Vec3 center = wormhole.center().subtract(camera);
        if (center.lengthSqr() < 1.0E-6) return;
        Vec3[] axes = facing(center);
        double extent = wormhole.radius() * 3.2;
        double cell = extent * 2.0 / VORTEX_PIXELS;
        for (int i = 0; i < VORTEX_PIXELS; i++) {
            for (int j = 0; j < VORTEX_PIXELS; j++) {
                double x = (i + 0.5) / VORTEX_PIXELS * 2.0 - 1.0;
                double y = (j + 0.5) / VORTEX_PIXELS * 2.0 - 1.0;
                double r = Math.hypot(x, y);
                if (r > 1.0) continue;
                double px = x * extent - cell * 0.5;
                double py = y * extent - cell * 0.5;
                boolean inHorizon = r < 0.17;
                if (horizon) {
                    if (inHorizon) square(pose, buffer, center, axes[0], axes[1], px, py, cell, 0xFF000000);
                    continue;
                }
                if (inHorizon) continue;

                double theta = Math.atan2(y, x);
                float swirl = 0.5F + 0.5F * Mth.sin((float) (theta * 3.0 + Math.log(r) * 6.0 - time * 0.12));
                float fade = (float) Math.pow(1.0 - r, 0.7);
                int alpha = quantize(swirl * fade * 1.2F);
                if (alpha == 0) continue;
                int tint = swirl > 0.75F ? 0xFFFFFFFF : ARGB.opaque(wormhole.color());
                square(pose, buffer, center, axes[0], axes[1], px, py, cell, ARGB.color(alpha, tint));
            }
        }
    }

    private static void rings(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Cosmos.Celestial body, double gameTime) {
        Vec3 center = body.center().subtract(camera);
        Quaterniond orientation = body.orientation(gameTime);
        Vec3 u = rotate(orientation, new Vec3(1.0, 0.0, 0.0));
        Vec3 v = rotate(orientation, new Vec3(0.0, 0.0, 1.0));
        double inner = 1.45;
        double outer = 2.4;
        double extent = body.radius() * outer;
        double cell = extent * 2.0 / RING_PIXELS;
        for (int i = 0; i < RING_PIXELS; i++) {
            for (int j = 0; j < RING_PIXELS; j++) {
                double x = (i + 0.5) / RING_PIXELS * 2.0 - 1.0;
                double y = (j + 0.5) / RING_PIXELS * 2.0 - 1.0;
                double r = Math.max(Math.abs(x), Math.abs(y)) * outer;
                if (r < inner || r > outer) continue;
                int band = (int) ((r - inner) / (outer - inner) * 10.0);
                float density = 0.3F + 0.7F * Math.abs(Mth.sin(band * 1.7F + 0.4F));
                float grain = PlanetTextures.hash(i, j, 1, body.id().hashCode());
                int tint = band % 3 == 0 ? 0xFFEFE3C8 : band % 3 == 1 ? ARGB.opaque(body.color()) : ARGB.opaque(body.accent());
                square(pose, buffer, center, u, v, x * extent - cell * 0.5, y * extent - cell * 0.5, cell,
                        ARGB.color(quantize(density * (0.5F + 0.25F * grain)), tint));
            }
        }
    }

    private static int stride(double radius, double distance) {
        double angular = distance <= radius ? 1.0 : radius / distance;
        if (angular < 0.01) return 8;
        if (angular < 0.025) return 4;
        if (angular < 0.06) return 2;
        return 1;
    }

    private static Vec3 rotate(Quaterniond orientation, Vec3 vector) {
        Vector3d rotated = orientation.transform(new Vector3d(vector.x, vector.y, vector.z));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    private static Vec3[] facing(Vec3 center) {
        Vec3 toward = center.normalize();
        Vec3 reference = Math.abs(toward.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = toward.cross(reference).normalize();
        return new Vec3[]{u, toward.cross(u)};
    }

    private static void square(PoseStack.Pose pose, VertexConsumer buffer, Vec3 origin, Vec3 u, Vec3 v, double s, double t, double size, int color) {
        Vec3 a = origin.add(u.scale(s)).add(v.scale(t));
        Vec3 b = a.add(u.scale(size));
        Vec3 c = b.add(v.scale(size));
        Vec3 d = a.add(v.scale(size));
        buffer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color);
        buffer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color);
        buffer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(color);
        buffer.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(color);
    }

    private static int quantize(float amount) {
        float stepped = Math.round(Mth.clamp(amount, 0.0F, 1.0F) * GLOW_LEVELS) / (float) GLOW_LEVELS;
        return Math.round(stepped * 255.0F);
    }

    private SpaceRenderer() {}
}
