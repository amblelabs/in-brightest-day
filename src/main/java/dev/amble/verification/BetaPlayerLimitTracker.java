package dev.amble.verification;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BetaPlayerLimitTracker {

    private static final Component KICK_MESSAGE = Component.translatable("gui.brightestday.play");
    public static final Identifier QUERY_ID = BrightestDay.id("verify");

    public static final Set<String> VERIFIED_PLAYERS = Collections.newSetFromMap(new WeakHashMap<>());

    public static void init() {
        ServerLoginConnectionEvents.QUERY_START.register((_, _, sender, _)
                -> sender.sendPacket(QUERY_ID, FriendlyByteBufs.empty()));

        ServerLoginNetworking.registerGlobalReceiver(QUERY_ID, (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (!understood) {
                handler.disconnect(KICK_MESSAGE);
                return;
            }

            boolean verified = buf.readBoolean();
            boolean success = BetaPlayerLimitTracker.handlePlayer(server, handler.getUserName(), verified);

            if (!success)
                handler.disconnect(KICK_MESSAGE);
        });

        ServerPlayerEvents.LEAVE.register(player -> VERIFIED_PLAYERS.remove(player.getGameProfile().name()));
    }

    public static int getMaxPlayerCount() {
        return VERIFIED_PLAYERS.size() + VERIFIED_PLAYERS.size() * BetaVerification.UNVERIFIED_PER_VERIFIED;
    }

    public static boolean handlePlayer(MinecraftServer server, String player, boolean verified) {
        if (verified) {
            BetaPlayerLimitTracker.VERIFIED_PLAYERS.add(player);
            return true;
        } else return !BrightestDayConfig.get().verifiedOnly && BetaPlayerLimitTracker.getMaxPlayerCount() >= server.getPlayerCount();
    }
}
