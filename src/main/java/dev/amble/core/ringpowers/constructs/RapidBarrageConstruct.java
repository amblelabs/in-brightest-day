package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.area.BarrageManager;
import net.minecraft.server.level.ServerPlayer;

public class RapidBarrageConstruct extends ConstructRingPower {
    public RapidBarrageConstruct() {
        super(BrightestDay.id("rapid_barrage"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().barrageCost;
    }

    @Override
    public int cooldownTicks() {
        return 6;
    }

    @Override
    public boolean sustained() {
        return true;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        BarrageManager.start(player, color);
    }
}
