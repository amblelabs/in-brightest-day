package dev.amble.core.space;

import dev.amble.BrightestDay;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class Cosmos {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, BrightestDay.id("space"));
    public static final ResourceKey<Level> OA = ResourceKey.create(Registries.DIMENSION, BrightestDay.id("oa"));

    private static final Vec3 OA_SYSTEM = new Vec3(300000.0, 0.0, 0.0);

    public enum Style { OCEANIC, ROCKY, GAS, STAR, LANTERN }

    public record Celestial(String id, Vec3 center, double radius, Style style, int color, int accent, boolean ringed,
                            @Nullable ResourceKey<Level> destination, double surfaceScale, double maxSurfaceOffset, double spinTicks) {
        public boolean enterable() {
            return this.destination != null;
        }

        public Quaterniond orientation(double time) {
            RandomSource random = RandomSource.create(this.id.hashCode());
            Vector3d baseAxis = new Vector3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            double baseAngle = random.nextDouble() * Math.PI * 2.0;
            Vector3d spinAxis = new Vector3d(random.nextGaussian() * 0.3, 1.0, random.nextGaussian() * 0.3).normalize();
            double spin = Math.PI * 2.0 * (time % this.spinTicks) / this.spinTicks;
            return new Quaterniond().rotateAxis(spin, spinAxis).mul(new Quaterniond().rotateAxis(baseAngle, baseAxis));
        }

        public boolean contains(Vec3 point, double time) {
            Vector3d local = orientation(time).conjugate().transform(new Vector3d(point.x - this.center.x, point.y - this.center.y, point.z - this.center.z));
            return Math.max(Math.abs(local.x), Math.max(Math.abs(local.y), Math.abs(local.z))) <= this.radius;
        }

        public String translationKey() {
            return "celestial." + BrightestDay.MOD_ID + "." + this.id;
        }
    }

    public record Wormhole(String id, Vec3 center, double radius, Vec3 exit, Vec3 exitHeading, int color) {
        public String translationKey() {
            return "celestial." + BrightestDay.MOD_ID + "." + this.id;
        }
    }

    public static final Celestial EARTH = new Celestial("earth", new Vec3(0.0, -1100.0, 0.0), 1000.0, Style.OCEANIC,
            0x2E6FD8, 0x3FA34D, false, Level.OVERWORLD, 64.0, Double.MAX_VALUE, 48000.0);
    public static final Celestial MOON = new Celestial("moon", new Vec3(1800.0, 250.0, -1200.0), 140.0, Style.ROCKY,
            0xB9B6AE, 0x8A877F, false, null, 1.0, 0.0, 24000.0);
    public static final Celestial GAS_GIANT = new Celestial("gas_giant", new Vec3(-6000.0, 900.0, 3500.0), 1400.0, Style.GAS,
            0xD9A066, 0x9C5B34, true, null, 1.0, 0.0, 30000.0);
    public static final Celestial SUN = new Celestial("sun", new Vec3(40000.0, 8000.0, -30000.0), 6000.0, Style.STAR,
            0xFFE8A0, 0xFFB347, false, null, 1.0, 0.0, 72000.0);
    public static final Celestial OA_PLANET = new Celestial("oa", OA_SYSTEM.add(0.0, -1100.0, 0.0), 900.0, Style.LANTERN,
            0x4A7A63, 0x00E03C, false, OA, 8.0, 400.0, 60000.0);
    public static final Celestial OA_MOON = new Celestial("oa_moon", OA_SYSTEM.add(-2200.0, 400.0, 1600.0), 220.0, Style.ROCKY,
            0x7C8C84, 0x56645C, false, null, 1.0, 0.0, 20000.0);

    public static final List<Celestial> BODIES = List.of(EARTH, MOON, GAS_GIANT, SUN, OA_PLANET, OA_MOON);

    public static final Wormhole TO_OA = new Wormhole("wormhole_oa", new Vec3(0.0, 300.0, -3000.0), 60.0,
            OA_SYSTEM.add(0.0, 300.0, -2700.0), new Vec3(0.0, 0.0, 1.0), 0x00E03C);
    public static final Wormhole TO_EARTH = new Wormhole("wormhole_earth", OA_SYSTEM.add(0.0, 300.0, -3000.0), 60.0,
            new Vec3(0.0, 300.0, -2700.0), new Vec3(0.0, 0.0, 1.0), 0x2D8CFF);

    public static final List<Wormhole> WORMHOLES = List.of(TO_OA, TO_EARTH);

    public static @Nullable Celestial planetFor(ResourceKey<Level> dimension) {
        for (Celestial body : BODIES) {
            if (dimension.equals(body.destination())) return body;
        }
        return null;
    }

    private Cosmos() {}
}
