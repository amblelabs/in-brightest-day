package dev.amble.core.ringpowers.impl;

import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.ringpowers.CorpsCombat;
import com.mojang.serialization.MapCodec;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.ScanS2CPayload;
import dev.amble.core.networking.payloads.s2c.ScanStartS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.scan.ScanReport;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

public class ScanRingPower extends RingPower<Unit> {
    public static final int SCAN_TICKS = 20;
    public static final int COOLDOWN_TICKS = 500;
    private static final int COMPLETION_TOLERANCE = 2;
    private static final double RANGE = 32.0;
    private static final double BREAK_DISTANCE = RANGE + 8.0;
    private static final int USE_COST = 15;

    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private record Pending(@Nullable Entity entity, BlockPos pos, long start) {}

    public ScanRingPower() {
        super(BrightestDay.id("scan"), EnumSet.allOf(LanternCorps.class), MapCodec.unitCodec(Unit.INSTANCE));
    }

    @Override
    public Unit createData() {
        return Unit.INSTANCE;
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    public static void start(ServerPlayer player) {
        if (!BrightestDayAttachments.has(player, RingPowerRegistry.SCAN)) return;
        if (!PowerRingItem.hasCharge(player)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }

        ServerLevel level = player.level();
        long now = level.getGameTime();
        long readyAt = READY_AT.getOrDefault(player.getUUID(), 0L);
        if (now < readyAt && readyAt - now <= COOLDOWN_TICKS) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.scan_cooldown", (readyAt - now + 19) / 20));
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        AABB searchArea = player.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, reach, searchArea,
                entity -> !entity.isSpectator(), 0.3F);

        if (entityHit == null && blockHit.getType() != HitResult.Type.BLOCK) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.nothing_to_scan"));
            return;
        }

        Entity entity = entityHit != null ? entityHit.getEntity() : null;
        BlockPos pos = entity != null ? BlockPos.ZERO : blockHit.getBlockPos();
        PENDING.put(player, new Pending(entity, pos, now));
        ArmedRingPower.raise(player);
        ServerPlayNetworking.send(player, new ScanStartS2CPayload(entity != null ? entity.getId() : ScanS2CPayload.NO_ENTITY, pos, CorpsColors.of(player)));
    }

    public static void cancel(ServerPlayer player) {
        PENDING.remove(player);
    }

    public static void complete(ServerPlayer player) {
        Pending pending = PENDING.remove(player);
        if (pending == null) return;

        ServerLevel level = player.level();
        long now = level.getGameTime();
        if (now - pending.start() < SCAN_TICKS - COMPLETION_TOLERANCE) return;

        Entity entity = pending.entity();
        if (entity != null && (!entity.isAlive() || entity.level() != level || player.distanceTo(entity) > BREAK_DISTANCE)) return;
        if (entity == null && player.distanceToSqr(Vec3.atCenterOf(pending.pos())) > BREAK_DISTANCE * BREAK_DISTANCE) return;

        if (!player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, CorpsCombat.utilityCost(player, USE_COST))) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }
        READY_AT.values().removeIf(ready -> ready <= now);
        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);
        RingRanks.fire(player, Trigger.SCAN, Milestone.Context.NONE);

        ScanReport report = entity != null ? ScanReport.of(player, entity) : ScanReport.of(player, level, pending.pos());
        int entityId = entity != null ? entity.getId() : ScanS2CPayload.NO_ENTITY;
        ServerPlayNetworking.send(player, new ScanS2CPayload(entityId, pending.pos(), report.title(), report.lines(), CorpsColors.of(player)));
    }
}
