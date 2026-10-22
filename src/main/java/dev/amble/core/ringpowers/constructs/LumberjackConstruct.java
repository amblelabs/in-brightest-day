package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.core.attacks.utility.LumberjackManager;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;

public class LumberjackConstruct extends ConstructRingPower {
    private static final int USE_COST = 60;
    private static final int COOLDOWN_TICKS = 15;

    public LumberjackConstruct() {
        super(BrightestDay.id("lumberjack"), EnumSet.complementOf(EnumSet.of(LanternCorps.RED)));
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
        Component failure = LumberjackManager.fell(player, color);
        if (failure == null) return;
        PowerRingItem.refund(player, this.cost(size));
        player.sendOverlayMessage(failure);
    }
}
