package dev.amble.core.ringpowers;

import dev.amble.core.rings.WhiteFusion;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class CorpsColors {
    private static final float MAX_DARKEN = 0.85F;
    private static final float MAX_LIGHTEN = 0.95F;
    private static final float LIGHTEN_DESATURATION = 0.7F;
    private static final float MAX_SATURATION_BOOST = 0.8F;

    public static int of(Player player) {
        LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(LanternCorps.GREEN);
        LanternCorps aligned = WhiteFusion.alignment(player);
        return apply((aligned != null ? aligned : corps).color(), BrightestDayAttachments.getColorTweak(player));
    }

    public static int apply(int rgb, ColorTweak tweak) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float hue = 0.0F;
        if (delta > 1.0E-5F) {
            if (max == r) hue = ((g - b) / delta) / 6.0F;
            else if (max == g) hue = ((b - r) / delta + 2.0F) / 6.0F;
            else hue = ((r - g) / delta + 4.0F) / 6.0F;
            if (hue < 0.0F) hue += 1.0F;
        }
        float saturation = max <= 0.0F ? 0.0F : delta / max;
        float value = max;

        float s = Math.max(tweak.saturation(), ColorTweak.MIN_SATURATION);
        saturation *= s >= 0.0F ? 1.0F + s * MAX_SATURATION_BOOST : 1.0F + s;

        float v = tweak.brightness();
        if (v >= 0.0F) {
            value += (1.0F - value) * v * MAX_LIGHTEN;
            saturation *= 1.0F - v * LIGHTEN_DESATURATION;
        } else {
            value *= 1.0F + v * MAX_DARKEN;
        }

        return Mth.hsvToRgb(hue, Mth.clamp(saturation, 0.0F, 1.0F), Mth.clamp(value, 0.0F, 1.0F)) & 0xFFFFFF;
    }

    private CorpsColors() {}
}
