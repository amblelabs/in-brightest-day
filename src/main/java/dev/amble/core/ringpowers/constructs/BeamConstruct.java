package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.beams.BeamManager;
import net.minecraft.server.level.ServerPlayer;

public class BeamConstruct extends ConstructRingPower {
    private static final int USE_COST = 120;

    public BeamConstruct() {
        super(BrightestDay.id("beam"));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public int cooldownTicks() {
        return 12;
    }

    @Override
    public boolean sustained() {
        return true;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        BeamManager.start(player, color);
    }
}
