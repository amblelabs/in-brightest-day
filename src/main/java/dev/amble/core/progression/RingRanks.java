package dev.amble.core.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public final class RingRanks {
    public static final int MAX_RANK = 4;
    private static final float[] ARSENAL = {0.0F, 1.0F / 3.0F, 0.55F, 0.78F, 1.0F};
    private static final float[] CAPACITY = {0.0F, 0.6F, 0.73F, 0.86F, 1.0F};
    private static final Map<RingPower<?>, Integer> RANK_GATED = Map.of(
            RingPowerRegistry.LUMBERJACK, 2,
            RingPowerRegistry.CONSTRUCT_HORSE, 1,
            RingPowerRegistry.CONSTRUCT_BOAT, 1,
            RingPowerRegistry.CONTAINMENT_SPHERE, 3,
            RingPowerRegistry.RING_COMPASS, 1,
            RingPowerRegistry.LIGHT_ORB, 1,
            RingPowerRegistry.INSIGNIA, 2);
    private static final Map<LanternCorps, Map<RingPower<?>, Integer>> CORPS_GATED = Map.of(
            LanternCorps.YELLOW, Map.of(RingPowerRegistry.GIANT_FIST, 2),
            LanternCorps.BLUE, Map.of(RingPowerRegistry.TOOL_FORGE, 3, RingPowerRegistry.GIANT_FIST, 2));
    private static final List<String> PRIORITY = List.of(
            "blast", "entity_shield", "boomerang_disc", "energy_whip", "chain_bolt", "wall", "piercing_lance", "swarm_missiles",
            "rapid_barrage", "area_shield", "beam", "giant_fist", "nova_burst", "ground_slam", "sentry_turret", "glider",
            "grappling_hook", "light_orb", "drill", "tool_forge");

    public record Ranks(Map<LanternCorps, Integer> ranks, Map<String, Integer> counters, Map<LanternCorps, List<String>> assigned) {
        public static final Ranks EMPTY = new Ranks(Map.of(), Map.of(), Map.of());

        public static final Codec<Ranks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(LanternCorps.CODEC, Codec.INT).optionalFieldOf("ranks", Map.of()).forGetter(Ranks::ranks),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(Ranks::counters),
                Codec.unboundedMap(LanternCorps.CODEC, Codec.STRING.listOf()).optionalFieldOf("milestones", Map.of()).forGetter(Ranks::assigned)
        ).apply(instance, Ranks::new));

        public int rank(LanternCorps corps) {
            return this.ranks.getOrDefault(corps, 1);
        }

        public int counter(String key) {
            return this.counters.getOrDefault(key, 0);
        }

        public Optional<Milestone> milestone(LanternCorps corps, int tier) {
            List<String> keys = this.assigned.getOrDefault(corps, List.of());
            int index = tier - Milestones.FIRST_TIER;
            if (index < 0 || index >= keys.size()) return Optional.empty();
            return Milestones.get(keys.get(index));
        }

        Ranks withRank(LanternCorps corps, int rank) {
            Map<LanternCorps, Integer> ranks = new EnumMap<>(LanternCorps.class);
            ranks.putAll(this.ranks);
            ranks.put(corps, rank);
            return new Ranks(Map.copyOf(ranks), this.counters, this.assigned);
        }

        Ranks withCounter(String key, int value) {
            Map<String, Integer> counters = new HashMap<>(this.counters);
            counters.put(key, value);
            return new Ranks(this.ranks, Map.copyOf(counters), this.assigned);
        }

        Ranks withAssigned(LanternCorps corps, List<String> keys) {
            Map<LanternCorps, List<String>> assigned = new EnumMap<>(LanternCorps.class);
            assigned.putAll(this.assigned);
            assigned.put(corps, List.copyOf(keys));
            return new Ranks(this.ranks, this.counters, Map.copyOf(assigned));
        }
    }

    public static final AttachmentType<Ranks> RANKS =
            AttachmentRegistry.<Ranks>builder()
                    .initializer(() -> Ranks.EMPTY)
                    .persistent(Ranks.CODEC)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.fromCodec(Ranks.CODEC), AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(BrightestDay.id("ring_ranks"));

    public static void init() {}

    public static Ranks get(Player player) {
        return player.getAttachedOrElse(RANKS, Ranks.EMPTY);
    }

    public static boolean ranked(LanternCorps corps) {
        return corps != LanternCorps.WHITE && corps != LanternCorps.BLACK;
    }

    public static int rank(Player player, LanternCorps corps) {
        return ranked(corps) ? get(player).rank(corps) : MAX_RANK;
    }

    public static void assign(ServerPlayer player) {
        Ranks ranks = get(player);
        Ranks updated = ranks;
        boolean multiplayer = IndigoOne.multiplayer(player.level().getServer());
        for (LanternCorps corps : LanternCorps.values()) {
            if (!Milestones.has(corps)) continue;
            List<String> keys = updated.assigned().getOrDefault(corps, List.of());
            if (keys.size() == Milestones.LAST_TIER - Milestones.FIRST_TIER + 1 && IntStream.range(0, keys.size()).allMatch(i -> Milestones.get(keys.get(i)).filter(milestone -> milestone.tier() == Milestones.FIRST_TIER + i).isPresent())) continue;
            updated = updated.withAssigned(corps, Milestones.roll(corps, player.getRandom(), multiplayer));
        }
        if (updated != ranks) player.setAttached(RANKS, updated);
    }

    public static void fire(ServerPlayer player, Trigger trigger, Milestone.Context context, int amount) {
        if (amount <= 0) return;
        LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
        if (corps == null || !Milestones.has(corps)) return;
        assign(player);

        Ranks ranks = get(player);
        int tier = ranks.rank(corps) + 1;
        if (tier > MAX_RANK) return;
        Milestone milestone = ranks.milestone(corps, tier).orElse(null);
        if (milestone == null || milestone.trigger() != trigger || !milestone.condition().test(player, context)) return;

        int current = ranks.counter(milestone.key());
        int value = switch (milestone.mode()) {
            case ACCUMULATE -> current + amount;
            case REACH -> Math.max(current, amount);
            case DISTINCT -> {
                String maskKey = milestone.key() + "_mask";
                int mask = ranks.counter(maskKey);
                if ((mask & (1 << amount)) != 0) yield current;
                ranks = ranks.withCounter(maskKey, mask | (1 << amount));
                yield current + 1;
            }
        };
        value = Math.min(milestone.goal(), value);
        if (value == current) {
            if (ranks != get(player)) player.setAttached(RANKS, ranks);
            return;
        }

        ranks = ranks.withCounter(milestone.key(), value);
        if (value >= milestone.goal()) {
            player.setAttached(RANKS, ranks.withRank(corps, tier));
            promoted(player, corps, tier);
            return;
        }
        player.setAttached(RANKS, ranks);
    }

    public static void fire(ServerPlayer player, Trigger trigger, Milestone.Context context) {
        fire(player, trigger, context, 1);
    }

    public static void setRank(ServerPlayer player, LanternCorps corps, int rank) {
        player.setAttached(RANKS, get(player).withRank(corps, Mth.clamp(rank, 1, MAX_RANK)));
    }

    public static void reset(ServerPlayer player) {
        player.setAttached(RANKS, Ranks.EMPTY);
        assign(player);
    }

    public static void reroll(ServerPlayer player, LanternCorps corps) {
        player.setAttached(RANKS, get(player).withAssigned(corps, Milestones.roll(corps, player.getRandom(), IndigoOne.multiplayer(player.level().getServer()))));
    }

    public static boolean force(ServerPlayer player, LanternCorps corps) {
        assign(player);
        Ranks ranks = get(player);
        int tier = ranks.rank(corps) + 1;
        Milestone milestone = ranks.milestone(corps, tier).orElse(null);
        if (milestone == null) return false;
        player.setAttached(RANKS, ranks.withCounter(milestone.key(), milestone.goal()).withRank(corps, tier));
        promoted(player, corps, tier);
        return true;
    }

    private static void promoted(ServerPlayer player, LanternCorps corps, int rank) {
        player.sendSystemMessage(Component.translatable("message.brightestday.rank_up", corps.displayName(), rank)
                .withStyle(ChatFormatting.BOLD).withColor(corps.color()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    public static float arsenalFraction(int rank) {
        return ARSENAL[Mth.clamp(rank, 1, MAX_RANK)];
    }

    public static float capacityFraction(int rank) {
        return CAPACITY[Mth.clamp(rank, 1, MAX_RANK)];
    }

    public static int capacity(Player player, LanternCorps corps) {
        MinecraftServer server = player.level().getServer();
        float battery = server == null ? 1.0F : CentralPowerBattery.strength(server, corps);
        return Math.round(BrightestDayComponents.MAX_POWER * CAPACITY[Mth.clamp(rank(player, corps), 1, MAX_RANK)] * battery);
    }

    public static void clampCharge(ServerPlayer player) {
        ItemStack ring = BrightestDayAttachments.getRing(player);
        LanternCorps corps = PowerRingItem.getCorps(ring).orElse(null);
        if (corps == null || !PowerRingItem.usesPower(ring)) return;

        int cap = capacity(player, corps);
        int battery = Math.round(CentralPowerBattery.strength(player.level().getServer(), corps) * 100.0F);
        BrightestDayComponents.ChargeCap info = cap >= BrightestDayComponents.MAX_POWER ? null
                : new BrightestDayComponents.ChargeCap(cap, Mth.clamp(rank(player, corps), 1, MAX_RANK), battery);
        boolean changed = !Objects.equals(ring.get(BrightestDayComponents.CHARGE_CAP), info);
        if (changed) {
            if (info == null) ring.remove(BrightestDayComponents.CHARGE_CAP);
            else ring.set(BrightestDayComponents.CHARGE_CAP, info);
        }
        if (PowerRingItem.getRingPower(ring) > cap) {
            ring.set(BrightestDayComponents.POWER_TYPE, cap);
            changed = true;
        }
        if (changed) BrightestDayAttachments.setRing(player, ring);
    }

    private static final Set<LanternCorps> FORGE_STARTERS = EnumSet.of(LanternCorps.GREEN, LanternCorps.YELLOW, LanternCorps.ORANGE,
            LanternCorps.STAR_SAPPHIRE, LanternCorps.INDIGO);

    public static boolean starter(LanternCorps corps, RingPower<?> power) {
        return power == RingPowerRegistry.TOOL_FORGE && FORGE_STARTERS.contains(corps);
    }

    public static Set<RingPower<?>> locked(Player player, LanternCorps corps) {
        if (!ranked(corps)) return Set.of();

        List<ConstructRingPower> arsenal = RingPowerRegistry.forCorps(corps).stream()
                .filter(ConstructRingPower.class::isInstance)
                .map(ConstructRingPower.class::cast)
                .filter(power -> !starter(corps, power))
                .filter(power -> !RANK_GATED.containsKey(power))
                .filter(power -> !CORPS_GATED.getOrDefault(corps, Map.of()).containsKey(power))
                .filter(power -> power != RingPowerRegistry.MEGAPHONE)
                .sorted(Comparator.comparingInt((ConstructRingPower power) -> power.corps().size() <= 4 ? 0 : 1)
                        .thenComparingInt(power -> {
                            int index = PRIORITY.indexOf(power.id().getPath());
                            return index < 0 ? PRIORITY.size() : index;
                        }))
                .toList();
        int unlocked = (int) Math.ceil(arsenal.size() * ARSENAL[Mth.clamp(rank(player, corps), 1, MAX_RANK)]);
        Set<RingPower<?>> locked = new HashSet<>();
        for (int i = unlocked; i < arsenal.size(); i++) locked.add(arsenal.get(i));
        int rank = rank(player, corps);
        RANK_GATED.forEach((power, required) -> {
            if (rank < required) locked.add(power);
        });
        CORPS_GATED.getOrDefault(corps, Map.of()).forEach((power, required) -> {
            if (rank < required) locked.add(power);
        });
        return locked;
    }

    private RingRanks() {}
}
