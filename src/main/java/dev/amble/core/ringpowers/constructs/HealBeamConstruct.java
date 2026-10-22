package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.beams.HealBeamManager;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPowerCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

public class HealBeamConstruct extends ConstructRingPower {
    private static final int USE_COST = 50;

    public HealBeamConstruct() {
        super(BrightestDay.id("heal_beam"), EnumSet.of(LanternCorps.BLUE, LanternCorps.WHITE));
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.UTILITY;
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public int cooldownTicks() {
        return 10;
    }

    @Override
    public boolean sustained() {
        return true;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (!(aim(player, HealBeamManager.RANGE).entity() instanceof LivingEntity target)) {
            PowerRingItem.refund(player, this.cost(size));
            player.sendOverlayMessage(Component.translatable("message.brightestday.nothing_to_heal"));
            return;
        }
        HealBeamManager.start(player, target, color);
    }
}
