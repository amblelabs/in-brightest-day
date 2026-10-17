package dev.amble.core.rings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingLoyalty;
import dev.amble.core.progression.Emotion;
import dev.amble.core.progression.RingOffers;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.progression.SpectrumMeters;
import dev.amble.core.progression.EmotionGates;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WhiteFusion {
    public static final List<LanternCorps> SPECTRUM = List.of(LanternCorps.GREEN, LanternCorps.YELLOW, LanternCorps.RED, LanternCorps.ORANGE,
            LanternCorps.BLUE, LanternCorps.INDIGO, LanternCorps.STAR_SAPPHIRE);
    private static final double CLUSTER_RADIUS = 2.5;
    private static final int FUSE_TICKS = 60;
    private static final double RISE = 1.6;
    private static final double ORBIT = 0.9;
    private static final int BASE_MINUTES = 5;
    private static final int BONUS_MINUTES = 25;
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long NOTICE_COOLDOWN = 100L;

    public record Return(UUID bearer, ItemStack ring) {
        public static final Codec<Return> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("bearer").forGetter(Return::bearer),
                ItemStack.CODEC.fieldOf("ring").forGetter(Return::ring)
        ).apply(instance, Return::new));
    }

    public record Fused(long expires, List<Return> returns) {
        public static final Codec<Fused> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("expires").forGetter(Fused::expires),
                Return.CODEC.listOf().optionalFieldOf("returns", List.of()).forGetter(Fused::returns)
        ).apply(instance, Fused::new));
    }

    public static final AttachmentType<Map<UUID, Fused>> FUSIONS =
            AttachmentRegistry.<Map<UUID, Fused>>builder()
                    .initializer(Map::of)
                    .persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Fused.CODEC))
                    .buildAndRegister(BrightestDay.id("white_fusions"));

    public static final AttachmentType<Map<UUID, List<ItemStack>>> PENDING_RETURNS =
            AttachmentRegistry.<Map<UUID, List<ItemStack>>>builder()
                    .initializer(Map::of)
                    .persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf()))
                    .buildAndRegister(BrightestDay.id("white_fusion_returns"));

    private static final Map<RingPower<?>, LanternCorps> SIGNATURES = Map.of(
            RingPowerRegistry.SCULPT, LanternCorps.GREEN,
            RingPowerRegistry.ENERGY_WHIP, LanternCorps.YELLOW,
            RingPowerRegistry.PLASMA_BURST, LanternCorps.RED,
            RingPowerRegistry.ORE_PROBE, LanternCorps.ORANGE,
            RingPowerRegistry.HEAL_BEAM, LanternCorps.BLUE,
            RingPowerRegistry.CRYSTAL_PRISON, LanternCorps.STAR_SAPPHIRE);

    private static final class Fusing {
        final UUID fuser;
        final ServerLevel level;
        final Vec3 center;
        final List<ItemEntity> items;
        int age;

        Fusing(UUID fuser, ServerLevel level, Vec3 center, List<ItemEntity> items) {
            this.fuser = fuser;
            this.level = level;
            this.center = center;
            this.items = items;
        }
    }

    private static final List<ItemEntity> TRACKED = new ArrayList<>();
    private static final List<Fusing> FUSING = new ArrayList<>();
    private static final Map<UUID, Long> NOTICED = new HashMap<>();

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof PowerRingItem) TRACKED.add(item);
        });
        ServerTickEvents.END_SERVER_TICK.register(WhiteFusion::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> deliverPending(server, handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Fusing fusing : FUSING) {
                fusing.items.forEach(item -> {
                    if (!item.isRemoved()) item.setNoGravity(false);
                });
            }
            FUSING.clear();
            TRACKED.clear();
            NOTICED.clear();
        });
    }

    public static boolean valid(MinecraftServer server, ItemStack stack) {
        BrightestDayComponents.FusedWhite data = stack.get(BrightestDayComponents.FUSED_WHITE);
        if (data == null || System.currentTimeMillis() >= data.expires()) return false;
        return server.overworld().getAttachedOrElse(FUSIONS, Map.of()).containsKey(data.id());
    }

    public static boolean fused(Player player) {
        return PowerRingItem.getWornRing(player).has(BrightestDayComponents.FUSED_WHITE);
    }

    public static @Nullable LanternCorps alignment(Player player) {
        BrightestDayComponents.FusedWhite data = PowerRingItem.getWornRing(player).get(BrightestDayComponents.FUSED_WHITE);
        return data == null ? null : data.alignment();
    }

    public static @Nullable LanternCorps signature(RingPower<?> power) {
        return SIGNATURES.get(power);
    }

    public static List<RingPower<?>> arsenal() {
        List<RingPower<?>> powers = new ArrayList<>();
        for (RingPower<?> power : RingPowerRegistry.all()) {
            boolean white = power.corps().contains(LanternCorps.WHITE);
            if (power instanceof ConstructRingPower ? white || SIGNATURES.containsKey(power) : power.isAvailableTo(LanternCorps.WHITE)) powers.add(power);
        }
        return List.copyOf(powers);
    }

    private static LanternCorps strongest(ServerPlayer player) {
        LanternCorps best = LanternCorps.GREEN;
        int bestRank = -1;
        int bestMeter = -1;
        for (LanternCorps corps : SPECTRUM) {
            int rank = RingRanks.rank(player, corps);
            int meter = Emotion.of(corps).map(emotion -> SpectrumMeters.get(player, emotion)).orElse(0);
            if (rank > bestRank || rank == bestRank && meter > bestMeter) {
                best = corps;
                bestRank = rank;
                bestMeter = meter;
            }
        }
        return best;
    }

    private static List<Emotion> lacking(ServerPlayer player) {
        List<Emotion> lacking = new ArrayList<>();
        for (Emotion emotion : Emotion.values()) {
            if (SPECTRUM.contains(emotion.corps()) && SpectrumMeters.get(player, emotion) < EmotionGates.BALANCE_FLOOR) lacking.add(emotion);
        }
        return lacking;
    }

    private static int minutes(ServerPlayer player) {
        float total = 0.0F;
        int counted = 0;
        for (Emotion emotion : Emotion.values()) {
            if (!SPECTRUM.contains(emotion.corps())) continue;
            total += Mth.clamp((SpectrumMeters.get(player, emotion) - Emotion.GATE) / (float) (Emotion.MAX - Emotion.GATE), 0.0F, 1.0F);
            counted++;
        }
        return BASE_MINUTES + Math.round(BONUS_MINUTES * total / Math.max(1, counted));
    }

    private static void tick(MinecraftServer server) {
        TRACKED.removeIf(item -> item.isRemoved() || !(item.getItem().getItem() instanceof PowerRingItem));
        for (Fusing fusing : List.copyOf(FUSING)) advance(server, fusing);

        if (server.getTickCount() % 10 == 0) {
            for (ItemEntity item : List.copyOf(TRACKED)) {
                if (ConstructRings.temporary(item.getItem()) && !ConstructRings.liveOnGround(server, item.getItem())) item.discard();
            }
            seekClusters(server);
        }
        if (server.getTickCount() % 20 == 0) expire(server);
        if (server.getTickCount() % 1200 == 0) {
            long now = server.overworld().getGameTime();
            NOTICED.values().removeIf(noticed -> now - noticed >= NOTICE_COOLDOWN);
        }
    }

    private static boolean fusingItem(ItemEntity item) {
        return FUSING.stream().anyMatch(fusing -> fusing.items.contains(item));
    }

    private static void seekClusters(MinecraftServer server) {
        Set<ItemEntity> claimed = new HashSet<>();
        for (ItemEntity anchor : List.copyOf(TRACKED)) {
            if (anchor.isRemoved() || claimed.contains(anchor) || fusingItem(anchor) || !(anchor.getOwner() instanceof ServerPlayer fuser)) continue;
            Map<LanternCorps, ItemEntity> found = new EnumMap<>(LanternCorps.class);
            for (ItemEntity other : TRACKED) {
                if (other.isRemoved() || other.level() != anchor.level() || claimed.contains(other) || fusingItem(other)) continue;
                if (other.distanceTo(anchor) > CLUSTER_RADIUS || !(other.getOwner() instanceof ServerPlayer owner) || owner != fuser) continue;
                if (other.getItem().has(BrightestDayComponents.FUSED_WHITE)) continue;
                LanternCorps corps = PowerRingItem.getCorps(other.getItem()).orElse(null);
                if (corps != null && SPECTRUM.contains(corps)) found.putIfAbsent(corps, other);
            }
            if (found.size() < SPECTRUM.size()) continue;

            List<Emotion> lacking = lacking(fuser);
            if (lacking.isEmpty() && !EmotionGates.balanced(fuser)) {
                long now = server.overworld().getGameTime();
                if (now - NOTICED.getOrDefault(fuser.getUUID(), Long.MIN_VALUE / 2) >= NOTICE_COOLDOWN) {
                    NOTICED.put(fuser.getUUID(), now);
                    fuser.sendOverlayMessage(Component.translatable("message.brightestday.white_fusion.unbalanced", EmotionGates.spread(fuser), EmotionGates.BALANCE_SPREAD).withColor(LanternCorps.WHITE.color()));
                }
                continue;
            }
            if (!lacking.isEmpty()) {
                long now = server.overworld().getGameTime();
                if (now - NOTICED.getOrDefault(fuser.getUUID(), Long.MIN_VALUE / 2) >= NOTICE_COOLDOWN) {
                    NOTICED.put(fuser.getUUID(), now);
                    Component missing = Component.empty();
                    for (int i = 0; i < lacking.size(); i++) {
                        if (i > 0) missing = missing.copy().append(", ");
                        missing = missing.copy().append(Component.translatable("emotion.brightestday." + lacking.get(i).getSerializedName()));
                    }
                    fuser.sendOverlayMessage(Component.translatable("message.brightestday.white_fusion.lacking", missing).withColor(LanternCorps.WHITE.color()));
                }
                continue;
            }

            List<ItemEntity> items = new ArrayList<>(found.values());
            claimed.addAll(items);
            Vec3 center = Vec3.ZERO;
            for (ItemEntity item : items) center = center.add(item.position());
            center = center.scale(1.0 / items.size());
            for (ItemEntity item : items) {
                item.setNoGravity(true);
                item.setPickUpDelay(FUSE_TICKS + 20);
                item.setUnlimitedLifetime();
            }
            ServerLevel level = (ServerLevel) anchor.level();
            FUSING.add(new Fusing(fuser.getUUID(), level, center, items));
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 0.7F);
        }
    }

    private static void advance(MinecraftServer server, Fusing fusing) {
        fusing.age++;
        if (fusing.items.stream().anyMatch(ItemEntity::isRemoved)) {
            fusing.items.forEach(item -> {
                if (!item.isRemoved()) item.setNoGravity(false);
            });
            FUSING.remove(fusing);
            return;
        }
        float progress = fusing.age / (float) FUSE_TICKS;
        double radius = ORBIT * (1.0 - progress * 0.85);
        double height = RISE * Math.min(1.0, progress * 1.6);
        for (int i = 0; i < fusing.items.size(); i++) {
            ItemEntity item = fusing.items.get(i);
            double angle = i * Mth.TWO_PI / fusing.items.size() + fusing.age * 0.12 * (1.0 + progress * 2.0);
            Vec3 target = fusing.center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            item.setDeltaMovement(target.subtract(item.position()).scale(0.4));
            LanternCorps corps = PowerRingItem.getCorps(item.getItem()).orElse(LanternCorps.WHITE);
            if (fusing.age % 2 == 0) fusing.level.sendParticles(new DustParticleOptions(corps.color(), 1.2F), item.getX(), item.getY() + 0.2, item.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
        }
        if (fusing.age < FUSE_TICKS) return;

        FUSING.remove(fusing);
        ServerPlayer fuser = server.getPlayerList().getPlayer(fusing.fuser);
        if (fuser == null || !lacking(fuser).isEmpty()) {
            fusing.items.forEach(item -> item.setNoGravity(false));
            return;
        }
        complete(server, fuser, fusing);
    }

    private static void complete(MinecraftServer server, ServerPlayer fuser, Fusing fusing) {
        List<Return> returns = new ArrayList<>();
        for (ItemEntity item : fusing.items) {
            ItemStack stack = item.getItem();
            if (ConstructRings.isConstruct(stack)) {
                ConstructRings.consumed(stack);
            } else {
                BrightestDayComponents.Sworn sworn = stack.get(BrightestDayComponents.SWORN_TO);
                returns.add(new Return(sworn != null ? sworn.owner() : fuser.getUUID(), stack.copy()));
            }
            item.discard();
        }

        int minutes = minutes(fuser);
        UUID id = UUID.randomUUID();
        long expires = System.currentTimeMillis() + minutes * MINUTE_MILLIS;
        Map<UUID, Fused> fusions = new HashMap<>(server.overworld().getAttachedOrElse(FUSIONS, Map.of()));
        fusions.put(id, new Fused(expires, List.copyOf(returns)));
        server.overworld().setAttached(FUSIONS, Map.copyOf(fusions));

        ItemStack white = RingOffers.forge(LanternCorps.WHITE, fuser);
        white.set(BrightestDayComponents.FUSED_WHITE, new BrightestDayComponents.FusedWhite(id, expires, strongest(fuser)));
        if (BrightestDayAttachments.getRing(fuser).isEmpty()) BrightestDayAttachments.setRing(fuser, white);
        else if (!fuser.addItem(white)) fuser.drop(white, false, Prediction.SERVER_ONLY);

        Vec3 at = fusing.center.add(0.0, RISE, 0.0);
        fusing.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 80, 0.4, 0.4, 0.4, 0.2);
        fusing.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 1.6F);
        fuser.sendSystemMessage(Component.translatable("message.brightestday.white_fusion.formed", minutes).withStyle(ChatFormatting.BOLD).withColor(LanternCorps.WHITE.color()));
    }

    private static void expire(MinecraftServer server) {
        Map<UUID, Fused> fusions = server.overworld().getAttachedOrElse(FUSIONS, Map.of());
        if (fusions.isEmpty()) return;
        long now = System.currentTimeMillis();
        Map<UUID, Fused> remaining = new HashMap<>(fusions);
        boolean changed = false;
        for (Map.Entry<UUID, Fused> entry : fusions.entrySet()) {
            if (now < entry.getValue().expires()) continue;
            remaining.remove(entry.getKey());
            changed = true;
            for (Return ring : entry.getValue().returns()) send(server, ring);
        }
        if (changed) server.overworld().setAttached(FUSIONS, Map.copyOf(remaining));
    }

    private static void send(MinecraftServer server, Return ring) {
        ServerPlayer bearer = server.getPlayerList().getPlayer(ring.bearer());
        if (bearer != null) {
            RingLoyalty.sendHome(bearer, ring.ring().copy(), bearer.level(), bearer.getEyePosition().add(0.0, 12.0, 0.0));
            return;
        }
        Map<UUID, List<ItemStack>> pending = new HashMap<>(server.overworld().getAttachedOrElse(PENDING_RETURNS, Map.of()));
        List<ItemStack> rings = new ArrayList<>(pending.getOrDefault(ring.bearer(), List.of()));
        rings.add(ring.ring().copy());
        pending.put(ring.bearer(), List.copyOf(rings));
        server.overworld().setAttached(PENDING_RETURNS, Map.copyOf(pending));
    }

    private static void deliverPending(MinecraftServer server, ServerPlayer player) {
        Map<UUID, List<ItemStack>> pending = server.overworld().getAttachedOrElse(PENDING_RETURNS, Map.of());
        List<ItemStack> rings = pending.get(player.getUUID());
        if (rings == null) return;
        Map<UUID, List<ItemStack>> remaining = new HashMap<>(pending);
        remaining.remove(player.getUUID());
        server.overworld().setAttached(PENDING_RETURNS, Map.copyOf(remaining));
        for (ItemStack ring : rings) {
            ItemStack copy = ring.copy();
            if (!player.addItem(copy)) player.drop(copy, false, Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.translatable("message.brightestday.white_fusion.returned").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
    }

    private WhiteFusion() {}
}
