package dev.amble.core.networking;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

public final class RelayLimit {
    private final int perTick;
    private final Map<ServerPlayer, int[]> counts = new WeakHashMap<>();

    public RelayLimit(int perTick) {
        this.perTick = perTick;
    }

    public boolean allow(ServerPlayer player) {
        int tick = player.level().getServer().getTickCount();
        int[] count = this.counts.computeIfAbsent(player, unused -> new int[]{tick, 0});
        if (count[0] != tick) {
            count[0] = tick;
            count[1] = 0;
        }
        return ++count[1] <= this.perTick;
    }
}
