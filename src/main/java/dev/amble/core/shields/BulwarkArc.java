package dev.amble.core.shields;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record BulwarkArc(Vec3 center, double radius, float yaw, double halfAngle, double bottom, double top) {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 5;
    private static final double FRONT = 1.0;
    private static final double RADIUS_PER_SIZE = 0.6;
    private static final double MIN_HALF_ANGLE = 60.0;
    private static final double MAX_HALF_ANGLE = 90.0;
    private static final double BELOW = 0.1;
    private static final double HEAD_ROOM = 0.5;
    private static final double SIDE_ROOM = 0.4;
    private static final double STANDARD_WIDTH = 0.6;
    private static final double SHELTER_MARGIN = 0.15;
    private static final double WALL_MARGIN = 0.3;
    private static final double OVERHANG = 1.0;

    public static BulwarkArc of(Entity holder, Vec3 feet, float yaw, int size) {
        int clamped = Mth.clamp(size, MIN_SIZE, MAX_SIZE);
        double width = holder.getBbWidth();
        double scale = width / STANDARD_WIDTH;
        double front = Math.max(FRONT * scale, width / 2.0 + SIDE_ROOM);
        double radius = front + (clamped - MIN_SIZE) * RADIUS_PER_SIZE * scale;
        double half = Math.toRadians(Mth.lerp((clamped - MIN_SIZE) / (double) (MAX_SIZE - MIN_SIZE), MIN_HALF_ANGLE, MAX_HALF_ANGLE));
        Vec3 center = feet.add(forward(yaw, 0.0).scale(front - radius));
        return new BulwarkArc(center, radius, yaw, half, feet.y - BELOW, feet.y + holder.getBbHeight() + HEAD_ROOM);
    }

    public static Vec3 forward(float yaw, double offset) {
        double radians = Math.toRadians(yaw) + offset;
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    public Vec3 point(double fraction, double height) {
        Vec3 out = forward(this.yaw, fraction * this.halfAngle).scale(this.radius);
        return new Vec3(this.center.x + out.x, this.bottom + height * (this.top - this.bottom), this.center.z + out.z);
    }

    public double arcLength() {
        return this.radius * this.halfAngle * 2.0;
    }

    public boolean shelters(Vec3 victim, double victimHeight, Vec3 source) {
        double vx = victim.x - this.center.x;
        double vz = victim.z - this.center.z;
        if (vx * vx + vz * vz > Mth.square(this.radius - SHELTER_MARGIN)) return false;
        if (victim.y + victimHeight < this.bottom || victim.y > this.top) return false;

        double sx = source.x - this.center.x;
        double sz = source.z - this.center.z;
        double distance = Math.sqrt(sx * sx + sz * sz);
        if (distance < this.radius - WALL_MARGIN || source.y > this.top + OVERHANG || source.y < this.bottom - OVERHANG) return false;
        Vec3 facing = forward(this.yaw, 0.0);
        double cos = (sx * facing.x + sz * facing.z) / Math.max(distance, 1.0E-4);
        return cos >= Math.cos(this.halfAngle);
    }
}
