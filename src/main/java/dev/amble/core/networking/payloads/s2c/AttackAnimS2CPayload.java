package dev.amble.core.networking.payloads.s2c;

import dev.amble.BrightestDay;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public record AttackAnimS2CPayload(int playerId, Identifier attack, int phase) implements CustomPacketPayload {
    public static final int CHARGE = 0;
    public static final int FIRE = 1;
    public static final int STOP = 2;

    public static final Type<AttackAnimS2CPayload> TYPE =
            new Type<>(BrightestDay.id("attack_anim"));

    public static final StreamCodec<ByteBuf, AttackAnimS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AttackAnimS2CPayload::playerId,
                    Identifier.STREAM_CODEC, AttackAnimS2CPayload::attack,
                    ByteBufCodecs.VAR_INT, AttackAnimS2CPayload::phase,
                    AttackAnimS2CPayload::new
            );

    public static void broadcast(ServerPlayer player, Identifier attack, int phase) {
        AttackAnimS2CPayload payload = new AttackAnimS2CPayload(player.getId(), attack, phase);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
