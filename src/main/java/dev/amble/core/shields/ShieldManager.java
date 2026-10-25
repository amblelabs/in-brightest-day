package dev.amble.core.shields;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.ringpowers.CorpsSynergy;
import dev.amble.core.networking.payloads.s2c.ShieldRemoveS2CPayload;
import dev.amble.core.networking.payloads.s2c.ShieldSpawnS2CPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.walls.WallManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ShieldManager {
    public static final int ENTITY_SHIELD_TICKS = 200;
    public static final int AREA_SHIELD_TICKS = 300;
    public static final int NO_EXPIRY = -1;
    private static final int AREA_DRAIN_PER_SECOND = 3;
    public static final int EXPAND_TICKS = 8;
    private static final float ENTITY_SHIELD_STRENGTH = 20.0F;
    private static final float ENTITY_SHIELD_PADDING = 0.35F;
    private static final float ENTITY_SHIELD_SCALE = 1.15F;
    private static final double DEFLECT_DAMPING = 0.6;
    private static final double BARRIER_PUSH = 0.45;

    private static final List<Shield> SHIELDS = new ArrayList<>();
    private static int nextId;

    private static final class Shield {
        final int id;
        final ServerLevel level;
        final @Nullable Entity target;
        final Vec3 center;
        final float radius;
        final float scale;
        final UUID caster;
        final long createdAt;
        final int color;
        final int duration;
        final Set<ServerPlayer> watchers = Collections.newSetFromMap(new WeakHashMap<>());
        float strength;
        int age;
        int barrier = -1;

        Shield(int id, ServerLevel level, @Nullable Entity target, Vec3 center, float radius, float scale, UUID caster, int color, int duration, float strength) {
            this.id = id;
            this.level = level;
            this.target = target;
            this.center = center;
            this.radius = radius;
            this.scale = scale;
            this.caster = caster;
            this.color = color;
            this.duration = duration;
            this.strength = strength;
            this.createdAt = level.getGameTime();
        }

        Vec3 center() {
            return this.target != null ? this.target.getBoundingBox().getCenter() : this.center;
        }

        double currentRadius() {
            return this.radius * Math.min(1.0, (this.age + 1.0) / EXPAND_TICKS);
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ShieldManager::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ShieldManager::allowDamage);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHIELDS.clear());
    }

    public static boolean shielded(Level level) {
        for (Shield shield : SHIELDS) {
            if (shield.level == level) return true;
        }
        return false;
    }

    public static boolean separates(Level level, Vec3 first, Vec3 second) {
        for (Shield shield : SHIELDS) {
            if (shield.level != level) continue;
            Vec3 center = shield.center();
            double radius = shield.currentRadius();
            double reach = radius * radius;
            if (first.distanceToSqr(center) <= reach != second.distanceToSqr(center) <= reach) return true;
        }
        return false;
    }

    public static float entityShieldRadius(Entity entity) {
        return Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.5F * ENTITY_SHIELD_SCALE + ENTITY_SHIELD_PADDING;
    }

    public static void shieldEntity(ServerLevel level, Entity target, int color, ServerPlayer caster) {
        SHIELDS.removeIf(shield -> {
            boolean replaced = shield.target == target;
            if (replaced) forget(shield);
            return replaced;
        });

        float scale = CorpsSynergy.empoweredByHope(caster) ? CorpsSynergy.HOPE_BUBBLE_SCALE
                : CorpsSynergy.weakenedByHope(caster) ? CorpsSynergy.DREAD_BUBBLE_SCALE : 1.0F;
        Shield shield = new Shield(nextId++, level, target, target.getBoundingBox().getCenter(), entityShieldRadius(target) * scale, scale,
                caster.getUUID(), color, ENTITY_SHIELD_TICKS, ENTITY_SHIELD_STRENGTH);
        SHIELDS.add(shield);
        syncWatchers(shield);
    }

    public static void shieldArea(ServerLevel level, Vec3 center, float radius, int color, ServerPlayer caster) {
        Shield shield = new Shield(nextId++, level, null, center, radius, 1.0F, caster.getUUID(), color, NO_EXPIRY, 0.0F);
        shield.barrier = WallManager.raiseHidden(level, shellBlocks(center, radius), color, caster);
        SHIELDS.add(shield);
        syncWatchers(shield);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) drainAreas(server);
        Iterator<Shield> iterator = SHIELDS.iterator();
        while (iterator.hasNext()) {
            Shield shield = iterator.next();
            boolean targetGone = shield.target != null && (shield.target.isRemoved() || !shield.target.isAlive());
            if (++shield.age >= shield.duration && shield.duration != NO_EXPIRY || targetGone) {
                iterator.remove();
                forget(shield);
                continue;
            }
            syncWatchers(shield);
            barrier(shield);
        }
    }

    private static void drainAreas(MinecraftServer server) {
        List<UUID> broke = new ArrayList<>();
        for (Shield shield : SHIELDS) {
            if (shield.target != null || shield.duration != NO_EXPIRY) continue;
            ServerPlayer caster = server.getPlayerList().getPlayer(shield.caster);
            if (caster == null || caster.hasInfiniteMaterials()) continue;
            if (!PowerRingItem.drainWorn(caster, AREA_DRAIN_PER_SECOND)) broke.add(shield.caster);
        }
        broke.forEach(ShieldManager::dismissAll);
    }

    private static List<BlockPos> shellBlocks(Vec3 center, float radius) {
        List<BlockPos> cells = new ArrayList<>();
        double limit = radius * radius;
        BlockPos origin = BlockPos.containing(center);
        int reach = Mth.ceil(radius) + 1;
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!within(pos, center, limit)) continue;
                    if (within(pos.above(), center, limit) && within(pos.below(), center, limit) && within(pos.north(), center, limit)
                            && within(pos.south(), center, limit) && within(pos.east(), center, limit) && within(pos.west(), center, limit)) continue;
                    cells.add(pos);
                }
            }
        }
        return cells;
    }

    private static boolean within(BlockPos pos, Vec3 center, double limit) {
        return Vec3.atCenterOf(pos).distanceToSqr(center) <= limit;
    }

    private static void barrier(Shield shield) {
        Vec3 center = shield.center();
        double radius = shield.currentRadius();

        for (Entity entity : shield.level.getEntities((Entity) null, new AABB(center, center).inflate(radius + 3.0))) {
            if (entity == shield.target || entity.getUUID().equals(shield.caster) || Mannequins.isHologram(entity)) continue;

            Vec3 position = entity.getBoundingBox().getCenter();
            Vec3 previous = position.subtract(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
            double distance = position.distanceTo(center);
            double previousDistance = previous.distanceTo(center);

            if (entity instanceof Projectile projectile) {
                if (shield.target != null && projectile.getOwner() == shield.target) continue;
                if (distance <= radius + 0.3 && previousDistance > radius - 0.3) deflect(projectile, center);
            } else if (shield.target == null && entity instanceof LivingEntity && previousDistance >= radius && distance < radius) {
                Vec3 outward = position.subtract(center);
                outward = outward.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : outward.normalize();
                entity.setDeltaMovement(outward.scale(BARRIER_PUSH));
                entity.needsSync = true;
            }
        }
    }

    private static void deflect(Projectile projectile, Vec3 center) {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 normal = projectile.position().subtract(center);
        if (normal.lengthSqr() < 1.0E-4) return;
        normal = normal.normalize();
        if (velocity.dot(normal) >= 0.0) return;

        Vec3 reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal))).scale(DEFLECT_DAMPING);
        projectile.setDeltaMovement(reflected);
        projectile.needsSync = true;
        projectile.level().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;

        for (Iterator<Shield> iterator = SHIELDS.iterator(); iterator.hasNext(); ) {
            Shield shield = iterator.next();
            if (shield.target != entity) continue;

            shield.strength -= amount;
            if (!entity.getUUID().equals(shield.caster) && entity.level().getServer() != null) {
                ServerPlayer caster = entity.level().getServer().getPlayerList().getPlayer(shield.caster);
                if (caster != null) RingRanks.fire(caster, Trigger.SHIELD, Milestone.Context.of(entity), Math.round(amount));
            }
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (shield.strength <= 0.0F) {
                iterator.remove();
                forget(shield);
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2F, 1.0F);
            }
            return false;
        }
        return true;
    }

    private static void syncWatchers(Shield shield) {
        Set<ServerPlayer> tracking = new HashSet<>();
        if (shield.target != null) {
            tracking.addAll(PlayerLookup.tracking(shield.target));
            if (shield.target instanceof ServerPlayer player) tracking.add(player);
        } else {
            tracking.addAll(PlayerLookup.tracking(shield.level, BlockPos.containing(shield.center)));
        }

        for (ServerPlayer player : tracking) {
            if (shield.watchers.add(player)) {
                int entityId = shield.target != null ? shield.target.getId() : ShieldSpawnS2CPayload.NO_ENTITY;
                ServerPlayNetworking.send(player, new ShieldSpawnS2CPayload(shield.id, entityId, shield.center(), shield.target != null ? shield.scale : shield.radius, shield.color, shield.duration, shield.age, casterId(shield)));
            }
        }

        shield.watchers.removeIf(player -> {
            boolean gone = !tracking.contains(player);
            if (gone && !player.hasDisconnected()) ServerPlayNetworking.send(player, new ShieldRemoveS2CPayload(shield.id));
            return gone;
        });
    }


    private static int casterId(Shield shield) {
        ServerPlayer caster = shield.level.getServer().getPlayerList().getPlayer(shield.caster);
        return caster == null ? ShieldSpawnS2CPayload.NO_ENTITY : caster.getId();
    }

    public static long latestCreatedAt(UUID caster) {
        return SHIELDS.stream().filter(shield -> shield.caster.equals(caster)).mapToLong(shield -> shield.createdAt).max().orElse(Long.MIN_VALUE);
    }

    public static boolean dismissLatest(UUID caster) {
        Shield latest = null;
        for (Shield shield : SHIELDS) {
            if (shield.caster.equals(caster) && (latest == null || shield.id > latest.id)) latest = shield;
        }
        if (latest == null) return false;

        SHIELDS.remove(latest);
        dismissed(latest);
        return true;
    }

    public static void dismissAll(UUID caster) {
        Iterator<Shield> iterator = SHIELDS.iterator();
        while (iterator.hasNext()) {
            Shield shield = iterator.next();
            if (!shield.caster.equals(caster)) continue;
            iterator.remove();
            dismissed(shield);
        }
    }

    private static void dismissed(Shield shield) {
        forget(shield);
        Vec3 center = shield.center();
        shield.level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static void forget(Shield shield) {
        if (shield.barrier >= 0) {
            WallManager.remove(shield.barrier, false);
            shield.barrier = -1;
        }
        for (ServerPlayer player : shield.watchers) {
            if (!player.hasDisconnected()) ServerPlayNetworking.send(player, new ShieldRemoveS2CPayload(shield.id));
        }
        shield.watchers.clear();
    }

    private ShieldManager() {}
}
