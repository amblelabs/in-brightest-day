package dev.amble.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.client.flight.FlightRenderTypes;
import dev.amble.core.space.Cosmos;
import dev.amble.core.space.GalaxyCatalog;
import dev.amble.core.space.PlanetBody;
import dev.amble.core.space.SpaceTravel;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Map;

public final class SpaceSkyRenderer {
    private static final int BODY_GRID = 10;
    private static final int PLANET_GRID = 16;
    private static final float CLOUD_SCALE = 1.015F;
    private static final float ATMOSPHERE_SCALE = 1.07F;
    private static final float HALO_SCALE = 1.16F;
    private static final float RING_INNER = 1.45F;
    private static final float RING_OUTER = 2.3F;
    private static final int RING_SEGMENTS = 48;
    private static final int WORMHOLE_VOXELS = 96;
    private static final float WORMHOLE_MIN_ANGLE = 0.045F;
    private static final float WORMHOLE_PILLAR = 0.8F;
    private static final float AMBIENT = 0.045F;
    private static final float SKY_FACTOR = 2.5F;
    private static final float STAR_FACTOR = 3.2F;
    private static final float HAZE = 0.45F;
    private static final float HAZE_ALPHA = 0.8F;
    private static final float SKY_ALTITUDE = (float) SpaceTravel.EXIT_ATMOSPHERE_Y;
    private static final float NEIGHBOUR_MIN_PITCH = 10.0F;
    private static final float NEIGHBOUR_PITCH_RANGE = 28.0F;
    private static final float NEIGHBOUR_MIN_SIZE = 1.5F;
    private static final float NEIGHBOUR_MAX_SIZE = 14.0F;
    private static final float[][] MOGO_BANDS = {{1.40F, 1.58F}, {1.66F, 1.90F}, {1.98F, 2.30F}};
    private static final int[] MOGO_BAND_ALPHA = {225, 175, 125};
    private static final float[] MOGO_BAND_SHADE = {1.0F, 0.82F, 0.66F};
    private static final float MOGO_RING_TILT = 68.0F;
    private static final int OVERWORLD_FOG = 0xC0D8FF;
    private static final int DUSK = 0xFF8A4C;
    private static final float SUN_CORE_WHITE = 0.55F;
    private static final float[] GLARE = {1.25F, 2.4F, 5.5F};
    private static final int[] GLARE_ALPHA = {235, 120, 42};
    private static final float SPECULAR_POWER = 48.0F;

    private record Body(Cosmos.Style style, long seed, int color, float yaw, float pitch, float size, float spin, float tilt, int ring, boolean glows) {}

    private record Haze(int color, float amount, float alpha) {
        static final Haze NONE = new Haze(0, 0.0F, 1.0F);
    }

    private record Frame(Quaternionf rotation, Vector3f center, float half) {}

    private static final Map<ResourceKey<Level>, List<Body>> BACKDROPS = Map.of(
            Cosmos.SECTOR_2814, List.of(
                    star(0xFFF2C0, 60, 28, 3.5F),
                    body(Cosmos.Style.ROCK, 11L, -35, 12, 2.4F, 0.3F, 0, 0),
                    body(Cosmos.Style.MARS, 12L, 150, 20, 0.6F, 1.5F, 25, 0),
                    body(Cosmos.Style.GAS, 13L, -120, 35, 1.2F, 2.0F, 5, 0),
                    body(Cosmos.Style.RINGED_GAS, 14L, -160, 52, 0.8F, 1.8F, 22, 0xD8C8A0),
                    body(Cosmos.Style.ICE, 15L, 100, 66, 0.4F, 1.2F, 30, 0)),
            Cosmos.SECTOR_0, List.of(star(0xD8F4FF, 40, 30, 4.5F)),
            Cosmos.SECTOR_666, List.of(star(0xFF3A1E, 80, 22, 6.0F)),
            Cosmos.SECTOR_1416, List.of(star(0xFF8AE0, -60, 35, 4.0F)),
            Cosmos.ODYM_GALAXY, List.of(star(0x7AB8FF, 120, 40, 4.0F)),
            Cosmos.ANTIMATTER, List.of(star(0x6A0010, 30, 20, 7.0F)));

    private static Vector3fc sun = direction(60, 28);
    private static int sunColor = 0xFFF2C0;
    private static RenderType solid = FlightRenderTypes.glass();

    private static Body star(int color, float yaw, float pitch, float size) {
        return new Body(Cosmos.Style.STAR, color, color, yaw, pitch, size, 1.0F, 0, 0, true);
    }

    private static Body body(Cosmos.Style style, long seed, float yaw, float pitch, float size, float spin, float tilt, int ring) {
        return new Body(style, seed, 0, yaw, pitch, size, spin, tilt, ring, false);
    }

    private SpaceSkyRenderer() {}

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        float blocks = Math.max(48, mc.options.getEffectiveRenderDistance() * 16);
        float reach = blocks * SKY_FACTOR;
        double ticks = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = (float) ((ticks % 72000.0) / 20.0);
        PoseStack poses = context.poseStack();
        SubmitNodeCollector out = context.submitNodeCollector();
        ResourceKey<Level> mogo = CosmosClient.mogoSector();

        if (SpaceTravel.isSpace(level)) {
            ResourceKey<Level> sector = level.dimension();
            lightFrom(sector);
            solid = FlightRenderTypes.SKY_SOLID;
            renderStars(poses, out, sector, blocks * STAR_FACTOR, 1.0F);
            for (Body body : BACKDROPS.getOrDefault(sector, List.of())) renderBody(poses, out, body, reach, seconds, 1.0F);
            for (Cosmos.Planet planet : Cosmos.planetsIn(sector, mogo)) {
                Vec3 offset = PlanetBody.center(planet).subtract(camera).scale(Cosmos.VIRTUAL_PER_BLOCK);
                renderPlanet(poses, out, planet, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z), reach, ticks, Haze.NONE);
            }
            for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) {
                if (wormhole.sector() != sector || wormhole.antimatter() && !CosmosClient.antimatterOpen()) continue;
                renderWormhole(poses, out, wormhole, camera, reach, seconds);
            }
            return;
        }

        Cosmos.Planet home = Cosmos.planetAt(level.dimension(), camera.x);
        if (home == null) return;
        ResourceKey<Level> sector = Cosmos.homeSector(home, mogo);
        if (sector == null) return;
        lightFrom(sector);
        solid = FlightRenderTypes.glass();
        float fade = SpaceTravel.atmosphereFade(level, camera.y);
        boolean overworld = level.dimension() == Level.OVERWORLD;
        int fog = overworld ? OVERWORLD_FOG : home.fog() != 0 ? home.fog() : 0x8090A0;

        if (fade > 0.0F) {
            renderStars(poses, out, sector, blocks * STAR_FACTOR, fade);
            for (Body body : BACKDROPS.getOrDefault(sector, List.of())) {
                if (!overworld && !body.glows) continue;
                renderBody(poses, out, body, reach, seconds, fade);
            }
            float size = (float) (Cosmos.PLANET_HALF_SIZE * home.scale());
            renderPlanet(poses, out, home, new Vector3f(0.0F, -(SKY_ALTITUDE * fade + 64.0F + size), 0.0F), reach, ticks, new Haze(fog, 0.0F, fade));
        }
        float neighbourAlpha = overworld ? fade : 1.0F;
        if (neighbourAlpha > 0.0F) {
            renderNeighbours(poses, out, home, sector, mogo, reach, ticks,
                    new Haze(fog, HAZE * (1.0F - fade), (HAZE_ALPHA + (1.0F - HAZE_ALPHA) * fade) * neighbourAlpha));
        }
        if (home == Cosmos.MOGO_PLANET && home.ringColor() != 0) renderSkyRing(poses, out, home.ringColor(), reach * 0.9F);
    }

    private static void lightFrom(ResourceKey<Level> sector) {
        sun = direction(60, 28);
        sunColor = 0xFFF2C0;
        for (Body body : BACKDROPS.getOrDefault(sector, List.of())) {
            if (!body.glows) continue;
            sun = direction(body.yaw, body.pitch);
            sunColor = ARGB.srgbLerp(0.5F, ARGB.opaque(body.color), 0xFFFFFFFF);
            return;
        }
    }

    private static void renderStars(PoseStack poses, SubmitNodeCollector out, ResourceKey<Level> sector, float distance, float alpha) {
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            for (GalaxyCatalog.Star star : Cosmos.CATALOG.stars()) {
                if (star.sector() != sector) continue;
                Vector3f dir = direction(star.yaw(), star.pitch()).mul(distance);
                float half = distance * (float) Math.tan(Math.toRadians(star.size()));
                VoxelRenderer.cube(pose, buffer, new Vec3(dir.x, dir.y, dir.z), half, ARGB.color(Math.round(230 * alpha), star.color()), false);
            }
        });
    }

    private static void renderNeighbours(PoseStack poses, SubmitNodeCollector out, Cosmos.Planet home, ResourceKey<Level> sector, ResourceKey<Level> mogo, float distance, double ticks, Haze haze) {
        for (Cosmos.Planet other : Cosmos.planetsIn(sector, mogo)) {
            if (other == home) continue;
            double dx = other.x() - home.x();
            double dz = other.z() - home.z();
            double gap = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            float pitch = NEIGHBOUR_MIN_PITCH + Math.floorMod((other.id() + home.id()).hashCode(), 1000) / 1000.0F * NEIGHBOUR_PITCH_RANGE;
            float size = Mth.clamp((float) Math.toDegrees(Math.atan(other.footprint() / gap)), NEIGHBOUR_MIN_SIZE, NEIGHBOUR_MAX_SIZE);
            Vector3f dir = direction(yaw, pitch).mul(distance);
            float half = distance * (float) Math.tan(Math.toRadians(size));
            poses.pushPose();
            poses.translate(dir.x, dir.y, dir.z);
            globe(poses, out, other, dir, half, ticks, haze);
            poses.popPose();
        }
    }

    private static void renderSkyRing(PoseStack poses, SubmitNodeCollector out, int ringColor, float radius) {
        poses.pushPose();
        poses.rotate(new Quaternionf().rotateY((float) Math.toRadians(30.0)).rotateX((float) Math.toRadians(MOGO_RING_TILT)));
        float unit = radius / MOGO_BANDS[MOGO_BANDS.length - 1][1];
        poses.scale(unit, unit, unit);
        layeredRing(out, poses, ringColor, 1.0F, 0.75F);
        poses.popPose();
    }

    private static void renderBody(PoseStack poses, SubmitNodeCollector out, Body body, float distance, float seconds, float alpha) {
        Vector3f dir = direction(body.yaw, body.pitch).mul(distance, new Vector3f());
        float halfSize = distance * (float) Math.tan(Math.toRadians(body.size));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.toRadians(body.tilt)).rotateY((float) Math.toRadians(seconds * body.spin));
        Frame frame = new Frame(rotation, dir, halfSize);

        poses.pushPose();
        poses.translate(dir.x, dir.y, dir.z);
        if (body.glows) {
            for (int i = GLARE.length - 1; i >= 0; i--) {
                float pulse = 0.92F + 0.08F * Mth.sin(seconds * (1.3F + i) + i);
                int tint = i == 0 ? ARGB.srgbLerp(SUN_CORE_WHITE, ARGB.opaque(body.color), 0xFFFFFFFF) : ARGB.opaque(body.color);
                glare(out, poses, dir, halfSize * GLARE[i], tint, Math.round(GLARE_ALPHA[i] * alpha * pulse));
            }
        }
        poses.pushPose();
        poses.rotate(rotation);
        poses.scale(halfSize, halfSize, halfSize);
        int core = body.glows ? ARGB.srgbLerp(SUN_CORE_WHITE, ARGB.opaque(body.color), 0xFFFFFFFF) : body.color;
        cube(out, poses, solid, PlanetSurfaces.surface(body.style, body.seed, core, BODY_GRID), BODY_GRID, frame, body.glows, 0, 1.0F, new Haze(0, 0.0F, alpha));
        if (!body.glows) {
            int atmosphere = PlanetSurfaces.atmosphere(body.style);
            if (atmosphere != 0) shell(out, poses, BODY_GRID, frame, atmosphere, Math.round(55 * alpha), ATMOSPHERE_SCALE);
        }
        if (body.ring != 0) ring(out, poses, rotation, body.ring, alpha);
        poses.popPose();
        poses.popPose();
    }

    private static void renderPlanet(PoseStack poses, SubmitNodeCollector out, Cosmos.Planet planet, Vector3f center, float reach, double ticks, Haze haze) {
        float size = (float) (Cosmos.PLANET_HALF_SIZE * planet.scale());
        float scale = reach / (center.length() + size * 1.75F);
        Vector3f placed = new Vector3f(center).mul(scale);
        poses.pushPose();
        poses.translate(placed.x, placed.y, placed.z);
        globe(poses, out, planet, placed, size * scale, ticks, haze);
        poses.popPose();
    }

    private static void globe(PoseStack poses, SubmitNodeCollector out, Cosmos.Planet planet, Vector3f center, float half, double ticks, Haze haze) {
        Quaternionf rotation = PlanetBody.orientation(planet, ticks);
        Frame frame = new Frame(rotation, center, half);
        int atmosphere = PlanetSurfaces.atmosphere(planet.style());
        poses.pushPose();
        poses.rotate(rotation);
        poses.scale(half, half, half);
        cube(out, poses, solid, PlanetSurfaces.surface(planet.style(), planet.seed(), 0, PLANET_GRID), PLANET_GRID, frame, false, atmosphere, 1.0F, haze);
        if (PlanetSurfaces.cloudy(planet.style())) {
            cube(out, poses, FlightRenderTypes.glass(), PlanetSurfaces.clouds(planet.style(), planet.seed(), PLANET_GRID), PLANET_GRID, frame, false, atmosphere, CLOUD_SCALE, haze);
        }
        if (atmosphere != 0) {
            shell(out, poses, PLANET_GRID, frame, atmosphere, Math.round(70 * haze.alpha), ATMOSPHERE_SCALE);
            shell(out, poses, PLANET_GRID, frame, atmosphere, Math.round(30 * haze.alpha), HALO_SCALE);
        }
        if (planet.ringColor() != 0) {
            Quaternionf tilt = new Quaternionf().rotateZ((float) Math.toRadians(12.0));
            poses.pushPose();
            poses.rotate(tilt);
            float shade = 0.3F + 0.7F * Math.abs(new Quaternionf(rotation).mul(tilt).transform(new Vector3f(0, 1, 0)).dot(sun));
            layeredRing(out, poses, planet.ringColor(), shade, haze.alpha);
            poses.popPose();
        }
        poses.popPose();
    }

    private static void glare(SubmitNodeCollector out, PoseStack poses, Vector3f toward, float radius, int color, int alpha) {
        if (alpha <= 0) return;
        Vector3f facing = new Vector3f(toward).normalize();
        Vector3f right = new Vector3f(facing).cross(0.0F, 1.0F, 0.0F);
        if (right.lengthSquared() < 1.0E-4F) right.set(1.0F, 0.0F, 0.0F);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(facing).normalize();
        int middle = ARGB.color(Math.min(255, alpha), color);
        int edge = ARGB.color(0, color);
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            for (int i = 0; i < 4; i++) {
                Vector3f p0 = point(right, up, Mth.HALF_PI * (i + 0.5F), radius * Mth.SQRT_OF_TWO);
                Vector3f p1 = point(right, up, Mth.HALF_PI * (i + 1.5F), radius * Mth.SQRT_OF_TWO);
                buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(middle);
                buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(middle);
                buffer.addVertex(pose, p1.x, p1.y, p1.z).setColor(edge);
                buffer.addVertex(pose, p0.x, p0.y, p0.z).setColor(edge);
            }
        });
    }

    private static void renderWormhole(PoseStack poses, SubmitNodeCollector out, Cosmos.Wormhole wormhole, Vec3 camera, float reach, float seconds) {
        Vector3f offset = new Vector3f((float) (wormhole.x() - camera.x), (float) (wormhole.y() - camera.y), (float) (wormhole.z() - camera.z));
        float distance = offset.length();
        if (distance < 1.0E-3F) return;
        float shrink = distance > reach ? reach / distance : 1.0F;
        offset.mul(shrink);
        float radius = Math.max((float) Cosmos.WORMHOLE_SIZE * shrink, reach * WORMHOLE_MIN_ANGLE);

        Vector3f facing = new Vector3f(offset).normalize();
        Vector3f right = new Vector3f(facing).cross(0.0F, 1.0F, 0.0F);
        if (right.lengthSquared() < 1.0E-4F) right.set(1.0F, 0.0F, 0.0F);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(facing).normalize();

        int color = ARGB.opaque(wormhole.color());
        int core = wormhole.antimatter() ? 0xF0100004 : 0xF0050010;
        float spin = seconds * (wormhole.antimatter() ? -1.6F : 1.2F);

        float pulse = 0.75F + 0.25F * Mth.sin(seconds * 2.2F);
        float pillar = reach * WORMHOLE_PILLAR;
        poses.pushPose();
        poses.translate(offset.x, offset.y, offset.z);
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            int halo = ARGB.color(Math.round(70 * pulse), color);
            int fringe = ARGB.color(0, color);
            for (int i = 0; i < RING_SEGMENTS; i++) {
                Vector3f p0 = point(right, up, i * Mth.TWO_PI / RING_SEGMENTS, radius * 0.9F);
                Vector3f p1 = point(right, up, (i + 1) * Mth.TWO_PI / RING_SEGMENTS, radius * 0.9F);
                Vector3f q0 = point(right, up, i * Mth.TWO_PI / RING_SEGMENTS, radius * 2.6F);
                Vector3f q1 = point(right, up, (i + 1) * Mth.TWO_PI / RING_SEGMENTS, radius * 2.6F);
                buffer.addVertex(pose, p0.x, p0.y, p0.z).setColor(halo);
                buffer.addVertex(pose, p1.x, p1.y, p1.z).setColor(halo);
                buffer.addVertex(pose, q1.x, q1.y, q1.z).setColor(fringe);
                buffer.addVertex(pose, q0.x, q0.y, q0.z).setColor(fringe);
            }
            float width = radius * 0.12F;
            int beam = ARGB.color(Math.round(110 * pulse), color);
            int tip = ARGB.color(0, color);
            for (int side = -1; side <= 1; side += 2) {
                float end = side * pillar;
                buffer.addVertex(pose, -right.x * width, 0.0F, -right.z * width).setColor(beam);
                buffer.addVertex(pose, right.x * width, 0.0F, right.z * width).setColor(beam);
                buffer.addVertex(pose, right.x * width, end, right.z * width).setColor(tip);
                buffer.addVertex(pose, -right.x * width, end, -right.z * width).setColor(tip);
            }
        });
        out.submitCustomGeometry(poses, FlightRenderTypes.glass(), (pose, buffer) -> {
            for (int i = 0; i < RING_SEGMENTS; i++) {
                Vector3f p0 = point(right, up, i * Mth.TWO_PI / RING_SEGMENTS, radius * 0.85F);
                Vector3f p1 = point(right, up, (i + 1) * Mth.TWO_PI / RING_SEGMENTS, radius * 0.85F);
                buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(core);
                buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(core);
                buffer.addVertex(pose, p1.x, p1.y, p1.z).setColor(core);
                buffer.addVertex(pose, p0.x, p0.y, p0.z).setColor(core);
            }
        });
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            float cube = radius * 0.05F;
            for (int i = 0; i < WORMHOLE_VOXELS; i++) {
                float angle = i * Mth.TWO_PI / WORMHOLE_VOXELS + spin;
                float wobble = 1.0F + 0.05F * Mth.sin(seconds * 3.0F + i);
                VoxelRenderer.cube(pose, buffer, vec(point(right, up, angle, radius * wobble)), cube, ARGB.color(230, color), false);
                VoxelRenderer.cube(pose, buffer, vec(point(right, up, -angle * 0.7F, radius * 1.18F * wobble)), cube * 1.6F, ARGB.color(60, color), false);
                float arm = (i % 24) / 24.0F;
                float swirl = angle * 2.0F + arm * Mth.TWO_PI * 0.5F - spin * 1.5F;
                VoxelRenderer.cube(pose, buffer, vec(point(right, up, swirl, radius * (0.15F + 0.65F * arm))), cube * 0.8F, ARGB.color(150, color), false);
            }
        });
        poses.popPose();
    }

    private static Vector3f point(Vector3f right, Vector3f up, float angle, float radius) {
        return new Vector3f(right).mul(Mth.cos(angle) * radius).add(new Vector3f(up).mul(Mth.sin(angle) * radius));
    }

    private static Vec3 vec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vector3f normal(Frame frame, Vector3fc[] face, float u, float v, float inflate) {
        Vector3f local = new Vector3f(face[0]).mul(inflate).add(new Vector3f(face[1]).mul(u)).add(new Vector3f(face[2]).mul(v));
        return frame.rotation.transform(local.normalize());
    }

    private static Vector3f view(Frame frame, Vector3f normal, float inflate) {
        return new Vector3f(normal).mul(frame.half * inflate).add(frame.center).negate().normalize();
    }

    private static int shade(int color, Vector3f normal, Vector3f view, int atmosphere) {
        float facing = normal.dot(sun);
        float diffuse = Mth.clamp((facing + 0.04F) / 1.04F, 0.0F, 1.0F);
        float light = AMBIENT + (1.0F - AMBIENT) * diffuse;
        int lit = ARGB.scaleRGB(ARGB.opaque(color), light);
        if (atmosphere != 0) {
            float dusk = Mth.clamp(1.0F - Math.abs(facing - 0.06F) / 0.16F, 0.0F, 1.0F);
            lit = ARGB.srgbLerp(0.35F * dusk, lit, ARGB.scaleRGB(DUSK, light));
        }
        if (facing > 0.0F && glossy(color)) {
            Vector3f halfway = new Vector3f(sun).add(view).normalize();
            float glint = (float) Math.pow(Math.max(0.0F, normal.dot(halfway)), SPECULAR_POWER) * 0.85F;
            lit = ARGB.srgbLerp(Math.min(1.0F, glint), lit, ARGB.opaque(sunColor));
        }
        return lit;
    }

    private static boolean glossy(int color) {
        int r = ARGB.red(color);
        int g = ARGB.green(color);
        int b = ARGB.blue(color);
        return b > r + 20 && b >= g;
    }

    private static void cube(SubmitNodeCollector out, PoseStack poses, RenderType type, int[][] faces, int grid, Frame frame, boolean glows, int atmosphere, float inflate, Haze haze) {
        out.submitCustomGeometry(poses, type, (pose, buffer) -> {
            for (int f = 0; f < 6; f++) {
                Vector3fc[] face = PlanetBody.FACES[f];
                for (int j = 0; j < grid; j++) {
                    for (int i = 0; i < grid; i++) {
                        int color = faces[f][j * grid + i];
                        int cellAlpha = ARGB.alpha(color);
                        if (cellAlpha == 0) continue;
                        float u0 = i * 2.0F / grid - 1.0F;
                        float u1 = (i + 1) * 2.0F / grid - 1.0F;
                        float v0 = j * 2.0F / grid - 1.0F;
                        float v1 = (j + 1) * 2.0F / grid - 1.0F;
                        int lit = ARGB.opaque(color);
                        if (!glows) {
                            Vector3f normal = normal(frame, face, (u0 + u1) * 0.5F, (v0 + v1) * 0.5F, inflate);
                            lit = shade(color, normal, view(frame, normal, inflate), atmosphere);
                        }
                        if (haze.amount > 0.0F) lit = ARGB.srgbLerp(haze.amount, lit, ARGB.opaque(haze.color));
                        cell(buffer, pose, face, inflate, u0 * inflate, u1 * inflate, v0 * inflate, v1 * inflate, ARGB.color(Math.round(cellAlpha * haze.alpha), lit));
                    }
                }
            }
        });
    }

    private static void shell(SubmitNodeCollector out, PoseStack poses, int grid, Frame frame, int color, int alpha, float inflate) {
        if (alpha <= 0) return;
        int cells = Math.max(4, grid / 2);
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            for (int f = 0; f < 6; f++) {
                Vector3fc[] face = PlanetBody.FACES[f];
                for (int j = 0; j < cells; j++) {
                    for (int i = 0; i < cells; i++) {
                        float u0 = i * 2.0F / cells - 1.0F;
                        float u1 = (i + 1) * 2.0F / cells - 1.0F;
                        float v0 = j * 2.0F / cells - 1.0F;
                        float v1 = (j + 1) * 2.0F / cells - 1.0F;
                        Vector3f normal = normal(frame, face, (u0 + u1) * 0.5F, (v0 + v1) * 0.5F, inflate);
                        float facing = normal.dot(sun);
                        float day = Mth.clamp((facing + 0.3F) / 1.3F, 0.0F, 1.0F);
                        float rim = 1.0F - Math.abs(normal.dot(view(frame, normal, inflate)));
                        float strength = day * day * (0.15F + 1.85F * rim * rim * rim);
                        float dusk = Mth.clamp(1.0F - Math.abs(facing - 0.05F) / 0.25F, 0.0F, 1.0F);
                        int tint = ARGB.srgbLerp(0.6F * dusk, ARGB.opaque(color), DUSK);
                        int a = Math.min(255, Math.round(alpha * strength));
                        if (a <= 0) continue;
                        cell(buffer, pose, face, inflate, u0 * inflate, u1 * inflate, v0 * inflate, v1 * inflate, ARGB.color(a, tint));
                    }
                }
            }
        });
    }

    private static void cell(VertexConsumer buffer, PoseStack.Pose pose, Vector3fc[] face, float depth, float u0, float u1, float v0, float v1, int color) {
        corner(buffer, pose, face, depth, u0, v0, color);
        corner(buffer, pose, face, depth, u1, v0, color);
        corner(buffer, pose, face, depth, u1, v1, color);
        corner(buffer, pose, face, depth, u0, v1, color);
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, Vector3fc[] face, float depth, float u, float v, int color) {
        Vector3fc n = face[0];
        Vector3fc r = face[1];
        Vector3fc w = face[2];
        buffer.addVertex(pose, n.x() * depth + r.x() * u + w.x() * v, n.y() * depth + r.y() * u + w.y() * v, n.z() * depth + r.z() * u + w.z() * v).setColor(color);
    }

    private static void ring(SubmitNodeCollector out, PoseStack poses, Quaternionf rotation, int ringColor, float alpha) {
        float shade = 0.3F + 0.7F * Math.max(0, Math.abs(rotation.transform(new Vector3f(0, 1, 0)).dot(sun)));
        annulus(out, poses, FlightRenderTypes.glass(), RING_INNER, RING_OUTER, ARGB.color(Math.round(200 * alpha), ARGB.scaleRGB(ARGB.opaque(ringColor), shade)), RING_SEGMENTS);
    }

    private static void layeredRing(SubmitNodeCollector out, PoseStack poses, int ringColor, float shade, float alpha) {
        for (int i = 0; i < MOGO_BANDS.length; i++) {
            float inner = MOGO_BANDS[i][0];
            float outer = MOGO_BANDS[i][1];
            int band = ARGB.scaleRGB(ARGB.opaque(ringColor), shade * MOGO_BAND_SHADE[i]);
            squareAnnulus(out, poses, FlightRenderTypes.glass(), inner, outer, ARGB.color(Math.round(MOGO_BAND_ALPHA[i] * alpha), band));
            squareAnnulus(out, poses, FlightRenderTypes.SKY_GLOW, inner - 0.03F, outer + 0.03F, ARGB.color(Math.round(55 * alpha), ARGB.opaque(ringColor)));
        }
    }

    private static void squareAnnulus(SubmitNodeCollector out, PoseStack poses, RenderType type, float inner, float outer, int color) {
        float[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        out.submitCustomGeometry(poses, type, (pose, buffer) -> {
            for (int i = 0; i < 4; i++) {
                float[] a = corners[i];
                float[] b = corners[(i + 1) % 4];
                buffer.addVertex(pose, a[0] * inner, 0.0F, a[1] * inner).setColor(color);
                buffer.addVertex(pose, a[0] * outer, 0.0F, a[1] * outer).setColor(color);
                buffer.addVertex(pose, b[0] * outer, 0.0F, b[1] * outer).setColor(color);
                buffer.addVertex(pose, b[0] * inner, 0.0F, b[1] * inner).setColor(color);
            }
        });
    }

    private static void annulus(SubmitNodeCollector out, PoseStack poses, RenderType type, float inner, float outer, int color, int segments) {
        out.submitCustomGeometry(poses, type, (pose, buffer) -> {
            for (int i = 0; i < segments; i++) {
                float a0 = i * Mth.TWO_PI / segments;
                float a1 = (i + 1) * Mth.TWO_PI / segments;
                buffer.addVertex(pose, Mth.cos(a0) * inner, 0.0F, Mth.sin(a0) * inner).setColor(color);
                buffer.addVertex(pose, Mth.cos(a0) * outer, 0.0F, Mth.sin(a0) * outer).setColor(color);
                buffer.addVertex(pose, Mth.cos(a1) * outer, 0.0F, Mth.sin(a1) * outer).setColor(color);
                buffer.addVertex(pose, Mth.cos(a1) * inner, 0.0F, Mth.sin(a1) * inner).setColor(color);
            }
        });
    }

    private static Vector3f direction(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new Vector3f((float) (Math.cos(pitch) * Math.sin(yaw)), (float) Math.sin(pitch), (float) (-Math.cos(pitch) * Math.cos(yaw)));
    }
}
