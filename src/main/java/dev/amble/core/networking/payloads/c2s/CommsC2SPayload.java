package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.comms.Comms;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CommsC2SPayload(Action action) implements CustomPacketPayload {
    public enum Action {
        NEXT,
        PREVIOUS,
        START,
        STOP,
        TEAM
    }

    public static final Type<CommsC2SPayload> TYPE =
            new Type<>(BrightestDay.id("comms"));

    public static final StreamCodec<ByteBuf, CommsC2SPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> new CommsC2SPayload(Action.values()[Math.floorMod(ordinal, Action.values().length)]), payload -> payload.action().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        switch (this.action) {
            case NEXT -> Comms.cycle(context.player(), 1);
            case PREVIOUS -> Comms.cycle(context.player(), -1);
            case START -> Comms.start(context.player());
            case STOP -> Comms.stop(context.server(), context.player().getUUID());
            case TEAM -> Comms.toggleTeam(context.player());
        }
    }
}
