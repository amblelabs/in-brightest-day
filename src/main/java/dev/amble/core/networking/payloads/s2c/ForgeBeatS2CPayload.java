package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ForgeBeatS2CPayload(boolean active, long beatAt, int strike, int total, int perfects, int streak, int color, int feedback) implements CustomPacketPayload {
    public static final int NONE_FEEDBACK = 0;
    public static final int HIT = 1;
    public static final int MISS = 2;
    public static final int PERFECT = 3;

    public static final Type<ForgeBeatS2CPayload> TYPE =
            new Type<>(BrightestDay.id("forge_beat"));

    public static final StreamCodec<ByteBuf, ForgeBeatS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ForgeBeatS2CPayload::active,
                    ByteBufCodecs.VAR_LONG, ForgeBeatS2CPayload::beatAt,
                    ByteBufCodecs.VAR_INT, ForgeBeatS2CPayload::strike,
                    ByteBufCodecs.VAR_INT, ForgeBeatS2CPayload::total,
                    ByteBufCodecs.VAR_INT, ForgeBeatS2CPayload::perfects,
                    ByteBufCodecs.VAR_INT, ForgeBeatS2CPayload::streak,
                    ByteBufCodecs.INT, ForgeBeatS2CPayload::color,
                    ByteBufCodecs.VAR_INT, ForgeBeatS2CPayload::feedback,
                    ForgeBeatS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
