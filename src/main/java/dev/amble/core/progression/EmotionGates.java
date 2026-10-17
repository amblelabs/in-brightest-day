package dev.amble.core.progression;

import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class EmotionGates {
    public static final int BLOCK = 500;
    public static final int GREEN_MARGIN = 200;
    public static final int BALANCE_FLOOR = 500;
    public static final int BALANCE_SPREAD = 250;

    public static Optional<Emotion> blocker(LanternCorps corps) {
        return Optional.ofNullable(switch (corps) {
            case GREEN, BLUE -> Emotion.FEAR;
            case YELLOW -> Emotion.WILL;
            case RED -> Emotion.LOVE;
            case STAR_SAPPHIRE -> Emotion.RAGE;
            case ORANGE -> Emotion.COMPASSION;
            case INDIGO -> Emotion.AVARICE;
            default -> null;
        });
    }

    public static boolean blocked(Player player, LanternCorps corps) {
        return blocker(corps).map(emotion -> SpectrumMeters.get(player, emotion) > BLOCK).orElse(false);
    }

    public static int willLead(Player player) {
        return SpectrumMeters.get(player, Emotion.WILL) - SpectrumMeters.get(player, Emotion.FEAR);
    }

    public static boolean allows(Player player, LanternCorps corps) {
        if (player.hasInfiniteMaterials()) return true;
        if (blocked(player, corps)) return false;
        return corps != LanternCorps.GREEN || willLead(player) >= GREEN_MARGIN;
    }

    public static int lowest(Player player) {
        int lowest = Emotion.MAX;
        for (Emotion emotion : Emotion.values()) lowest = Math.min(lowest, SpectrumMeters.get(player, emotion));
        return lowest;
    }

    public static int spread(Player player) {
        int lowest = Emotion.MAX;
        int highest = 0;
        for (Emotion emotion : Emotion.values()) {
            int value = SpectrumMeters.get(player, emotion);
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }
        return highest - lowest;
    }

    public static boolean balanced(Player player) {
        return lowest(player) >= BALANCE_FLOOR && spread(player) <= BALANCE_SPREAD;
    }

    private EmotionGates() {}
}
