package dev.amble.core.sync;

import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.CompassS2CPayload;
import dev.amble.core.progression.WorldProgress;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RingCompass {
    public static final int DURATION_TICKS = 200;

    private record Reading(BlockPos target, long ends, boolean lantern) {}

    private static final Map<UUID, Reading> READINGS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RingCompass::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READINGS.remove(handler.player.getUUID()));
    }

    public static boolean project(ServerPlayer player, int color) {
        LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
        if (corps == null) return false;
        boolean lantern = corps == LanternCorps.ORANGE;
        BlockPos target = lantern ? lantern(player) : core(player, corps);
        if (target == null) {
            if (!lantern && WorldProgress.get(player.level().getServer()).batteries().stream().anyMatch(battery -> battery.corps() == corps)) {
                player.sendOverlayMessage(Component.translatable("message.brightestday.compass.elsewhere").withColor(corps.color()));
            } else {
                player.sendOverlayMessage(Component.translatable(lantern ? "message.brightestday.compass.no_lantern" : "message.brightestday.compass.no_core").withColor(corps.color()));
            }
            return false;
        }

        READINGS.put(player.getUUID(), new Reading(target, player.level().getGameTime() + DURATION_TICKS, lantern));
        CompassS2CPayload payload = new CompassS2CPayload(player.getId(), target, color, DURATION_TICKS);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
        report(player, READINGS.get(player.getUUID()), corps.color());
        return true;
    }

    private static @Nullable BlockPos lantern(ServerPlayer player) {
        GlobalPos bound = PowerRingItem.getWornRing(player).get(BrightestDayComponents.BOUND_LANTERN);
        return bound != null && bound.dimension() == player.level().dimension() ? bound.pos() : null;
    }

    private static @Nullable BlockPos core(ServerPlayer player, LanternCorps corps) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean bestActive = false;
        for (WorldProgress.Battery battery : WorldProgress.get(player.level().getServer()).batteries()) {
            if (battery.corps() != corps || !battery.in(player.level())) continue;
            double distance = player.position().distanceToSqr(Vec3.atCenterOf(battery.pos()));
            if (battery.active() && !bestActive || battery.active() == bestActive && distance < bestDistance) {
                best = battery.pos();
                bestDistance = distance;
                bestActive = battery.active();
            }
        }
        return best;
    }

    private static void report(ServerPlayer player, Reading reading, int color) {
        int distance = (int) Math.round(Math.sqrt(player.position().distanceToSqr(Vec3.atCenterOf(reading.target()))));
        player.sendOverlayMessage(Component.translatable(reading.lantern() ? "message.brightestday.compass.lantern" : "message.brightestday.compass.core", distance).withColor(color));
    }

    private static void tick(MinecraftServer server) {
        if (READINGS.isEmpty() || server.getTickCount() % 20 != 0) return;
        READINGS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level().getGameTime() > entry.getValue().ends()) return true;
            report(player, entry.getValue(), PowerRingItem.getWornCorps(player).map(LanternCorps::color).orElse(0xFFFFFF));
            return false;
        });
    }

    private RingCompass() {}
}
