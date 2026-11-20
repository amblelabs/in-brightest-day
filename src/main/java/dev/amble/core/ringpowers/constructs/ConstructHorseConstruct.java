package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.mounts.ConstructMounts;
import net.minecraft.server.level.ServerPlayer;

public class ConstructHorseConstruct extends ConstructRingPower {

    public ConstructHorseConstruct() {
        super(BrightestDay.id("construct_horse"));
    }

    @Override
    public int baseCost() {
        return BrightestDayConfig.get().horseCost;
    }

    @Override
    public int cooldownTicks(int size) {
        return 10;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ConstructMounts.summon(player, ConstructMounts.Kind.HORSE, color);
    }
}
