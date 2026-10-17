package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.comms.Comms;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VoiceMutedC2SPayload(boolean muted) implements CustomPacketPayload {

    public static final Type<VoiceMutedC2SPayload> TYPE =
            new Type<>(BrightestDay.id("voice_muted"));

    public static final StreamCodec<ByteBuf, VoiceMutedC2SPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, VoiceMutedC2SPayload::muted, VoiceMutedC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        Comms.setMuted(context.player().getUUID(), this.muted);
    }
}
