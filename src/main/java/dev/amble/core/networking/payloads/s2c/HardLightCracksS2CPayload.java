package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record HardLightCracksS2CPayload(List<BlockPos> cells, byte[] stages) implements CustomPacketPayload {
    public static final int BROKEN = 10;

    public static final Type<HardLightCracksS2CPayload> TYPE =
            new Type<>(BrightestDay.id("hard_light_cracks"));

    public static final StreamCodec<ByteBuf, HardLightCracksS2CPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), HardLightCracksS2CPayload::cells,
                    ByteBufCodecs.BYTE_ARRAY, HardLightCracksS2CPayload::stages,
                    HardLightCracksS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
