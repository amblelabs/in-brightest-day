package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.sync.RingCompass;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;

public class RingCompassConstruct extends ConstructRingPower {
    private static final int USE_COST = 10;
    private static final int COOLDOWN_TICKS = 4;

    public RingCompassConstruct() {
        super(BrightestDay.id("ring_compass"), EnumSet.of(LanternCorps.GREEN, LanternCorps.YELLOW, LanternCorps.RED, LanternCorps.BLUE,
                LanternCorps.ORANGE, LanternCorps.INDIGO, LanternCorps.STAR_SAPPHIRE));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (!RingCompass.project(player, color)) PowerRingItem.refund(player, this.cost(size));
    }
}
