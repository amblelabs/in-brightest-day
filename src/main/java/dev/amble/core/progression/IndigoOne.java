package dev.amble.core.progression;

import dev.amble.core.loyalty.RingBonds;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.BrightestDayItems;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingLoyalty;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import dev.amble.core.team.LanternTeams;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import dev.amble.core.networking.payloads.s2c.TribeRosterS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class IndigoOne {
    private static final int CHANNEL_TICKS = 100;
    private static final int FORCED_CHANNEL_TICKS = 160;
    private static final float FORCE_CHARGE = 0.5F;
    private static final float FORCE_HEALTH = 0.3F;
    private static final double CHANNEL_RANGE = 5.0;
    private static final int INDIGO = LanternCorps.INDIGO.color();
    private static final Identifier HOLD = BrightestDay.id("indigo_hold");
    private static final int GATHER_TICKS = 60;
    private static final long GATHER_COOLDOWN = 20L * 60 * 5;

    private record Channel(UUID target, long ends, float indigoHealth, float targetHealth, boolean frozen) {}

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, Long> LAST_GIFT = new HashMap<>();
    private record Gathering(long until, Optional<UUID> target) {}

    private static final Map<UUID, Gathering> GATHERING = new HashMap<>();
    private static final Map<UUID, Long> GATHERED_AT = new HashMap<>();
    private static final long REGIFT_COOLDOWN = 24000L;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(IndigoOne::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer indigo) || !(entity instanceof ServerPlayer target)) return InteractionResult.PASS;
            return embrace(indigo, target) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0.0F || !(entity instanceof ServerPlayer victim) || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
            if (PowerRingItem.getWornCorps(attacker).orElse(null) != LanternCorps.INDIGO) return;
            BrightestDayConfig config = BrightestDayConfig.get();
            if (config.indigoSlowSeconds <= 0) return;
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, config.indigoSlowSeconds * 20, Math.max(0, config.indigoSlowLevel - 1)), attacker);
        });
    }

    public static boolean multiplayer(MinecraftServer server) {
        return server.isDedicatedServer() || server.isPublished();
    }

    public static boolean isIndigoOne(Player player) {
        MinecraftServer server = player.level().getServer();
        return server != null && WorldProgress.get(server).indigoOne().map(player.getUUID()::equals).orElse(false);
    }

    private static boolean embrace(ServerPlayer indigo, ServerPlayer target) {
        if (!indigo.getMainHandItem().isEmpty()) return false;
        return begin(indigo, target);
    }

    public static boolean begin(ServerPlayer indigo, ServerPlayer target) {
        if (PowerRingItem.getCorps(BrightestDayAttachments.getRing(indigo)).orElse(null) != LanternCorps.INDIGO) return false;

        LanternCorps corps = PowerRingItem.getCorps(BrightestDayAttachments.getRing(target)).orElse(null);
        if (corps == null || corps == LanternCorps.INDIGO) return false;
        if (corps == LanternCorps.RED) {
            indigo.sendOverlayMessage(Component.translatable("message.brightestday.indigo.red_heart").withColor(INDIGO));
            return true;
        }
        boolean willing = SpectrumMeters.get(target, Emotion.COMPASSION) >= Emotion.GATE;
        if (!willing && !weakened(target)) {
            indigo.sendOverlayMessage(Component.translatable("message.brightestday.indigo.closed", target.getDisplayName()).withColor(INDIGO));
            return true;
        }
        if (CHANNELS.containsKey(indigo.getUUID())) return true;
        if (!CorpsCaps.admits(target, LanternCorps.INDIGO)) {
            CorpsCaps.refuse(indigo, LanternCorps.INDIGO);
            return true;
        }

        int ticks = willing ? CHANNEL_TICKS : FORCED_CHANNEL_TICKS;
        boolean frozen = PowerRingItem.getChargeFraction(BrightestDayAttachments.getRing(target)) < FORCE_CHARGE;
        if (frozen) freeze(target);
        CHANNELS.put(indigo.getUUID(), new Channel(target.getUUID(), indigo.level().getGameTime() + ticks, indigo.getHealth(), target.getHealth(), frozen));
        target.sendSystemMessage(Component.translatable(willing ? "message.brightestday.indigo.embracing" : "message.brightestday.indigo.forcing", indigo.getDisplayName())
                .withStyle(ChatFormatting.ITALIC).withColor(INDIGO));
        indigo.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.7F);
        return true;
    }

    private static boolean weakened(ServerPlayer target) {
        return PowerRingItem.getChargeFraction(BrightestDayAttachments.getRing(target)) < FORCE_CHARGE
                || target.getHealth() < target.getMaxHealth() * FORCE_HEALTH;
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        CHANNELS.entrySet().removeIf(entry -> {
            ServerPlayer indigo = server.getPlayerList().getPlayer(entry.getKey());
            Channel channel = entry.getValue();
            ServerPlayer target = server.getPlayerList().getPlayer(channel.target());
            if (indigo == null || target == null || indigo.level() != target.level() || indigo.distanceTo(target) > CHANNEL_RANGE
                    || indigo.getHealth() < channel.indigoHealth() || target.getHealth() < channel.targetHealth()) {
                if (indigo != null) indigo.sendOverlayMessage(Component.translatable("message.brightestday.indigo.broken").withColor(INDIGO));
                if (target != null && channel.frozen()) release(target);
                return true;
            }
            if (channel.frozen()) hold(target);

            Vec3 from = indigo.getEyePosition();
            Vec3 to = target.getBoundingBox().getCenter();
            for (int i = 0; i < 6; i++) {
                Vec3 point = from.lerp(to, indigo.getRandom().nextDouble());
                indigo.level().sendParticles(new DustParticleOptions(INDIGO, 1.2F), point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            if (now < channel.ends()) return false;

            if (channel.frozen()) release(target);
            if (CorpsCaps.check(indigo, LanternCorps.INDIGO) && CorpsCaps.admits(target, LanternCorps.INDIGO)) convert(indigo, target);
            return true;
        });
        tickGather(server, now);

        if (server.getTickCount() % 100 != 0 || !multiplayer(server)) return;
        Optional<UUID> current = WorldProgress.get(server).indigoOne();
        if (current.isPresent()) {
            ServerPlayer bearer = server.getPlayerList().getPlayer(current.get());
            if (bearer != null && !bearer.isSpectator() && BrightestDayAttachments.getRing(bearer).isEmpty() && !RingBonds.bonded(bearer) && !RingLoyalty.inFlight(LanternCorps.INDIGO)) {
                Long gifted = LAST_GIFT.get(bearer.getUUID());
                if (gifted == null || now - gifted >= REGIFT_COOLDOWN) {
                    LAST_GIFT.put(bearer.getUUID(), now);
                    if (gifted != null) RingOffers.bestow(bearer, LanternCorps.INDIGO);
                }
            }
            return;
        }
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.size() < BrightestDayConfig.get().indigoMinPlayers) return;
        List<ServerPlayer> ringless = online.stream().filter(player -> BrightestDayAttachments.getRing(player).isEmpty() && !player.isSpectator() && !RingBonds.bonded(player)).toList();
        if (ringless.isEmpty()) return;

        ServerPlayer chosen = ringless.get(server.overworld().getRandom().nextInt(ringless.size()));
        LAST_GIFT.put(chosen.getUUID(), now);
        WorldProgress.update(server, state -> state.withIndigoOne(chosen.getUUID()));
        chosen.sendSystemMessage(Component.translatable("message.brightestday.indigo.chosen").withStyle(ChatFormatting.BOLD).withColor(INDIGO));
        RingOffers.bestow(chosen, LanternCorps.INDIGO);
    }

    private static void freeze(ServerPlayer target) {
        for (AttributeInstance attribute : new AttributeInstance[]{target.getAttribute(Attributes.MOVEMENT_SPEED), target.getAttribute(Attributes.JUMP_STRENGTH)}) {
            if (attribute != null && !attribute.hasModifier(HOLD)) attribute.addTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        hold(target);
        target.sendOverlayMessage(Component.translatable("message.brightestday.indigo.held").withColor(INDIGO));
    }

    private static void hold(ServerPlayer target) {
        if (FlightRingPower.isFlying(target)) FlightRingPower.setEnabled(target, false);
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
        target.needsSync = true;
    }

    private static void release(ServerPlayer target) {
        for (AttributeInstance attribute : new AttributeInstance[]{target.getAttribute(Attributes.MOVEMENT_SPEED), target.getAttribute(Attributes.JUMP_STRENGTH)}) {
            if (attribute != null) attribute.removeModifier(HOLD);
        }
    }

    private static List<ServerPlayer> tribe(ServerPlayer leader) {
        List<ServerPlayer> members = new ArrayList<>();
        for (ServerPlayer member : leader.level().getServer().getPlayerList().getPlayers()) {
            if (member == leader || member.isSpectator() || PowerRingItem.getWornCorps(member).orElse(null) != LanternCorps.INDIGO) continue;
            members.add(member);
        }
        return members;
    }

    private static boolean ready(ServerPlayer leader) {
        if (!isIndigoOne(leader) || GATHERING.containsKey(leader.getUUID())) return false;
        Long last = GATHERED_AT.get(leader.getUUID());
        long now = leader.level().getGameTime();
        if (last != null && now - last < GATHER_COOLDOWN) {
            leader.sendOverlayMessage(Component.translatable("message.brightestday.indigo.gather_cooldown", (GATHER_COOLDOWN - (now - last) + 19) / 20).withColor(INDIGO));
            return false;
        }
        return true;
    }

    public static void roster(ServerPlayer leader) {
        if (!ready(leader)) return;
        List<TribeRosterS2CPayload.Member> members = tribe(leader).stream()
                .map(member -> new TribeRosterS2CPayload.Member(member.getUUID(), member.getGameProfile().name()))
                .toList();
        if (members.isEmpty()) {
            leader.sendOverlayMessage(Component.translatable("message.brightestday.indigo.gather_empty").withColor(INDIGO));
            return;
        }
        ServerPlayNetworking.send(leader, new TribeRosterS2CPayload(members));
    }

    public static void gather(ServerPlayer leader, Optional<UUID> target) {
        if (!ready(leader)) return;
        long now = leader.level().getGameTime();
        GATHERING.put(leader.getUUID(), new Gathering(now + GATHER_TICKS, target));
        leader.sendOverlayMessage(Component.translatable("message.brightestday.indigo.gathering").withColor(INDIGO));
        leader.level().playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    private static void tickGather(MinecraftServer server, long now) {
        GATHERING.entrySet().removeIf(entry -> {
            ServerPlayer leader = server.getPlayerList().getPlayer(entry.getKey());
            if (leader == null || !leader.isAlive() || !isIndigoOne(leader)) return true;
            Gathering gathering = entry.getValue();
            float angle = now * 0.35F;
            for (int strand = 0; strand < 3; strand++) {
                float a = angle + strand * Mth.TWO_PI / 3.0F;
                leader.level().sendParticles(new DustParticleOptions(INDIGO, 1.4F), leader.getX() + Mth.cos(a) * 1.2, leader.getY() + 0.2 + (gathering.until() - now) % 20 * 0.1, leader.getZ() + Mth.sin(a) * 1.2, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (now < gathering.until()) return false;

            GATHERED_AT.put(leader.getUUID(), now);
            int called = 0;
            for (ServerPlayer member : tribe(leader)) {
                if (gathering.target().isPresent() && !gathering.target().get().equals(member.getUUID())) continue;
                Vec3 offset = new Vec3(member.getRandom().nextDouble() * 3.0 - 1.5, 0.0, member.getRandom().nextDouble() * 3.0 - 1.5);
                member.teleportTo(leader.level(), leader.getX() + offset.x, leader.getY(), leader.getZ() + offset.z, Set.of(), member.getYRot(), member.getXRot(), true);
                member.sendSystemMessage(Component.translatable("message.brightestday.indigo.gathered", leader.getDisplayName()).withStyle(ChatFormatting.ITALIC).withColor(INDIGO));
                member.level().playSound(null, member.getX(), member.getY(), member.getZ(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
                called++;
            }
            leader.sendOverlayMessage(Component.translatable("message.brightestday.indigo.gather_done", called).withColor(INDIGO));
            leader.level().playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.7F);
            return true;
        });
    }

    private static void convert(ServerPlayer indigo, ServerPlayer target) {
        ItemStack old = BrightestDayAttachments.getRing(target);
        float charge = PowerRingItem.getChargeFraction(old);
        ItemStack ring = new ItemStack(BrightestDayItems.ring(LanternCorps.INDIGO));
        ring.set(BrightestDayComponents.POWER_TYPE, Math.round(RingRanks.capacity(target, LanternCorps.INDIGO) * charge));
        RingBonds.release(target.level().getServer(), old);
        RingBonds.bind(target, ring);
        BrightestDayAttachments.setRing(target, ring);
        BrightestDayBlocks.lantern(LanternCorps.INDIGO).ifPresent(lantern -> {
            ItemStack gift = new ItemStack(lantern);
            if (!target.addItem(gift)) target.drop(gift, false, Prediction.SERVER_ONLY);
        });

        Component message = Component.translatable("message.brightestday.indigo.converted", target.getDisplayName()).withStyle(ChatFormatting.BOLD).withColor(INDIGO);
        indigo.sendSystemMessage(message);
        target.sendSystemMessage(message);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
        LanternTeams.joinTribe(target);
        RingRanks.fire(indigo, Trigger.CONVERT, Milestone.Context.of(target));
    }

    private IndigoOne() {}
}
