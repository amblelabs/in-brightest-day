package dev.amble.core.shields;

import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.BulwarkS2CPayload;
import dev.amble.core.poses.Poses;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class BulwarkManager {
    private static final Identifier SLOW_MODIFIER = BrightestDay.id("bulwark");
    private static final double BASE_SLOW = 0.35;
    private static final double SLOW_PER_SIZE = 0.1;
    private static final int DRAIN_BASE = 8;
    private static final int DRAIN_PER_SIZE = 4;
    private static final double MELEE_REACH = 4.5;
    private static final double REPEL = 0.5;

    private static final Map<ServerPlayer, Bulwark> BULWARKS = new HashMap<>();

    private static final class Bulwark {
        final int color;
        int size;

        Bulwark(int color, int size) {
            this.color = color;
            this.size = size;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BulwarkManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BULWARKS.clear());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !block(entity, source));
        EntityTrackingEvents.START_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayer player && BULWARKS.get(player) instanceof Bulwark bulwark) {
                ServerPlayNetworking.send(watcher, new BulwarkS2CPayload(player.getId(), true, bulwark.size, bulwark.color));
            }
        });
    }

    public static void start(ServerPlayer player, int color, int size) {
        if (BULWARKS.containsKey(player)) return;
        Bulwark bulwark = new Bulwark(color, Mth.clamp(size, BulwarkArc.MIN_SIZE, BulwarkArc.MAX_SIZE));
        BULWARKS.put(player, bulwark);
        slow(player, bulwark.size);
        broadcast(player, bulwark, true);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    public static void resize(ServerPlayer player, int size) {
        Bulwark bulwark = BULWARKS.get(player);
        int clamped = RingPowerRegistry.BULWARK.clampSize(player, size);
        if (bulwark == null || bulwark.size == clamped) return;
        bulwark.size = clamped;
        slow(player, clamped);
        broadcast(player, bulwark, true);
    }

    public static void stop(ServerPlayer player) {
        Bulwark bulwark = BULWARKS.remove(player);
        if (bulwark == null) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_MODIFIER);
        broadcast(player, bulwark, false);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public static boolean isRaised(Entity entity) {
        return entity instanceof ServerPlayer player && BULWARKS.containsKey(player);
    }

    private static void slow(ServerPlayer player, int size) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOW_MODIFIER);
        speed.addTransientModifier(new AttributeModifier(SLOW_MODIFIER, -(BASE_SLOW + SLOW_PER_SIZE * (size - BulwarkArc.MIN_SIZE)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static boolean block(LivingEntity victim, DamageSource source) {
        if (BULWARKS.isEmpty()) return false;
        Vec3 origin = source.getSourcePosition();
        if (origin == null) return false;
        for (Map.Entry<ServerPlayer, Bulwark> entry : BULWARKS.entrySet()) {
            ServerPlayer holder = entry.getKey();
            if (holder.level() != victim.level() || source.getEntity() == holder) continue;
            BulwarkArc arc = BulwarkArc.of(holder, holder.position(), holder.getYRot(), entry.getValue().size);
            if (!arc.shelters(victim.position(), victim.getBbHeight(), origin)) continue;

            victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.9F + victim.getRandom().nextFloat() * 0.2F);
            if (source.getDirectEntity() instanceof LivingEntity attacker && attacker.distanceTo(victim) <= MELEE_REACH) {
                Vec3 away = attacker.position().subtract(arc.center()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    attacker.push(away.normalize().scale(REPEL).add(0.0, 0.1, 0.0));
                    attacker.needsSync = true;
                }
            }
            return true;
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        boolean drainTick = server.getTickCount() % 20 == 0;
        for (ServerPlayer player : new ArrayList<>(BULWARKS.keySet())) {
            Bulwark bulwark = BULWARKS.get(player);
            boolean selected = ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.BULWARK;
            boolean outOfCharge = !PowerRingItem.hasCharge(player)
                    || drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, DRAIN_BASE + DRAIN_PER_SIZE * (bulwark.size - BulwarkArc.MIN_SIZE));
            if (player.isRemoved() || !player.isAlive() || !ArmedRingPower.isArmed(player) && !Poses.channeling(player) || !selected || outOfCharge) stop(player);
        }
    }

    private static void broadcast(ServerPlayer player, Bulwark bulwark, boolean active) {
        BulwarkS2CPayload payload = new BulwarkS2CPayload(player.getId(), active, bulwark.size, bulwark.color);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private BulwarkManager() {}
}
