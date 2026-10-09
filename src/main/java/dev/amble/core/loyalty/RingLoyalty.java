package dev.amble.core.loyalty;

import com.mojang.math.Transformation;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.blocks.LanternBlock;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.oath.VoiceAnswers;
import dev.amble.core.progression.Emotion;
import dev.amble.core.progression.SpectrumMeters;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RingLoyalty {
    private static final double SKY_ENTRY_HEIGHT = 48.0;
    private static final double SKY_ENTRY_SPREAD = 24.0;
    private static final double START_SPEED = 0.35;
    private static final double ACCELERATION = 0.04;
    private static final double MAX_SPEED = 2.5;
    private static final double ARRIVAL_DISTANCE = 1.0;
    private static final int MAX_FLIGHT_TICKS = 20 * 60;
    private static final int TRAIL_STEPS = 4;
    private static final double GIFT_DIRECT_RANGE = 256.0;
    private static final int QUESTION_TICKS = 20 * 60;
    private static final int QUESTION_FADE = 10;
    private static final double HOVER_DISTANCE = 1.3;
    private static final double HOVER_DROP = 0.15;
    private static final float HOVER_SCALE = 0.6F;
    private static final float HOVER_SPIN = 4.0F;
    private static final double HOVER_BOB = 0.04;
    private static final String HOVER_TAG = "brightestday_ring_question";
    private static final Set<LanternCorps> GIFTABLE = EnumSet.of(LanternCorps.YELLOW, LanternCorps.STAR_SAPPHIRE);
    private static final Set<LanternCorps> SILENT = EnumSet.of(LanternCorps.INDIGO, LanternCorps.BLACK, LanternCorps.WHITE);

    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final List<ItemEntity> PENDING = new ArrayList<>();
    private static final Set<UUID> ASKED = new HashSet<>();
    private static final Set<UUID> DISPLAYS = new HashSet<>();
    private static final Map<UUID, VoiceAnswers.Answer> CHAT_ANSWERS = new HashMap<>();

    public interface Offer {
        boolean accept(ServerPlayer player, ItemStack ring);

        void refuse(ServerPlayer player, ItemStack ring);
    }

    private enum Kind { LOYALTY, BESTOW, GIFT, RETURN }

    private static final class Flight {
        final ServerLevel level;
        Vec3 position;
        final UUID target;
        final ItemStack ring;
        final Kind kind;
        final boolean asks;
        @Nullable UUID formerBearer;
        Set<UUID> refused = new HashSet<>();
        @Nullable UUID sender;
        @Nullable Offer offer;
        int age;
        int hover = -1;
        Display.@Nullable ItemDisplay display;

        Flight(ServerLevel level, Vec3 position, UUID target, ItemStack ring, Kind kind, boolean asks) {
            this.level = level;
            this.position = position;
            this.target = target;
            this.ring = ring;
            this.kind = kind;
            this.asks = asks;
        }

        boolean personal() {
            return this.kind == Kind.GIFT || this.kind == Kind.RETURN;
        }
    }

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) onDeath(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(RingLoyalty::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && giftable(item.getItem())) PENDING.add(item);
            if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(HOVER_TAG) && !DISPLAYS.contains(display.getUUID())) display.discard();
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!ASKED.contains(sender.getUUID())) return true;
            VoiceAnswers.Answer answer = VoiceAnswers.parse(message.signedContent());
            if (answer == null) return true;
            CHAT_ANSWERS.put(sender.getUUID(), answer);
            return false;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PENDING.clear();
            for (Flight flight : List.copyOf(FLIGHTS)) land(server, flight);
            FLIGHTS.clear();
        });
    }

    public static void bind(ItemStack ring, ServerLevel level, BlockPos pos) {
        if (PowerRingItem.getCorps(ring).map(LanternCorps::hasLantern).orElse(false)) {
            ring.set(BrightestDayComponents.BOUND_LANTERN, GlobalPos.of(level.dimension(), pos));
        }
    }

    public static boolean asks(LanternCorps corps) {
        return !SILENT.contains(corps);
    }

    private static void onDeath(ServerPlayer player) {
        ItemStack ring = BrightestDayAttachments.getRing(player);
        if (ring.isEmpty()) return;

        int threshold = BrightestDayConfig.get().ringLoyaltyDeaths;
        if (threshold <= 0) return;

        int deaths = ring.getOrDefault(BrightestDayComponents.RING_DEATHS, 0) + 1;
        int color = corps(ring).color();
        if (deaths < threshold) {
            ring.set(BrightestDayComponents.RING_DEATHS, deaths);
            BrightestDayAttachments.setRing(player, ring);
            player.sendSystemMessage(Component.translatable("message.brightestday.loyalty_wavers", threshold - deaths).withColor(color));
            return;
        }

        BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
        ring.remove(BrightestDayComponents.RING_DEATHS);
        RingBonds.release(player.level().getServer(), ring);
        player.sendSystemMessage(Component.translatable("message.brightestday.loyalty_departed").withStyle(ChatFormatting.ITALIC).withColor(color));
        depart(player.level(), player.getEyePosition(), ring, player.getUUID(), Set.of());
    }

    private static void depart(ServerLevel level, Vec3 origin, ItemStack ring, @Nullable UUID formerBearer, Set<UUID> refused) {
        MinecraftServer server = level.getServer();
        Set<UUID> excluded = new HashSet<>(refused);
        if (formerBearer != null) excluded.add(formerBearer);
        ServerPlayer target = findWorthy(server, level, origin, excluded, corps(ring));
        if (target == null) {
            drop(level, origin, ring);
            return;
        }

        Vec3 start = target.level() == level && target.position().distanceTo(origin) <= BrightestDayConfig.get().ringLoyaltySearchRadius
                ? origin
                : skyEntry(target);
        Flight flight = new Flight(target.level(), start, target.getUUID(), ring, Kind.LOYALTY, asks(corps(ring)));
        flight.formerBearer = formerBearer;
        flight.refused = new HashSet<>(refused);
        FLIGHTS.add(flight);
        flight.level.playSound(null, start.x, start.y, start.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.4F);
    }

    private static @Nullable ServerPlayer findWorthy(MinecraftServer server, ServerLevel level, Vec3 origin, Set<UUID> excluded, LanternCorps corps) {
        double radius = BrightestDayConfig.get().ringLoyaltySearchRadius;
        Emotion emotion = Emotion.of(corps).orElse(null);
        ServerPlayer best = null;
        int bestMeter = -1;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer candidate : level.players()) {
            if (!isWorthy(candidate, excluded)) continue;
            double distance = candidate.position().distanceToSqr(origin);
            if (distance > radius * radius) continue;
            int meter = emotion == null ? 0 : SpectrumMeters.get(candidate, emotion);
            if (meter > bestMeter || meter == bestMeter && distance < bestDistance) {
                best = candidate;
                bestMeter = meter;
                bestDistance = distance;
            }
        }
        if (best != null) return best;

        List<ServerPlayer> elsewhere = new ArrayList<>();
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (isWorthy(candidate, excluded)) elsewhere.add(candidate);
        }
        if (elsewhere.isEmpty()) return null;
        if (emotion != null) return elsewhere.stream().max(Comparator.comparingInt(candidate -> SpectrumMeters.get(candidate, emotion))).orElse(null);
        return elsewhere.get(level.getRandom().nextInt(elsewhere.size()));
    }

    public static void deliver(ServerPlayer target, ItemStack ring) {
        deliver(target, ring, asks(corps(ring)), null);
    }

    public static void deliver(ServerPlayer target, ItemStack ring, boolean ask) {
        deliver(target, ring, ask, null);
    }

    public static void offer(ServerPlayer target, ItemStack ring, Offer offer) {
        deliver(target, ring, asks(corps(ring)), offer);
    }

    private static void deliver(ServerPlayer target, ItemStack ring, boolean ask, @Nullable Offer offer) {
        Vec3 start = skyEntry(target);
        Flight flight = new Flight(target.level(), start, target.getUUID(), ring, Kind.BESTOW, ask);
        flight.offer = offer;
        FLIGHTS.add(flight);
        target.level().playSound(null, start.x, start.y, start.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.4F);
    }

    public static boolean inFlight(LanternCorps corps) {
        return FLIGHTS.stream().anyMatch(flight -> !flight.personal() && corps(flight.ring) == corps);
    }

    public static boolean awaiting(ServerPlayer player) {
        return FLIGHTS.stream().anyMatch(flight -> !flight.personal() && flight.target.equals(player.getUUID()));
    }

    private static boolean isWorthy(ServerPlayer player, Set<UUID> excluded) {
        return !excluded.contains(player.getUUID())
                && player.isAlive()
                && !player.isSpectator()
                && BrightestDayAttachments.getRing(player).isEmpty()
                && !RingBonds.bonded(player);
    }

    private static Vec3 skyEntry(ServerPlayer target) {
        float angle = target.getRandom().nextFloat() * Mth.TWO_PI;
        return target.getEyePosition().add(Mth.cos(angle) * SKY_ENTRY_SPREAD, SKY_ENTRY_HEIGHT, Mth.sin(angle) * SKY_ENTRY_SPREAD);
    }

    private static void tick(MinecraftServer server) {
        sendPending(server);
        Iterator<Flight> iterator = FLIGHTS.iterator();
        List<Flight> lost = new ArrayList<>();
        List<Flight> added = new ArrayList<>();
        while (iterator.hasNext()) {
            Flight flight = iterator.next();
            ServerPlayer target = server.getPlayerList().getPlayer(flight.target);
            boolean valid = target != null && target.level() == flight.level
                    && (flight.personal() || isWorthy(target, flight.refused));
            if (!valid || flight.hover < 0 && ++flight.age > MAX_FLIGHT_TICKS) {
                iterator.remove();
                finish(flight, target);
                if (flight.personal() && target != null && target.level() != flight.level && flight.age <= MAX_FLIGHT_TICKS) {
                    added.add(redirect(flight, target));
                } else {
                    lost.add(flight);
                }
                continue;
            }

            if (flight.hover >= 0) {
                VoiceAnswers.Answer answer = hover(target, flight);
                if (answer == null) continue;
                iterator.remove();
                finish(flight, target);
                if (answer == acceptance(corps(flight.ring))) accepted(server, target, flight);
                else refused(server, target, flight, added);
                continue;
            }

            Vec3 destination = target.getEyePosition().subtract(0.0, 0.4, 0.0);
            Vec3 path = destination.subtract(flight.position);
            double distance = path.length();
            double speed = Math.min(START_SPEED + flight.age * ACCELERATION, MAX_SPEED);
            if (distance <= Math.max(speed, ARRIVAL_DISTANCE)) {
                if (flight.kind == Kind.RETURN) {
                    iterator.remove();
                    giveBack(target, flight);
                } else if (flight.asks) {
                    if (!ASKED.contains(target.getUUID())) ask(target, flight);
                } else {
                    iterator.remove();
                    arrive(target, flight.ring);
                }
                continue;
            }

            Vec3 next = flight.position.add(path.scale(speed / distance));
            trail(flight.level, flight.position, next, corps(flight.ring).color());
            flight.position = next;
            if (flight.age % 20 == 0) {
                flight.level.playSound(null, next.x, next.y, next.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
            }
        }

        for (Flight flight : lost) {
            if (flight.kind == Kind.LOYALTY && flight.age <= MAX_FLIGHT_TICKS) depart(flight.level, flight.position, flight.ring, flight.formerBearer, flight.refused);
            else if (flight.kind == Kind.BESTOW && flight.offer == null && flight.age <= MAX_FLIGHT_TICKS) depart(flight.level, flight.position, flight.ring, null, flight.refused);
            else if (flight.kind == Kind.BESTOW && flight.offer != null) dissipate(flight);
            else drop(flight.level, flight.position, flight.ring);
        }
        FLIGHTS.addAll(added);
    }

    private static Flight redirect(Flight flight, ServerPlayer target) {
        Flight moved = new Flight(target.level(), skyEntry(target), target.getUUID(), flight.ring, flight.kind, flight.asks);
        moved.sender = flight.sender;
        moved.age = flight.age;
        return moved;
    }

    private static VoiceAnswers.Answer acceptance(LanternCorps corps) {
        return corps == LanternCorps.GREEN ? VoiceAnswers.Answer.NO : VoiceAnswers.Answer.YES;
    }

    private static void accepted(MinecraftServer server, ServerPlayer target, Flight flight) {
        switch (flight.kind) {
            case GIFT -> acceptGift(server, target, flight);
            case BESTOW -> {
                if (flight.offer == null || flight.offer.accept(target, flight.ring)) arrive(target, flight.ring);
                else dissipate(flight);
            }
            default -> arrive(target, flight.ring);
        }
    }

    private static void refused(MinecraftServer server, ServerPlayer target, Flight flight, List<Flight> added) {
        LanternCorps corps = corps(flight.ring);
        target.sendSystemMessage(Component.translatable("message.brightestday.ring_question.refused", corps.displayName()).withStyle(ChatFormatting.ITALIC).withColor(corps.color()));
        flight.level.playSound(null, flight.position.x, flight.position.y, flight.position.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        switch (flight.kind) {
            case GIFT -> returnGift(server, target, flight, added);
            case LOYALTY -> {
                Set<UUID> refused = new HashSet<>(flight.refused);
                refused.add(target.getUUID());
                depart(flight.level, flight.position, flight.ring, flight.formerBearer, refused);
            }
            default -> {
                if (flight.offer != null) flight.offer.refuse(target, flight.ring);
                else dissipate(flight);
            }
        }
    }

    private static void dissipate(Flight flight) {
        Vec3 at = flight.position;
        flight.level.sendParticles(new DustParticleOptions(corps(flight.ring).color(), 1.6F), at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.0);
        flight.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.05);
    }

    public static boolean giftable(ItemStack stack) {
        return stack.getItem() instanceof PowerRingItem
                && PowerRingItem.getCorps(stack).map(GIFTABLE::contains).orElse(false)
                && stack.has(DataComponents.CUSTOM_NAME)
                && !stack.has(BrightestDayComponents.SWORN_TO)
                && !stack.has(BrightestDayComponents.RING_BOND)
                && stack.getOrDefault(BrightestDayComponents.POWER_TYPE, 0) <= 0;
    }

    private static void sendPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<ItemEntity> pending = List.copyOf(PENDING);
        PENDING.clear();
        for (ItemEntity item : pending) {
            if (!item.isRemoved() && giftable(item.getItem())) send(server, item);
        }
    }

    private static void send(MinecraftServer server, ItemEntity item) {
        ItemStack stack = item.getItem();
        String name = stack.getHoverName().getString().trim();
        ServerPlayer sender = item.getOwner() instanceof ServerPlayer owner ? owner : null;
        ServerPlayer target = server.getPlayerList().getPlayerByName(name);
        int color = corps(stack).color();
        if (target == null) {
            if (sender != null) sender.sendOverlayMessage(Component.translatable("message.brightestday.ring_gift.offline", name).withColor(color));
            return;
        }

        ItemStack ring = stack.copy();
        ring.remove(DataComponents.CUSTOM_NAME);
        ServerLevel origin = (ServerLevel) item.level();
        Vec3 from = item.position().add(0.0, 0.25, 0.0);
        item.discard();

        Vec3 start = target.level() == origin && target.position().distanceTo(from) <= GIFT_DIRECT_RANGE ? from : skyEntry(target);
        Flight flight = new Flight(target.level(), start, target.getUUID(), ring, Kind.GIFT, true);
        flight.sender = sender == null ? new UUID(0L, 0L) : sender.getUUID();
        FLIGHTS.add(flight);
        origin.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.6F);
        origin.sendParticles(new DustParticleOptions(color, 1.6F), from.x, from.y, from.z, 20, 0.2, 0.3, 0.2, 0.0);
        if (sender != null) sender.sendOverlayMessage(Component.translatable("message.brightestday.ring_gift.sent", corps(ring).displayName(), target.getDisplayName()).withColor(color));
    }

    private static void ask(ServerPlayer target, Flight flight) {
        LanternCorps corps = corps(flight.ring);
        boolean voice = VoiceAnswers.listen(target);
        ASKED.add(target.getUUID());
        CHAT_ANSWERS.remove(target.getUUID());
        flight.hover = 0;
        RingSuspension.lift(target);

        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, flight.level);
        display.setItemStack(flight.ring.copy());
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBrightnessOverride(new Brightness(15, 15));
        display.setPosRotInterpolationDuration(2);
        display.setTransformation(new Transformation(null, null, new Vector3f(HOVER_SCALE), null));
        display.setGlowColorOverride(corps.color());
        display.setGlowingTag(true);
        display.addTag(HOVER_TAG);
        Vec3 center = hoverPoint(target);
        display.setPos(center.x, center.y, center.z);
        DISPLAYS.add(display.getUUID());
        flight.level.addFreshEntity(display);
        flight.display = display;
        flight.position = center;

        Component question = Component.translatable("message.brightestday.ring_question." + corps.getSerializedName());
        Component prompt = Component.translatable(voice ? "message.brightestday.ring_question.prompt.voice" : "message.brightestday.ring_question.prompt.chat");
        target.connection.send(new ClientboundSetTitlesAnimationPacket(QUESTION_FADE, QUESTION_TICKS, QUESTION_FADE));
        target.connection.send(new ClientboundSetSubtitleTextPacket(prompt.copy().withColor(corps.color())));
        target.connection.send(new ClientboundSetTitleTextPacket(question.copy().withColor(corps.color())));
        target.sendSystemMessage(question.copy().withStyle(ChatFormatting.BOLD).withColor(corps.color())
                .append(Component.literal(" ")).append(prompt.copy().withStyle(ChatFormatting.ITALIC)));
        flight.level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static Vec3 hoverPoint(ServerPlayer target) {
        return target.getEyePosition().add(target.getLookAngle().scale(HOVER_DISTANCE)).subtract(0.0, HOVER_DROP, 0.0);
    }

    private static VoiceAnswers.@Nullable Answer hover(ServerPlayer target, Flight flight) {
        Vec3 center = hoverPoint(target);
        Display.ItemDisplay display = flight.display;
        if (display != null && !display.isRemoved()) {
            display.setPos(center.x, center.y + Mth.sin(flight.hover * 0.12F) * HOVER_BOB, center.z);
            display.setYRot(flight.hover * HOVER_SPIN);
        }
        if (flight.hover % 6 == 0) {
            flight.level.sendParticles(new DustParticleOptions(corps(flight.ring).color(), 0.8F), center.x, center.y, center.z, 2, 0.12, 0.12, 0.12, 0.0);
        }
        if (flight.hover % 40 == 0) flight.level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.8F);
        flight.position = center;
        flight.hover++;

        UUID id = target.getUUID();
        VoiceAnswers.Answer answer = CHAT_ANSWERS.remove(id);
        if (answer == null) answer = VoiceAnswers.answer(id);
        if (answer == null && flight.hover >= QUESTION_TICKS) {
            VoiceAnswers.Answer accept = acceptance(corps(flight.ring));
            answer = accept == VoiceAnswers.Answer.YES ? VoiceAnswers.Answer.NO : VoiceAnswers.Answer.YES;
        }
        return answer;
    }

    private static void finish(Flight flight, @Nullable ServerPlayer target) {
        if (flight.display != null) {
            DISPLAYS.remove(flight.display.getUUID());
            flight.display.discard();
            flight.display = null;
        }
        if (flight.hover < 0) return;
        UUID id = flight.target;
        ASKED.remove(id);
        CHAT_ANSWERS.remove(id);
        VoiceAnswers.end(id);
        if (target != null) RingSuspension.release(target);
        if (target != null) target.connection.send(new ClientboundClearTitlesPacket(true));
    }

    private static void acceptGift(MinecraftServer server, ServerPlayer target, Flight flight) {
        ItemStack ring = flight.ring;
        LanternCorps corps = corps(ring);
        if (!target.addItem(ring)) target.drop(ring, false, Prediction.SERVER_ONLY);

        ServerLevel level = target.level();
        level.sendParticles(new DustParticleOptions(corps.color(), 1.8F), target.getX(), target.getY(1.0), target.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.2F, 1.2F);
        target.sendSystemMessage(Component.translatable("message.brightestday.ring_gift.received", corps.displayName()).withColor(corps.color()));

        ServerPlayer sender = flight.sender == null ? null : server.getPlayerList().getPlayer(flight.sender);
        if (sender != null && sender != target) {
            sender.sendOverlayMessage(Component.translatable("message.brightestday.ring_gift.delivered", corps.displayName(), target.getDisplayName()).withColor(corps.color()));
        }
    }

    private static void returnGift(MinecraftServer server, ServerPlayer target, Flight flight, List<Flight> added) {
        ServerPlayer sender = flight.sender == null ? null : server.getPlayerList().getPlayer(flight.sender);
        if (sender == null || sender == target) {
            drop(flight.level, flight.position, flight.ring);
            return;
        }
        Vec3 start = sender.level() == flight.level ? flight.position : skyEntry(sender);
        Flight back = new Flight(sender.level(), start, sender.getUUID(), flight.ring, Kind.RETURN, false);
        back.sender = flight.sender;
        added.add(back);
    }

    private static void giveBack(ServerPlayer sender, Flight flight) {
        ItemStack ring = flight.ring;
        LanternCorps corps = corps(ring);
        if (!sender.addItem(ring)) sender.drop(ring, false, Prediction.SERVER_ONLY);
        sender.level().playSound(null, sender.getX(), sender.getY(), sender.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
        sender.sendSystemMessage(Component.translatable("message.brightestday.ring_gift.returned", corps.displayName()).withColor(corps.color()));
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, int color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.4F);
        for (int i = 0; i < TRAIL_STEPS; i++) {
            Vec3 point = from.lerp(to, i / (double) TRAIL_STEPS);
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private static void land(MinecraftServer server, Flight flight) {
        ServerPlayer target = server.getPlayerList().getPlayer(flight.target);
        finish(flight, target);
        switch (flight.kind) {
            case RETURN -> {
                if (target != null) giveBack(target, flight);
                else drop(flight.level, flight.position, flight.ring);
            }
            case GIFT -> drop(flight.level, flight.position, flight.ring);
            default -> {
                if (target != null && isWorthy(target, flight.refused) && (flight.offer == null || flight.offer.accept(target, flight.ring))) arrive(target, flight.ring);
                else if (flight.offer == null) drop(flight.level, flight.position, flight.ring);
            }
        }
    }

    private static void arrive(ServerPlayer bearer, ItemStack ring) {
        LanternCorps corps = corps(ring);
        RingBonds.bind(bearer, ring);
        BrightestDayAttachments.setRing(bearer, ring);

        ServerLevel level = bearer.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, bearer.getX(), bearer.getY(1.0), bearer.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
        level.playSound(null, bearer.getX(), bearer.getY(), bearer.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.0F);

        bearer.sendSystemMessage(Component.translatable("message.brightestday.loyalty_chosen", corps.displayName())
                .withStyle(ChatFormatting.BOLD)
                .withColor(corps.color()));
        if (corps.hasLantern()) revealLantern(bearer, ring, corps);
    }

    private static void revealLantern(ServerPlayer bearer, ItemStack ring, LanternCorps corps) {
        GlobalPos bound = ring.get(BrightestDayComponents.BOUND_LANTERN);
        if (bound != null && lanternStands(bearer.level().getServer(), bound, corps)) {
            BlockPos pos = bound.pos();
            bearer.sendSystemMessage(Component.translatable("message.brightestday.loyalty_lantern_found",
                    pos.getX(), pos.getY(), pos.getZ(), bound.dimension().identifier().toString()).withColor(corps.color()));
            return;
        }

        BrightestDayBlocks.lantern(corps).ifPresent(lantern -> {
            ring.remove(BrightestDayComponents.BOUND_LANTERN);
            BrightestDayAttachments.setRing(bearer, ring);

            ItemStack gift = new ItemStack(lantern);
            if (!bearer.addItem(gift)) bearer.drop(gift, false, Prediction.SERVER_ONLY);
            bearer.sendSystemMessage(Component.translatable("message.brightestday.loyalty_lantern_lost").withColor(corps.color()));
        });
    }

    private static boolean lanternStands(MinecraftServer server, GlobalPos bound, LanternCorps corps) {
        ServerLevel level = server.getLevel(bound.dimension());
        return level != null
                && level.getBlockState(bound.pos()).getBlock() instanceof LanternBlock lantern
                && lantern.corps() == corps;
    }

    private static void drop(ServerLevel level, Vec3 position, ItemStack ring) {
        ItemEntity item = new ItemEntity(level, position.x, position.y, position.z, ring);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static LanternCorps corps(ItemStack ring) {
        return PowerRingItem.getCorps(ring).orElse(LanternCorps.GREEN);
    }

    private RingLoyalty() {}
}
