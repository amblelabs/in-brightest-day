package dev.amble.core.walls;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class WallLines {
    private static final long FRESH_TICKS = 40L;
    private static final double REACH_SLACK = 3.0;

    public record Line(Vec3 start, Vec3 end) {}

    private record Pending(Vec3 start, Vec3 end, long at) {}

    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();

    public static void set(ServerPlayer player, Vec3 start, Vec3 end) {
        PENDING.put(player, new Pending(start, end, player.level().getGameTime()));
    }

    public static @Nullable Line take(ServerPlayer player, double range) {
        Pending pending = PENDING.remove(player);
        if (pending == null || player.level().getGameTime() - pending.at() > FRESH_TICKS) return null;
        double reach = (range + REACH_SLACK) * (range + REACH_SLACK);
        Vec3 eye = player.getEyePosition();
        if (eye.distanceToSqr(pending.start()) > reach || eye.distanceToSqr(pending.end()) > reach) return null;
        return new Line(pending.start(), pending.end());
    }

    private WallLines() {}
}
