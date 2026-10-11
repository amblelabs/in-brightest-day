package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.CompactFlyer;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlightCompactC2SPayload(boolean compact) implements CustomPacketPayload {

    public static final Type<FlightCompactC2SPayload> TYPE =
            new Type<>(BrightestDay.id("flight_compact"));

    public static final StreamCodec<ByteBuf, FlightCompactC2SPayload> CODEC =
            ByteBufCodecs.BOOL.map(FlightCompactC2SPayload::new, FlightCompactC2SPayload::compact);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        if (!(context.player() instanceof CompactFlyer flyer)) return;
        boolean compact = this.compact && FlightRingPower.isFlying(context.player());
        if (!compact && !FlightRingPower.fitsStanding(context.player())) return;
        flyer.brightestday$setCompact(compact);
    }
}
