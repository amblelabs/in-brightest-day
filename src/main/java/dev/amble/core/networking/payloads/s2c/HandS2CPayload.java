package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import dev.amble.core.hand.HandPose;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record HandS2CPayload(Vec3 origin, Vec3 direction, Vec3 up, float reach, int color, int windup, int travel, boolean impact, float scale,
                             HandPose pose) implements CustomPacketPayload {

    public static final Type<HandS2CPayload> TYPE = new Type<>(BrightestDay.id("hand"));

    public static final StreamCodec<ByteBuf, HandS2CPayload> CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, HandS2CPayload::origin,
            Vec3.STREAM_CODEC, HandS2CPayload::direction,
            Vec3.STREAM_CODEC, HandS2CPayload::up,
            ByteBufCodecs.FLOAT, HandS2CPayload::reach,
            ByteBufCodecs.INT, HandS2CPayload::color,
            ByteBufCodecs.VAR_INT, HandS2CPayload::windup,
            ByteBufCodecs.VAR_INT, HandS2CPayload::travel,
            ByteBufCodecs.BOOL, HandS2CPayload::impact,
            ByteBufCodecs.FLOAT, HandS2CPayload::scale,
            HandPose.STREAM_CODEC, HandS2CPayload::pose,
            HandS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
