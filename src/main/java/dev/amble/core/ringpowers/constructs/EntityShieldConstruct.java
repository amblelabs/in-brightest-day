package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.shields.ShieldManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EntityShieldConstruct extends ConstructRingPower {
    private static final double RANGE = 32.0;
    private static final int USE_COST = 200;

    public EntityShieldConstruct() {
        super(BrightestDay.id("entity_shield"), CorpsArsenal.shared(LanternCorps.BLUE));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public int cooldownTicks() {
        return 6;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {
        Entity hit = aim(player, RANGE).entity();
        Entity target = hit instanceof LivingEntity ? hit : player;
        ShieldManager.shieldEntity(player.level(), target, color, player);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
    }
}
