package dev.amble.core.walls;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WallGeometry {
    private static final double TRACE_STEP = 0.35;

    public static int width(int size) {
        return size * 2 + 1;
    }

    public static int height(int size) {
        return size + 2;
    }

    public static List<BlockPos> cells(Vec3 base, float yaw, int size) {
        float radians = yaw * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(radians), 0.0, -Mth.sin(radians));
        double halfWidth = width(size) / 2.0;
        int baseY = Mth.floor(base.y + 1.0E-3);

        Set<BlockPos> cells = new LinkedHashSet<>();
        for (double u = -halfWidth + TRACE_STEP * 0.5; u < halfWidth; u += TRACE_STEP) {
            Vec3 point = base.add(right.scale(u));
            for (int row = 0; row < height(size); row++) {
                cells.add(new BlockPos(Mth.floor(point.x), baseY + row, Mth.floor(point.z)));
            }
        }

        List<BlockPos> ordered = new ArrayList<>(cells);
        BlockPos center = BlockPos.containing(base);
        ordered.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return ordered;
    }

    public static @Nullable Vec3 anchor(Level level, Entity viewer, Vec3 eye, Vec3 look, double range) {
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getLocation().add(hit.getDirection().getUnitVec3().scale(0.5));
    }

    public static Vec3 clampLength(Vec3 start, Vec3 end, double maxLength) {
        Vec3 flat = new Vec3(end.x - start.x, 0.0, end.z - start.z);
        double length = flat.length();
        if (length <= maxLength) return end;
        return start.add(end.subtract(start).scale(maxLength / length));
    }

    public static List<BlockPos> line(Vec3 start, Vec3 end, int height) {
        double length = Math.hypot(end.x - start.x, end.z - start.z);
        int steps = Math.max(Mth.ceil(length / TRACE_STEP), 1);
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.lerp(end, (double) i / steps);
            int baseY = Mth.floor(point.y + 1.0E-3);
            for (int row = 0; row < height; row++) {
                cells.add(new BlockPos(Mth.floor(point.x), baseY + row, Mth.floor(point.z)));
            }
        }
        return new ArrayList<>(cells);
    }

    private WallGeometry() {}
}
