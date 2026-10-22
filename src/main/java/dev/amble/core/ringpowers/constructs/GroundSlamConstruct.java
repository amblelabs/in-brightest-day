package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.area.SlamManager;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

public class GroundSlamConstruct extends ConstructRingPower {
    public GroundSlamConstruct() {
        super(BrightestDay.id("ground_slam"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().slamCost;
    }

    @Override
    public int cooldownTicks() {
        return 12;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (SlamManager.isSlamming(player)) {
            PowerRingItem.refund(player, this.cost(size));
            return;
        }
        if (!SlamManager.canSlam(player)) {
            PowerRingItem.refund(player, this.cost(size));
            player.sendOverlayMessage(Component.translatable("message.brightestday.ground_slam_airborne"));
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        SlamManager.start(player, color);
    }
}
