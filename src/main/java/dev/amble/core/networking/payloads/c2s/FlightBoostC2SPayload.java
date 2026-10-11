package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.flight.FlightBoost;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlightBoostC2SPayload(boolean boosting) implements CustomPacketPayload {

    public static final Type<FlightBoostC2SPayload> TYPE =
            new Type<>(BrightestDay.id("flight_boost"));

    public static final StreamCodec<ByteBuf, FlightBoostC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, FlightBoostC2SPayload::boosting,
                    FlightBoostC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        if (this.boosting) FlightBoost.request(context.player());
    }
}
