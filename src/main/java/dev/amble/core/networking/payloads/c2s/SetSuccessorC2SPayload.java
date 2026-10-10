package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.loyalty.RingBonds;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetSuccessorC2SPayload(String name) implements CustomPacketPayload {

    public static final Type<SetSuccessorC2SPayload> TYPE =
            new Type<>(BrightestDay.id("set_successor"));

    public static final StreamCodec<ByteBuf, SetSuccessorC2SPayload> CODEC =
            ByteBufCodecs.stringUtf8(16).map(SetSuccessorC2SPayload::new, SetSuccessorC2SPayload::name);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        RingBonds.setSuccessor(context.player(), this.name);
    }
}
