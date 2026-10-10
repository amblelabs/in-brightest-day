package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CommsTargetS2CPayload(String name, boolean team) implements CustomPacketPayload {
    public static final CommsTargetS2CPayload NONE = new CommsTargetS2CPayload("", false);

    public static final Type<CommsTargetS2CPayload> TYPE =
            new Type<>(BrightestDay.id("comms_target"));

    public static final StreamCodec<ByteBuf, CommsTargetS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CommsTargetS2CPayload::name,
                    ByteBufCodecs.BOOL, CommsTargetS2CPayload::team,
                    CommsTargetS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
