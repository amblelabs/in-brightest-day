package dev.amble.core.sculpt;

import dev.amble.core.blocks.HardLightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final double MAX_CAGE_HEIGHT = 24.0;
    private static final double CAGE_MARGIN = 1.0;
    private static final double CAGE_HEADROOM = 2.0;
    private static final double GROUND_SEARCH = 32.0;
    private static final int CAGE_SKIRT = 8;
    private static final double TUBE_WALL = 1.4;

    public record Cage(Vec3 center, double radiusX, double radiusZ, double height) {}

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

    public static @Nullable Cage cage(Level level, List<Vec3> ring) {
        if (ring.size() < MIN_CAGE_POINTS) return null;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int[] grounds = new int[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            Vec3 point = ring.get(i);
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
            grounds[i] = ground(level, point);
        }
        Arrays.sort(grounds);
        int base = grounds[grounds.length / 2];

        double radiusX = Mth.clamp((maxX - minX) / 2.0 + CAGE_MARGIN, MIN_CAGE_RADIUS, MAX_CAGE_RADIUS);
        double radiusZ = Mth.clamp((maxZ - minZ) / 2.0 + CAGE_MARGIN, MIN_CAGE_RADIUS, MAX_CAGE_RADIUS);
        Vec3 center = new Vec3(Mth.floor((minX + maxX) / 2.0) + 0.5, base, Mth.floor((minZ + maxZ) / 2.0) + 0.5);

        double clearance = contentsTop(level, center, radiusX, radiusZ) - base + CAGE_HEADROOM;
        double height = Mth.clamp(Math.max(Math.min(radiusX, radiusZ), clearance), MIN_CAGE_RADIUS, MAX_CAGE_HEIGHT);
        return new Cage(center, radiusX, radiusZ, height);
    }

    private static int ground(Level level, Vec3 point) {
        BlockHitResult hit = level.clip(new ClipContext(point, point.subtract(0.0, GROUND_SEARCH, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return Mth.floor(point.y);
        return Mth.floor(hit.getLocation().y + 1.0E-3);
    }

    private static double contentsTop(Level level, Vec3 center, double radiusX, double radiusZ) {
        int base = Mth.floor(center.y);
        int ceiling = base + (int) MAX_CAGE_HEIGHT;
        double top = base;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int reachX = Mth.ceil(radiusX), reachZ = Mth.ceil(radiusZ);
        for (int dx = -reachX; dx <= reachX; dx++) {
            for (int dz = -reachZ; dz <= reachZ; dz++) {
                if ((dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ) >= 1.0) continue;
                int x = Mth.floor(center.x) + dx, z = Mth.floor(center.z) + dz;
                for (int y = ceiling; y >= base; y--) {
                    if (HardLightBlock.canReplace(level.getBlockState(pos.set(x, y, z)))) continue;
                    top = Math.max(top, y + 1);
                    break;
                }
            }
        }
        AABB footprint = new AABB(center.x - radiusX, base, center.z - radiusZ, center.x + radiusX, ceiling, center.z + radiusZ);
        for (Entity entity : level.getEntities((Entity) null, footprint, entity -> !entity.isSpectator())) {
            top = Math.max(top, entity.getBoundingBox().maxY);
        }
        return Math.min(top, ceiling);
    }

    private static boolean insideCage(Cage cage, int dx, int dy, int dz) {
        if (dy < 0) return true;
        double x = dx / cage.radiusX(), y = dy / cage.height(), z = dz / cage.radiusZ();
        return x * x + y * y + z * z < 1.0;
    }

    public static List<BlockPos> dome(Level level, Cage cage) {
        BlockPos origin = BlockPos.containing(cage.center());
        int reachX = Mth.ceil(cage.radiusX()), reachZ = Mth.ceil(cage.radiusZ()), reachY = Mth.ceil(cage.height());

        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -reachX; dx <= reachX; dx++) {
            for (int dz = -reachZ; dz <= reachZ; dz++) {
                for (int dy = 0; dy <= reachY; dy++) {
                    if (!insideCage(cage, dx, dy, dz)) continue;
                    boolean edge = !insideCage(cage, dx + 1, dy, dz) || !insideCage(cage, dx - 1, dy, dz)
                            || !insideCage(cage, dx, dy, dz + 1) || !insideCage(cage, dx, dy, dz - 1) || !insideCage(cage, dx, dy + 1, dz);
                    if (!edge) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    cells.add(pos);
                    if (dy == 0) skirt(level, pos, cells);
                }
            }
        }
        cells.sort(Comparator.comparingInt(BlockPos::getY));
        return cells;
    }

    private static void skirt(Level level, BlockPos rim, List<BlockPos> cells) {
        for (int depth = 1; depth <= CAGE_SKIRT; depth++) {
            BlockPos below = rim.below(depth);
            if (!HardLightBlock.canReplace(level.getBlockState(below))) return;
            cells.add(below);
        }
    }

    private SculptGeometry() {}
}
