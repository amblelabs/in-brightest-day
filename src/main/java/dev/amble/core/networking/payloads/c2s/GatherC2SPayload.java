package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.impl.GatherRingPower;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.UUID;

public record GatherC2SPayload(boolean choose, Optional<UUID> target) implements CustomPacketPayload {

    public static final GatherC2SPayload REQUEST = new GatherC2SPayload(false, Optional.empty());

    public static final Type<GatherC2SPayload> TYPE =
            new Type<>(BrightestDay.id("gather_tribe"));

    public static final StreamCodec<ByteBuf, GatherC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GatherC2SPayload::choose,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), GatherC2SPayload::target,
            GatherC2SPayload::new);

    public static GatherC2SPayload whole() {
        return new GatherC2SPayload(true, Optional.empty());
    }

    public static GatherC2SPayload member(UUID member) {
        return new GatherC2SPayload(true, Optional.of(member));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        if (this.choose) GatherRingPower.fire(context.player(), this.target);
        else GatherRingPower.request(context.player());
    }
}
