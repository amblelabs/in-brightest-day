package dev.amble.core.progression;

import dev.amble.config.BrightestDayConfig;
import net.minecraft.server.level.ServerPlayer;

public final class EmotionTriggers {
    public static void onMeterChanged(ServerPlayer player, Emotion emotion, int before, int after) {
        switch (emotion) {
            case WILL -> {
                int threshold = BrightestDayConfig.get().greenRingWill;
                if (before < threshold && after >= threshold) RingOffers.considerGreen(player);
            }
            case COMPASSION -> {
            }
            default -> {}
        }
    }

    private EmotionTriggers() {}
}
