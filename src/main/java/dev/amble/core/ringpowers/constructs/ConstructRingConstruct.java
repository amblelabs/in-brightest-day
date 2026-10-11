package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.rings.ConstructRings;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;

public class ConstructRingConstruct extends ConstructRingPower {
    private static final int CHARGE_TICKS = 10;

    public ConstructRingConstruct() {
        super(BrightestDay.id("construct_ring"), EnumSet.of(LanternCorps.GREEN, LanternCorps.YELLOW, LanternCorps.RED, LanternCorps.ORANGE,
                LanternCorps.BLUE, LanternCorps.INDIGO, LanternCorps.STAR_SAPPHIRE));
    }

    @Override
    public int useCost() {
        return 0;
    }

    @Override
    public int chargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int maxSize() {
        return ConstructRings.MAX_STEPS;
    }

    @Override
    public int empoweredMaxSize() {
        return ConstructRings.MAX_STEPS;
    }

    @Override
    public Component describeSize(int size) {
        return Component.translatable("gui.brightestday.construct_ring.minutes", this.clampSize(size) * ConstructRings.MINUTES_PER_STEP);
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ConstructRings.create(player, size);
    }
}
