package dev.amble.client.flight;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import dev.amble.core.flight.GroundLaunch;
import dev.amble.core.networking.payloads.s2c.GroundLaunchS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class LaunchAnimations {
    private static final int OFF_TICKS = 5;

    private static final Map<Player, Integer> STATES = new WeakHashMap<>();
    private static final Map<Player, Integer> RELEASING = new WeakHashMap<>();
    private static final Map<Player, Integer> CHARGING = new WeakHashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GroundLaunchS2CPayload.TYPE, (payload, context) -> {
            if (context.client().level != null && context.client().level.getEntity(payload.playerId()) instanceof Player player) apply(player, payload.state());
        });
        ClientTickEvents.END_CLIENT_TICK.register(LaunchAnimations::tick);
    }

    public static boolean active(Player player) {
        return STATES.containsKey(player);
    }

    public static void apply(Player player, int state) {
        PlayerAnimationController controller = FlightAnimations.controller(player);
        if (controller == null) return;
        Integer previous = STATES.put(player, state);
        switch (state) {
            case GroundLaunch.CHARGING -> {
                RELEASING.remove(player);
                CHARGING.put(player, 0);
                if (previous == null || previous != GroundLaunch.CHARGING) FlightAnimations.launchCharge(controller);
            }
            case GroundLaunch.LAUNCHED -> {
                CHARGING.remove(player);
                FlightAnimations.launchOff(controller);
                RELEASING.put(player, OFF_TICKS);
            }
            default -> {
                RELEASING.remove(player);
                CHARGING.remove(player);
                STATES.remove(player);
                if (previous != null && !FlightAnimator.phaseActive(player)) FlightAnimations.rise(controller);
            }
        }
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        CHARGING.replaceAll((player, ticks) -> {
            if (player == client.player || player.isRemoved()) return ticks;
            int next = Math.min(ticks + 1, GroundLaunch.FULL_CHARGE);
            GroundLaunchClient.chargeEffects(client, player, next);
            return next;
        });
        CHARGING.keySet().removeIf(Player::isRemoved);
        Iterator<Map.Entry<Player, Integer>> iterator = RELEASING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            iterator.remove();
            Player player = entry.getKey();
            STATES.remove(player);
            PlayerAnimationController controller = FlightAnimations.controller(player);
            if (controller != null && !FlightAnimator.phaseActive(player)) FlightAnimations.rise(controller);
        }
    }

    private LaunchAnimations() {}
}
