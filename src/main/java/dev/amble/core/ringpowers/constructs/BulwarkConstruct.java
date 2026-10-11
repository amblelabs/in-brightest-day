package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.shields.BulwarkArc;
import dev.amble.core.shields.BulwarkManager;
import net.minecraft.server.level.ServerPlayer;

public class BulwarkConstruct extends ConstructRingPower {
    private static final int USE_COST = 40;
    private static final int CHARGE_TICKS = 4;

    public BulwarkConstruct() {
        super(BrightestDay.id("bulwark"), CorpsArsenal.shared(LanternCorps.BLUE));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public int chargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public boolean sustained() {
        return true;
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int minSize() {
        return BulwarkArc.MIN_SIZE;
    }

    @Override
    public int maxSize() {
        return BulwarkArc.MAX_SIZE;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        BulwarkManager.start(player, color, this.clampSize(player, size));
    }
}
