package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MagnetS2CPayload(int playerId, int size, int color) implements CustomPacketPayload {

    public static final Type<MagnetS2CPayload> TYPE =
            new Type<>(BrightestDay.id("magnet"));

    public static final StreamCodec<ByteBuf, MagnetS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MagnetS2CPayload::playerId,
                    ByteBufCodecs.VAR_INT, MagnetS2CPayload::size,
                    ByteBufCodecs.INT, MagnetS2CPayload::color,
                    MagnetS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
