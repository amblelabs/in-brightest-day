package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.projectile.DiscManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class BoomerangDiscConstruct extends ConstructRingPower {
    public BoomerangDiscConstruct() {
        super(BrightestDay.id("boomerang_disc"));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().discCost;
    }

    @Override
    public int cooldownTicks() {
        return 15;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        DiscManager.launch(player, color);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
    }
}
