package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.visuals.InsigniaAnchor;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetInsigniaAnchorC2SPayload(InsigniaAnchor anchor) implements CustomPacketPayload {

    public static final Type<SetInsigniaAnchorC2SPayload> TYPE =
            new Type<>(BrightestDay.id("set_insignia_anchor"));

    public static final StreamCodec<ByteBuf, SetInsigniaAnchorC2SPayload> CODEC =
            InsigniaAnchor.STREAM_CODEC.map(SetInsigniaAnchorC2SPayload::new, SetInsigniaAnchorC2SPayload::anchor);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        BrightestDayAttachments.setInsigniaAnchor(context.player(), this.anchor.sanitized());
    }
}
