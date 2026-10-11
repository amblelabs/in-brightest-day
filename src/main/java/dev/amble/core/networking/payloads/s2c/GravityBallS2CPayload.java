package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GravityBallS2CPayload(int id, boolean active, double x, double y, double z, int strength, int color) implements CustomPacketPayload {

    public static final Type<GravityBallS2CPayload> TYPE =
            new Type<>(BrightestDay.id("gravity_ball"));

    public static final StreamCodec<ByteBuf, GravityBallS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GravityBallS2CPayload::id,
                    ByteBufCodecs.BOOL, GravityBallS2CPayload::active,
                    ByteBufCodecs.DOUBLE, GravityBallS2CPayload::x,
                    ByteBufCodecs.DOUBLE, GravityBallS2CPayload::y,
                    ByteBufCodecs.DOUBLE, GravityBallS2CPayload::z,
                    ByteBufCodecs.VAR_INT, GravityBallS2CPayload::strength,
                    ByteBufCodecs.INT, GravityBallS2CPayload::color,
                    GravityBallS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
