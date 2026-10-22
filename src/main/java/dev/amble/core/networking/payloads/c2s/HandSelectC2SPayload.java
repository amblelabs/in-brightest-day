package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.hand.HandPoses;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandSelectC2SPayload(int direction, int pose) implements CustomPacketPayload {
    public static final Type<HandSelectC2SPayload> TYPE = new Type<>(BrightestDay.id("hand_select"));

    public static final StreamCodec<ByteBuf, HandSelectC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HandSelectC2SPayload::direction,
            ByteBufCodecs.VAR_INT, HandSelectC2SPayload::pose,
            HandSelectC2SPayload::new
    );

    public void handle(ServerPlayNetworking.Context context) {
        HandPoses.select(context.player(), this.direction, this.pose);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
