package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.attacks.utility.GrappleManager;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

public class GrapplingHookConstruct extends ConstructRingPower {
    private static final int COST = 40;
    private static final int COOLDOWN_TICKS = 5;

    public GrapplingHookConstruct() {
        super(BrightestDay.id("grappling_hook"));
    }

    @Override
    public int useCost() {
        return COST;
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        if (GrappleManager.isActive(player)) {
            PowerRingItem.refund(player, this.cost(size));
            GrappleManager.release(player);
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        GrappleManager.launch(player, color);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 0.8F);
    }
}
