package dev.amble.core.flight;

import dev.amble.core.networking.payloads.s2c.LandingS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

public final class Landings {
    private static final long MIN_GAP_TICKS = 10L;
    private static final int MAX_POSE = 8;

    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();

    public static void relay(ServerPlayer player, int pose, float strength) {
        long now = player.level().getGameTime();
        Long last = LAST.get(player);
        if (last != null && now - last < MIN_GAP_TICKS) return;
        LAST.put(player, now);
        LandingS2CPayload payload = new LandingS2CPayload(player.getId(), Mth.clamp(pose, -1, MAX_POSE), Mth.clamp(strength, 0.0F, 1.0F));
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private Landings() {}
}
