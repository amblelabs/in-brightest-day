package dev.amble.core.networking.payloads.c2s;

import dev.amble.BrightestDay;
import dev.amble.core.forge.ForgeLight;
import dev.amble.core.networking.payloads.s2c.ForgeStrokeS2CPayload;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record ForgeStrokeC2SPayload(int action, List<Vec3> directions) implements CustomPacketPayload {

    public static final int START = 0;
    public static final int POINTS = 1;
    public static final int END_SUCCESS = 2;
    public static final int END_FAIL = 3;
    public static final int MAX_POINTS = 64;

    public static final Type<ForgeStrokeC2SPayload> TYPE =
            new Type<>(BrightestDay.id("forge_stroke"));

    public static final StreamCodec<ByteBuf, ForgeStrokeC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ForgeStrokeC2SPayload::action,
                    Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_POINTS)), ForgeStrokeC2SPayload::directions,
                    ForgeStrokeC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        boolean drawing = this.action == START || this.action == POINTS;
        if (drawing && (ArmedRingPower.isAbilityMode(player)
                || ArmedRingPower.selectedConstruct(player).orElse(null) != RingPowerRegistry.TOOL_FORGE)) return;
        if (this.action == START) ArmedRingPower.raise(player);
        if (drawing && !this.directions.isEmpty()) ForgeLight.follow(player, this.directions.getLast());
        else if (!drawing) ForgeLight.clear(player);

        ForgeStrokeS2CPayload relay = new ForgeStrokeS2CPayload(player.getId(), this.action, this.directions);
        ServerPlayNetworking.send(player, relay);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, relay);
        }
    }
}
