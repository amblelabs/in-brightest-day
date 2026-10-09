package dev.amble.core.forge;

import dev.amble.core.light.LightManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ForgeLight {
    private static final double DRAW_DISTANCE = 2.5;
    private static final int TIMEOUT_TICKS = 40;

    private record Glow(ServerLevel level, BlockPos pos, long touched) {}

    private static final Map<UUID, Glow> GLOWS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ForgeLight::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) clear(player);
        });
    }

    public static void follow(ServerPlayer player, Vec3 direction) {
        ServerLevel level = player.level();
        BlockPos tip = BlockPos.containing(player.getEyePosition().add(direction.normalize().scale(DRAW_DISTANCE)));
        Glow current = GLOWS.get(player.getUUID());
        long now = level.getGameTime();
        if (current != null && current.level() == level && current.pos().equals(tip)) {
            GLOWS.put(player.getUUID(), new Glow(level, tip, now));
            return;
        }
        BlockPos target = LightManager.findAir(level, tip, 1);
        if (target == null || !level.mayInteract(player, target)) return;
        if (current != null && current.level() == level && current.pos().equals(target)) {
            GLOWS.put(player.getUUID(), new Glow(level, target, now));
            return;
        }
        clear(player);
        if (LightManager.place(level, target)) GLOWS.put(player.getUUID(), new Glow(level, target.immutable(), now));
    }

    public static void clear(ServerPlayer player) {
        Glow glow = GLOWS.remove(player.getUUID());
        if (glow != null) LightManager.remove(glow.level(), glow.pos());
    }

    private static void tick(MinecraftServer server) {
        if (GLOWS.isEmpty()) return;
        GLOWS.entrySet().removeIf(entry -> {
            Glow glow = entry.getValue();
            if (glow.level().getGameTime() - glow.touched() <= TIMEOUT_TICKS) return false;
            LightManager.remove(glow.level(), glow.pos());
            return true;
        });
    }

    private ForgeLight() {}
}
