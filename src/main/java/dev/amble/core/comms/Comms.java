package dev.amble.core.comms;

import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.networking.payloads.s2c.CommsIncomingS2CPayload;
import dev.amble.core.networking.payloads.s2c.CommsTalkingS2CPayload;
import dev.amble.core.networking.payloads.s2c.CommsTargetS2CPayload;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.team.LanternTeams;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class Comms {
    public static final String VOICE_CHAT_MOD_ID = "voicechat";
    private static final int CHECK_INTERVAL = 10;

    private static final Map<UUID, UUID> DIALED = new ConcurrentHashMap<>();
    private static final Map<UUID, List<UUID>> TRANSMITTING = new ConcurrentHashMap<>();
    private static final Set<UUID> TEAM_CHANNEL = ConcurrentHashMap.newKeySet();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Comms::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            stop(server, id);
            DIALED.remove(id);
            TEAM_CHANNEL.remove(id);
        });
        EntityTrackingEvents.START_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayer player) ServerPlayNetworking.send(watcher, new CommsTalkingS2CPayload(player.getId(), TRANSMITTING.containsKey(player.getUUID())));
        });
    }

    private static volatile Predicate<UUID> voiceReady = id -> false;

    public static void setVoiceReady(Predicate<UUID> check) {
        voiceReady = check;
    }

    public static boolean voiceReady(UUID player) {
        return available() && voiceReady.test(player);
    }

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded(VOICE_CHAT_MOD_ID);
    }

    public static List<UUID> receivers(UUID sender) {
        return TRANSMITTING.getOrDefault(sender, List.of());
    }

    public static void toggleTeam(ServerPlayer player) {
        if (!BrightestDayAttachments.has(player, RingPowerRegistry.COMMS)) return;
        boolean team = TEAM_CHANNEL.add(player.getUUID());
        if (!team) TEAM_CHANNEL.remove(player.getUUID());
        ServerPlayer dialed = dialed(player);
        ServerPlayNetworking.send(player, new CommsTargetS2CPayload(team || dialed == null ? "" : dialed.getScoreboardName(), team));
        player.sendOverlayMessage(Component.translatable(team ? "message.brightestday.comms.team_on" : "message.brightestday.comms.team_off"));
        retune(player);
    }

    private static void retune(ServerPlayer player) {
        if (!TRANSMITTING.containsKey(player.getUUID())) return;
        stop(player.level().getServer(), player.getUUID());
        start(player);
    }

    public static void cycle(ServerPlayer player, int direction) {
        if (!BrightestDayAttachments.has(player, RingPowerRegistry.COMMS)) return;
        boolean wasTeam = TEAM_CHANNEL.remove(player.getUUID());
        List<ServerPlayer> teammates = teammates(player);
        if (teammates.isEmpty()) {
            DIALED.remove(player.getUUID());
            ServerPlayNetworking.send(player, CommsTargetS2CPayload.NONE);
            return;
        }

        UUID current = DIALED.get(player.getUUID());
        int index = -1;
        for (int i = 0; i < teammates.size(); i++) {
            if (teammates.get(i).getUUID().equals(current)) index = i;
        }
        int next = index < 0 ? (direction >= 0 ? 0 : teammates.size() - 1) : Math.floorMod(index + direction, teammates.size());
        ServerPlayer target = teammates.get(next);
        DIALED.put(player.getUUID(), target.getUUID());
        ServerPlayNetworking.send(player, new CommsTargetS2CPayload(target.getScoreboardName(), false));

        List<UUID> transmitting = TRANSMITTING.get(player.getUUID());
        if (transmitting != null && (wasTeam || !transmitting.equals(List.of(target.getUUID())))) retune(player);
    }

    public static void start(ServerPlayer player) {
        if (!available() || !BrightestDayAttachments.has(player, RingPowerRegistry.COMMS) || ArmedRingPower.activeAbility(player).orElse(null) != RingPowerRegistry.COMMS) return;
        List<ServerPlayer> targets = listeners(player);
        if (targets.isEmpty() && !TEAM_CHANNEL.contains(player.getUUID())) {
            cycle(player, 1);
            targets = listeners(player);
        }
        if (targets.isEmpty()) return;
        List<UUID> ids = targets.stream().map(ServerPlayer::getUUID).toList();
        List<UUID> current = TRANSMITTING.get(player.getUUID());
        if (ids.equals(current)) return;
        if (current != null) stop(player.level().getServer(), player.getUUID());

        TRANSMITTING.put(player.getUUID(), ids);
        for (ServerPlayer target : targets) ServerPlayNetworking.send(target, new CommsIncomingS2CPayload(player.getScoreboardName(), true));
        broadcastTalking(player, true);
    }

    public static void stop(MinecraftServer server, UUID sender) {
        List<UUID> targets = TRANSMITTING.remove(sender);
        if (targets == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(sender);
        for (UUID target : targets) {
            ServerPlayer receiver = server.getPlayerList().getPlayer(target);
            if (receiver != null) ServerPlayNetworking.send(receiver, new CommsIncomingS2CPayload(player != null ? player.getScoreboardName() : "", false));
        }
        if (player != null) broadcastTalking(player, false);
    }

    private static @Nullable ServerPlayer dialed(ServerPlayer player) {
        UUID id = DIALED.get(player.getUUID());
        if (id == null) return null;
        ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(id);
        return target != null && LanternTeams.areTeammates(player, target) ? target : null;
    }

    private static List<ServerPlayer> listeners(ServerPlayer player) {
        if (TEAM_CHANNEL.contains(player.getUUID())) return teammates(player);
        ServerPlayer target = dialed(player);
        return target == null ? List.of() : List.of(target);
    }

    private static List<ServerPlayer> teammates(ServerPlayer player) {
        List<ServerPlayer> teammates = new ArrayList<>();
        LanternTeams.team(player).ifPresent(team -> {
            for (ServerPlayer member : LanternTeams.members(player.level().getServer(), team)) {
                if (member != player) teammates.add(member);
            }
        });
        teammates.sort(Comparator.comparing(member -> member.getScoreboardName()));
        return teammates;
    }

    private static void broadcastTalking(ServerPlayer player, boolean talking) {
        CommsTalkingS2CPayload payload = new CommsTalkingS2CPayload(player.getId(), talking);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0 || TRANSMITTING.isEmpty()) return;
        for (UUID sender : List.copyOf(TRANSMITTING.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(sender);
            if (player == null || !BrightestDayAttachments.has(player, RingPowerRegistry.COMMS)) {
                stop(server, sender);
                continue;
            }
            List<UUID> ids = listeners(player).stream().map(ServerPlayer::getUUID).toList();
            if (ids.isEmpty()) stop(server, sender);
            else if (!ids.equals(TRANSMITTING.get(sender))) retune(player);
        }
    }

    private Comms() {}
}
