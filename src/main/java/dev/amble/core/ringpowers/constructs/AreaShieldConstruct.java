package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.shields.ShieldManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class AreaShieldConstruct extends ConstructRingPower {
    public static final double RANGE = 24.0;
    private static final int BASE_COST = 100;
    private static final int COST_PER_BLOCK = 30;

    public AreaShieldConstruct() {
        super(BrightestDay.id("area_shield"), CorpsArsenal.shared(LanternCorps.BLUE));
    }

    @Override
    public int cost(int radius) {
        return BASE_COST + COST_PER_BLOCK * this.costSize(radius);
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return 8;
    }

    @Override
    public int minSize() {
        return 2;
    }

    @Override
    public int maxSize() {
        return 10;
    }

    @Override
    public int defaultSize() {
        return 4;
    }

    @Override
    public int empoweredMaxSize() {
        return 15;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {
        Vec3 center = aim(player, RANGE).end();
        ShieldManager.shieldArea(player.level(), center, this.clampSize(player, radius), color, player);
        player.level().playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.8F);
        player.level().playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
    }
}
