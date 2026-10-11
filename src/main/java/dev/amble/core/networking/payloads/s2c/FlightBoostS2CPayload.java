package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlightBoostS2CPayload(int ticks, int cooldown) implements CustomPacketPayload {

    public static final Type<FlightBoostS2CPayload> TYPE =
            new Type<>(BrightestDay.id("flight_boost_burst"));

    public static final StreamCodec<ByteBuf, FlightBoostS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FlightBoostS2CPayload::ticks,
                    ByteBufCodecs.VAR_INT, FlightBoostS2CPayload::cooldown,
                    FlightBoostS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
