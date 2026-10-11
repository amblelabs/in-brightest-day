package dev.amble.core.ringpowers;

import dev.amble.core.rings.ConstructRings;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.EmotionSources;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.team.LanternTeams;
import dev.amble.core.team.RingDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CorpsCombat {
    private static final float GREEN_COST = 0.85F;
    private static final float YELLOW_DAMAGE = 1.1F;
    private static final int FEAR_TICKS = 60;
    private static final float RED_DAMAGE = 1.35F;
    private static final float RED_DESPERATION = 0.5F;
    private static final float RED_COST = 1.4F;
    private static final int ORANGE_CHARGE_PER_DAMAGE = 4;
    private static final float BLUE_DAMAGE = 0.5F;
    private static final float BLUE_COST = 0.6F;
    private static final float INDIGO_UTILITY_COST = 0.7F;
    private static final float SAPPHIRE_DAMAGE = 1.2F;
    private static final double SAPPHIRE_BOND_RADIUS = 16.0;
    private static final long COMBAT_TICKS = 100;

    private static final Map<UUID, Long> LAST_RING_HIT = new HashMap<>();

    public static float scaleDamage(DamageSource source, float amount) {
        Optional<Player> attacker = ringAttacker(source);
        if (attacker.isEmpty()) return amount;

        Player player = attacker.get();
        if (ConstructRings.isConstruct(PowerRingItem.getWornRing(player))) amount *= ConstructRings.WEAK_DAMAGE;
        return switch (PowerRingItem.getWornCorps(player).orElse(LanternCorps.GREEN)) {
            case YELLOW -> amount * YELLOW_DAMAGE;
            case RED -> amount * RED_DAMAGE * (1.0F + RED_DESPERATION * (1.0F - player.getHealth() / player.getMaxHealth()));
            case BLUE -> amount * BLUE_DAMAGE;
            case STAR_SAPPHIRE -> bonded(player) ? amount * SAPPHIRE_DAMAGE : amount;
            default -> amount;
        };
    }

    public static void afterHit(LivingEntity victim, DamageSource source, float amount) {
        if (!(ringAttacker(source).orElse(null) instanceof ServerPlayer player)) return;
        LAST_RING_HIT.put(player.getUUID(), player.level().getGameTime());

        switch (PowerRingItem.getWornCorps(player).orElse(LanternCorps.GREEN)) {
            case YELLOW -> {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FEAR_TICKS, 0), player);
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FEAR_TICKS, 0), player);
                EmotionSources.feared(victim, player);
            }
            case ORANGE -> steal(player, victim, Math.round(amount * ORANGE_CHARGE_PER_DAMAGE));
            default -> {}
        }
    }

    public static int scaleCost(Player player, int amount) {
        if (amount <= 0) return amount;
        float multiplier = switch (PowerRingItem.getWornCorps(player).orElse(LanternCorps.GREEN)) {
            case GREEN -> GREEN_COST;
            case RED -> RED_COST;
            case BLUE -> BLUE_COST;
            default -> 1.0F;
        };
        return Math.max(1, Math.round(amount * multiplier));
    }

    public static int utilityCost(Player player, int amount) {
        if (amount <= 0 || PowerRingItem.getWornCorps(player).orElse(null) != LanternCorps.INDIGO) return amount;
        return Math.max(1, Math.round(amount * INDIGO_UTILITY_COST));
    }

    private static void steal(ServerPlayer thief, LivingEntity victim, int amount) {
        if (amount <= 0) return;
        boolean lantern = false;
        if (victim instanceof Player target) {
            ItemStack ring = PowerRingItem.getWornRing(target);
            if (!ring.isEmpty()) {
                lantern = true;
                amount = Math.min(amount, PowerRingItem.getRingPower(ring));
                PowerRingItem.drainRing(ring, amount);
                if (ring == BrightestDayAttachments.getRing(target)) BrightestDayAttachments.setRing(target, ring);
            }
        }

        ItemStack ring = PowerRingItem.getWornRing(thief);
        PowerRingItem.chargeRing(ring, amount);
        RingRanks.fire(thief, Trigger.STEAL, new Milestone.Context(victim, lantern ? "lantern" : "", true, false), amount);
        if (ring == BrightestDayAttachments.getRing(thief)) BrightestDayAttachments.setRing(thief, ring);
    }

    public static boolean recentlyFought(Player player) {
        Long last = LAST_RING_HIT.get(player.getUUID());
        return last != null && player.level().getGameTime() - last <= COMBAT_TICKS;
    }

    public static boolean inBondedCombat(Player player) {
        Long last = LAST_RING_HIT.get(player.getUUID());
        return last != null && player.level().getGameTime() - last <= COMBAT_TICKS && bonded(player);
    }

    private static boolean bonded(Player player) {
        for (Player other : player.level().players()) {
            if (other != player && other.distanceToSqr(player) <= SAPPHIRE_BOND_RADIUS * SAPPHIRE_BOND_RADIUS
                    && LanternTeams.areTeammates(player, other)) return true;
        }
        return false;
    }

    private static Optional<Player> ringAttacker(DamageSource source) {
        if (!source.is(RingDamage.RING_CONSTRUCT) || !(source.getEntity() instanceof Player player)) return Optional.empty();
        return Optional.of(player);
    }

    private CorpsCombat() {}
}
