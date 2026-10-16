package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.joml.Vector3fc;

import java.util.List;

public record CarriedS2CPayload(int ownerId, int source, float reach, List<Entry> carried) implements CustomPacketPayload {
    public static final int SPHERE = 0;
    public static final int MAGNET = 1;

    public record Entry(int entityId, Vector3fc offset) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::entityId,
                ByteBufCodecs.VECTOR3F, Entry::offset,
                Entry::new
        );
    }

    public static final Type<CarriedS2CPayload> TYPE =
            new Type<>(BrightestDay.id("carried"));

    public static final StreamCodec<ByteBuf, CarriedS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CarriedS2CPayload::ownerId,
                    ByteBufCodecs.VAR_INT, CarriedS2CPayload::source,
                    ByteBufCodecs.FLOAT, CarriedS2CPayload::reach,
                    Entry.CODEC.apply(ByteBufCodecs.list()), CarriedS2CPayload::carried,
                    CarriedS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
