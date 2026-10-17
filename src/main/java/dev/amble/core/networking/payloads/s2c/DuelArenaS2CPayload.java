package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.joml.Vector3fc;

public record DuelArenaS2CPayload(int id, Vector3fc center, float radius, int color) implements CustomPacketPayload {

    public static final Type<DuelArenaS2CPayload> TYPE =
            new Type<>(BrightestDay.id("duel_arena"));

    public static final StreamCodec<ByteBuf, DuelArenaS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelArenaS2CPayload::id,
                    ByteBufCodecs.VECTOR3F, DuelArenaS2CPayload::center,
                    ByteBufCodecs.FLOAT, DuelArenaS2CPayload::radius,
                    ByteBufCodecs.INT, DuelArenaS2CPayload::color,
                    DuelArenaS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
