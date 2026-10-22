package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.flight.GroundLaunch;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GroundLaunchC2SPayload(int chargeTicks) implements CustomPacketPayload {

    public static final Type<GroundLaunchC2SPayload> TYPE =
            new Type<>(BrightestDay.id("ground_launch"));

    public static final StreamCodec<ByteBuf, GroundLaunchC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GroundLaunchC2SPayload::chargeTicks,
                    GroundLaunchC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        if (!GroundLaunch.launch(context.player(), this.chargeTicks)) GroundLaunch.charging(context.player(), false);
    }
}
