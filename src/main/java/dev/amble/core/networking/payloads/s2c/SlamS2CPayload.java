package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record SlamS2CPayload(Vec3 center, float radius, int color, int playerId) implements CustomPacketPayload {

    public static final Type<SlamS2CPayload> TYPE =
            new Type<>(BrightestDay.id("ground_slam"));

    public static final StreamCodec<ByteBuf, SlamS2CPayload> CODEC =
            StreamCodec.composite(
                    Vec3.STREAM_CODEC, SlamS2CPayload::center,
                    ByteBufCodecs.FLOAT, SlamS2CPayload::radius,
                    ByteBufCodecs.INT, SlamS2CPayload::color,
                    ByteBufCodecs.VAR_INT, SlamS2CPayload::playerId,
                    SlamS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
