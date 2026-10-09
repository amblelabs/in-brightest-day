package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.sphere.ContainmentSphere;
import net.minecraft.server.level.ServerPlayer;

public class ContainmentSphereConstruct extends ConstructRingPower {

    public ContainmentSphereConstruct() {
        super(BrightestDay.id("containment_sphere"));
    }

    @Override
    public int cost(int radius) {
        return BrightestDayConfig.get().sphereCostPerRadius * this.costSize(radius);
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
        return 1;
    }

    @Override
    public int maxSize() {
        return 5;
    }

    @Override
    public int defaultSize() {
        return 2;
    }

    @Override
    public int empoweredMaxSize() {
        return 8;
    }

    @Override
    public int chargeTicks() {
        return BrightestDayConfig.get().sphereChargeTicks;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {
        if (ContainmentSphere.isActive(player)) {
            ContainmentSphere.stop(player);
            PowerRingItem.refund(player, this.cost(radius));
            return;
        }
        if (!ContainmentSphere.start(player, this.clampSize(player, radius), color)) PowerRingItem.refund(player, this.cost(radius));
    }
}
