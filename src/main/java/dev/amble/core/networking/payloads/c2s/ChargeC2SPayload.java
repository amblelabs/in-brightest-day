package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.AttackAnimS2CPayload;
import dev.amble.core.networking.payloads.s2c.ChargeS2CPayload;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public record ChargeC2SPayload(boolean charging, int ticks) implements CustomPacketPayload {
    private static final int MAX_TICKS = 200;

    public static final Type<ChargeC2SPayload> TYPE =
            new Type<>(BrightestDay.id("charge"));

    public static final StreamCodec<ByteBuf, ChargeC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ChargeC2SPayload::charging,
                    ByteBufCodecs.VAR_INT, ChargeC2SPayload::ticks,
                    ChargeC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        boolean charging = this.charging && !player.isSpectator() && PowerRingItem.hasCharge(player) && !ArmedRingPower.isAbilityMode(player);
        if (charging) ArmedRingPower.startCharge(player);
        ArmedRingPower.selectedConstruct(player).ifPresent(construct -> AttackAnimS2CPayload.broadcast(player, construct.id(), charging ? AttackAnimS2CPayload.CHARGE : AttackAnimS2CPayload.STOP));

        ChargeS2CPayload relay = new ChargeS2CPayload(player.getId(), charging, Mth.clamp(this.ticks, 1, MAX_TICKS));
        ServerPlayNetworking.send(player, relay);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, relay);
        }
    }
}
