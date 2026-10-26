package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// stages holds one crack stage per BulwarkArc damage cell, row-major; HardLightCracksS2CPayload.BROKEN marks a hole
public record BulwarkS2CPayload(int playerId, boolean active, int size, int color, byte[] stages) implements CustomPacketPayload {

    public static final Type<BulwarkS2CPayload> TYPE =
            new Type<>(BrightestDay.id("bulwark"));

    public static final StreamCodec<ByteBuf, BulwarkS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BulwarkS2CPayload::playerId,
                    ByteBufCodecs.BOOL, BulwarkS2CPayload::active,
                    ByteBufCodecs.VAR_INT, BulwarkS2CPayload::size,
                    ByteBufCodecs.INT, BulwarkS2CPayload::color,
                    ByteBufCodecs.BYTE_ARRAY, BulwarkS2CPayload::stages,
                    BulwarkS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
