package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClockS2CPayload(long time, int rate) implements CustomPacketPayload {

    public static final Type<ClockS2CPayload> TYPE =
            new Type<>(BrightestDay.id("cosmos_clock"));

    public static final StreamCodec<ByteBuf, ClockS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ClockS2CPayload::time,
                    ByteBufCodecs.VAR_INT, ClockS2CPayload::rate,
                    ClockS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
