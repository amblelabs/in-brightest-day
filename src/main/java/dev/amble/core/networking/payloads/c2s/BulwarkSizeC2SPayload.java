package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.shields.BulwarkManager;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BulwarkSizeC2SPayload(int size) implements CustomPacketPayload {

    public static final Type<BulwarkSizeC2SPayload> TYPE =
            new Type<>(BrightestDay.id("bulwark_size"));

    public static final StreamCodec<ByteBuf, BulwarkSizeC2SPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(BulwarkSizeC2SPayload::new, BulwarkSizeC2SPayload::size);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        BulwarkManager.resize(context.player(), this.size);
    }
}
