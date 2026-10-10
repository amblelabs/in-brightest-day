package dev.amble.client.flight;

import dev.amble.client.BrightestDayKeybinds;
import dev.amble.core.flight.FlightBoost;
import dev.amble.core.networking.payloads.c2s.FlightBoostC2SPayload;
import dev.amble.core.networking.payloads.c2s.FlightSpeedC2SPayload;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class FlightControls {

    private static @Nullable LocalPlayer syncedPlayer;
    private static boolean syncedBoost;

    public static boolean onScroll(int wheel) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || wheel == 0 || !BrightestDayKeybinds.FLIGHT.isDown() || !ArmedRingPower.isArmed(player) || !FlightRingPower.hasFlight(player)) return false;
        stepSpeed(client, player, Integer.signum(wheel));
        return true;
    }

    public static void stepSpeed(Minecraft client, LocalPlayer player, int direction) {
        if (!FlightRingPower.hasFlight(player)) return;

        int current = FlightRingPower.speedLevel(player);
        int level = Mth.clamp(current + direction, 0, FlightRingPower.SPEED_LEVELS - 1);
        if (level != current) {
            FlightRingPower.setSpeedLevel(player, level);
            if (ClientPlayNetworking.canSend(FlightSpeedC2SPayload.TYPE)) ClientPlayNetworking.send(new FlightSpeedC2SPayload(level));
        }

        client.gui.hud.setOverlayMessage(Component.translatable("message.brightestday.flight_speed",
                level + 1, FlightRingPower.SPEED_LEVELS, formatSpeed(FlightRingPower.speedForLevel(level) * 20.0)), false);
    }

    public static void syncBoost(LocalPlayer player, boolean held) {
        boolean boosting = held && FlightRingPower.isFlying(player);
        FlightBoost.setBoosting(player, boosting);
        if (player == syncedPlayer && boosting == syncedBoost) return;
        if (!ClientPlayNetworking.canSend(FlightBoostC2SPayload.TYPE)) return;

        ClientPlayNetworking.send(new FlightBoostC2SPayload(boosting));
        syncedPlayer = player;
        syncedBoost = boosting;
    }

    public static String formatSpeed(double blocksPerSecond) {
        return blocksPerSecond < 10.0
                ? String.format(Locale.ROOT, "%.1f", blocksPerSecond)
                : String.format(Locale.ROOT, "%.0f", blocksPerSecond);
    }

    private FlightControls() {}
}
