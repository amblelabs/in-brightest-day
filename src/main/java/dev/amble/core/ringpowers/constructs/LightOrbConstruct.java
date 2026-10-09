package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.light.LightOrbManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LightOrbConstruct extends ConstructRingPower {

    public LightOrbConstruct() {
        super(BrightestDay.id("light_orb"), CorpsArsenal.shared(LanternCorps.BLUE, LanternCorps.RED));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().lightOrbCost;
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int maxSize() {
        return 5;
    }

    @Override
    public int empoweredMaxSize() {
        return 7;
    }

    @Override
    public int chargeTicks() {
        return BrightestDayConfig.get().lightOrbChargeTicks;
    }

    @Override
    public Component describeSize(int size) {
        return Component.literal(String.valueOf(this.costSize(size)));
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (LightOrbManager.place(player, this.clampSize(player, size), color)) return;
        PowerRingItem.refund(player, this.cost(size));
        player.sendOverlayMessage(Component.translatable("message.brightestday.light_orb_blocked"));
    }
}
