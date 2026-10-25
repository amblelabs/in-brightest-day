package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record ShieldSpawnS2CPayload(int id, int entityId, Vec3 center, float radius, int color, int duration, int age, int casterId) implements CustomPacketPayload {

    public static final int NO_ENTITY = -1;

    public static final Type<ShieldSpawnS2CPayload> TYPE =
            new Type<>(BrightestDay.id("shield_spawn"));

    public static final StreamCodec<ByteBuf, ShieldSpawnS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ShieldSpawnS2CPayload::id,
                    ByteBufCodecs.INT, ShieldSpawnS2CPayload::entityId,
                    Vec3.STREAM_CODEC, ShieldSpawnS2CPayload::center,
                    ByteBufCodecs.FLOAT, ShieldSpawnS2CPayload::radius,
                    ByteBufCodecs.INT, ShieldSpawnS2CPayload::color,
                    ByteBufCodecs.VAR_INT, ShieldSpawnS2CPayload::duration,
                    ByteBufCodecs.VAR_INT, ShieldSpawnS2CPayload::age,
                    ByteBufCodecs.INT, ShieldSpawnS2CPayload::casterId,
                    ShieldSpawnS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
