package dev.amble.core.ringpowers.impl;

import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import com.mojang.serialization.MapCodec;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

public class SelfHealRingPower extends RingPower<Unit> {
    private static final Map<ServerPlayer, Integer> STREAK = new WeakHashMap<>();

    public SelfHealRingPower() {
        super(BrightestDay.id("self_heal"), EnumSet.of(LanternCorps.BLUE, LanternCorps.WHITE), MapCodec.unitCodec(Unit.INSTANCE));
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.UTILITY;
    }

    @Override
    public Unit createData() {
        return Unit.INSTANCE;
    }

    @Override
    public void tick(ServerPlayer player, Unit data) {
        BrightestDayConfig config = BrightestDayConfig.get();
        if (!this.isHealing(player, config)) {
            STREAK.remove(player);
            return;
        }
        if (player.tickCount % Math.max(1, config.selfHealIntervalTicks) != 0) return;

        int streak = STREAK.merge(player, 1, Integer::sum) - 1;
        float before = player.getHealth();
        player.heal(Math.min(config.selfHealMaxPulse, config.selfHealAmount * (float) Math.pow(config.selfHealGrowth, streak)));
        RingRanks.fire(player, Trigger.SELF_HEAL, Milestone.Context.NONE, Math.round(player.getHealth() - before));
        player.level().sendParticles(new DustParticleOptions(CorpsColors.of(player), 0.8F),
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                3, player.getBbWidth() * 0.4, player.getBbHeight() * 0.3, player.getBbWidth() * 0.4, 0.0);
    }

    @Override
    public int drainPerSecond(ServerPlayer player, Unit data) {
        BrightestDayConfig config = BrightestDayConfig.get();
        return this.isHealing(player, config) ? config.selfHealDrainPerSecond : 0;
    }

    private boolean isHealing(ServerPlayer player, BrightestDayConfig config) {
        return config.selfHealAmount > 0.0F && player.isAlive() && !player.isSpectator()
                && player.getHealth() < player.getMaxHealth() && PowerRingItem.hasCharge(player);
    }
}
