package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CosmosS2CPayload(Identifier mogoSector, boolean antimatterOpen) implements CustomPacketPayload {

    public static final Type<CosmosS2CPayload> TYPE =
            new Type<>(BrightestDay.id("cosmos"));

    public static final StreamCodec<ByteBuf, CosmosS2CPayload> CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, CosmosS2CPayload::mogoSector,
                    ByteBufCodecs.BOOL, CosmosS2CPayload::antimatterOpen,
                    CosmosS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
