package dev.amble.core.ringpowers;

import dev.amble.core.networking.payloads.s2c.BloodHuntS2CPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BloodHunt {
    public static final float DAMAGE_BONUS = 0.3F;
    private static final int RED = LanternCorps.RED.color();
    private static final int UPDATE_INTERVAL = 10;

    private static final Map<UUID, UUID> HUNTS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BloodHunt::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            UUID id = entity.getUUID();
            MinecraftServer server = entity.level() instanceof ServerLevel level ? level.getServer() : null;
            HUNTS.entrySet().removeIf(entry -> {
                boolean over = entry.getKey().equals(id) || entry.getValue().equals(id);
                if (over) end(server, entry.getKey());
                return over;
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HUNTS.remove(handler.player.getUUID()));
    }

    public static boolean isPrey(Player hunter, Entity entity) {
        return entity.getUUID().equals(HUNTS.get(hunter.getUUID()));
    }

    public static void mark(ServerPlayer hunter, LivingEntity prey) {
        HUNTS.put(hunter.getUUID(), prey.getUUID());
        sync(hunter, prey);
        hunter.sendOverlayMessage(Component.translatable("message.brightestday.hunt.marked", prey.getDisplayName()).withColor(RED));
        hunter.level().playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.8F);
        if (prey instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.brightestday.hunt.hunted", hunter.getDisplayName()).withStyle(ChatFormatting.ITALIC).withColor(RED));
        }
    }

    private static void end(@Nullable MinecraftServer server, UUID hunterId) {
        if (server == null) return;
        ServerPlayer hunter = server.getPlayerList().getPlayer(hunterId);
        if (hunter == null) return;
        ServerPlayNetworking.send(hunter, BloodHuntS2CPayload.NONE);
        hunter.sendOverlayMessage(Component.translatable("message.brightestday.hunt.ended").withColor(RED));
    }

    private static void sync(ServerPlayer hunter, Entity prey) {
        ServerPlayNetworking.send(hunter, new BloodHuntS2CPayload(prey.getId(), prey.getX(), prey.getY() + prey.getBbHeight() / 2.0, prey.getZ()));
    }

    private static @Nullable Entity find(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % UPDATE_INTERVAL != 0 || HUNTS.isEmpty()) return;
        HUNTS.entrySet().removeIf(entry -> {
            ServerPlayer hunter = server.getPlayerList().getPlayer(entry.getKey());
            if (hunter == null) return true;
            Entity prey = find(server, entry.getValue());
            if (prey == null || !prey.isAlive()) {
                end(server, entry.getKey());
                return true;
            }
            if (prey.level() == hunter.level()) sync(hunter, prey);
            return false;
        });
    }

    private BloodHunt() {}
}
