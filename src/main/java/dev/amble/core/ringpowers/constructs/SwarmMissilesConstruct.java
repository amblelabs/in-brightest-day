package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.projectile.SwarmManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SwarmMissilesConstruct extends ConstructRingPower {
    public SwarmMissilesConstruct() {
        super(BrightestDay.id("swarm_missiles"), CorpsArsenal.exclusive(LanternCorps.YELLOW));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().swarmCost;
    }

    @Override
    public int cooldownTicks() {
        return 20;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        SwarmManager.launch(player, color);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.5F);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.8F);
    }
}
