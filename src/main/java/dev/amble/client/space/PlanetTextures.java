package dev.amble.client.space;

import dev.amble.core.space.Cosmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

final class PlanetTextures {
    static final int LARGE_PIXELS = 48;
    static final int SMALL_PIXELS = 24;
    private static final double LARGE_BODY = 500.0;
    private static final int CRATERS = 22;
    private static final float DITHER = 0.07F;

    static final Vec3[][] FACES = {
            {new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)},
            {new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)},
            {new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1)},
            {new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1)},
            {new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0)},
            {new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 1, 0)}
    };

    record Texture(int pixels, int[][] colors, boolean[][] emissive, int[][] clouds) {}

    private static final Map<String, Texture> CACHE = new HashMap<>();

    static Texture of(Cosmos.Celestial body) {
        return CACHE.computeIfAbsent(body.id(), unused -> generate(body));
    }

    static int pixels(Cosmos.Celestial body) {
        return body.radius() >= LARGE_BODY ? LARGE_PIXELS : SMALL_PIXELS;
    }

    static Vec3 direction(int face, int i, int j, int pixels) {
        double s = (i + 0.5) / pixels * 2.0 - 1.0;
        double t = (j + 0.5) / pixels * 2.0 - 1.0;
        return FACES[face][0].add(FACES[face][1].scale(s)).add(FACES[face][2].scale(t)).normalize();
    }

    private static Texture generate(Cosmos.Celestial body) {
        int pixels = pixels(body);
        int seed = body.id().hashCode();
        Vec3[] craters = craters(seed);
        int[][] colors = new int[6][pixels * pixels];
        boolean[][] emissive = new boolean[6][pixels * pixels];
        int[][] clouds = body.style() == Cosmos.Style.OCEANIC ? new int[6][pixels * pixels] : null;

        for (int face = 0; face < 6; face++) {
            for (int i = 0; i < pixels; i++) {
                for (int j = 0; j < pixels; j++) {
                    Vec3 dir = direction(face, i, j, pixels);
                    int index = i * pixels + j;
                    int color = switch (body.style()) {
                        case OCEANIC -> earth(dir, seed);
                        case ROCKY -> rocky(dir, seed, craters, body.color(), body.accent());
                        case GAS -> gas(dir, seed);
                        case STAR -> star(dir, seed);
                        case LANTERN -> oa(dir, seed, emissive[face], index);
                    };
                    if (body.style() == Cosmos.Style.STAR) emissive[face][index] = true;
                    colors[face][index] = dither(color, dir, seed);
                    if (clouds != null) clouds[face][index] = cloud(dir, seed);
                }
            }
        }
        return new Texture(pixels, colors, emissive, clouds);
    }

    private static int earth(Vec3 dir, int seed) {
        float elevation = fbm(dir.scale(2.2), seed, 5);
        float moisture = fbm(dir.scale(3.1).add(17.0, 3.0, 9.0), seed + 1, 4);
        double latitude = Math.abs(dir.y);
        if (latitude > 0.9 + (fbm(dir.scale(6.0), seed + 2, 3) - 0.5) * 0.12) {
            return moisture > 0.5 ? 0xFFEEF4FF : 0xFFD6E4F2;
        }

        float sea = 0.52F;
        if (elevation < sea) {
            float depth = (sea - elevation) / sea;
            if (depth > 0.25F) return 0xFF163676;
            if (depth > 0.12F) return 0xFF1D4A9E;
            if (depth > 0.04F) return 0xFF2A66C2;
            return 0xFF4588D6;
        }

        float height = (elevation - sea) / (1.0F - sea);
        if (height < 0.05F) return 0xFFD8C88C;
        if (height > 0.62F) return 0xFFF0F3F6;
        if (height > 0.45F) return 0xFF7D766A;
        if (moisture < 0.4F && latitude < 0.45) return moisture < 0.33F ? 0xFFD9B26A : 0xFFC4954F;
        if (moisture > 0.6F) return 0xFF2E6226;
        if (moisture > 0.5F) return 0xFF3E7D30;
        return 0xFF5DA544;
    }

    private static int cloud(Vec3 dir, int seed) {
        float cover = fbm(dir.scale(3.4).add(41.0, 7.0, 13.0), seed + 9, 5);
        float band = (float) Math.abs(Math.sin(dir.y * 6.0)) * 0.08F;
        float value = cover + band;
        if (value > 0.71F) return ARGB.color(230, 0xFFFFFF);
        if (value > 0.67F) return ARGB.color(150, 0xF2F6FF);
        if (value > 0.64F) return ARGB.color(70, 0xE6EEFF);
        return 0;
    }

    private static int rocky(Vec3 dir, int seed, Vec3[] craters, int light, int dark) {
        float base = fbm(dir.scale(4.0), seed, 5);
        float maria = fbm(dir.scale(1.6).add(5.0, 11.0, 2.0), seed + 3, 3);
        int color = ARGB.srgbLerp(Mth.clamp((base - 0.3F) * 2.0F, 0.0F, 1.0F), ARGB.opaque(dark), ARGB.opaque(light));
        if (maria < 0.4F) color = ARGB.scaleRGB(color, 0.72F);
        for (int k = 0; k < craters.length; k++) {
            double radius = 0.05 + 0.17 * hash(k, 0, 0, seed + 77);
            double angle = Math.acos(Mth.clamp(dir.dot(craters[k]), -1.0, 1.0));
            if (angle < radius * 0.72) color = ARGB.scaleRGB(color, angle < radius * 0.25 ? 0.82F : 0.62F);
            else if (angle < radius) color = ARGB.scaleRGB(color, 1.3F);
        }
        return ARGB.opaque(color);
    }

    private static int gas(Vec3 dir, int seed) {
        int[] palette = {0xFFE8C79A, 0xFFD9A066, 0xFFC07A45, 0xFFF0DDB8, 0xFFB5653A, 0xFFE2B47E};
        float turbulence = (fbm(dir.scale(4.5), seed, 4) - 0.5F) * 0.28F;
        int band = Math.floorMod((int) Math.floor((dir.y + turbulence) * 9.0), palette.length);
        double longitude = Math.atan2(dir.z, dir.x);
        double stormLat = (dir.y + 0.32) / 0.09;
        double stormLon = (longitude - 0.9) / 0.32;
        double storm = stormLat * stormLat + stormLon * stormLon;
        if (storm < 0.55) return 0xFFC4472E;
        if (storm < 1.0) return 0xFFE0704A;
        return palette[band];
    }

    private static int star(Vec3 dir, int seed) {
        float granulation = fbm(dir.scale(11.0), seed, 4);
        float spots = fbm(dir.scale(3.0).add(9.0, 9.0, 9.0), seed + 5, 3);
        if (spots > 0.72F) return 0xFF7A3A10;
        if (granulation > 0.62F) return 0xFFFFFBEA;
        if (granulation > 0.52F) return 0xFFFFE37A;
        if (granulation > 0.42F) return 0xFFFFC145;
        return 0xFFFF9A2E;
    }

    private static int oa(Vec3 dir, int seed, boolean[] emissive, int index) {
        if (dir.z > 0.0 && Math.acos(Mth.clamp(dir.z, -1.0, 1.0)) < 0.06) {
            emissive[index] = true;
            return 0xFFB8FFCC;
        }

        float crust = fbm(dir.scale(3.5), seed, 5);
        float ridge = 1.0F - Math.abs(fbm(dir.scale(7.0), seed + 4, 4) * 2.0F - 1.0F);
        double longitude = Math.atan2(dir.z, dir.x) / (Math.PI * 2.0) * 36.0;
        double latitude = Math.asin(Mth.clamp(dir.y, -1.0, 1.0)) / Math.PI * 18.0;
        boolean street = Math.abs(longitude - Math.round(longitude)) < 0.12 || Math.abs(latitude - Math.round(latitude)) < 0.12;
        float city = fbm(dir.scale(2.6).add(3.0, 1.0, 8.0), seed + 6, 4);
        if (city > 0.55F && (street || city > 0.7F && hash((int) (dir.x * 997), (int) (dir.y * 997), (int) (dir.z * 997), seed) > 0.6F)) {
            emissive[index] = true;
            return city > 0.66F ? 0xFF3CFF7A : 0xFF22B850;
        }
        if (ridge > 0.86F) return 0xFF4E4E57;
        if (crust > 0.6F) return 0xFF3A3A42;
        if (crust > 0.45F) return 0xFF2B2B31;
        return 0xFF1C1B21;
    }

    private static int dither(int color, Vec3 dir, int seed) {
        float jitter = (hash((int) Math.floor(dir.x * 4096), (int) Math.floor(dir.y * 4096), (int) Math.floor(dir.z * 4096), seed + 13) - 0.5F) * 2.0F * DITHER;
        return ARGB.opaque(ARGB.scaleRGB(color, 1.0F + jitter));
    }

    private static Vec3[] craters(int seed) {
        RandomSource random = RandomSource.create(seed);
        Vec3[] centers = new Vec3[CRATERS];
        for (int k = 0; k < CRATERS; k++) {
            centers[k] = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
        }
        return centers;
    }

    static float fbm(Vec3 point, int seed, int octaves) {
        float total = 0.0F;
        float amplitude = 0.5F;
        float weight = 0.0F;
        Vec3 p = point;
        for (int octave = 0; octave < octaves; octave++) {
            total += noise(p, seed + octave * 31) * amplitude;
            weight += amplitude;
            amplitude *= 0.5F;
            p = p.scale(2.03);
        }
        return total / weight;
    }

    static float noise(Vec3 point, int seed) {
        int x0 = Mth.floor(point.x);
        int y0 = Mth.floor(point.y);
        int z0 = Mth.floor(point.z);
        float fx = smooth((float) (point.x - x0));
        float fy = smooth((float) (point.y - y0));
        float fz = smooth((float) (point.z - z0));
        float x00 = Mth.lerp(fx, hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed));
        float x10 = Mth.lerp(fx, hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed));
        float x01 = Mth.lerp(fx, hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed));
        float x11 = Mth.lerp(fx, hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed));
        return Mth.lerp(fz, Mth.lerp(fy, x00, x10), Mth.lerp(fy, x01, x11));
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    static float hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 974634109;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    private PlanetTextures() {}
}
