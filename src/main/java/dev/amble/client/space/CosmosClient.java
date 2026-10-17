package dev.amble.client.space;

import dev.amble.core.networking.payloads.s2c.CosmosS2CPayload;
import dev.amble.core.networking.payloads.s2c.ClockS2CPayload;
import dev.amble.core.space.Cosmos;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.amble.core.space.SpaceTravel;
import dev.amble.core.space.SpaceWarp;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

public final class CosmosClient {
    private static final int READOUT_INTERVAL = 5;
    private static final double READOUT_SPEED = 8.0 / Cosmos.COMPRESSION;
    private static ResourceKey<Level> mogoSector = Cosmos.SECTOR_0;
    private static boolean antimatterOpen;
    private static long clockBase;
    private static int clockRate = 1;
    private static long clockReceivedAt;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CosmosS2CPayload.TYPE, (payload, context) -> {
            mogoSector = ResourceKey.create(Registries.DIMENSION, payload.mogoSector());
            Cosmos.knownMogo = mogoSector;
            antimatterOpen = payload.antimatterOpen();
        });
        ClientPlayNetworking.registerGlobalReceiver(ClockS2CPayload.TYPE, (payload, context) -> {
            clockBase = payload.time();
            clockRate = payload.rate();
            clockReceivedAt = context.client().level == null ? 0L : context.client().level.getGameTime();
        });
        ClientTickEvents.END_CLIENT_TICK.register(CosmosClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            mogoSector = Cosmos.SECTOR_0;
            antimatterOpen = false;
        });
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.isPaused() || player.tickCount % READOUT_INTERVAL != 0) return;
        if (!SpaceTravel.isSpace(player.level())) return;
        double speed = player.getDeltaMovement().length();
        if (speed < READOUT_SPEED) return;
        client.gui.hud.setOverlayMessage(Component.translatable("hud.brightestday.warp", String.format(Locale.ROOT, "%.1f", speed * Cosmos.COMPRESSION / SpaceWarp.MACH)).withColor(0x9FD8FF), false);
    }

    public static void snapshot(Consumer<CustomPacketPayload> out) {
        out.accept(new CosmosS2CPayload(mogoSector.identifier(), antimatterOpen));
    }

    public static double clock(float partialTicks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return clockBase;
        return clockBase + clockRate * (client.level.getGameTime() - clockReceivedAt + partialTicks);
    }

    public static ResourceKey<Level> mogoSector() {
        return mogoSector;
    }

    public static boolean antimatterOpen() {
        return antimatterOpen;
    }

    private CosmosClient() {}
}
