package dev.amble.core.ringpowers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class CorpsSynergy {
    private static final int CHECK_INTERVAL = 10;
    private static final double EXIT_MARGIN = 1.25;
    private static final float HOPE_COST_MULTIPLIER = 0.25F;
    public static final float HOPE_BUBBLE_SCALE = 1.5F;
    private static final float DREAD_COST_MULTIPLIER = 2.0F;
    private static final float DREAD_SIZE_SCALE = 0.6F;
    public static final float DREAD_BUBBLE_SCALE = 0.75F;

    public record Influence(LanternCorps corps, boolean strengthens) {
        public static final Codec<Influence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LanternCorps.CODEC.fieldOf("corps").forGetter(Influence::corps),
                Codec.BOOL.fieldOf("strengthens").forGetter(Influence::strengthens)
        ).apply(instance, Influence::new));

        public static final StreamCodec<ByteBuf, Influence> STREAM_CODEC = StreamCodec.composite(
                LanternCorps.STREAM_CODEC, Influence::corps,
                ByteBufCodecs.BOOL, Influence::strengthens,
                Influence::new
        );
    }

    public record State(boolean hope, boolean will, boolean dread, boolean boost, List<Influence> influences) {
        public static final State NONE = new State(false, false, false, false, List.of());

        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("hope", false).forGetter(State::hope),
                Codec.BOOL.optionalFieldOf("will", false).forGetter(State::will),
                Codec.BOOL.optionalFieldOf("dread", false).forGetter(State::dread),
                Codec.BOOL.optionalFieldOf("boost", false).forGetter(State::boost),
                Influence.CODEC.listOf().optionalFieldOf("influences", List.of()).forGetter(State::influences)
        ).apply(instance, State::new));

        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::hope,
                ByteBufCodecs.BOOL, State::will,
                ByteBufCodecs.BOOL, State::dread,
                ByteBufCodecs.BOOL, State::boost,
                Influence.STREAM_CODEC.apply(ByteBufCodecs.list()), State::influences,
                State::new
        );

        boolean has(LanternCorps corps, boolean strengthens) {
            return this.influences.contains(new Influence(corps, strengthens));
        }
    }

    public static final AttachmentType<State> SYNERGY =
            AttachmentRegistry.<State>builder()
                    .initializer(() -> State.NONE)
                    .syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(BrightestDay.id("corps_synergy"));

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CorpsSynergy::tick);
    }

    public static State get(Player player) {
        return player.getAttachedOrElse(SYNERGY, State.NONE);
    }

    public static boolean empoweredByHope(Player player) {
        return get(player).hope();
    }

    public static boolean borrowsWill(Player player) {
        return get(player).will();
    }

    public static boolean weakenedByHope(Player player) {
        return get(player).dread();
    }

    public static int scaleCost(Player player, int amount) {
        if (amount <= 0) return amount;
        amount = CorpsCombat.scaleCost(player, amount);
        State state = get(player);
        if (state.hope() || state.boost()) return Math.max(1, Math.round(amount * HOPE_COST_MULTIPLIER));
        if (state.dread()) return Math.round(amount * DREAD_COST_MULTIPLIER);
        return amount;
    }

    public static int weakenSize(int minSize, int maxSize) {
        return Math.max(minSize, Math.round(maxSize * DREAD_SIZE_SCALE));
    }

    public static LanternCorps powerCorps(Player player, LanternCorps corps) {
        return corps == LanternCorps.BLUE && borrowsWill(player) ? LanternCorps.GREEN : corps;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;

        BrightestDayConfig config = BrightestDayConfig.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State previous = get(player);
            LanternCorps corps = PowerRingItem.hasCharge(player) ? PowerRingItem.getWornCorps(player).orElse(null) : null;
            List<Influence> influences = new ArrayList<>();
            boolean hope = false;
            boolean will = false;
            boolean boost = false;
            boolean dread = false;

            if (corps == LanternCorps.GREEN) {
                hope = near(player, previous, LanternCorps.BLUE, true, config.synergyLinkRadius);
                if (hope) influences.add(new Influence(LanternCorps.BLUE, true));
                if (!hope) {
                    for (LanternCorps shaker : List.of(LanternCorps.YELLOW, LanternCorps.ORANGE)) {
                        if (near(player, previous, shaker, false, config.synergyDreadRadius)) influences.add(new Influence(shaker, false));
                    }
                    dread = !influences.isEmpty();
                }
            } else if (corps == LanternCorps.BLUE) {
                will = near(player, previous, LanternCorps.GREEN, true, config.synergyLinkRadius);
                if (will) influences.add(new Influence(LanternCorps.GREEN, true));
            } else if (corps == LanternCorps.INDIGO) {
                boost = near(player, previous, LanternCorps.BLUE, true, config.synergyLinkRadius);
                if (boost) influences.add(new Influence(LanternCorps.BLUE, true));
            } else if (corps == LanternCorps.YELLOW || corps == LanternCorps.RED || corps == LanternCorps.ORANGE) {
                dread = near(player, previous, LanternCorps.BLUE, false, config.synergyDreadRadius);
                if (dread) influences.add(new Influence(LanternCorps.BLUE, false));
            }

            State next = new State(hope, will, dread, boost, List.copyOf(influences));
            if (next.equals(previous)) continue;
            player.setAttached(SYNERGY, next);
            announce(player, previous, next);
        }
    }

    private static boolean near(ServerPlayer player, State previous, LanternCorps corps, boolean strengthens, double radius) {
        return isNear(player, corps, previous.has(corps, strengthens) ? radius * EXIT_MARGIN : radius);
    }

    private static void announce(ServerPlayer player, State previous, State next) {
        for (Influence influence : next.influences()) {
            if (previous.influences().contains(influence)) continue;
            String key = influence.strengthens() ? "message.brightestday.synergy.strengthened" : "message.brightestday.synergy.weakened";
            player.sendOverlayMessage(Component.translatable(key, influence.corps().displayName()).withColor(influence.corps().color()));
            return;
        }
        for (Influence influence : previous.influences()) {
            if (next.influences().contains(influence)) continue;
            player.sendOverlayMessage(Component.translatable("message.brightestday.synergy.faded", influence.corps().displayName()).withColor(influence.corps().color()));
            return;
        }
    }

    private static boolean isNear(ServerPlayer player, LanternCorps corps, double radius) {
        for (ServerPlayer other : player.level().players()) {
            if (other == player || other.isSpectator() || other.distanceToSqr(player) > radius * radius) continue;
            if (PowerRingItem.hasCharge(other) && PowerRingItem.getWornCorps(other).orElse(null) == corps) return true;
        }
        return false;
    }

    private CorpsSynergy() {}
}
