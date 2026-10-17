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
    private static final int PLANET_GRID = 32;
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
    private static final float STAR_SCALE = 0.35F;
    private static final float STAR_HALO = 2.5F;
    private static final float HAZE = 0.45F;
    private static final float HAZE_ALPHA = 0.8F;
    private static final float[][] MOGO_BANDS = {{1.35F, 1.62F}, {1.62F, 1.95F}, {1.95F, 2.35F}};
    private static final int[] MOGO_BAND_ALPHA = {215, 190, 150};
    private static final float[] MOGO_BAND_SHADE = {1.0F, 0.85F, 0.7F};
    private static final float MOGO_BAND_THICKNESS = 0.035F;
    private static final float RING_TILT = 25.0F;
    private static final float OWN_RING_ALPHA = 0.8F;
    private static final float DUSK_BAND = 0.15F;
    private static final float HORIZON_BAND = 0.06F;
    private static final int OVERWORLD_FOG = 0xC0D8FF;
    private static final int DUSK = 0xFF8A4C;
    private static final float SUN_CORE_WHITE = 0.7F;
    private static final int SUN_WARMTH = 0xFFFFD866;
    private static final float SUN_WARM_SHARE = 0.35F;
    private static final int GLARE_LAYERS = 10;
    private static final float GLARE_SPREAD = 0.25F;
    private static final int GLARE_PEAK = 150;
    private static final float SPECULAR_POWER = 48.0F;

    private record Body(Cosmos.Style style, long seed, int color, float yaw, float pitch, float size, float spin, float tilt, int ring, boolean glows) {}

    private record Haze(int color, float amount, float alpha) {
        static final Haze NONE = new Haze(0, 0.0F, 1.0F);
    }

    private record Frame(Quaternionf rotation, Vector3f center, float half, Vector3fc light) {}

    private static final Map<ResourceKey<Level>, List<Body>> BACKDROPS = Map.of(
            Cosmos.SECTOR_2814, List.of(
                    body(Cosmos.Style.GAS, 13L, -120, 35, 1.2F, 2.0F, 5, 0),
                    body(Cosmos.Style.RINGED_GAS, 14L, -160, 52, 0.8F, 1.8F, 22, 0xD8C8A0),
                    body(Cosmos.Style.ICE, 15L, 100, 66, 0.4F, 1.2F, 30, 0)));

    private static Vector3fc sun = direction(60, 28);
    private static int sunColor = 0xFFF2C0;
    private static RenderType solid = FlightRenderTypes.glass();

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
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double ticks = level.getGameTime() + partialTicks;
        double clock = CosmosClient.clock(partialTicks);
        float seconds = (float) ((ticks % 72000.0) / 20.0);
        PoseStack poses = context.poseStack();
        SubmitNodeCollector out = context.submitNodeCollector();
        ResourceKey<Level> mogo = CosmosClient.mogoSector();

        if (SpaceTravel.isSpace(level)) {
            ResourceKey<Level> sector = level.dimension();
            Quaternionf view = new Quaternionf();
            lightFrom(sector, view, camera, clock);
            solid = FlightRenderTypes.SKY_SOLID;
            renderStarsOf(poses, out, sector, view, camera, reach, clock, seconds, false);
            renderStars(poses, out, sector, view, blocks * STAR_FACTOR, 1.0F, seconds);
            for (Body body : BACKDROPS.getOrDefault(sector, List.of())) renderBody(poses, out, body, view, reach, seconds, 1.0F);
            for (Cosmos.Planet planet : Cosmos.planetsIn(sector, mogo)) {
                Vec3 at = PlanetBody.center(planet, clock);
                sun = sunOf(sector, at, clock);
                Vec3 offset = at.subtract(camera).scale(Cosmos.VIRTUAL_PER_BLOCK);
                renderPlanet(poses, out, planet, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z), reach, PlanetBody.orientation(planet, sector, clock), Haze.NONE, 1.0F);
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
        Quaternionf orientation = PlanetBody.orientation(home, sector, clock);
        Quaternionf view = PlanetBody.worldToSurface(orientation);
        lightFrom(sector, view, PlanetBody.center(home, clock), clock);
        Vector3fc homeSun = sun;
        solid = FlightRenderTypes.glass();
        float fade = SpaceTravel.atmosphereFade(level, camera.y);
        boolean overworld = level.dimension() == Level.OVERWORLD;
        int fog = overworld ? OVERWORLD_FOG : home.fog() != 0 ? home.fog() : 0x8090A0;
        float night = 1.0F - daylight(sun);
        float starAlpha = Math.max(fade, night * night * night);

        renderStars(poses, out, sector, view, blocks * STAR_FACTOR, starAlpha, seconds);
        for (Body body : BACKDROPS.getOrDefault(sector, List.of())) {
            renderBody(poses, out, body, view, reach, seconds, body.glows ? 1.0F : starAlpha);
        }
        Vec3 eye = eye(home, orientation, camera, clock);
        renderStarsOf(poses, out, sector, view, eye, reach, clock, seconds, true);
        float neighbourAlpha = overworld ? fade : 1.0F;
        Haze neighbourHaze = new Haze(fog, HAZE * (1.0F - fade), (HAZE_ALPHA + (1.0F - HAZE_ALPHA) * fade) * neighbourAlpha);
        for (Cosmos.Planet planet : Cosmos.planetsIn(sector, mogo)) {
            boolean own = planet == home;
            if (!own && neighbourAlpha <= 0.0F) continue;
            if (own && fade <= 0.0F && planet.ringColor() == 0) continue;
            Vec3 at = PlanetBody.center(planet, clock);
            Vec3 offset = at.subtract(eye).scale(Cosmos.VIRTUAL_PER_BLOCK);
            Vector3f relative = view.transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
            Quaternionf rotation = new Quaternionf(view).mul(PlanetBody.orientation(planet, sector, clock));
            sun = view.transform(sunOf(sector, at, clock));
            if (own) renderPlanet(poses, out, planet, relative, reach, rotation, new Haze(fog, 0.0F, fade), OWN_RING_ALPHA);
            else renderPlanet(poses, out, planet, relative, reach, rotation, neighbourHaze, neighbourHaze.alpha);
        }
        sun = homeSun;
    }

    private static Vec3 eye(Cosmos.Planet home, Quaternionf orientation, Vec3 camera, double clock) {
        double half = home.surfaceHalf();
        float u = (float) Mth.clamp((camera.x - home.regionX()) / half, -1.0, 1.0);
        float w = (float) Mth.clamp(camera.z / half, -1.0, 1.0);
        double climb = Mth.clamp((camera.y - SpaceTravel.ATMOSPHERE_START_Y) / (SpaceTravel.EXIT_ATMOSPHERE_Y - SpaceTravel.ATMOSPHERE_START_Y), 0.0, 1.0);
        float lift = (float) (1.0 + climb * PlanetBody.DEPARTURE_MARGIN / home.footprint());
        Vector3f local = PlanetBody.fromSurface(PlanetBody.SURFACE, new Vector3f(u, lift, w)).mul((float) home.footprint());
        return PlanetBody.center(home, clock).add(PlanetBody.toWorld(orientation, local));
    }

    public static float daylight(Vector3fc sunDirection) {
        float t = Mth.clamp((sunDirection.y() + DUSK_BAND) / (2.0F * DUSK_BAND), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float surfaceDaylight(ClientLevel level, Vec3 position, float partialTicks) {
        Cosmos.Planet home = Cosmos.planetAt(level.dimension(), position.x);
        ResourceKey<Level> sector = home == null ? null : Cosmos.homeSector(home, CosmosClient.mogoSector());
        if (sector == null) return 1.0F;
        double clock = CosmosClient.clock(partialTicks);
        return daylight(PlanetBody.worldToSurface(PlanetBody.orientation(home, sector, clock)).transform(sunOf(sector, PlanetBody.center(home, clock), clock)));
    }

    private static Vector3f sunOf(ResourceKey<Level> sector, Vec3 from, double clock) {
        return Cosmos.sunFrom(sector, from.x, from.y, from.z, clock);
    }

    private static void renderStarsOf(PoseStack poses, SubmitNodeCollector out, ResourceKey<Level> sector, Quaternionf view, Vec3 from, float reach, double clock, float seconds, boolean surface) {
        for (Cosmos.Star star : Cosmos.stars(sector, clock)) {
            Vec3 offset = star.center().subtract(from).scale(Cosmos.VIRTUAL_PER_BLOCK);
            Vector3f relative = view.transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
            renderSun(poses, out, relative, (float) (star.half() * Cosmos.VIRTUAL_PER_BLOCK), reach, star.color(), seconds, 1.0F, surface);
        }
    }

    private static void lightFrom(ResourceKey<Level> sector, Quaternionf view, Vec3 from, double clock) {
        sun = view.transform(sunOf(sector, from, clock));
        Cosmos.Sector info = Cosmos.sector(sector);
        sunColor = ARGB.srgbLerp(0.5F, ARGB.opaque(info == null ? 0xFFF2C0 : info.starColor()), 0xFFFFFFFF);
    }

    private static float horizon(Vector3f direction) {
        float t = Mth.clamp((direction.y / direction.length() + HORIZON_BAND) / (2.0F * HORIZON_BAND), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void renderStars(PoseStack poses, SubmitNodeCollector out, ResourceKey<Level> sector, Quaternionf view, float distance, float alpha, float seconds) {
        if (alpha <= 0.01F) return;
        boolean surface = !view.equals(new Quaternionf());
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            int index = 0;
            for (GalaxyCatalog.Star star : Cosmos.CATALOG.stars()) {
                if (star.sector() != sector) continue;
                index++;
                Vector3f dir = view.transform(direction(star.yaw(), star.pitch())).mul(distance);
                float twinkle = 0.75F + 0.25F * Mth.sin(seconds * (1.5F + (index % 7) * 0.4F) + index);
                float shown = (surface ? alpha * horizon(dir) : alpha) * twinkle;
                if (shown <= 0.01F) continue;
                float half = distance * (float) Math.tan(Math.toRadians(star.size() * STAR_SCALE));
                Vector3f facing = new Vector3f(dir).normalize();
                Vector3f right = new Vector3f(facing).cross(0.0F, 1.0F, 0.0F);
                if (right.lengthSquared() < 1.0E-4F) right.set(1.0F, 0.0F, 0.0F);
                right.normalize().mul(half);
                Vector3f up = new Vector3f(right).cross(facing).normalize().mul(half);
                point(buffer, pose, dir, right, up, ARGB.color(Math.round(255 * shown), star.color()));
                point(buffer, pose, dir, right.mul(STAR_HALO), up.mul(STAR_HALO), ARGB.color(Math.round(70 * shown), star.color()));
            }
        });
    }

    private static void point(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, Vector3f right, Vector3f up, int color) {
        buffer.addVertex(pose, center.x - right.x - up.x, center.y - right.y - up.y, center.z - right.z - up.z).setColor(color);
        buffer.addVertex(pose, center.x + right.x - up.x, center.y + right.y - up.y, center.z + right.z - up.z).setColor(color);
        buffer.addVertex(pose, center.x + right.x + up.x, center.y + right.y + up.y, center.z + right.z + up.z).setColor(color);
        buffer.addVertex(pose, center.x - right.x + up.x, center.y - right.y + up.y, center.z - right.z + up.z).setColor(color);
    }

    private static void renderBody(PoseStack poses, SubmitNodeCollector out, Body body, Quaternionf view, float distance, float seconds, float alpha) {
        Vector3f dir = view.transform(direction(body.yaw, body.pitch)).mul(distance);
        if (!view.equals(new Quaternionf())) alpha *= horizon(dir);
        if (alpha <= 0.01F) return;
        float halfSize = distance * (float) Math.tan(Math.toRadians(body.size));
        Quaternionf rotation = new Quaternionf(view).rotateZ((float) Math.toRadians(body.tilt)).rotateY((float) Math.toRadians(seconds * body.spin));
        Frame frame = new Frame(rotation, dir, halfSize, new Vector3f(sun));

        poses.pushPose();
        poses.translate(dir.x, dir.y, dir.z);
        if (body.glows) corona(out, poses, dir, halfSize, body.color, alpha, seconds);
        poses.pushPose();
        poses.rotate(rotation);
        poses.scale(halfSize, halfSize, halfSize);
        int core = body.glows ? ARGB.srgbLerp(SUN_CORE_WHITE, ARGB.srgbLerp(SUN_WARM_SHARE, ARGB.opaque(body.color), SUN_WARMTH), 0xFFFFFFFF) : body.color;
        cube(out, poses, solid, PlanetSurfaces.surface(body.style, body.seed, core, BODY_GRID), BODY_GRID, frame, body.glows, 0, 1.0F, new Haze(0, 0.0F, alpha));
        if (!body.glows) {
            int atmosphere = PlanetSurfaces.atmosphere(body.style);
            if (atmosphere != 0) shell(out, poses, BODY_GRID, frame, atmosphere, Math.round(55 * alpha), ATMOSPHERE_SCALE);
        }
        if (body.ring != 0) ring(out, poses, rotation, body.ring, alpha);
        poses.popPose();
        poses.popPose();
    }

    private static void corona(SubmitNodeCollector out, PoseStack poses, Vector3f toward, float half, int color, float alpha, float seconds) {
        int warm = ARGB.srgbLerp(SUN_WARM_SHARE, ARGB.opaque(color), SUN_WARMTH);
        float pulse = 0.94F + 0.06F * Mth.sin(seconds * 1.7F);
        for (int i = GLARE_LAYERS - 1; i >= 0; i--) {
            float t = (float) i / (GLARE_LAYERS - 1);
            float falloff = (1.0F - t) * (1.0F - t);
            int tint = ARGB.srgbLerp(t, ARGB.srgbLerp(SUN_CORE_WHITE, warm, 0xFFFFFFFF), warm);
            glare(out, poses, toward, half * (1.05F + GLARE_SPREAD * i * (1.0F + 0.15F * i)), tint, Math.round(GLARE_PEAK * falloff * alpha * pulse));
        }
    }

    private static void renderSun(PoseStack poses, SubmitNodeCollector out, Vector3f relative, float size, float reach, int color, float seconds, float alpha, boolean surface) {
        float scale = reach / (relative.length() + size * 1.75F);
        Vector3f placed = new Vector3f(relative).mul(scale);
        if (surface) alpha *= horizon(placed);
        if (alpha <= 0.01F) return;
        float half = size * scale;
        int core = ARGB.srgbLerp(SUN_CORE_WHITE, ARGB.srgbLerp(SUN_WARM_SHARE, ARGB.opaque(color), SUN_WARMTH), 0xFFFFFFFF);
        poses.pushPose();
        poses.translate(placed.x, placed.y, placed.z);
        corona(out, poses, placed, half, color, alpha, seconds);
        disc(out, poses, placed, half, ARGB.color(Math.round(255 * alpha), core));
        poses.popPose();
    }

    private static void disc(SubmitNodeCollector out, PoseStack poses, Vector3f toward, float radius, int color) {
        Vector3f facing = new Vector3f(toward).normalize();
        Vector3f right = new Vector3f(facing).cross(0.0F, 1.0F, 0.0F);
        if (right.lengthSquared() < 1.0E-4F) right.set(1.0F, 0.0F, 0.0F);
        right.normalize().mul(radius);
        Vector3f up = new Vector3f(right).cross(facing).normalize().mul(radius);
        Vector3f back = new Vector3f(facing).mul(radius * 0.05F);
        out.submitCustomGeometry(poses, solid, (pose, buffer) -> point(buffer, pose, back, right, up, color));
    }

    private static void renderPlanet(PoseStack poses, SubmitNodeCollector out, Cosmos.Planet planet, Vector3f center, float reach, Quaternionf rotation, Haze haze, float ringAlpha) {
        float size = (float) (Cosmos.PLANET_HALF_SIZE * planet.scale());
        float scale = reach / (center.length() + size * 1.75F);
        Vector3f placed = new Vector3f(center).mul(scale);
        poses.pushPose();
        poses.translate(placed.x, placed.y, placed.z);
        globe(poses, out, planet, placed, size * scale, rotation, haze, ringAlpha);
        poses.popPose();
    }

    private static void globe(PoseStack poses, SubmitNodeCollector out, Cosmos.Planet planet, Vector3f center, float half, Quaternionf rotation, Haze haze, float ringAlpha) {
        Frame frame = new Frame(rotation, center, half, new Vector3f(sun));
        int atmosphere = PlanetSurfaces.atmosphere(planet.style());
        poses.pushPose();
        poses.rotate(rotation);
        poses.scale(half, half, half);
        if (haze.alpha > 0.01F) {
            cube(out, poses, solid, PlanetSurfaces.surface(planet.style(), planet.seed(), 0, PLANET_GRID), PLANET_GRID, frame, false, atmosphere, 1.0F, haze);
            if (PlanetSurfaces.cloudy(planet.style())) {
                cube(out, poses, FlightRenderTypes.glass(), PlanetSurfaces.clouds(planet.style(), planet.seed(), PLANET_GRID), PLANET_GRID, frame, false, atmosphere, CLOUD_SCALE, haze);
            }
            if (atmosphere != 0) {
                shell(out, poses, PLANET_GRID, frame, atmosphere, Math.round(70 * haze.alpha), ATMOSPHERE_SCALE);
                shell(out, poses, PLANET_GRID, frame, atmosphere, Math.round(30 * haze.alpha), HALO_SCALE);
            }
        }
        if (planet.ringColor() != 0 && ringAlpha > 0.01F) {
            Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(RING_TILT));
            poses.pushPose();
            poses.rotate(tilt);
            float shade = 0.3F + 0.7F * Math.abs(new Quaternionf(rotation).mul(tilt).transform(new Vector3f(0, 1, 0)).dot(sun));
            layeredRing(out, poses, planet.ringColor(), shade, ringAlpha);
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
        int fill = ARGB.color(Math.min(255, alpha), color);
        out.submitCustomGeometry(poses, FlightRenderTypes.SKY_GLOW, (pose, buffer) -> {
            for (int i = 0; i < 4; i++) {
                Vector3f corner = point(right, up, Mth.HALF_PI * (i + 0.5F), radius * Mth.SQRT_OF_TWO);
                buffer.addVertex(pose, corner.x, corner.y, corner.z).setColor(fill);
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

    private static int shade(int color, Vector3f normal, Vector3f view, int atmosphere, Vector3fc toSun) {
        float facing = normal.dot(toSun);
        float diffuse = Mth.clamp((facing + 0.04F) / 1.04F, 0.0F, 1.0F);
        float light = AMBIENT + (1.0F - AMBIENT) * diffuse;
        int lit = ARGB.scaleRGB(ARGB.opaque(color), light);
        if (atmosphere != 0) {
            float dusk = Mth.clamp(1.0F - Math.abs(facing - 0.06F) / 0.16F, 0.0F, 1.0F);
            lit = ARGB.srgbLerp(0.35F * dusk, lit, ARGB.scaleRGB(DUSK, light));
        }
        if (facing > 0.0F && glossy(color)) {
            Vector3f halfway = new Vector3f(toSun).add(view).normalize();
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
                            lit = shade(color, normal, view(frame, normal, inflate), atmosphere, frame.light);
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
                        float facing = normal.dot(frame.light);
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
        squareAnnulus(out, poses, FlightRenderTypes.glass(), RING_INNER, RING_OUTER, 0.0F, ARGB.color(Math.round(200 * alpha), ARGB.scaleRGB(ARGB.opaque(ringColor), shade)));
    }

    private static void layeredRing(SubmitNodeCollector out, PoseStack poses, int ringColor, float shade, float alpha) {
        for (int i = 0; i < MOGO_BANDS.length; i++) {
            float inner = MOGO_BANDS[i][0];
            float outer = MOGO_BANDS[i][1];
            int band = ARGB.scaleRGB(ARGB.opaque(ringColor), shade * MOGO_BAND_SHADE[i]);
            int color = ARGB.color(Math.round(MOGO_BAND_ALPHA[i] * alpha), band);
            for (float y : new float[]{-MOGO_BAND_THICKNESS, 0.0F, MOGO_BAND_THICKNESS}) squareAnnulus(out, poses, FlightRenderTypes.glass(), inner, outer, y, color);
        }
        squareAnnulus(out, poses, FlightRenderTypes.SKY_GLOW, MOGO_BANDS[0][0] - 0.04F, MOGO_BANDS[MOGO_BANDS.length - 1][1] + 0.04F, 0.0F, ARGB.color(Math.round(60 * alpha), ARGB.opaque(ringColor)));
    }

    private static void squareAnnulus(SubmitNodeCollector out, PoseStack poses, RenderType type, float inner, float outer, float y, int color) {
        float[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        out.submitCustomGeometry(poses, type, (pose, buffer) -> {
            for (int i = 0; i < 4; i++) {
                float[] a = corners[i];
                float[] b = corners[(i + 1) % 4];
                buffer.addVertex(pose, a[0] * inner, y, a[1] * inner).setColor(color);
                buffer.addVertex(pose, a[0] * outer, y, a[1] * outer).setColor(color);
                buffer.addVertex(pose, b[0] * outer, y, b[1] * outer).setColor(color);
                buffer.addVertex(pose, b[0] * inner, y, b[1] * inner).setColor(color);
            }
        });
    }

    private static Vector3f direction(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new Vector3f((float) (Math.cos(pitch) * Math.sin(yaw)), (float) Math.sin(pitch), (float) (-Math.cos(pitch) * Math.cos(yaw)));
    }
}
