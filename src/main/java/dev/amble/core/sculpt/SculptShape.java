package dev.amble.core.sculpt;

import net.minecraft.util.Mth;

import java.util.Locale;

public enum SculptShape {
    FREEFORM("~"),
    STAIRS("/"),
    TUBE("□"),
    CAGE("∩"),
    FLOOR("▭");

    private static final SculptShape[] VALUES = values();

    private final String glyph;

    SculptShape(String glyph) {
        this.glyph = glyph;
    }

    public String glyph() {
        return this.glyph;
    }

    public String translationKey() {
        return "sculpt_shape.brightestday." + this.name().toLowerCase(Locale.ROOT);
    }

    public SculptShape cycle(int direction) {
        return VALUES[Math.floorMod(this.ordinal() + Integer.signum(direction), VALUES.length)];
    }

    public static SculptShape byId(int id) {
        return VALUES[Mth.clamp(id, 0, VALUES.length - 1)];
    }
}
