package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.visuals.Insignia;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToggleInsigniaC2SPayload() implements CustomPacketPayload {

    public static final ToggleInsigniaC2SPayload INSTANCE = new ToggleInsigniaC2SPayload();

    public static final Type<ToggleInsigniaC2SPayload> TYPE =
            new Type<>(BrightestDay.id("toggle_insignia"));

    public static final StreamCodec<ByteBuf, ToggleInsigniaC2SPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        Insignia.request(context.player());
    }
}
