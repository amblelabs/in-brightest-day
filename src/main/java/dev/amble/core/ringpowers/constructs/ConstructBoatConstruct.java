package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.mounts.ConstructMounts;
import net.minecraft.server.level.ServerPlayer;

public class ConstructBoatConstruct extends ConstructRingPower {

    public ConstructBoatConstruct() {
        super(BrightestDay.id("construct_boat"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().boatCost;
    }

    @Override
    public int cooldownTicks() {
        return 10;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        ConstructMounts.summon(player, ConstructMounts.Kind.BOAT, color);
    }
}
