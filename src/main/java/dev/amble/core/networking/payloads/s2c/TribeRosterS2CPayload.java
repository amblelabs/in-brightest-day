package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

public record TribeRosterS2CPayload(List<Member> members) implements CustomPacketPayload {

    public record Member(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Member> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                Member::new);
    }

    public static final Type<TribeRosterS2CPayload> TYPE =
            new Type<>(BrightestDay.id("tribe_roster"));

    public static final StreamCodec<ByteBuf, TribeRosterS2CPayload> CODEC =
            Member.CODEC.apply(ByteBufCodecs.list()).map(TribeRosterS2CPayload::new, TribeRosterS2CPayload::members);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
