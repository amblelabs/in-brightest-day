package dev.amble.core.ringpowers;

import dev.amble.core.rings.ConstructRings;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.EmotionSources;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.progression.RingOffers;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.LanternTeams;
import dev.amble.core.team.RingDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
    private static final int ORANGE_MOB_CHARGE_PER_DAMAGE = 1;
    private static final int ORANGE_MOB_CHARGE_PER_MINUTE = 200;
    private static final long MINUTE_TICKS = 1200;
    private static final float ORANGE_DAMAGE = 0.85F;
    private static final float ORANGE_COST = 1.15F;
    private static final int HUNGER_WARNING_SECONDS = 10;
    private static final float BLUE_DAMAGE = 0.5F;
    private static final float BLUE_COST = 0.6F;
    private static final float INDIGO_UTILITY_COST = 0.7F;
    private static final float SAPPHIRE_DAMAGE = 1.2F;
    private static final double SAPPHIRE_BOND_RADIUS = 16.0;
    private static final long COMBAT_TICKS = 100;

    private static final Map<UUID, Long> LAST_RING_HIT = new HashMap<>();
    private static final Map<UUID, long[]> MOB_THEFT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) hunger(server);
            if (server.getTickCount() % 200 != 0) return;
            long now = server.overworld().getGameTime();
            LAST_RING_HIT.values().removeIf(last -> now - last > COMBAT_TICKS);
            MOB_THEFT.values().removeIf(window -> now - window[0] >= MINUTE_TICKS);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_RING_HIT.clear();
            MOB_THEFT.clear();
        });
    }

    public static float scaleDamage(DamageSource source, float amount) {
        Optional<Player> attacker = ringAttacker(source);
        if (attacker.isEmpty()) return amount;

        Player player = attacker.get();
        if (ConstructRings.isConstruct(PowerRingItem.getWornRing(player))) amount *= ConstructRings.WEAK_DAMAGE;
        return switch (PowerRingItem.getWornCorps(player).orElse(LanternCorps.GREEN)) {
            case YELLOW -> amount * YELLOW_DAMAGE;
            case RED -> amount * RED_DAMAGE * (1.0F + RED_DESPERATION * (1.0F - player.getHealth() / player.getMaxHealth()));
            case BLUE -> amount * BLUE_DAMAGE;
            case ORANGE -> amount * ORANGE_DAMAGE;
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
            case ORANGE -> steal(player, victim, amount);
            default -> {}
        }
    }

    public static int scaleCost(Player player, int amount) {
        if (amount <= 0) return amount;
        float multiplier = switch (PowerRingItem.getWornCorps(player).orElse(LanternCorps.GREEN)) {
            case GREEN -> GREEN_COST;
            case RED -> RED_COST;
            case BLUE -> BLUE_COST;
            case ORANGE -> ORANGE_COST;
            default -> 1.0F;
        };
        return Math.max(1, Math.round(amount * multiplier));
    }

    public static int utilityCost(Player player, int amount) {
        if (amount <= 0 || PowerRingItem.getWornCorps(player).orElse(null) != LanternCorps.INDIGO) return amount;
        return Math.max(1, Math.round(amount * INDIGO_UTILITY_COST));
    }

    private static void steal(ServerPlayer thief, LivingEntity victim, float damage) {
        ItemStack victimRing = victim instanceof Player target ? PowerRingItem.getWornRing(target) : ItemStack.EMPTY;
        boolean lantern = !victimRing.isEmpty();
        int amount;
        if (lantern) {
            amount = Math.min(Math.round(damage * ORANGE_CHARGE_PER_DAMAGE), PowerRingItem.getRingPower(victimRing));
            if (amount <= 0) return;
            PowerRingItem.drainRing(victimRing, amount);
            if (victim instanceof Player target && victimRing == BrightestDayAttachments.getRing(target)) BrightestDayAttachments.setRing(target, victimRing);
        } else {
            amount = mobTheft(thief, Math.round(damage * ORANGE_MOB_CHARGE_PER_DAMAGE));
            if (amount <= 0) return;
        }

        ItemStack ring = PowerRingItem.getWornRing(thief);
        PowerRingItem.chargeRing(ring, amount);
        RingRanks.fire(thief, Trigger.STEAL, new Milestone.Context(victim, lantern ? "lantern" : "", true, false), amount);
        if (ring == BrightestDayAttachments.getRing(thief)) BrightestDayAttachments.setRing(thief, ring);
    }

    private static int mobTheft(ServerPlayer thief, int wanted) {
        if (wanted <= 0) return 0;
        long now = thief.level().getGameTime();
        long[] window = MOB_THEFT.computeIfAbsent(thief.getUUID(), id -> new long[]{now, 0L});
        if (now - window[0] >= MINUTE_TICKS) {
            window[0] = now;
            window[1] = 0L;
        }
        int allowed = (int) Math.min(wanted, ORANGE_MOB_CHARGE_PER_MINUTE - window[1]);
        if (allowed <= 0) return 0;
        window[1] += allowed;
        return allowed;
    }

    private static void hunger(MinecraftServer server) {
        BrightestDayConfig config = BrightestDayConfig.get();
        if (config.orangeHungerPerSecond <= 0) return;
        boolean warn = server.getTickCount() % (20 * HUNGER_WARNING_SECONDS) == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            ItemStack ring = PowerRingItem.getWornRing(player);
            if (PowerRingItem.getCorps(ring).orElse(null) != LanternCorps.ORANGE || PowerRingItem.getRingPower(ring) <= 0) continue;
            int hoard = RingOffers.hoard(player);
            if (hoard >= config.orangeHoardValue) continue;
            PowerRingItem.drainRing(ring, config.orangeHungerPerSecond);
            if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
            if (warn) player.sendOverlayMessage(Component.translatable("message.brightestday.orange.hunger", hoard, config.orangeHoardValue).withColor(LanternCorps.ORANGE.color()));
        }
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
