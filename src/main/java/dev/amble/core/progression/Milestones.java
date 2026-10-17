package dev.amble.core.progression;

import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.ringpowers.CorpsCombat;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.team.LanternTeams;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Milestones {
    public static final int FIRST_TIER = 2;
    public static final int LAST_TIER = 4;

    private static final Map<String, Milestone> ALL = new LinkedHashMap<>();
    private static final Map<LanternCorps, Map<Integer, List<Milestone>>> POOLS = new EnumMap<>(LanternCorps.class);
    private static final Set<EntityType<?>> OUTCLASSING = Set.of(EntityTypes.WARDEN, EntityTypes.WITHER, EntityTypes.ELDER_GUARDIAN,
            EntityTypes.RAVAGER, EntityTypes.EVOKER);
    private static final double PET_RANGE = 16.0;
    private static final int KEEP_SECONDS = 600;

    private static Milestone.Condition always() {
        return (player, context) -> true;
    }

    private static Milestone.Condition hostile() {
        return (player, context) -> context.target() instanceof Enemy;
    }

    private static Milestone.Condition ringHostile() {
        return (player, context) -> context.ring() && context.target() instanceof Enemy;
    }

    private static Milestone.Condition type(EntityType<?>... types) {
        return (player, context) -> {
            if (context.target() == null) return false;
            for (EntityType<?> type : types) if (context.target().getType() == type) return true;
            return false;
        };
    }

    private static Milestone.Condition bearer() {
        return (player, context) -> context.target() instanceof Player victim && !BrightestDayAttachments.getRing(victim).isEmpty();
    }

    private static Milestone.Condition construct(String... ids) {
        return (player, context) -> {
            for (String id : ids) if (context.detail().equals(id)) return true;
            return false;
        };
    }

    private static Milestone.Condition detail(String value) {
        return (player, context) -> context.detail().equals(value);
    }

    private static Milestone.Condition below(float hearts) {
        return (player, context) -> player.getHealth() < hearts * 2.0F;
    }

    private static Milestone.Condition belowFraction(float fraction) {
        return (player, context) -> player.getHealth() < player.getMaxHealth() * fraction;
    }

    private static Milestone.Condition holding(Item item, int count) {
        return (player, context) -> player.getInventory().countItem(item) >= count;
    }

    private static Milestone.Condition meter(Emotion emotion, int value) {
        return (player, context) -> SpectrumMeters.get(player, emotion) >= value;
    }

    private static Milestone.Condition outside(boolean nether) {
        return (player, context) -> nether ? player.level().dimension() == Level.NETHER : player.level().dimension() != Level.OVERWORLD;
    }

    private static Milestone.Condition and(Milestone.Condition first, Milestone.Condition second) {
        return (player, context) -> first.test(player, context) && second.test(player, context);
    }

    private static Milestone.Condition pets() {
        return (player, context) -> !player.level().getEntitiesOfClass(TamableAnimal.class, new AABB(player.blockPosition()).inflate(PET_RANGE),
                pet -> pet.isOwnedBy(player)).isEmpty();
    }

    private static Milestone.Condition petCombat() {
        return (player, context) -> CorpsCombat.recentlyFought(player) && pets().test(player, context);
    }

    private static Milestone.Condition teamOf(int size) {
        return (player, context) -> LanternTeams.team(player)
                .map(team -> LanternTeams.members(player.level().getServer(), team).size() >= size).orElse(false);
    }

    private static Milestone.Condition hunted(boolean playersOnly) {
        return (player, context) -> context.target() != null && (!playersOnly || context.target() instanceof Player);
    }

    private static Milestone register(Milestone milestone) {
        ALL.put(milestone.key(), milestone);
        return milestone;
    }

    private static void pool(LanternCorps corps, int tier, String key, int goal, Trigger trigger, Milestone.Condition condition) {
        add(new Milestone(key, corps, tier, goal, trigger, condition, Milestone.Mode.ACCUMULATE, null));
    }

    private static void pool(LanternCorps corps, int tier, String key, int goal, Trigger trigger, Milestone.Condition condition, Milestone.Mode mode) {
        add(new Milestone(key, corps, tier, goal, trigger, condition, mode, null));
    }

    private static void keep(LanternCorps corps, int tier, String key, Milestone.Condition condition) {
        pool(corps, tier, key, KEEP_SECONDS, Trigger.SECOND, condition, Milestone.Mode.STREAK);
    }

    private static void versus(LanternCorps corps, int tier, String key, int goal, Trigger trigger, Milestone.Condition condition,
                               String soloKey, int soloGoal, Trigger soloTrigger, Milestone.Condition soloCondition, Milestone.Mode soloMode) {
        register(new Milestone(soloKey, corps, tier, soloGoal, soloTrigger, soloCondition, soloMode, null));
        add(new Milestone(key, corps, tier, goal, trigger, condition, Milestone.Mode.ACCUMULATE, soloKey));
    }

    private static void versus(LanternCorps corps, int tier, String key, int goal, Trigger trigger, Milestone.Condition condition,
                               String soloKey, int soloGoal, Trigger soloTrigger, Milestone.Condition soloCondition) {
        versus(corps, tier, key, goal, trigger, condition, Milestone.Mode.ACCUMULATE, soloKey, soloGoal, soloTrigger, soloCondition);
    }

    private static void versus(LanternCorps corps, int tier, String key, int goal, Trigger trigger, Milestone.Condition condition, Milestone.Mode mode,
                               String soloKey, int soloGoal, Trigger soloTrigger, Milestone.Condition soloCondition) {
        register(new Milestone(soloKey, corps, tier, soloGoal, soloTrigger, soloCondition, Milestone.Mode.ACCUMULATE, null));
        add(new Milestone(key, corps, tier, goal, trigger, condition, mode, soloKey));
    }

    private static void add(Milestone milestone) {
        register(milestone);
        POOLS.computeIfAbsent(milestone.corps(), corps -> new HashMap<>())
                .computeIfAbsent(milestone.tier(), tier -> new ArrayList<>())
                .add(milestone);
    }

    static {
        LanternCorps green = LanternCorps.GREEN;
        pool(green, 2, "green_low_win", 1, Trigger.KILL, (player, context) -> (hostile().test(player, context) || context.target() instanceof Player) && EmotionSources.recentlyLow(player));
        pool(green, 2, "green_constructs", 50, Trigger.CONSTRUCT, always());
        pool(green, 2, "green_ring_damage", 300, Trigger.RING_DAMAGE, always());
        pool(green, 2, "green_flight", 5000, Trigger.FLY, always());
        pool(green, 2, "green_hostiles", 40, Trigger.KILL, ringHostile());
        pool(green, 3, "green_outclass", 1, Trigger.KILL, (player, context) -> context.target() != null && OUTCLASSING.contains(context.target().getType()));
        pool(green, 3, "green_otherworld", 600, Trigger.SECOND, outside(false));
        pool(green, 3, "green_imagination", 25, Trigger.CONSTRUCT, construct("sculpt", "giant_fist"));
        versus(green, 3, "green_duels", 2, Trigger.KILL, bearer(),
                "green_raiders", 15, Trigger.KILL, (player, context) -> context.target() != null && context.target().getType().builtInRegistryHolder().is(EntityTypeTags.RAIDERS));
        pool(green, 3, "green_endure", 60, Trigger.SECOND, below(3.0F));
        pool(green, 4, "green_boss", 1, Trigger.KILL, type(EntityTypes.WITHER, EntityTypes.WARDEN));
        pool(green, 4, "green_dragon", 1, Trigger.KILL, type(EntityTypes.ENDER_DRAGON));
        pool(green, 4, "green_will", 1, Trigger.SECOND, meter(Emotion.WILL, Emotion.MAX));
        versus(green, 4, "green_last_stand", 5, Trigger.KILL, and(bearer(), belowFraction(0.5F)),
                "green_last_stand_solo", 25, Trigger.KILL, and(hostile(), belowFraction(0.5F)));
        pool(green, 4, "green_elders", 3, Trigger.KILL, type(EntityTypes.ELDER_GUARDIAN));

        LanternCorps yellow = LanternCorps.YELLOW;
        pool(yellow, 2, "yellow_fear", 25, Trigger.FEAR, always());
        pool(yellow, 2, "yellow_stalker", 15, Trigger.KILL, (player, context) -> player.isShiftKeyDown() || player.isInvisible());
        pool(yellow, 2, "yellow_nightfall", 30, Trigger.KILL, (player, context) -> hostile().test(player, context) && player.level().isDarkOutside());
        pool(yellow, 2, "yellow_arsenal", 30, Trigger.CONSTRUCT, construct("swarm_missiles", "energy_whip"));
        pool(yellow, 2, "yellow_ring_damage", 400, Trigger.RING_DAMAGE, always());
        pool(yellow, 3, "yellow_feared_kills", 10, Trigger.KILL, (player, context) -> context.feared());
        pool(yellow, 3, "yellow_scatter", 20, Trigger.FEAR, (player, context) -> context.target() instanceof Animal);
        versus(yellow, 3, "yellow_terror", 3, Trigger.FEARED_DEATH, always(),
                "yellow_terror_solo", 5, Trigger.KILL, (player, context) -> context.feared() && type(EntityTypes.ENDERMAN, EntityTypes.PIGLIN, EntityTypes.ZOMBIFIED_PIGLIN).test(player, context));
        pool(yellow, 3, "yellow_battery", 10, Trigger.RECHARGE, detail("battery"));
        pool(yellow, 3, "yellow_creepers", 10, Trigger.KILL, type(EntityTypes.CREEPER));
        versus(yellow, 4, "yellow_dread", 3, Trigger.FEARED_DEATH, always(),
                "yellow_dread_solo", 1, Trigger.KILL, (player, context) -> context.feared() && type(EntityTypes.WARDEN).test(player, context));
        pool(yellow, 4, "yellow_wither", 1, Trigger.KILL, type(EntityTypes.WITHER));
        pool(yellow, 4, "yellow_fear_meter", 1, Trigger.SECOND, meter(Emotion.FEAR, Emotion.MAX));
        versus(yellow, 4, "yellow_seek", 1, Trigger.SEEK, always(),
                "yellow_seek_solo", 30, Trigger.RECHARGE, detail("battery"));
        pool(yellow, 4, "yellow_horde", 100, Trigger.KILL, ringHostile());

        LanternCorps red = LanternCorps.RED;
        pool(red, 2, "red_burst", 10, Trigger.STREAK, always(), Milestone.Mode.REACH);
        pool(red, 2, "red_ring_damage", 500, Trigger.RING_DAMAGE, always());
        pool(red, 2, "red_beasts", 25, Trigger.KILL, (player, context) -> context.target() instanceof Animal || context.target() instanceof NeutralMob);
        pool(red, 2, "red_desperate", 10, Trigger.KILL, and(hostile(), belowFraction(0.5F)));
        pool(red, 2, "red_burning", 15, Trigger.KILL, (player, context) -> context.target() != null && context.target().isOnFire());
        versus(red, 3, "red_duels", 3, Trigger.KILL, bearer(),
                "red_ravagers", 3, Trigger.KILL, type(EntityTypes.RAVAGER));
        pool(red, 3, "red_plasma", 20, Trigger.CONSTRUCT, construct("plasma_burst"));
        pool(red, 3, "red_inferno", 50, Trigger.KILL, (player, context) -> player.level().dimension() == Level.NETHER && hostile().test(player, context));
        pool(red, 3, "red_piglins", 30, Trigger.KILL, type(EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.HOGLIN));
        pool(red, 3, "red_frenzy", 20, Trigger.STREAK, always(), Milestone.Mode.REACH);
        versus(red, 4, "red_annihilate", 1, Trigger.PLASMA_KILL, (player, context) -> context.target() instanceof Player,
                "red_annihilate_solo", 1, Trigger.KILL, type(EntityTypes.WARDEN));
        pool(red, 4, "red_rage", 1, Trigger.SECOND, meter(Emotion.RAGE, Emotion.MAX));
        pool(red, 4, "red_massacre", 200, Trigger.KILL, ringHostile());
        pool(red, 4, "red_wither", 1, Trigger.KILL, type(EntityTypes.WITHER));
        pool(red, 4, "red_brink", 5, Trigger.KILL, and(hostile(), below(2.0F)));

        LanternCorps orange = LanternCorps.ORANGE;
        keep(orange, 2, "orange_hoard", holding(Items.DIAMOND, 32));
        pool(orange, 2, "orange_diamonds", 30, Trigger.MINE, detail("diamond"));
        pool(orange, 2, "orange_emeralds", 20, Trigger.MINE, detail("emerald"));
        keep(orange, 2, "orange_gold", holding(Items.GOLD_INGOT, 64));
        pool(orange, 2, "orange_probe", 15, Trigger.CONSTRUCT, construct("ore_probe"));
        versus(orange, 3, "orange_steal", 2000, Trigger.STEAL, detail("lantern"),
                "orange_steal_solo", 3000, Trigger.STEAL, always());
        pool(orange, 3, "orange_debris", 16, Trigger.MINE, detail("debris"));
        pool(orange, 3, "orange_lumber", 25, Trigger.CONSTRUCT, construct("lumberjack"));
        keep(orange, 3, "orange_netherite", holding(Items.NETHERITE_INGOT, 4));
        pool(orange, 3, "orange_trades", 25, Trigger.TRADE, always());
        versus(orange, 4, "orange_ring_kill", 1, Trigger.KILL, bearer(),
                "orange_vault", KEEP_SECONDS, Trigger.SECOND, holding(Items.DIAMOND_BLOCK, 16), Milestone.Mode.STREAK);
        pool(orange, 4, "orange_avarice", 1, Trigger.SECOND, meter(Emotion.AVARICE, Emotion.MAX));
        keep(orange, 4, "orange_ingots", holding(Items.NETHERITE_INGOT, 8));
        keep(orange, 4, "orange_emerald_vault", holding(Items.EMERALD_BLOCK, 32));
        keep(orange, 4, "orange_elytra", holding(Items.ELYTRA, 1));

        LanternCorps blue = LanternCorps.BLUE;
        pool(blue, 2, "blue_heal", 200, Trigger.HEAL, always());
        pool(blue, 2, "blue_self_heal", 100, Trigger.SELF_HEAL, always());
        pool(blue, 2, "blue_gardener", 100, Trigger.PLANT, always());
        pool(blue, 2, "blue_trades", 30, Trigger.TRADE, always());
        pool(blue, 2, "blue_lights", 20, Trigger.CONSTRUCT, construct("light_orb"));
        pool(blue, 3, "blue_cure", 3, Trigger.CURE, always());
        versus(blue, 3, "blue_second_chance", 1, Trigger.HOPE_GRANT, always(),
                "blue_raid", 1, Trigger.SECOND, (player, context) -> player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
        pool(blue, 3, "blue_shield", 300, Trigger.SHIELD, always());
        pool(blue, 3, "blue_walls", 40, Trigger.CONSTRUCT, construct("wall"));
        pool(blue, 3, "blue_protector", 15, Trigger.RESCUE, hunted(false));
        pool(blue, 4, "blue_hope", 1, Trigger.SECOND, meter(Emotion.HOPE, Emotion.MAX));
        pool(blue, 4, "blue_hero", 1, Trigger.SECOND, (player, context) -> player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
        pool(blue, 4, "blue_mender", 1000, Trigger.HEAL, always());
        pool(blue, 4, "blue_bulwark", 1000, Trigger.SHIELD, always());
        versus(blue, 4, "blue_corps", 1, Trigger.SECOND, teamOf(4),
                "blue_cures", 10, Trigger.CURE, always());

        LanternCorps indigo = LanternCorps.INDIGO;
        versus(indigo, 2, "indigo_mimic", 4, Trigger.MIMIC, always(), Milestone.Mode.DISTINCT,
                "indigo_tractor", 120, Trigger.TRACTOR, always());
        versus(indigo, 2, "indigo_gifts", 10, Trigger.GIFT, always(),
                "indigo_shepherd", 10, Trigger.BREED, always());
        pool(indigo, 2, "indigo_rescue", 10, Trigger.RESCUE, hunted(false));
        pool(indigo, 2, "indigo_scan", 20, Trigger.SCAN, always());
        pool(indigo, 2, "indigo_flight", 3000, Trigger.FLY, always());
        versus(indigo, 3, "indigo_save", 5, Trigger.RESCUE, (player, context) -> context.target() instanceof Player hunted && hunted.getHealth() < hunted.getMaxHealth() * 0.3F,
                "indigo_save_solo", 10, Trigger.RESCUE, (player, context) -> context.target() instanceof AbstractVillager || context.target() instanceof TamableAnimal);
        pool(indigo, 3, "indigo_tame", 5, Trigger.TAME, always());
        pool(indigo, 3, "indigo_cure", 2, Trigger.CURE, always());
        pool(indigo, 3, "indigo_shield", 200, Trigger.SHIELD, always());
        pool(indigo, 3, "indigo_mercy", 600, Trigger.SECOND, (player, context) -> EmotionSources.peaceful(player));
        versus(indigo, 4, "indigo_convert", 1, Trigger.CONVERT, always(),
                "indigo_guardian_angel", 25, Trigger.RESCUE, hunted(false));
        pool(indigo, 4, "indigo_compassion", 1, Trigger.SECOND, meter(Emotion.COMPASSION, Emotion.MAX));
        versus(indigo, 4, "indigo_spectrum", 6, Trigger.MIMIC, always(), Milestone.Mode.DISTINCT,
                "indigo_menagerie", 10, Trigger.TAME, always());
        pool(indigo, 4, "indigo_hero", 1, Trigger.SECOND, (player, context) -> player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
        pool(indigo, 4, "indigo_pacifist", 1800, Trigger.SECOND, (player, context) -> EmotionSources.peaceful(player));

        LanternCorps sapphire = LanternCorps.STAR_SAPPHIRE;
        pool(sapphire, 2, "sapphire_encase", 10, Trigger.ENCASE, always());
        pool(sapphire, 2, "sapphire_breed", 20, Trigger.BREED, always());
        versus(sapphire, 2, "sapphire_gifts", 20, Trigger.GIFT, always(),
                "sapphire_tame", 3, Trigger.TAME, always());
        pool(sapphire, 2, "sapphire_crystals", 25, Trigger.CONSTRUCT, construct("crystal_prison"));
        pool(sapphire, 2, "sapphire_affection", 1, Trigger.SECOND, meter(Emotion.LOVE, 500));
        versus(sapphire, 3, "sapphire_bond", 600, Trigger.SECOND, (player, context) -> CorpsCombat.inBondedCombat(player),
                "sapphire_pack", 300, Trigger.SECOND, petCombat());
        pool(sapphire, 3, "sapphire_menagerie", 10, Trigger.TAME, always());
        pool(sapphire, 3, "sapphire_battery", 10, Trigger.RECHARGE, detail("battery"));
        pool(sapphire, 3, "sapphire_protector", 15, Trigger.RESCUE, hunted(false));
        pool(sapphire, 3, "sapphire_prisons", 40, Trigger.ENCASE, always());
        versus(sapphire, 4, "sapphire_guard", 1, Trigger.GUARD, always(),
                "sapphire_nursery", 100, Trigger.BREED, always());
        pool(sapphire, 4, "sapphire_love", 1, Trigger.SECOND, meter(Emotion.LOVE, Emotion.MAX));
        versus(sapphire, 4, "sapphire_seek", 1, Trigger.SEEK, always(),
                "sapphire_seek_solo", 30, Trigger.RECHARGE, detail("battery"));
        pool(sapphire, 4, "sapphire_dragon", 1, Trigger.KILL, type(EntityTypes.ENDER_DRAGON));
        versus(sapphire, 4, "sapphire_union", 1, Trigger.SECOND, teamOf(3),
                "sapphire_family", 20, Trigger.TAME, always());
    }

    public static Optional<Milestone> get(String key) {
        return Optional.ofNullable(ALL.get(key));
    }

    public static List<Milestone> pool(LanternCorps corps, int tier) {
        return POOLS.getOrDefault(corps, Map.of()).getOrDefault(tier, List.of());
    }

    public static boolean has(LanternCorps corps) {
        return POOLS.containsKey(corps);
    }

    public static List<String> roll(LanternCorps corps, RandomSource random, boolean multiplayer) {
        List<String> keys = new ArrayList<>();
        for (int tier = FIRST_TIER; tier <= LAST_TIER; tier++) {
            List<Milestone> options = pool(corps, tier);
            if (options.isEmpty()) continue;
            Milestone chosen = options.get(random.nextInt(options.size()));
            keys.add(!multiplayer && chosen.singleplayer() != null ? chosen.singleplayer() : chosen.key());
        }
        return keys;
    }

    public static Iterable<String> keys() {
        return ALL.keySet();
    }

    private Milestones() {}
}
