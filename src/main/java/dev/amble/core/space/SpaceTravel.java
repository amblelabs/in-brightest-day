package dev.amble.core.space;

import dev.amble.BrightestDay;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class SpaceTravel {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, BrightestDay.id("space"));

    public static final double ATMOSPHERE_START_Y = 320;
    public static final double EXIT_ATMOSPHERE_Y = 700;
    public static final double REENTRY_Y = EXIT_ATMOSPHERE_Y - 24;
    public static final double SPACE_FLOOR_Y = 16;
    public static final double SPACE_GRAVITY_SCALE = 0.08;
    public static final int COOLDOWN_TICKS = 10;

    public static boolean isSpace(Level level) {
        return Cosmos.isSector(level.dimension());
    }

    public static boolean isWorld(ResourceKey<Level> key) {
        return key == Level.OVERWORLD || Cosmos.isSurface(key);
    }

    public static boolean isSeamless(ResourceKey<Level> from, ResourceKey<Level> to) {
        boolean fromFamily = isWorld(from) || Cosmos.isSector(from);
        boolean toFamily = isWorld(to) || Cosmos.isSector(to);
        return fromFamily && toFamily && from != to;
    }

    public static double gravityScale(Level level) {
        Cosmos.Sector sector = Cosmos.sector(level.dimension());
        if (sector == null || sector.antimatter()) return 1.0;
        return SPACE_GRAVITY_SCALE;
    }

    public static float atmosphereFade(Level level, double y) {
        if (isSpace(level)) return 1.0F;
        if (!isWorld(level.dimension())) return 0.0F;
        float t = (float) Mth.clamp((y - ATMOSPHERE_START_Y) / (EXIT_ATMOSPHERE_Y - ATMOSPHERE_START_Y), 0.0, 1.0);
        return t * t * (3.0F - 2.0F * t);
    }

    private SpaceTravel() {}
}
