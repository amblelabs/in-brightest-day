package dev.amble.core.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SpaceWarp {
    public static final double MACH = 343.0 / 20.0;
    public static final double APPARENT_MAX_MACH = 10.0;
    public static final double MAX_SPEED = MACH * APPARENT_MAX_MACH / Cosmos.COMPRESSION;
    private static final double CALM_DISTANCE = 120.0 / Cosmos.COMPRESSION;
    private static final double BRAKE_DIVISOR = 12.0;

    public static double nearest(Level level, Vec3 position, ResourceKey<Level> mogo) {
        ResourceKey<Level> sector = level.dimension();
        if (!Cosmos.isSector(sector)) return 0.0;
        double nearest = Double.MAX_VALUE;
        for (Cosmos.Planet planet : Cosmos.planetsIn(sector, mogo)) {
            nearest = Math.min(nearest, position.distanceTo(PlanetBody.center(planet)) - planet.footprint() * Math.sqrt(3.0));
        }
        if (Cosmos.solar(sector)) nearest = Math.min(nearest, position.distanceTo(SolarHeat.center()) - Cosmos.SUN_HALF * SolarHeat.WARM);
        for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) {
            if (wormhole.sector() != sector) continue;
            nearest = Math.min(nearest, position.distanceTo(new Vec3(wormhole.x(), wormhole.y(), wormhole.z())) - Cosmos.WORMHOLE_SIZE);
        }
        return Math.max(0.0, nearest);
    }

    public static double limit(Level level, Vec3 position, ResourceKey<Level> mogo, double base) {
        if (!Cosmos.isSector(level.dimension())) return base;
        double room = (nearest(level, position, mogo) - CALM_DISTANCE) / BRAKE_DIVISOR;
        return Math.max(base, Math.min(MAX_SPEED, room));
    }

    private SpaceWarp() {}
}
