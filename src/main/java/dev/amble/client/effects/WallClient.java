package dev.amble.client.effects;

import dev.amble.core.networking.payloads.c2s.WallLineC2SPayload;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.WallConstruct;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.walls.WallGeometry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WallClient {
    private static @Nullable Vec3 start;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(WallClient::tick);
    }

    private static boolean selected(LocalPlayer player) {
        return ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.WALL;
    }

    public static @Nullable Vec3 anchor(LocalPlayer player, float partialTicks) {
        return WallGeometry.anchor(player.level(), player, player.getEyePosition(partialTicks), player.getViewVector(partialTicks), WallConstruct.RANGE);
    }

    public static @Nullable Vec3 start() {
        return start;
    }

    public static Vec3 end(Vec3 from, Vec3 to) {
        return WallGeometry.clampLength(from, to, WallConstruct.MAX_LENGTH);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !selected(player) || !BlastEffects.charging()) {
            start = null;
            return;
        }
        if (start == null) start = anchor(player, 1.0F);
    }

    public static void beforeFire(LocalPlayer player) {
        if (!selected(player)) return;
        Vec3 from = start != null ? start : anchor(player, 1.0F);
        Vec3 to = anchor(player, 1.0F);
        start = null;
        if (from == null) return;
        if (to == null) to = from;
        if (ClientPlayNetworking.canSend(WallLineC2SPayload.TYPE)) ClientPlayNetworking.send(new WallLineC2SPayload(from, end(from, to)));
    }

    private WallClient() {}
}
