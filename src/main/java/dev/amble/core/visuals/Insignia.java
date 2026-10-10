package dev.amble.core.visuals;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.InsigniaS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPowerRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Insignia {
    private static final Map<LanternCorps, Identifier> TEXTURES = new EnumMap<>(Map.of(
            LanternCorps.GREEN, texture("will"),
            LanternCorps.YELLOW, texture("fear"),
            LanternCorps.RED, texture("rage"),
            LanternCorps.ORANGE, texture("greed"),
            LanternCorps.BLUE, texture("hope"),
            LanternCorps.INDIGO, texture("compassion"),
            LanternCorps.STAR_SAPPHIRE, texture("love")));

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    private static Identifier texture(String name) {
        return BrightestDay.id("textures/insignia/" + name + ".png");
    }

    public static @Nullable Identifier texture(LanternCorps corps) {
        return TEXTURES.get(corps);
    }

    public static boolean has(LanternCorps corps) {
        return TEXTURES.containsKey(corps);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Insignia::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.player.getUUID()));
        EntityTrackingEvents.START_TRACKING.register((entity, watcher) -> {
            if (ACTIVE.containsKey(entity.getUUID())) ServerPlayNetworking.send(watcher, new InsigniaS2CPayload(entity.getId(), true));
        });
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static long latestCreatedAt(UUID owner) {
        return ACTIVE.getOrDefault(owner, Long.MIN_VALUE);
    }

    public static boolean available(Player player) {
        return BrightestDayAttachments.has(player, RingPowerRegistry.INSIGNIA) && PowerRingItem.getWornCorps(player).map(Insignia::has).orElse(false);
    }

    public static void request(ServerPlayer player) {
        if (!ACTIVE.containsKey(player.getUUID())) {
            if (!available(player)) return;
            if (!PowerRingItem.hasCharge(player)) {
                player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
                return;
            }
        }
        toggle(player);
    }

    public static void toggle(ServerPlayer player) {
        if (ACTIVE.containsKey(player.getUUID())) {
            hide(player);
            return;
        }
        ACTIVE.put(player.getUUID(), player.level().getGameTime());
        broadcast(player, true);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    public static void hide(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) == null) return;
        broadcast(player, false);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    private static void broadcast(ServerPlayer player, boolean active) {
        InsigniaS2CPayload payload = new InsigniaS2CPayload(player.getId(), active);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        int interval = Math.max(1, BrightestDayConfig.get().insigniaDrainSeconds) * 20;
        for (UUID id : Map.copyOf(ACTIVE).keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                ACTIVE.remove(id);
                continue;
            }
            LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
            if (corps == null || !has(corps) || !player.isAlive()) {
                hide(player);
                continue;
            }
            if ((server.getTickCount() - ACTIVE.get(id)) % interval == 0 && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, 1)) hide(player);
        }
    }

    private Insignia() {}
}
