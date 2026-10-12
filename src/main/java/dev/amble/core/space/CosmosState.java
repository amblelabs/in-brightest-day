package dev.amble.core.space;

import dev.amble.BrightestDay;
import dev.amble.core.networking.payloads.s2c.CosmosS2CPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class CosmosState {
    private static final long DAY = 24000L;
    private static final long DUSK = 13000L;
    private static final long DAWN = 23000L;

    public static final AttachmentType<Identifier> MOGO_SECTOR =
            AttachmentRegistry.<Identifier>builder()
                    .initializer(() -> Cosmos.SECTOR_0.identifier())
                    .persistent(Identifier.CODEC)
                    .buildAndRegister(BrightestDay.id("mogo_sector"));

    private static boolean antimatterOpen;

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(server, handler.player));
    }

    public static ResourceKey<Level> mogoSector(MinecraftServer server) {
        Identifier id = server.overworld().getAttachedOrElse(MOGO_SECTOR, Cosmos.SECTOR_0.identifier());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        Cosmos.Sector sector = Cosmos.sector(key);
        return sector == null || sector.antimatter() ? Cosmos.SECTOR_0 : key;
    }

    public static void moveMogo(MinecraftServer server, ResourceKey<Level> sector) {
        server.overworld().setAttached(MOGO_SECTOR, sector.identifier());
        broadcast(server);
    }

    public static boolean antimatterOpen() {
        return antimatterOpen;
    }

    static void tick(MinecraftServer server) {
        long time = Math.floorMod(server.overworld().getOverworldClockTime(), DAY);
        boolean open = time >= DUSK && time < DAWN;
        if (open == antimatterOpen) return;
        antimatterOpen = open;
        broadcast(server);
        ServerLevel sector = server.getLevel(Cosmos.SECTOR_0);
        if (sector == null) return;
        for (ServerPlayer player : sector.players()) {
            player.sendSystemMessage(Component.translatable(open ? "message.brightestday.antimatter.open" : "message.brightestday.antimatter.closed").withColor(0xC0102A));
        }
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(server, player);
    }

    public static void send(MinecraftServer server, ServerPlayer player) {
        ServerPlayNetworking.send(player, new CosmosS2CPayload(mogoSector(server).identifier(), antimatterOpen));
    }

    private CosmosState() {}
}
