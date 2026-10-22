package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.comms.Comms;
import dev.amble.core.comms.Megaphone;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;

public class MegaphoneConstruct extends ConstructRingPower {
    private static final int USE_COST = 10;
    private static final int COOLDOWN_TICKS = 4;

    public MegaphoneConstruct() {
        super(BrightestDay.id("megaphone"), EnumSet.allOf(LanternCorps.class));
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
    public boolean sustained() {
        return true;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {
        if (!Comms.available()) {
            PowerRingItem.refund(player, this.cost(radius));
            player.sendOverlayMessage(Component.translatable("message.brightestday.megaphone.no_voice_chat"));
            return;
        }
        Megaphone.start(player);
    }
}
