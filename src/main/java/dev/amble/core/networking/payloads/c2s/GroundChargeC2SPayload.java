package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.flight.GroundLaunch;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GroundChargeC2SPayload(boolean charging) implements CustomPacketPayload {

    public static final Type<GroundChargeC2SPayload> TYPE =
            new Type<>(BrightestDay.id("ground_charge"));

    public static final StreamCodec<ByteBuf, GroundChargeC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, GroundChargeC2SPayload::charging,
                    GroundChargeC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        GroundLaunch.charging(context.player(), this.charging);
    }
}
