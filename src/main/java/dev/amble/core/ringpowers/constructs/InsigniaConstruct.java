package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.visuals.Insignia;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;

public class InsigniaConstruct extends ConstructRingPower {
    private static final int USE_COST = 2;
    private static final int COOLDOWN_TICKS = 4;

    public InsigniaConstruct() {
        super(BrightestDay.id("insignia"), EnumSet.of(LanternCorps.GREEN, LanternCorps.YELLOW, LanternCorps.RED, LanternCorps.ORANGE,
                LanternCorps.BLUE, LanternCorps.INDIGO, LanternCorps.STAR_SAPPHIRE));
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
        Insignia.toggle(player);
    }
}
