package dev.amble.core.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.glide.GlideManager;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Pilgrimage {
    private static final int BLUE = LanternCorps.BLUE.color();
    private static final double CHECK_IN_RADIUS = 3.5;
    private static final double CHECK_IN_HEIGHT = 6.0;
    private static final double TELEPORT_DISTANCE = 6.0;
    private static final double MAX_SPEED = 1.2;
    private static final int SPEED_GRACE = 5;
    private static final int CHECK_INTERVAL = 10;

    public record State(boolean active, int next, int total, Optional<BlockPos> target) {
        public static final State NONE = new State(false, 0, 0, Optional.empty());

        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("active", false).forGetter(State::active),
                Codec.INT.optionalFieldOf("next", 0).forGetter(State::next),
                Codec.INT.optionalFieldOf("total", 0).forGetter(State::total),
                BlockPos.CODEC.optionalFieldOf("target").forGetter(State::target)
        ).apply(instance, State::new));

        public boolean complete() {
            return this.active && this.total > 0 && this.next >= this.total;
        }
    }

    public static final AttachmentType<State> STATE =
            AttachmentRegistry.<State>builder()
                    .initializer(() -> State.NONE)
                    .persistent(State.CODEC)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.fromCodec(State.CODEC), AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(BrightestDay.id("pilgrimage"));

    private record Trace(ResourceKey<Level> dimension, Vec3 position, int fastTicks) {}

    private static final Map<UUID, Trace> TRACES = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Pilgrimage::tick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> TRACES.remove(newPlayer.getUUID()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TRACES.remove(handler.player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRACES.remove(handler.player.getUUID()));
    }

    public static State get(ServerPlayer player) {
        return player.getAttachedOrElse(STATE, State.NONE);
    }

    public static boolean complete(ServerPlayer player) {
        return get(player).complete();
    }

    public static void set(ServerPlayer player, int next) {
        int total = BlueSanctuary.shrineCount();
        int clamped = Math.max(0, Math.min(next, total));
        player.setAttached(STATE, new State(clamped > 0, clamped, total, target(player.level().getServer(), clamped, total)));
    }

    public static void reset(ServerPlayer player) {
        player.setAttached(STATE, State.NONE);
        TRACES.remove(player.getUUID());
    }

    public static void abandon(ServerPlayer player) {
        if (!get(player).active()) return;
        reset(player);
        player.sendSystemMessage(Component.translatable("message.brightestday.pilgrimage.abandoned").withStyle(ChatFormatting.ITALIC).withColor(BLUE));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    private static Optional<BlockPos> target(MinecraftServer server, int next, int total) {
        WorldProgress world = WorldProgress.get(server);
        return next >= total ? world.sanctuary() : world.shrine(next);
    }

    private static void tick(MinecraftServer server) {
        boolean check = server.getTickCount() % CHECK_INTERVAL == 0;
        WorldProgress world = WorldProgress.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!world.mayBeBlessed(player.getUUID(), server.overworld().getGameTime(), BlueSanctuary.BLESSING_COOLDOWN)
                    || hopeBearer(player)) continue;
            State state = get(player);
            if (state.active() && !state.complete() && !player.isSpectator() && !player.isCreative()) {
                String reason = violation(player);
                if (reason != null) {
                    voidPilgrimage(player, reason);
                    continue;
                }
            } else {
                TRACES.remove(player.getUUID());
            }
            if (check && player.level().dimension() == Level.OVERWORLD) checkIn(player, state, world);
        }
    }

    private static boolean hopeBearer(ServerPlayer player) {
        if (PowerRingItem.getWornCorps(player).orElse(null) == LanternCorps.BLUE) return true;
        return RingBonds.owned(player.level().getServer(), player.getUUID()).map(entry -> entry.getValue().corps() == LanternCorps.BLUE).orElse(false);
    }

    private static String violation(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        boolean boating = vehicle instanceof AbstractBoat;
        if (vehicle != null && !boating) return "riding";
        if (player.getAbilities().flying || player.isFallFlying() || FlightRingPower.isFlying(player) || GlideManager.isGliding(player)) return "flying";

        Vec3 position = player.position();
        Trace trace = TRACES.get(player.getUUID());
        if (trace == null) {
            TRACES.put(player.getUUID(), new Trace(player.level().dimension(), position, 0));
            return null;
        }
        if (trace.dimension() != player.level().dimension()) return "teleport";

        Vec3 delta = position.subtract(trace.position());
        if (!boating && delta.length() > TELEPORT_DISTANCE) return "teleport";
        double horizontal = delta.horizontalDistance();
        int fast = !boating && horizontal > MAX_SPEED ? trace.fastTicks() + 1 : 0;
        if (fast > SPEED_GRACE) return "speed";
        TRACES.put(player.getUUID(), new Trace(player.level().dimension(), position, fast));
        return null;
    }

    private static void voidPilgrimage(ServerPlayer player, String reason) {
        reset(player);
        player.sendSystemMessage(Component.translatable("message.brightestday.pilgrimage.void." + reason).withStyle(ChatFormatting.BOLD).withColor(BLUE));
        player.sendSystemMessage(Component.translatable("message.brightestday.pilgrimage.restart").withStyle(ChatFormatting.ITALIC).withColor(BLUE));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    private static void checkIn(ServerPlayer player, State state, WorldProgress world) {
        int total = BlueSanctuary.shrineCount();
        if (state.complete()) return;

        int expected = state.active() ? state.next() : 0;
        Optional<BlockPos> shrine = world.shrine(expected);
        if (shrine.isPresent() && near(player, shrine.get())) {
            int next = expected + 1;
            player.setAttached(STATE, new State(true, next, total, target(player.level().getServer(), next, total)));
            TRACES.put(player.getUUID(), new Trace(player.level().dimension(), player.position(), 0));
            announce(player, shrine.get(), next, total, expected == 0);
            return;
        }

        if (!state.active()) return;
        for (WorldProgress.Shrine other : world.shrines()) {
            if (other.index() > expected && near(player, other.pos())) {
                player.sendOverlayMessage(Component.translatable("message.brightestday.pilgrimage.skipped", expected + 1).withColor(BLUE));
                return;
            }
        }
    }

    private static boolean near(ServerPlayer player, BlockPos shrine) {
        Vec3 center = Vec3.atBottomCenterOf(shrine);
        double dx = player.getX() - center.x;
        double dz = player.getZ() - center.z;
        return dx * dx + dz * dz <= CHECK_IN_RADIUS * CHECK_IN_RADIUS && Math.abs(player.getY() - center.y) <= CHECK_IN_HEIGHT;
    }

    private static void announce(ServerPlayer player, BlockPos shrine, int reached, int total, boolean first) {
        ServerLevel level = player.level();
        level.sendParticles(new DustParticleOptions(BLUE, 1.8F), shrine.getX() + 0.5, shrine.getY() + 1.5, shrine.getZ() + 0.5, 30, 0.5, 0.8, 0.5, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, shrine.getX() + 0.5, shrine.getY() + 2.0, shrine.getZ() + 0.5, 10, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, shrine, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.8F + 0.6F * reached / Math.max(1, total));

        if (first) {
            player.sendSystemMessage(Component.translatable("message.brightestday.pilgrimage.begin").withStyle(ChatFormatting.ITALIC).withColor(BLUE));
        }
        if (reached >= total) {
            player.sendSystemMessage(Component.translatable("message.brightestday.pilgrimage.finished").withStyle(ChatFormatting.BOLD).withColor(BLUE));
            SpectrumMeters.add(player, Emotion.HOPE, BrightestDayConfig.get().hopeShrine * 5);
        } else {
            player.sendOverlayMessage(Component.translatable("message.brightestday.pilgrimage.check_in", reached, total).withColor(BLUE));
            SpectrumMeters.add(player, Emotion.HOPE, BrightestDayConfig.get().hopeShrine);
        }
    }

    private Pilgrimage() {}
}
