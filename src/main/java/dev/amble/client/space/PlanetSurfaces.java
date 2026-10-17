package dev.amble.client.space;

import dev.amble.core.space.Cosmos;
import dev.amble.core.space.PlanetBody;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.Map;

public final class PlanetSurfaces {
    private record Key(Cosmos.Style style, long seed, int color, int grid, boolean clouds) {}

    private static final Map<Key, int[][]> CACHE = new HashMap<>();

    public static int[][] surface(Cosmos.Style style, long seed, int color, int grid) {
        return CACHE.computeIfAbsent(new Key(style, seed, color, grid, false), key -> build(key, false));
    }

    public static int[][] clouds(Cosmos.Style style, long seed, int grid) {
        return CACHE.computeIfAbsent(new Key(style, seed, 0, grid, true), key -> build(key, true));
    }

    public static int atmosphere(Cosmos.Style style) {
        return switch (style) {
            case EARTH -> 0x6FA8FF;
            case MOGO -> 0x7FD8A8;
            case OA -> 0x7A8C88;
            case ODYM -> 0x8FE0D0;
            case RYUT -> 0xC06040;
            case ZAMARON -> 0xFFA0D0;
            case QWARD -> 0xA02838;
            case MARS -> 0xE09070;
            case ICE -> 0xB0E0FF;
            case DESERT -> 0xE8C890;
            case LAVA -> 0xFF6020;
            case GAS, RINGED_GAS -> 0xE8D0A0;
            default -> 0;
        };
    }

    public static boolean cloudy(Cosmos.Style style) {
        return style == Cosmos.Style.EARTH || style == Cosmos.Style.MOGO || style == Cosmos.Style.OA || style == Cosmos.Style.ODYM;
    }

    private static int[][] build(Key key, boolean clouds) {
        int[][] faces = new int[6][key.grid() * key.grid()];
        for (int f = 0; f < 6; f++) {
            Vector3fc[] face = PlanetBody.FACES[f];
            for (int j = 0; j < key.grid(); j++) {
                for (int i = 0; i < key.grid(); i++) {
                    float u = (i + 0.5F) / key.grid() * 2.0F - 1.0F;
                    float v = (j + 0.5F) / key.grid() * 2.0F - 1.0F;
                    Vector3f point = new Vector3f(face[0]).add(new Vector3f(face[1]).mul(u)).add(new Vector3f(face[2]).mul(v)).normalize();
                    faces[f][j * key.grid() + i] = clouds ? cloud(key, point) : color(key, point);
                }
            }
        }
        return faces;
    }

    private static int cloud(Key key, Vector3f p) {
        float n = fbm(p, 2.4F, key.seed() + 77L);
        float threshold = key.style() == Cosmos.Style.OA ? -0.05F : 0.18F;
        if (n < threshold) return 0;
        int base = key.style() == Cosmos.Style.OA ? 0x4A525C : 0xF4F8FF;
        float alpha = Mth.clamp((n - threshold) * 4.0F, 0.35F, 0.9F);
        return ARGB.color(Math.round(alpha * 255), base);
    }

    private static int color(Key key, Vector3f p) {
        float n = fbm(p, 2.4F, key.seed());
        float m = fbm(p, 6.0F, key.seed() + 31L);
        float lat = Math.abs(p.y());
        return 0xFF000000 | switch (key.style()) {
            case EARTH -> earth(n, m, lat, -0.02F);
            case MOGO -> lat > 0.9F ? 0xEAF4EA : n < -0.25F ? mix(0x1E5FA8, 0x2E7FD0, n + 1.0F) : mix(0x2E8E3A, 0x1F6A2A, m * 0.5F + 0.5F);
            case OA -> m > 0.42F ? 0x9AE0FF : mix(0x1C242C, n < 0.0F ? 0x22303A : 0x24302A, m * 0.5F + 0.5F);
            case ODYM -> n < -0.3F ? 0x2A7FB0 : mix(0x155A20, m > 0.0F ? 0x2FA040 : 0x1F7A2E, n * 0.5F + 0.5F);
            case RYUT -> Math.abs(m) < 0.035F ? 0xFF6A20 : mix(0x2A120C, 0x6A2A18, n * 0.5F + 0.5F);
            case ZAMARON -> m > 0.5F ? 0xFF8AE0 : mix(0x7A3A2A, 0xC86A4A, n * 0.5F + 0.5F);
            case QWARD -> mix(0x3A0608, m > 0.2F ? 0x8A1A1A : 0x5A0A10, n * 0.5F + 0.5F);
            case MARS -> lat > 0.86F ? 0xF4EEE8 : mix(0xA04020, 0xC8603A, n * 0.5F + 0.5F);
            case ROCK -> m > 0.35F ? 0x5A5A5E : mix(0x7A7A7E, 0xA8A8AC, n * 0.5F + 0.5F);
            case MOON -> Math.abs(m) < 0.05F ? 0x6E6E70 : m > 0.4F ? 0x7E7E80 : mix(0x9C9C9E, 0xC8C8CA, n * 0.5F + 0.5F);
            case MERCURY -> Math.abs(m) < 0.03F ? 0xC85A1E : mix(0x4A4440, 0x7A706A, n * 0.5F + 0.5F);
            case GAS -> band(p.y() * 9.0F + n * 1.8F, 0xD8B48A, 0xB07A50, 0xEAD8B8);
            case RINGED_GAS -> band(p.y() * 8.0F + n * 1.2F, 0xE8D8A8, 0xC8B080, 0xF4ECD0);
            case ICE -> band(p.y() * 6.0F + n * 1.5F, 0x3A6AE0, 0x5A8AF0, 0x4A7AE8);
            case DESERT -> m > 0.45F ? 0xB08A50 : mix(0xC89A5A, 0xE8C890, n * 0.5F + 0.5F);
            case LAVA -> Math.abs(m) < 0.06F ? 0xFF7A1A : mix(0x1A0A08, 0x3A1A12, n * 0.5F + 0.5F);
            case STAR -> ARGB.scaleRGB(key.color(), 0.97F + 0.03F * n) & 0xFFFFFF;
        };
    }

    private static int earth(float n, float m, float lat, float sea) {
        if (lat > 0.88F) return 0xF0F6FF;
        if (n < sea) return mix(0x1A4A9E, 0x2E6FD0, Mth.clamp(n + 0.6F, 0.0F, 1.0F));
        if (m > 0.35F) return 0xD8C080;
        return n < 0.18F ? 0x3E8E3A : n < 0.32F ? 0x6E8A3A : 0x8A7A5A;
    }

    private static int band(float t, int a, int b, int c) {
        float s = Mth.sin(t);
        return s > 0.4F ? a : s > -0.3F ? b : c;
    }

    private static int mix(int a, int b, float t) {
        return ARGB.srgbLerp(Mth.clamp(t, 0.0F, 1.0F), 0xFF000000 | a, 0xFF000000 | b) & 0xFFFFFF;
    }

    private static float fbm(Vector3f p, float frequency, long seed) {
        float total = 0.0F;
        float amplitude = 0.6F;
        float f = frequency;
        for (int octave = 0; octave < 3; octave++) {
            total += amplitude * noise(p.x() * f, p.y() * f, p.z() * f, seed + octave * 1013L);
            f *= 2.1F;
            amplitude *= 0.5F;
        }
        return total;
    }

    private static float noise(float x, float y, float z, long seed) {
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        int z0 = Mth.floor(z);
        float fx = smooth(x - x0);
        float fy = smooth(y - y0);
        float fz = smooth(z - z0);
        float c00 = Mth.lerp(fx, hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed));
        float c10 = Mth.lerp(fx, hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed));
        float c01 = Mth.lerp(fx, hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed));
        float c11 = Mth.lerp(fx, hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed));
        return Mth.lerp(fz, Mth.lerp(fy, c00, c10), Mth.lerp(fy, c01, c11));
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float hash(int x, int y, int z, long seed) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return ((h & 0xFFFFFF) / (float) 0xFFFFFF) * 2.0F - 1.0F;
    }

    private PlanetSurfaces() {}
}
