package dev.amble.core.flight;

import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.FlightBoostS2CPayload;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class FlightBoost {
    public static final int BURST_TICKS = 40;
    private static final int BURST_COST = 40;
    private static final int MIN_COOLDOWN = 100;
    private static final int MAX_COOLDOWN = 300;

    private static final Map<Player, Integer> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, Integer> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FlightBoost::tickServer);
    }

    public static boolean isBoosting(Player player) {
        return (player.level().isClientSide() ? CLIENT : SERVER).containsKey(player);
    }

    public static int cooldownTicks(Player player) {
        float fraction = FlightRingPower.speedLevel(player) / (float) Math.max(1, FlightRingPower.SPEED_LEVELS - 1);
        return Math.round(MIN_COOLDOWN + (MAX_COOLDOWN - MIN_COOLDOWN) * fraction);
    }

    public static void request(ServerPlayer player) {
        if (!FlightRingPower.isFlying(player) || SERVER.containsKey(player)) return;
        long now = player.level().getGameTime();
        long ready = READY_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < ready) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.boost.recharging", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, BURST_COST)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }
        int cooldown = cooldownTicks(player);
        SERVER.put(player, BURST_TICKS);
        READY_AT.put(player.getUUID(), now + BURST_TICKS + cooldown);
        ServerPlayNetworking.send(player, new FlightBoostS2CPayload(BURST_TICKS, cooldown));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    public static void burst(ServerPlayer player) {
        SERVER.put(player, BURST_TICKS);
        ServerPlayNetworking.send(player, new FlightBoostS2CPayload(BURST_TICKS, 0));
    }

    public static void startClient(Player player, int ticks) {
        CLIENT.put(player, ticks);
    }

    public static void tickClient(Player player) {
        Integer remaining = CLIENT.get(player);
        if (remaining == null) return;
        if (remaining <= 1 || !FlightRingPower.canFly(player)) CLIENT.remove(player);
        else CLIENT.put(player, remaining - 1);
    }

    private static void tickServer(MinecraftServer server) {
        synchronized (SERVER) {
            SERVER.entrySet().removeIf(entry -> entry.getValue() <= 1 || !FlightRingPower.canFly(entry.getKey()));
            SERVER.replaceAll((player, remaining) -> remaining - 1);
        }
        if (server.getTickCount() % 1200 == 0) {
            long now = server.overworld().getGameTime();
            READY_AT.values().removeIf(ready -> ready < now);
        }
    }

    private FlightBoost() {}
}
