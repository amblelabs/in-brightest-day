package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.attacks.utility.OreProbeManager;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;

public class OreProbeConstruct extends ConstructRingPower {
    private static final int USE_COST = 120;
    private static final int COOLDOWN_TICKS = 20;

    public OreProbeConstruct() {
        super(BrightestDay.id("ore_probe"), EnumSet.of(LanternCorps.ORANGE, LanternCorps.WHITE));
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
        OreProbeManager.launch(player, color);
    }
}
