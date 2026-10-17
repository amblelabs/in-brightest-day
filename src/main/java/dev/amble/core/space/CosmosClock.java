package dev.amble.core.space;

import dev.amble.core.networking.payloads.s2c.ClockS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.function.DoubleSupplier;

public final class CosmosClock {
    private static final int REFRESH_TICKS = 40;
    private static long last = Long.MIN_VALUE;
    private static int rate = 1;
    public static DoubleSupplier client = () -> 0.0;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CosmosClock::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, payload(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            last = Long.MIN_VALUE;
            rate = 1;
        });
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getOverworldClockTime();
    }

    public static double now(Level level) {
        if (level.isClientSide()) return client.getAsDouble();
        MinecraftServer server = level.getServer();
        return server == null ? 0.0 : now(server);
    }

    private static ClockS2CPayload payload(MinecraftServer server) {
        return new ClockS2CPayload(now(server), rate);
    }

    private static void tick(MinecraftServer server) {
        long now = now(server);
        boolean changed = false;
        if (last != Long.MIN_VALUE) {
            long delta = now - last;
            int observed = delta == 0 ? 0 : 1;
            changed = delta != rate || observed != rate;
            rate = observed;
        }
        last = now;
        if (!changed && server.getTickCount() % REFRESH_TICKS != 0) return;
        ClockS2CPayload payload = payload(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, payload);
    }

    private CosmosClock() {}
}
