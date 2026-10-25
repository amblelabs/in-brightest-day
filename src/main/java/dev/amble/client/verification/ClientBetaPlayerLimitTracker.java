package dev.amble.client.verification;

import dev.amble.verification.BetaPlayerLimitTracker;
import dev.amble.verification.BetaVerification;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.CompletableFuture;

public class ClientBetaPlayerLimitTracker {

    public static void init() {
        ClientLoginConnectionEvents.QUERY_START.register((_, _) -> {
            ClientLoginNetworking.registerReceiver(BetaPlayerLimitTracker.QUERY_ID, (_, _, _, _) -> {
                FriendlyByteBuf resp = FriendlyByteBufs.create();
                resp.writeBoolean(!BetaVerification.isBetaLocked());
                return CompletableFuture.completedFuture(resp);
            });
        });
    }
}
