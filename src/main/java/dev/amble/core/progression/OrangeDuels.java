package dev.amble.core.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingLoyalty;
import dev.amble.core.networking.payloads.s2c.DuelArenaS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.constructs.ConstructDismissal;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class OrangeDuels {
    private static final int COUNTDOWN_TICKS = 200;
    private static final int REFRESH_TICKS = 40;
    private static final int FLAME_INTERVAL = 5;
    private static final int FLAME_POINTS = 48;
    private static final double WALL_MARGIN = 1.0;
    private static final double SHOVE = 1.5;
    private static final Set<Relative> RELATIVE = EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.Y_ROT, Relative.X_ROT);

    public record Pending(UUID challenger, UUID holder) {
        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("challenger").forGetter(Pending::challenger),
                UUIDUtil.CODEC.fieldOf("holder").forGetter(Pending::holder)
        ).apply(instance, Pending::new));

        boolean involves(UUID player) {
            return this.challenger.equals(player) || this.holder.equals(player);
        }
    }

    public static final AttachmentType<List<Pending>> QUEUE = AttachmentRegistry.<List<Pending>>builder()
            .initializer(List::of)
            .persistent(Pending.CODEC.listOf())
            .buildAndRegister(BrightestDay.id("orange_duels"));

    public static final AttachmentType<Unit> DUELING = AttachmentRegistry.<Unit>builder()
            .syncWith(StreamCodec.unit(Unit.INSTANCE), AttachmentSyncPredicate.all())
            .buildAndRegister(BrightestDay.id("orange_dueling"));

    private static final class Fight {
        final UUID challenger;
        final UUID holder;
        final long beginsAt;
        long endsAt;
        @Nullable ServerLevel level;
        Vec3 center = Vec3.ZERO;
        double radius;
        boolean started;

        Fight(Pending pending, long beginsAt) {
            this.challenger = pending.challenger();
            this.holder = pending.holder();
            this.beginsAt = beginsAt;
        }

        boolean involves(UUID player) {
            return this.challenger.equals(player) || this.holder.equals(player);
        }

        UUID other(UUID player) {
            return this.challenger.equals(player) ? this.holder : this.challenger;
        }
    }

    private static final List<Fight> FIGHTS = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(OrangeDuels::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Fight fight : List.copyOf(FIGHTS)) {
                if (!fight.started) queue(server, new Pending(fight.challenger, fight.holder));
                release(server, fight);
            }
            FIGHTS.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            player.removeAttached(DUELING);
            for (Pending pending : pending(server)) {
                if (!pending.holder().equals(player.getUUID())) continue;
                Component name = displayName(server, pending.challenger());
                player.sendSystemMessage(Component.translatable("message.brightestday.duel.awaits", name).withStyle(ChatFormatting.BOLD).withColor(LanternCorps.ORANGE.color()));
            }
            for (Fight fight : FIGHTS) {
                if (fight.started && fight.level == player.level()) ServerPlayNetworking.send(player, arena(fight, true));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            for (Fight fight : List.copyOf(FIGHTS)) {
                if (!fight.involves(id)) continue;
                if (fight.started) finish(server, fight, fight.other(id), "message.brightestday.duel.forfeit", handler.player.getDisplayName());
                else {
                    FIGHTS.remove(fight);
                    queue(server, new Pending(fight.challenger, fight.holder));
                }
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            Fight fight = active(player.getUUID());
            if (fight == null) return true;
            player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.5F));
            player.clearFire();
            player.removeAllEffects();
            finish(player.level().getServer(), fight, fight.other(player.getUUID()), null, null);
            return false;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer victim) || !(source.getEntity() instanceof Player attacker) || attacker == victim) return true;
            Fight victimFight = active(victim.getUUID());
            Fight attackerFight = active(attacker.getUUID());
            if (victimFight == null && attackerFight == null) return true;
            return victimFight != null && victimFight == attackerFight;
        });
    }

    public static boolean dueling(Player player) {
        return player.hasAttached(DUELING);
    }

    public static boolean involved(MinecraftServer server, UUID player) {
        return FIGHTS.stream().anyMatch(fight -> fight.involves(player)) || pending(server).stream().anyMatch(pending -> pending.involves(player));
    }

    public static void challenge(ServerPlayer challenger, UUID holder) {
        MinecraftServer server = challenger.level().getServer();
        queue(server, new Pending(challenger.getUUID(), holder));
        Component name = displayName(server, holder);
        challenger.sendSystemMessage(Component.translatable("message.brightestday.duel.summoned", name).withStyle(ChatFormatting.BOLD).withColor(LanternCorps.ORANGE.color()));
        ServerPlayer online = server.getPlayerList().getPlayer(holder);
        if (online == null) {
            challenger.sendSystemMessage(Component.translatable("message.brightestday.duel.queued", name).withStyle(ChatFormatting.ITALIC).withColor(LanternCorps.ORANGE.color()));
        }
    }

    private static List<Pending> pending(MinecraftServer server) {
        return server.overworld().getAttachedOrElse(QUEUE, List.of());
    }

    private static void queue(MinecraftServer server, Pending pending) {
        List<Pending> queue = new ArrayList<>(pending(server));
        if (queue.contains(pending)) return;
        queue.add(pending);
        server.overworld().setAttached(QUEUE, List.copyOf(queue));
    }

    private static void dequeue(MinecraftServer server, Pending pending) {
        List<Pending> queue = new ArrayList<>(pending(server));
        if (queue.remove(pending)) server.overworld().setAttached(QUEUE, List.copyOf(queue));
    }

    private static @Nullable Fight active(UUID player) {
        for (Fight fight : FIGHTS) if (fight.started && fight.involves(player)) return fight;
        return null;
    }

    private static Component displayName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getDisplayName();
        return server.services().nameToIdCache().get(id).map(profile -> Component.literal(profile.name())).orElse(Component.literal("?"));
    }

    private static boolean ready(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isChangingDimension();
    }

    private static boolean bearsOrange(ServerPlayer player) {
        return !takeable(player).isEmpty();
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Pending pending : pending(server)) {
            if (FIGHTS.stream().anyMatch(fight -> fight.involves(pending.challenger()) || fight.involves(pending.holder()))) continue;
            ServerPlayer challenger = server.getPlayerList().getPlayer(pending.challenger());
            ServerPlayer holder = server.getPlayerList().getPlayer(pending.holder());
            if (holder != null && !bearsOrange(holder) || challenger != null && !BrightestDayAttachments.getRing(challenger).isEmpty()) {
                dequeue(server, pending);
                continue;
            }
            if (!ready(challenger) || !ready(holder)) continue;
            dequeue(server, pending);
            FIGHTS.add(new Fight(pending, now + COUNTDOWN_TICKS));
            Component call = Component.translatable("message.brightestday.duel.countdown").withStyle(ChatFormatting.BOLD).withColor(LanternCorps.ORANGE.color());
            holder.sendSystemMessage(Component.translatable("message.brightestday.duel.warning", challenger.getDisplayName()).withStyle(ChatFormatting.BOLD).withColor(LanternCorps.ORANGE.color()));
            holder.sendSystemMessage(call);
            challenger.sendSystemMessage(call);
        }

        for (Fight fight : List.copyOf(FIGHTS)) {
            if (!FIGHTS.contains(fight)) continue;
            ServerPlayer challenger = server.getPlayerList().getPlayer(fight.challenger);
            ServerPlayer holder = server.getPlayerList().getPlayer(fight.holder);
            if (!fight.started) {
                if (now < fight.beginsAt) continue;
                if (!ready(challenger) || !ready(holder) || !bearsOrange(holder)) {
                    FIGHTS.remove(fight);
                    if (holder != null && bearsOrange(holder)) queue(server, new Pending(fight.challenger, fight.holder));
                    continue;
                }
                begin(fight, challenger, holder, now);
                continue;
            }
            if (challenger == null || holder == null) continue;
            if (challenger.level() != fight.level) {
                finish(server, fight, fight.holder, "message.brightestday.duel.forfeit", challenger.getDisplayName());
                continue;
            }
            if (holder.level() != fight.level) {
                finish(server, fight, fight.challenger, "message.brightestday.duel.forfeit", holder.getDisplayName());
                continue;
            }
            if (now >= fight.endsAt) {
                finish(server, fight, fight.holder, "message.brightestday.duel.timeout", holder.getDisplayName());
                continue;
            }
            enforce(fight, challenger, holder);
            if (now % FLAME_INTERVAL == 0) flames(fight);
            if (now % REFRESH_TICKS == 0) broadcast(fight, true);
        }
    }

    private static void begin(Fight fight, ServerPlayer challenger, ServerPlayer holder, long now) {
        BrightestDayConfig config = BrightestDayConfig.get();
        fight.level = holder.level();
        fight.center = holder.position();
        fight.radius = config.orangeArenaRadius;
        fight.endsAt = now + config.orangeDuelSeconds * 20L;
        fight.started = true;
        Vec3 spot = fight.center.add(holder.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(fight.radius * 0.5));
        challenger.teleportTo(fight.level, spot.x, fight.center.y, spot.z, Set.of(), holder.getYRot() + 180.0F, 0.0F, true);
        for (ServerPlayer duelist : List.of(challenger, holder)) {
            ConstructDismissal.dismissAll(duelist);
            FlightRingPower.setEnabled(duelist, false);
            duelist.setAttached(DUELING, Unit.INSTANCE);
            duelist.sendSystemMessage(Component.translatable("message.brightestday.duel.begin", config.orangeDuelSeconds).withStyle(ChatFormatting.BOLD).withColor(LanternCorps.ORANGE.color()));
        }
        fight.level.playSound(null, fight.center.x, fight.center.y, fight.center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.5F);
        fight.level.playSound(null, fight.center.x, fight.center.y, fight.center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5F, 0.6F);
        broadcast(fight, true);
    }

    private static void enforce(Fight fight, ServerPlayer challenger, ServerPlayer holder) {
        double inner = fight.radius - WALL_MARGIN;
        for (ServerPlayer duelist : List.of(challenger, holder)) {
            Vec3 offset = duelist.position().subtract(fight.center).multiply(1.0, 0.0, 1.0);
            double distance = offset.length();
            if (distance <= inner) continue;
            Vec3 back = offset.scale(inner / distance - 1.0);
            duelist.teleportTo(fight.level, back.x, 0.0, back.z, RELATIVE, 0.0F, 0.0F, false);
            duelist.setDeltaMovement(Vec3.ZERO);
        }
        for (ServerPlayer other : fight.level.players()) {
            if (fight.involves(other.getUUID()) || other.isSpectator() || other.isCreative()) continue;
            Vec3 offset = other.position().subtract(fight.center).multiply(1.0, 0.0, 1.0);
            double distance = offset.length();
            if (distance > fight.radius + WALL_MARGIN) continue;
            Vec3 outward = distance < 1.0E-3 ? new Vec3(1.0, 0.0, 0.0) : offset.scale(1.0 / distance);
            Vec3 push = outward.scale(fight.radius + WALL_MARGIN + SHOVE - distance);
            other.teleportTo(fight.level, push.x, 0.0, push.z, RELATIVE, 0.0F, 0.0F, false);
            other.sendOverlayMessage(Component.translatable("message.brightestday.duel.walled").withColor(LanternCorps.ORANGE.color()));
        }
    }

    private static void flames(Fight fight) {
        double offset = (fight.level.getGameTime() % 40) / 40.0 * Mth.TWO_PI / FLAME_POINTS;
        for (int i = 0; i < FLAME_POINTS; i++) {
            double angle = i * Mth.TWO_PI / FLAME_POINTS + offset;
            double x = fight.center.x + Math.cos(angle) * fight.radius;
            double z = fight.center.z + Math.sin(angle) * fight.radius;
            fight.level.sendParticles(ParticleTypes.FLAME, x, fight.center.y + 0.2, z, 2, 0.1, 0.4, 0.1, 0.01);
        }
    }

    private static void finish(MinecraftServer server, Fight fight, UUID winner, @Nullable String reasonKey, @Nullable Component reasonName) {
        FIGHTS.remove(fight);
        release(server, fight);
        ServerPlayer challenger = server.getPlayerList().getPlayer(fight.challenger);
        ServerPlayer holder = server.getPlayerList().getPlayer(fight.holder);
        int color = LanternCorps.ORANGE.color();
        if (reasonKey != null) {
            Component reason = Component.translatable(reasonKey, reasonName).withColor(color);
            if (challenger != null) challenger.sendSystemMessage(reason);
            if (holder != null) holder.sendSystemMessage(reason);
        }
        if (winner.equals(fight.challenger) && challenger != null && holder != null) {
            ItemStack ring = takeOrange(holder);
            if (!ring.isEmpty()) {
                RingLoyalty.deliver(challenger, ring, false);
                challenger.sendSystemMessage(Component.translatable("message.brightestday.duel.won").withStyle(ChatFormatting.BOLD).withColor(color));
                holder.sendSystemMessage(Component.translatable("message.brightestday.duel.lost", challenger.getDisplayName()).withStyle(ChatFormatting.BOLD).withColor(color));
                return;
            }
        }
        if (winner.equals(fight.challenger) && challenger != null && holder == null) {
            challenger.sendSystemMessage(Component.translatable("message.brightestday.duel.forfeit_won").withStyle(ChatFormatting.BOLD).withColor(color));
            return;
        }
        if (holder != null) holder.sendSystemMessage(Component.translatable("message.brightestday.duel.held").withStyle(ChatFormatting.BOLD).withColor(color));
        if (challenger != null) challenger.sendSystemMessage(Component.translatable("message.brightestday.duel.failed").withStyle(ChatFormatting.BOLD).withColor(color));
    }

    private static void release(MinecraftServer server, Fight fight) {
        for (UUID id : List.of(fight.challenger, fight.holder)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.removeAttached(DUELING);
        }
        if (fight.started) broadcast(fight, false);
    }

    private static DuelArenaS2CPayload arena(Fight fight, boolean active) {
        return new DuelArenaS2CPayload(fight.holder.hashCode(), fight.center.toVector3f(), active ? (float) fight.radius : 0.0F, LanternCorps.ORANGE.color());
    }

    private static void broadcast(Fight fight, boolean active) {
        if (fight.level == null) return;
        DuelArenaS2CPayload payload = arena(fight, active);
        for (ServerPlayer player : fight.level.players()) ServerPlayNetworking.send(player, payload);
    }

    private static ItemStack takeable(ServerPlayer player) {
        ItemStack worn = BrightestDayAttachments.getRing(player);
        if (PowerRingItem.getCorps(worn).orElse(null) == LanternCorps.ORANGE) return worn;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (PowerRingItem.getCorps(stack).orElse(null) == LanternCorps.ORANGE) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeOrange(ServerPlayer victim) {
        ItemStack worn = BrightestDayAttachments.getRing(victim);
        if (PowerRingItem.getCorps(worn).orElse(null) == LanternCorps.ORANGE) {
            BrightestDayAttachments.setRing(victim, ItemStack.EMPTY);
            return worn;
        }
        for (int slot = 0; slot < victim.getInventory().getContainerSize(); slot++) {
            ItemStack stack = victim.getInventory().getItem(slot);
            if (PowerRingItem.getCorps(stack).orElse(null) != LanternCorps.ORANGE) continue;
            victim.getInventory().setItem(slot, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private OrangeDuels() {}
}
