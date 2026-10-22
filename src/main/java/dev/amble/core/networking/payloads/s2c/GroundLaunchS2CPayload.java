package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GroundLaunchS2CPayload(int playerId, int state) implements CustomPacketPayload {

    public static final Type<GroundLaunchS2CPayload> TYPE =
            new Type<>(BrightestDay.id("ground_launch_state"));

    public static final StreamCodec<ByteBuf, GroundLaunchS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GroundLaunchS2CPayload::playerId,
                    ByteBufCodecs.VAR_INT, GroundLaunchS2CPayload::state,
                    GroundLaunchS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
