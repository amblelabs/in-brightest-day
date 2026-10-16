package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.magnet.MagnetManager;
import dev.amble.core.ringpowers.RingPowerCategory;
import net.minecraft.server.level.ServerPlayer;

public class MagnetConstruct extends ConstructRingPower {
    private static final int BASE_COST = 40;
    private static final int COST_PER_SIZE = 20;
    private static final int CHARGE_TICKS = 8;

    public MagnetConstruct() {
        super(BrightestDay.id("magnet"));
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.UTILITY;
    }

    @Override
    public int cost(int size) {
        return BASE_COST + COST_PER_SIZE * this.costSize(size);
    }

    @Override
    public int useCost() {
        return this.cost(this.defaultSize());
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int minSize() {
        return MagnetManager.MIN_SIZE;
    }

    @Override
    public int maxSize() {
        return MagnetManager.MAX_SIZE;
    }

    @Override
    public int defaultSize() {
        return 2;
    }

    @Override
    public int empoweredMaxSize() {
        return MagnetManager.EMPOWERED_SIZE;
    }

    @Override
    public int chargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (MagnetManager.isActive(player)) {
            MagnetManager.stop(player);
            PowerRingItem.refund(player, this.cost(size));
            return;
        }
        MagnetManager.start(player, this.clampSize(player, size), color);
    }
}
