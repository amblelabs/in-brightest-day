package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.walls.WallLines;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record WallLineC2SPayload(Vec3 start, Vec3 end) implements CustomPacketPayload {
    public static final Type<WallLineC2SPayload> TYPE = new Type<>(BrightestDay.id("wall_line"));

    public static final StreamCodec<ByteBuf, WallLineC2SPayload> CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, WallLineC2SPayload::start,
            Vec3.STREAM_CODEC, WallLineC2SPayload::end,
            WallLineC2SPayload::new
    );

    public void handle(ServerPlayNetworking.Context context) {
        WallLines.set(context.player(), this.start, this.end);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
