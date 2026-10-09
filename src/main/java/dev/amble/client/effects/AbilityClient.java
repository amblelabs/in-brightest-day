package dev.amble.client.effects;

import dev.amble.client.BrightestDayKeybinds;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.LanternBlockItem;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.AcidC2SPayload;
import dev.amble.core.networking.payloads.c2s.BerserkC2SPayload;
import dev.amble.core.networking.payloads.c2s.ConcussiveC2SPayload;
import dev.amble.core.networking.payloads.c2s.ConversionC2SPayload;
import dev.amble.core.networking.payloads.c2s.GatherC2SPayload;
import dev.amble.core.networking.payloads.s2c.TribeRosterS2CPayload;
import dev.amble.client.screens.TribeScreen;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

public final class AbilityClient {
    private static @Nullable RingPower<?> pressed;
    private static boolean spewing;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AbilityClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(TribeRosterS2CPayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() == null) context.client().gui.setScreen(new TribeScreen(payload.members()));
        });
    }

    public static boolean wantsUse(LocalPlayer player) {
        return ArmedRingPower.isArmed(player)
                && PowerRingItem.hasCharge(player)
                && ArmedRingPower.isAbilityMode(player)
                && !ConstructClient.isLookingAtLantern()
                && !LanternBlockItem.isChargingLantern(player);
    }

    public static boolean holding(LocalPlayer player, RingPower<?> ability) {
        return RingInput.useHeld(Minecraft.getInstance()) && wantsUse(player) && ArmedRingPower.activeAbility(player).orElse(null) == ability;
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            pressed = null;
            return;
        }

        RingPower<?> held = RingInput.useHeld(client) && wantsUse(player) ? ArmedRingPower.activeAbility(player).orElse(null) : null;
        if (held != null && held != pressed) {
            if (held == RingPowerRegistry.CONCUSSIVE) ClientPlayNetworking.send(ConcussiveC2SPayload.INSTANCE);
            if (held == RingPowerRegistry.CONVERSION) ClientPlayNetworking.send(ConversionC2SPayload.INSTANCE);
            if (held == RingPowerRegistry.BERSERK) ClientPlayNetworking.send(BerserkC2SPayload.INSTANCE);
            if (held == RingPowerRegistry.GATHER) ClientPlayNetworking.send(GatherC2SPayload.REQUEST);
        }
        pressed = held;

        boolean hasAcid = BrightestDayAttachments.get(player, RingPowerRegistry.ACID).isPresent();
        boolean spew = hasAcid && (held == RingPowerRegistry.ACID || client.gui.screen() == null && BrightestDayKeybinds.ACID_VOMIT.isDown());
        if (spew != spewing) {
            spewing = spew;
            ClientPlayNetworking.send(new AcidC2SPayload(spew));
        }
    }

    private AbilityClient() {}
}
