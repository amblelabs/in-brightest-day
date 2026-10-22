package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.hand.HandPose;
import dev.amble.core.hand.HandPoses;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandPoseSaveC2SPayload(int slot, String name, HandPose pose) implements CustomPacketPayload {
    public static final Type<HandPoseSaveC2SPayload> TYPE = new Type<>(BrightestDay.id("hand_pose_save"));

    public static final StreamCodec<ByteBuf, HandPoseSaveC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HandPoseSaveC2SPayload::slot,
            ByteBufCodecs.stringUtf8(HandPoses.MAX_NAME * 4), HandPoseSaveC2SPayload::name,
            HandPose.STREAM_CODEC, HandPoseSaveC2SPayload::pose,
            HandPoseSaveC2SPayload::new
    );

    public void handle(ServerPlayNetworking.Context context) {
        HandPoses.save(context.player(), this.slot, this.name, this.pose);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
