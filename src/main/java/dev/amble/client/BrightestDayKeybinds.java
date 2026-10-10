package dev.amble.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amble.BrightestDay;
import dev.amble.client.flight.FlightControls;
import dev.amble.client.screens.SpectrumScreen;
import dev.amble.client.team.TeamScreen;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.networking.payloads.c2s.ConcussiveC2SPayload;
import dev.amble.core.networking.payloads.c2s.DismissConstructC2SPayload;
import dev.amble.core.networking.payloads.c2s.SetColorTweakC2SPayload;
import dev.amble.core.networking.payloads.c2s.ToggleLightC2SPayload;
import dev.amble.core.networking.payloads.c2s.UsePowerC2SPayload;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.RingPowerRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class BrightestDayKeybinds {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(BrightestDay.id("main"));

    public static final KeyMapping FLIGHT = register("flight", InputConstants.KEY_Z);
    public static final KeyMapping RAISE_RING = register("raise_ring", InputConstants.KEY_X);
    public static final KeyMapping CYCLE_CONSTRUCT = register("cycle_construct", InputConstants.KEY_R);
    public static final KeyMapping ABILITY_WHEEL = register("ability_wheel", InputConstants.KEY_C);
    public static final KeyMapping DISMISS_CONSTRUCT = register("dismiss_construct", InputConstants.KEY_G);
    public static final KeyMapping CONCUSSIVE_BLAST = register("concussive_blast", InputConstants.KEY_B);
    public static final KeyMapping ACID_VOMIT = register("acid_vomit", InputConstants.KEY_N);
    public static final KeyMapping TEAM = register("team", InputConstants.KEY_J);
    public static final KeyMapping SPECTRUM = register("spectrum", InputConstants.KEY_K);
    public static final KeyMapping STYLE_WHEEL = register("style_wheel", InputConstants.KEY_Y);

    public static final KeyMapping TOGGLE_LIGHT = register("toggle_light", InputConstants.KEY_V);
    public static final KeyMapping TOGGLE_SUIT = register("toggle_suit", InputConstants.KEY_PERIOD);
    public static final KeyMapping TOGGLE_MASK = register("toggle_mask", InputConstants.KEY_H);

    public static final KeyMapping FLIGHT_BOOST = register("flight_boost", InputConstants.KEY_LALT);

    private static boolean raiseHeld;
    private static final int DOUBLE_TAP_TICKS = 7;
    private static int ticks;
    private static int lastFlightTap = -DOUBLE_TAP_TICKS - 1;

    private static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.brightestday." + name,
                InputConstants.Type.KEYBOARD,
                key,
                CATEGORY
        ));
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BrightestDayKeybinds::tick);
    }

    private static void tick(Minecraft client) {
        if (client.player == null) return;

        FlightControls.syncBoost(client.player, FLIGHT_BOOST.isDown());
        if (client.gui.screen() != null) {
            drain(FLIGHT, RAISE_RING, DISMISS_CONSTRUCT, CONCUSSIVE_BLAST, TEAM, TOGGLE_LIGHT, TOGGLE_SUIT, TOGGLE_MASK, SPECTRUM);
            raiseHeld = RAISE_RING.isDown();
            return;
        }

        ticks++;
        while (FLIGHT.consumeClick()) {
            if (ticks - lastFlightTap <= DOUBLE_TAP_TICKS) {
                ClientPlayNetworking.send(new UsePowerC2SPayload(RingPowerRegistry.FLIGHT.id()));
                lastFlightTap = -DOUBLE_TAP_TICKS - 1;
            } else {
                lastFlightTap = ticks;
            }
        }

        boolean raisePressed = false;
        while (RAISE_RING.consumeClick()) {
            raisePressed = true;
        }
        if (raisePressed && !raiseHeld) ClientPlayNetworking.send(new UsePowerC2SPayload(RingPowerRegistry.ARMED.id()));
        raiseHeld = RAISE_RING.isDown();

        while (DISMISS_CONSTRUCT.consumeClick()) {
            ClientPlayNetworking.send(DismissConstructC2SPayload.INSTANCE);
        }

        while (CONCUSSIVE_BLAST.consumeClick()) {
            if (BrightestDayAttachments.get(client.player, RingPowerRegistry.CONCUSSIVE).isPresent()) ClientPlayNetworking.send(ConcussiveC2SPayload.INSTANCE);
        }

        while (TEAM.consumeClick()) {
            client.gui.setScreen(new TeamScreen(null));
        }

        while (SPECTRUM.consumeClick()) {
            client.gui.setScreen(new SpectrumScreen(null));
        }

        while (TOGGLE_LIGHT.consumeClick()) {
            ClientPlayNetworking.send(ToggleLightC2SPayload.INSTANCE);
        }

        while (TOGGLE_SUIT.consumeClick()) toggleSuit();

        while (TOGGLE_MASK.consumeClick()) toggleMask();

    }

    private static void drain(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            while (key.consumeClick()) {
            }
        }
    }

    public static void toggleSuit() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ColorTweak tweak = BrightestDayAttachments.getColorTweak(client.player);
        setTweak(client, tweak.withSuit(!tweak.suit()));
    }

    public static void toggleMask() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ColorTweak tweak = BrightestDayAttachments.getColorTweak(client.player);
        setTweak(client, tweak.withMask(!tweak.mask()));
    }

    private static void setTweak(Minecraft client, ColorTweak tweak) {
        BrightestDayAttachments.setColorTweak(client.player, tweak);
        ClientPlayNetworking.send(new SetColorTweakC2SPayload(tweak));
    }

    private BrightestDayKeybinds() {}
}
