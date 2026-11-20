package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.flight.Landings;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LandingC2SPayload(int pose, float strength) implements CustomPacketPayload {
    public static final Type<LandingC2SPayload> TYPE = new Type<>(BrightestDay.id("landing"));

    public static final StreamCodec<ByteBuf, LandingC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LandingC2SPayload::pose,
            ByteBufCodecs.FLOAT, LandingC2SPayload::strength,
            LandingC2SPayload::new
    );

    public void handle(ServerPlayNetworking.Context context) {
        Landings.relay(context.player(), this.pose, this.strength);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
