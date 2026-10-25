package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.core.sculpt.SculptManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SculptConstruct extends ConstructRingPower {
    private static final int USE_COST = 10;

    public SculptConstruct() {
        super(BrightestDay.id("sculpt"), CorpsArsenal.exclusive(LanternCorps.GREEN));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int maxSize() {
        return 12;
    }

    @Override
    public int defaultSize() {
        return 3;
    }

    @Override
    public int empoweredMaxSize() {
        return 16;
    }

    @Override
    public Component describeSize(int size) {
        return Component.literal(String.valueOf(this.costSize(size)));
    }

    @Override
    public boolean sustained() {
        return true;
    }

    @Override
    public int chargeTicks() {
        return 0;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        SculptManager.start(player, this.clampSize(player, size), color);
    }
}
