package dev.amble.client.effects;

import dev.amble.client.hud.RingFeed;
import dev.amble.core.networking.payloads.c2s.CommsC2SPayload;
import dev.amble.core.networking.payloads.s2c.CommsIncomingS2CPayload;
import dev.amble.core.networking.payloads.s2c.CommsTalkingS2CPayload;
import dev.amble.core.networking.payloads.s2c.CommsTargetS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class CommsClient {
    private static final float RAISE_SPEED = 0.3F;
    private static final int INCOMING_COLOR = 0xFFB8E0FF;
    private static final int DOUBLE_CLICK_TICKS = 8;

    private static final Set<Integer> TALKING = new HashSet<>();
    private static final Map<Player, float[]> AMOUNTS = new WeakHashMap<>();
    private static String dialed = "";
    private static String incoming = "";
    private static boolean transmitting;
    private static boolean team;
    private static boolean locked;
    private static boolean wasHolding;
    private static int lastPress = -DOUBLE_CLICK_TICKS - 1;
    private static int ticks;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CommsTargetS2CPayload.TYPE, (payload, context) -> {
            dialed = payload.name();
            team = payload.team();
        });
        ClientPlayNetworking.registerGlobalReceiver(CommsIncomingS2CPayload.TYPE, (payload, context) -> incoming = payload.active() ? payload.name() : "");
        ClientPlayNetworking.registerGlobalReceiver(CommsTalkingS2CPayload.TYPE, (payload, context) -> {
            if (payload.talking()) TALKING.add(payload.playerId());
            else TALKING.remove(payload.playerId());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TALKING.clear();
            dialed = "";
            incoming = "";
            transmitting = false;
            team = false;
            locked = false;
            wasHolding = false;
        });
        ClientTickEvents.START_CLIENT_TICK.register(CommsClient::pickTeam);
        ClientTickEvents.END_CLIENT_TICK.register(CommsClient::tick);
    }

    private static boolean selected(LocalPlayer player) {
        return ArmedRingPower.isArmed(player) && ArmedRingPower.activeAbility(player).orElse(null) == RingPowerRegistry.COMMS;
    }

    public static boolean onScroll(int wheel) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || wheel == 0 || !selected(player)) return false;
        ClientPlayNetworking.send(new CommsC2SPayload(wheel > 0 ? CommsC2SPayload.Action.PREVIOUS : CommsC2SPayload.Action.NEXT));
        return true;
    }

    private static void pickTeam(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gui.screen() != null || !selected(player)) return;
        if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS) return;
        while (client.options.keyPickItem.consumeClick()) ClientPlayNetworking.send(new CommsC2SPayload(CommsC2SPayload.Action.TEAM));
    }

    private static void tick(Minecraft client) {
        LocalPlayer local = client.player;
        if (local == null || client.level == null) return;

        ticks++;
        boolean holding = AbilityClient.holding(local, RingPowerRegistry.COMMS);
        if (holding && !wasHolding) {
            if (locked) {
                locked = false;
                lastPress = -DOUBLE_CLICK_TICKS - 1;
            } else if (ticks - lastPress <= DOUBLE_CLICK_TICKS) {
                locked = true;
                local.sendOverlayMessage(Component.translatable("message.brightestday.comms.locked"));
            } else {
                lastPress = ticks;
            }
        }
        wasHolding = holding;
        if (locked && !selected(local)) locked = false;

        boolean live = holding || locked;
        if (live != transmitting) {
            transmitting = live;
            ClientPlayNetworking.send(new CommsC2SPayload(live ? CommsC2SPayload.Action.START : CommsC2SPayload.Action.STOP));
        }

        if (client.isPaused()) return;
        for (AbstractClientPlayer player : client.level.players()) {
            boolean talking = TALKING.contains(player.getId());
            float[] amount = AMOUNTS.get(player);
            if (amount == null) {
                if (!talking) continue;
                amount = new float[2];
                AMOUNTS.put(player, amount);
            }
            amount[1] = amount[0];
            amount[0] += ((talking ? 1.0F : 0.0F) - amount[0]) * RAISE_SPEED;
            if (!talking && amount[0] < 0.001F) AMOUNTS.remove(player);
        }
    }

    public static float talking(Player player, float partialTicks) {
        float[] amount = AMOUNTS.get(player);
        return amount == null ? 0.0F : Mth.lerp(partialTicks, amount[1], amount[0]);
    }

    public static List<RingFeed.Line> pinned(LocalPlayer player) {
        List<RingFeed.Line> lines = new ArrayList<>(2);
        if (selected(player)) {
            Component target = team ? Component.translatable("hud.brightestday.comms.team") : Component.literal(dialed);
            Component status = !team && dialed.isEmpty()
                    ? Component.translatable("hud.brightestday.comms.none")
                    : Component.translatable(transmitting ? (locked ? "hud.brightestday.comms.locked" : "hud.brightestday.comms.transmitting") : "hud.brightestday.comms.dialed", target);
            lines.add(new RingFeed.Line(status, ARGB.opaque(CorpsColors.of(player))));
        }
        if (!incoming.isEmpty()) lines.add(new RingFeed.Line(Component.translatable("hud.brightestday.comms.incoming", incoming), INCOMING_COLOR));
        return lines;
    }

    private CommsClient() {}
}
