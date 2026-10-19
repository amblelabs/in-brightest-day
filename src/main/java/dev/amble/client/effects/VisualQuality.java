package dev.amble.client.effects;

import dev.amble.client.config.BrightestDayClientConfig;
import net.minecraft.util.Mth;

public final class VisualQuality {
    private static final float GLOW_CUTOFF = 0.5F;

    public static float level() {
        BrightestDayClientConfig config = BrightestDayClientConfig.get();
        return config == null ? 1.0F : Mth.clamp(config.effectsQuality, 0.25F, 1.5F);
    }

    public static int count(int base) {
        return Math.max(1, Math.round(base * level()));
    }

    public static boolean glow() {
        return level() >= GLOW_CUTOFF;
    }

    private VisualQuality() {}
}
