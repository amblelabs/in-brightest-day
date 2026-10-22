package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.weapon.TurretManager;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SentryTurretConstruct extends ConstructRingPower {
    public SentryTurretConstruct() {
        super(BrightestDay.id("sentry_turret"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().turretCost;
    }

    @Override
    public int cooldownTicks() {
        return 20;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (TurretManager.place(player, color)) return;
        PowerRingItem.refund(player, this.cost(size));
        player.sendOverlayMessage(Component.translatable("message.brightestday.sentry_turret_blocked"));
    }
}
