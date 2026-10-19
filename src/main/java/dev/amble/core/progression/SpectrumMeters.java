package dev.amble.core.progression;

import dev.amble.config.BrightestDayConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.Map;

public final class SpectrumMeters {
    private static final int TIER = 250;
    private static final long DAY = 24000L;
    private static final int DECAY = 10;
    private static final double BATTERY_RANGE = 16.0;

    public record Meters(Map<Emotion, Integer> values, Map<Emotion, Long> fed) {
        public static final Meters EMPTY = new Meters(Map.of(), Map.of());

        public static final Codec<Meters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Emotion.CODEC, Codec.INT).optionalFieldOf("values", Map.of()).forGetter(Meters::values),
                Codec.unboundedMap(Emotion.CODEC, Codec.LONG).optionalFieldOf("fed", Map.of()).forGetter(Meters::fed)
        ).apply(instance, Meters::new));

        public int get(Emotion emotion) {
            return this.values.getOrDefault(emotion, 0);
        }

        Meters with(Emotion emotion, int value, long fedAt) {
            Map<Emotion, Integer> values = new EnumMap<>(Emotion.class);
            values.putAll(this.values);
            values.put(emotion, value);
            Map<Emotion, Long> fed = new EnumMap<>(Emotion.class);
            fed.putAll(this.fed);
            if (fedAt >= 0) fed.put(emotion, fedAt);
            return new Meters(Map.copyOf(values), Map.copyOf(fed));
        }
    }

    public static final AttachmentType<Meters> METERS =
            AttachmentRegistry.<Meters>builder()
                    .initializer(() -> Meters.EMPTY)
                    .persistent(Meters.CODEC)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.fromCodec(Meters.CODEC), AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(BrightestDay.id("spectrum_meters"));

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SpectrumMeters::tick);
    }

    public static Meters get(Player player) {
        return player.getAttachedOrElse(METERS, Meters.EMPTY);
    }

    public static int get(Player player, Emotion emotion) {
        return get(player).get(emotion);
    }

    public static boolean passes(Player player, Emotion emotion) {
        return get(player, emotion) >= Emotion.GATE;
    }

    public static void add(ServerPlayer player, Emotion emotion, int amount) {
        if (amount <= 0) return;
        Meters meters = get(player);
        int before = meters.get(emotion);
        int after = Mth.clamp(before + amount, 0, Emotion.MAX);
        player.setAttached(METERS, meters.with(emotion, after, player.level().getGameTime()));
        if (after / TIER > before / TIER) {
            player.sendOverlayMessage(Component.translatable("progression.brightestday." + emotion.getSerializedName() + "." + (after / TIER))
                    .withColor(emotion.corps().color()));
        }
        EmotionTriggers.onMeterChanged(player, emotion, before, after);
    }

    public static void set(ServerPlayer player, Emotion emotion, int value) {
        Meters meters = get(player);
        int before = meters.get(emotion);
        int after = Mth.clamp(value, 0, Emotion.MAX);
        player.setAttached(METERS, meters.with(emotion, after, player.level().getGameTime()));
        EmotionTriggers.onMeterChanged(player, emotion, before, after);
    }

    public static long lastFed(Player player, Emotion emotion) {
        return get(player).fed().getOrDefault(emotion, 0L);
    }

    public static void drain(ServerPlayer player, Emotion emotion, int amount) {
        Meters meters = get(player);
        int before = meters.get(emotion);
        int after = Math.max(0, before - amount);
        if (after == before) return;
        player.setAttached(METERS, meters.with(emotion, after, lastFed(player, emotion)));
        EmotionTriggers.onMeterChanged(player, emotion, before, after);
    }

    public static int idleFloor(int value) {
        return value >= Emotion.GATE ? Emotion.GATE : 0;
    }

    public static boolean atBattery(ServerPlayer player) {
        LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
        if (corps == null || player.level().dimension() != Level.OVERWORLD) return false;
        return WorldProgress.get(player.level().getServer()).batteries().stream()
                .anyMatch(battery -> battery.corps() == corps && battery.pos().closerToCenterThan(player.position(), BATTERY_RANGE));
    }

    public static boolean sustained(ServerPlayer player, Emotion emotion) {
        return PowerRingItem.getWornCorps(player).orElse(null) == emotion.corps();
    }

    public static void reset(ServerPlayer player) {
        player.setAttached(METERS, Meters.EMPTY);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 1200 != 0) return;
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Meters meters = get(player);
            boolean resting = atBattery(player);
            Meters decayed = meters;
            BrightestDayConfig config = BrightestDayConfig.get();
            for (Emotion emotion : Emotion.values()) {
                int value = decayed.get(emotion);
                if (emotion == Emotion.RAGE) {
                    long calm = now - decayed.fed().getOrDefault(emotion, 0L);
                    if (value > 0 && !sustained(player, emotion) && calm >= config.rageCalmSeconds * 20L) decayed = decayed.with(emotion, Math.max(0, value - config.rageDecayPerMinute), -1L);
                    continue;
                }
                if (value <= idleFloor(value) || sustained(player, emotion) || resting) continue;
                long fed = decayed.fed().getOrDefault(emotion, 0L);
                if (now - fed < DAY) continue;
                decayed = decayed.with(emotion, Math.max(idleFloor(value), value - DECAY), fed + DAY);
            }
            if (decayed != meters) player.setAttached(METERS, decayed);
        }
    }

    private SpectrumMeters() {}
}
