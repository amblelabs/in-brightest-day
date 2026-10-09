package dev.amble.core.tractor;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.ringpowers.CorpsCombat;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.TractorS2CPayload;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.ringpowers.impl.TractorBeamRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import dev.amble.BrightestDay;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class TractorManager {
    private static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK, BrightestDay.id("tractor_immune"));
    public static final double RANGE = 24.0;
    private static final double MIN_DISTANCE = 2.0;
    private static final double DISTANCE_STEP = 1.0;
    private static final double BREAK_DISTANCE = RANGE + 8.0;
    private static final double FOLLOW = 0.35;
    private static final double MAX_SPEED = 1.6;
    private static final double GRAVITY_COMPENSATION = 0.08;
    private static final int DRAIN_PER_SECOND = 8;

    private static final Map<ServerPlayer, Grip> GRIPS = new HashMap<>();

    private static final class Grip {
        final Entity target;
        final Struggle struggle = new Struggle();
        double distance;

        Grip(Entity target, double distance) {
            this.target = target;
            this.distance = distance;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TractorManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GRIPS.clear());
    }

    public static boolean isHolding(ServerPlayer player) {
        return GRIPS.containsKey(player);
    }

    public static void grab(ServerPlayer player) {
        if (GRIPS.containsKey(player) || !TractorBeamRingPower.isActive(player) || !PowerRingItem.hasCharge(player)) return;

        Entity target = findTarget(player);
        if (target == null) return;

        double distance = Mth.clamp(player.getEyePosition().distanceTo(target.getBoundingBox().getCenter()), MIN_DISTANCE, RANGE);
        GRIPS.put(player, new Grip(target, distance));
        ArmedRingPower.raise(player);
        broadcast(player, target.getId());
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public static void release(ServerPlayer player) {
        Grip grip = GRIPS.remove(player);
        if (grip == null) return;

        if (grip.target instanceof FallingBlockEntity block) block.setNoGravity(false);
        broadcast(player, TractorS2CPayload.NO_TARGET);
    }

    public static void adjust(ServerPlayer player, int steps) {
        Grip grip = GRIPS.get(player);
        if (grip != null) grip.distance = Mth.clamp(grip.distance + steps * DISTANCE_STEP, MIN_DISTANCE, RANGE);
    }

    private static @Nullable Entity findTarget(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        AABB searchArea = player.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, reach, searchArea,
                entity -> !Mannequins.isHologram(entity) && !entity.isSpectator() && entity.isAlive() && !isHeld(entity) && !entity.isPassengerOfSameVehicle(player), 0.3F);
        if (entityHit != null) return entityHit.getEntity();

        if (blockHit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.is(IMMUNE) || state.getDestroySpeed(level, pos) < 0.0F || !level.mayInteract(player, pos)) return null;

        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
        block.setNoGravity(true);
        block.time = 1;
        return block;
    }

    private static boolean isHeld(Entity entity) {
        return GRIPS.values().stream().anyMatch(grip -> grip.target == entity);
    }

    private static void tick(MinecraftServer server) {
        boolean drainTick = server.getTickCount() % 20 == 0;

        for (ServerPlayer player : new ArrayList<>(GRIPS.keySet())) {
            Grip grip = GRIPS.get(player);
            Entity target = grip.target;
            Vec3 eye = player.getEyePosition();
            Vec3 center = target.getBoundingBox().getCenter();

            boolean invalid = player.isRemoved() || !player.isAlive() || !target.isAlive() || target.isRemoved()
                    || target.level() != player.level() || !TractorBeamRingPower.isActive(player) || !ArmedRingPower.isArmed(player)
                    || center.distanceTo(eye) > BREAK_DISTANCE;
            boolean outOfCharge = drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, CorpsCombat.utilityCost(player, DRAIN_PER_SECOND));
            if (invalid || outOfCharge) {
                release(player);
                continue;
            }
            if (grip.struggle.escaped(target)) {
                release(player);
                breakFree(player, target);
                continue;
            }
            if (drainTick) RingRanks.fire(player, Trigger.TRACTOR, Milestone.Context.NONE);

            Vec3 goal = eye.add(player.getLookAngle().scale(grip.distance));
            Vec3 velocity = goal.subtract(center).scale(FOLLOW);
            if (velocity.length() > MAX_SPEED) velocity = velocity.normalize().scale(MAX_SPEED);
            if (!target.isNoGravity()) velocity = velocity.add(0.0, GRAVITY_COMPENSATION, 0.0);

            grip.struggle.push(target, velocity);
            if (target instanceof FallingBlockEntity block) block.time = 1;
        }
    }

    public static void breakFree(ServerPlayer holder, Entity escapee) {
        holder.sendOverlayMessage(Component.translatable("message.brightestday.break_free.holder", escapee.getDisplayName()));
        if (escapee instanceof ServerPlayer player) player.sendOverlayMessage(Component.translatable("message.brightestday.break_free.escaped"));
        escapee.level().playSound(null, escapee.getX(), escapee.getY(), escapee.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void broadcast(ServerPlayer player, int targetId) {
        TractorS2CPayload payload = new TractorS2CPayload(player.getId(), targetId);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private TractorManager() {}
}
