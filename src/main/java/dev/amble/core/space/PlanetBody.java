package dev.amble.core.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Random;

public final class PlanetBody {
    public static final double DAY = 24000.0;
    private static final double NOON = 6000.0;
    private static final double FIXED_MIDNIGHT = 18000.0;
    public static final double DEPARTURE_MARGIN = 6.0;
    private static final double ORBIT_PERIOD = 240000.0;
    private static final double ORBIT_REFERENCE = 2400.0 * Cosmos.SPREAD;
    private static final double MOON_PERIOD = 48000.0;

    public static final Vector3fc[][] FACES = {
            {new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0)},
            {new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0)},
            {new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1)},
            {new Vector3f(0, -1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)},
            {new Vector3f(0, 0, 1), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0)},
            {new Vector3f(0, 0, -1), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0)},
    };
    public static final int TOP = 2;
    public static final int SURFACE = 4;
    private static final Quaternionf SURFACE_FRAME = frame(SURFACE);

    public record Entry(int face, double u, double v) {}

    public static Quaternionf orientation(Cosmos.Planet planet, @Nullable ResourceKey<Level> sector, double clock) {
        Vec3 center = center(planet, clock);
        Vector3f sun = Cosmos.sunFrom(sector, center.x, center.y, center.z, clock);
        Random random = new Random(planet.seed() ^ 0x5EEDL);
        Vector3f helper = Math.abs(sun.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f first = new Vector3f(sun).cross(helper).normalize();
        Vector3f second = new Vector3f(sun).cross(first).normalize();
        float around = random.nextFloat() * (float) (Math.PI * 2.0);
        Vector3f axis = first.mul(Mth.cos(around)).add(second.mul(Mth.sin(around))).normalize();
        Quaternionf base = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
        Vector3f local = new Quaternionf(base).conjugate().transform(new Vector3f(sun));
        double noon = Math.atan2(local.x, local.z);
        double time = planet == Cosmos.OA_PLANET ? FIXED_MIDNIGHT : clock;
        double turn = ((time - NOON) % DAY) / DAY * Math.PI * 2.0;
        return base.rotateY((float) (noon + turn));
    }

    private static Quaternionf frame(int face) {
        Vector3fc[] basis = FACES[face];
        Matrix3f rows = new Matrix3f().setRow(0, new Vector3f(basis[1])).setRow(1, new Vector3f(basis[0])).setRow(2, new Vector3f(basis[2]).negate());
        return rows.getNormalizedRotation(new Quaternionf());
    }

    public static Quaternionf surfaceFrame() {
        return new Quaternionf(SURFACE_FRAME);
    }

    public static Quaternionf worldToSurface(Quaternionf orientation) {
        return new Quaternionf(SURFACE_FRAME).mul(new Quaternionf(orientation).conjugate());
    }

    public static Vec3 center(Cosmos.Planet planet, double clock) {
        if (planet == Cosmos.MOON_PLANET) {
            Vec3 earth = center(Cosmos.EARTH, clock);
            double dx = planet.x() - Cosmos.EARTH.x();
            double dz = planet.z() - Cosmos.EARTH.z();
            double angle = (clock % MOON_PERIOD) / MOON_PERIOD * Math.PI * 2.0;
            return earth.add(dx * Math.cos(angle) - dz * Math.sin(angle), 0.0, dx * Math.sin(angle) + dz * Math.cos(angle));
        }
        double radius = Math.sqrt(planet.x() * planet.x() + planet.z() * planet.z());
        if (radius < 1.0) return new Vec3(planet.x(), Cosmos.PLANET_CENTER_Y, planet.z());
        double period = ORBIT_PERIOD * Math.pow(radius / ORBIT_REFERENCE, 1.5);
        double angle = Math.atan2(planet.z(), planet.x()) + (clock % period) / period * Math.PI * 2.0;
        return new Vec3(Math.cos(angle) * radius, Cosmos.PLANET_CENTER_Y, Math.sin(angle) * radius);
    }

    public static Vector3f toLocal(Quaternionf orientation, Vec3 world) {
        return new Quaternionf(orientation).conjugate().transform(new Vector3f((float) world.x, (float) world.y, (float) world.z));
    }

    public static Vec3 toWorld(Quaternionf orientation, Vector3f local) {
        Vector3f world = orientation.transform(new Vector3f(local));
        return new Vec3(world.x, world.y, world.z);
    }

    public static @Nullable Entry entry(Cosmos.Planet planet, @Nullable ResourceKey<Level> sector, double clock, Vec3 from, Vec3 to) {
        Quaternionf orientation = orientation(planet, sector, clock);
        double half = planet.footprint();
        Vec3 center = center(planet, clock);
        Vector3f a = toLocal(orientation, from.subtract(center)).div((float) half);
        Vector3f b = toLocal(orientation, to.subtract(center)).div((float) half);
        if (inside(a)) return nearestFace(a);
        float[] start = {a.x, a.y, a.z};
        float[] delta = {b.x - a.x, b.y - a.y, b.z - a.z};
        float enter = 0.0F;
        float exit = 1.0F;
        int axis = -1;
        float sign = 0.0F;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(delta[i]) < 1.0E-7F) {
                if (Math.abs(start[i]) > 1.0F) return null;
                continue;
            }
            float t0 = (-1.0F - start[i]) / delta[i];
            float t1 = (1.0F - start[i]) / delta[i];
            float near = Math.min(t0, t1);
            float far = Math.max(t0, t1);
            if (near > enter) {
                enter = near;
                axis = i;
                sign = delta[i] > 0.0F ? -1.0F : 1.0F;
            }
            exit = Math.min(exit, far);
            if (enter > exit) return null;
        }
        if (axis < 0) return null;
        Vector3f hit = new Vector3f(a).add(new Vector3f(b).sub(a).mul(enter));
        Vector3f normal = new Vector3f(axis == 0 ? sign : 0.0F, axis == 1 ? sign : 0.0F, axis == 2 ? sign : 0.0F);
        return project(normal, hit);
    }

    private static boolean inside(Vector3f point) {
        return Math.abs(point.x) <= 1.0F && Math.abs(point.y) <= 1.0F && Math.abs(point.z) <= 1.0F;
    }

    private static Entry nearestFace(Vector3f point) {
        float ax = Math.abs(point.x);
        float ay = Math.abs(point.y);
        float az = Math.abs(point.z);
        Vector3f normal = ax >= ay && ax >= az ? new Vector3f(Math.signum(point.x), 0, 0)
                : ay >= az ? new Vector3f(0, Math.signum(point.y), 0) : new Vector3f(0, 0, Math.signum(point.z));
        return project(normal, point);
    }

    private static Entry project(Vector3f normal, Vector3f point) {
        for (int f = 0; f < FACES.length; f++) {
            if (FACES[f][0].dot(normal) > 0.5F) return new Entry(f, point.dot(FACES[f][1]), point.dot(FACES[f][2]));
        }
        return new Entry(TOP, point.x, -point.z);
    }

    public static Vector3f toSurface(int face, Vector3f local) {
        Vector3fc[] basis = FACES[face];
        return new Vector3f(local.dot(basis[1]), local.dot(basis[0]), -local.dot(basis[2]));
    }

    public static Vector3f fromSurface(int face, Vector3f surface) {
        Vector3fc[] basis = FACES[face];
        return new Vector3f(basis[1]).mul(surface.x).add(new Vector3f(basis[0]).mul(surface.y)).sub(new Vector3f(basis[2]).mul(surface.z));
    }

    private PlanetBody() {}
}
