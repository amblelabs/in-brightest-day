package dev.amble.core.sculpt;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SculptGeometry {
    public static final double RANGE = 48.0;
    public static final double STEP = 0.5;
    private static final double DEFAULT_DEPTH = 6.0;
    private static final double MIN_DEPTH = 2.0;
    private static final int MIN_CAGE_POINTS = 6;
    private static final double MIN_CAGE_RADIUS = 2.0;
    private static final double MAX_CAGE_RADIUS = 12.0;
    private static final double TUBE_WALL = 1.4;

    public record Cage(Vec3 center, double radius) {}

    public static @Nullable Vec3 trace(Level level, Entity viewer, Vec3 eye, Vec3 look, double depth) {
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().add(hit.getDirection().getUnitVec3().scale(0.5));
        }
        return eye.add(look.scale(Double.isNaN(depth) ? DEFAULT_DEPTH : depth));
    }

    public static double lockDepth(Vec3 eye, Vec3 point) {
        return Math.max(eye.distanceTo(point), MIN_DEPTH);
    }

    public static Vec3 flatDirection(float yaw) {
        return Vec3.directionFromRotation(0.0F, yaw);
    }

    public static void ribbon(Vec3 point, int y, Vec3 direction, int width, Set<BlockPos> out) {
        Vec3 right = new Vec3(-direction.z, 0.0, direction.x);
        double half = width / 2.0;
        for (double u = -half + STEP * 0.5; u < half; u += STEP) {
            out.add(BlockPos.containing(point.x + right.x * u, y + 0.5, point.z + right.z * u));
        }
    }

    public static void patch(Vec3 point, int y, int width, Set<BlockPos> out) {
        int low = -(width - 1) / 2;
        for (int dx = low; dx < low + width; dx++) {
            for (int dz = low; dz < low + width; dz++) {
                out.add(BlockPos.containing(point.x + dx, y + 0.5, point.z + dz));
            }
        }
    }

    public static @Nullable Vec3 onPlane(Vec3 eye, Vec3 look, int y) {
        double plane = y + 0.5;
        if (Math.abs(look.y) < 1.0E-4) return null;
        double t = (plane - eye.y) / look.y;
        if (t <= 0.0 || t > RANGE) return null;
        return eye.add(look.scale(t));
    }

    public static int tubeHeight(int width) {
        return Math.max(width, 2);
    }

    public static void tube(Vec3 from, Vec3 to, int width, Set<BlockPos> shell, Set<BlockPos> interior) {
        Vec3 a = new Vec3(Mth.floor(from.x) + 0.5, from.y, Mth.floor(from.z) + 0.5);
        Vec3 b = new Vec3(Mth.floor(to.x) + 0.5, to.y, Mth.floor(to.z) + 0.5);
        int height = tubeHeight(width);
        double half = width / 2.0;
        double outer = half + TUBE_WALL;
        int reach = Mth.ceil(outer) + 1;
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.sqrt(dx * dx + dz * dz);

        int minX = Mth.floor(Math.min(a.x, b.x)) - reach, maxX = Mth.floor(Math.max(a.x, b.x)) + reach;
        int minZ = Mth.floor(Math.min(a.z, b.z)) - reach, maxZ = Mth.floor(Math.max(a.z, b.z)) + reach;
        int minY = Mth.floor(Math.min(a.y, b.y)) - 1, maxY = Mth.floor(Math.max(a.y, b.y)) + height;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x + 0.5 - a.x;
                double cz = z + 0.5 - a.z;
                double side;
                double across;
                int base;
                int top;
                if (length < 1.0) {
                    side = Math.abs(cx);
                    across = Math.abs(cz);
                    base = Mth.floor(Math.min(a.y, b.y));
                    top = Mth.floor(Math.max(a.y, b.y)) + height - 1;
                } else {
                    double along = (cx * dx + cz * dz) / length;
                    if (along < -0.5 || along > length + 0.5) continue;
                    side = Math.abs((cx * -dz + cz * dx) / length);
                    across = 0.0;
                    base = Mth.floor(Mth.lerp(Mth.clamp(along / length, 0.0, 1.0), a.y, b.y));
                    top = base + height - 1;
                }
                if (side >= outer || across >= outer) continue;
                boolean inside = side < half && across < half;
                for (int y = Math.max(minY, base - 1); y <= Math.min(maxY + height, top + 1); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (inside && y >= base && y <= top) {
                        interior.add(pos);
                    } else {
                        shell.add(pos);
                    }
                }
            }
        }
        shell.removeAll(interior);
    }

    public static @Nullable Cage cage(List<Vec3> ring) {
        if (ring.size() < MIN_CAGE_POINTS) return null;

        double x = 0.0;
        double z = 0.0;
        double y = Double.MAX_VALUE;
        for (Vec3 point : ring) {
            x += point.x;
            z += point.z;
            y = Math.min(y, point.y);
        }
        x /= ring.size();
        z /= ring.size();

        double spread = 0.0;
        for (Vec3 point : ring) spread += Math.sqrt((point.x - x) * (point.x - x) + (point.z - z) * (point.z - z));
        double radius = Mth.clamp(spread / ring.size(), MIN_CAGE_RADIUS, MAX_CAGE_RADIUS);
        return new Cage(new Vec3(Mth.floor(x) + 0.5, Mth.floor(y), Mth.floor(z) + 0.5), radius);
    }

    public static List<BlockPos> dome(Cage cage) {
        Vec3 center = cage.center();
        double radius = cage.radius();
        int reach = Mth.ceil(radius) + 1;
        BlockPos origin = BlockPos.containing(center);

        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = 0; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance >= radius - 0.5 && distance < radius + 0.5) cells.add(origin.offset(dx, dy, dz));
                }
            }
        }
        cells.sort(Comparator.comparingInt(BlockPos::getY));
        return cells;
    }

    private SculptGeometry() {}
}
