package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.weapon.HandManager;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class HandConstruct extends ConstructRingPower {
    private static final float[] SCALES = {0.75F, 1.0F, 1.25F, 1.5F, 1.8F};
    private static final int HOLD_TICKS = 100;

    public HandConstruct() {
        super(BrightestDay.id("hand"), CorpsArsenal.shared(LanternCorps.RED, LanternCorps.BLUE));
    }

    public static float scale(int size) {
        return SCALES[Math.clamp(size, 1, SCALES.length) - 1];
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().fistCost;
    }

    @Override
    public int cost(int radius) {
        return Math.round(this.useCost() * scale(this.clampSize(radius)));
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int maxSize() {
        return 4;
    }

    @Override
    public int defaultSize() {
        return 2;
    }

    @Override
    public int empoweredMaxSize() {
        return SCALES.length;
    }

    @Override
    public Component describeSize(int size) {
        return Component.literal(Math.round(scale(this.costSize(size)) * 100) + "%");
    }

    @Override
    public int chargeTicks() {
        return HOLD_TICKS;
    }

    @Override
    public int releaseTicks() {
        return 1;
    }

    @Override
    public int cooldownTicks() {
        return 15;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        HandManager.strike(player, color, scale(this.clampSize(player, size)));
    }
}
