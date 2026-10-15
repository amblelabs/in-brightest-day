package dev.amble.core.attacks.projectile;

import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.team.RingDamage;
import dev.amble.core.team.RingTargets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class CrystalManager {
    private static final double SPEED = 2.0;
    private static final double HIT_PADDING = 0.4;
    private static final int MASH_TICKS = 8;
    private static final float DAMAGE_TAKEN = 0.5F;
    private static final int SHELL_INTERVAL = 4;
    private static final String PRISON_TAG = "brightestday.crystal_prison";

    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final Map<LivingEntity, Prison> PRISONS = new HashMap<>();

    private static final class Shard {
        final ServerPlayer owner;
        final ServerLevel level;
        final int color;
        final Vec3 origin;
        Vec3 position;
        final Vec3 velocity;

        Shard(ServerPlayer owner, int color, Vec3 origin, Vec3 velocity) {
            this.owner = owner;
            this.level = owner.level();
            this.color = color;
            this.origin = origin;
            this.position = origin;
            this.velocity = velocity;
        }
    }

    private static final class Prison {
        final Vec3 anchor;
        final int color;
        final boolean hadNoAi;
        int ticks;
        boolean jumping;

        Prison(Vec3 anchor, int color, boolean hadNoAi, int ticks) {
            this.anchor = anchor;
            this.color = color;
            this.hadNoAi = hadNoAi;
            this.ticks = ticks;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CrystalManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SHARDS.removeIf(shard -> shard.owner == handler.player);
            release(handler.player);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && mob.entityTags().contains(PRISON_TAG) && !PRISONS.containsKey(mob)) {
                mob.setNoAi(false);
                mob.removeTag(PRISON_TAG);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Map.Entry<LivingEntity, Prison> entry : List.copyOf(PRISONS.entrySet())) free(entry.getKey(), entry.getValue());
            PRISONS.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SHARDS.clear();
            PRISONS.clear();
        });
    }

    public static boolean isEncased(Entity entity) {
        return entity instanceof LivingEntity living && PRISONS.containsKey(living);
    }

    public static float protect(LivingEntity victim, float damage) {
        return PRISONS.containsKey(victim) ? damage * DAMAGE_TAKEN : damage;
    }

    public static void launch(ServerPlayer player, int color) {
        Vec3 look = player.getLookAngle();
        SHARDS.add(new Shard(player, color, player.getEyePosition().add(look.scale(0.6)), look.scale(SPEED)));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.4F);
    }

    private static void tick(MinecraftServer server) {
        tickShards();
        tickPrisons();
    }

    private static void tickShards() {
        double range = BrightestDayConfig.get().crystalRange;
        Iterator<Shard> iterator = SHARDS.iterator();
        while (iterator.hasNext()) {
            Shard shard = iterator.next();
            Vec3 next = shard.position.add(shard.velocity);
            HitResult block = shard.level.clip(new ClipContext(shard.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shard.owner));
            Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();

            Entity target = null;
            double nearest = Double.MAX_VALUE;
            for (Entity entity : shard.level.getEntities(shard.owner, new AABB(shard.position, end).inflate(HIT_PADDING),
                    entity -> RingTargets.isTarget(shard.owner, entity))) {
                double distance = entity.distanceToSqr(shard.position);
                if (distance < nearest) {
                    nearest = distance;
                    target = entity;
                }
            }

            shard.level.sendParticles(new DustParticleOptions(shard.color, 1.2F), end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.0);
            if (target != null) {
                iterator.remove();
                if (RingTargets.root(target) instanceof LivingEntity living) {
                    encase(living, shard.color);
                    RingRanks.fire(shard.owner, Trigger.ENCASE, Milestone.Context.of(living));
                } else {
                    RingTargets.hurt(shard.level, target, RingDamage.source(shard.level, shard.owner), 1.0F);
                }
            } else if (block.getType() != HitResult.Type.MISS || end.distanceTo(shard.origin) > range) {
                iterator.remove();
                shard.level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.05);
            } else {
                shard.position = end;
            }
        }
    }

    private static void encase(LivingEntity target, int color) {
        boolean hadNoAi = target instanceof Mob mob && mob.isNoAi();
        Prison previous = PRISONS.get(target);
        PRISONS.put(target, new Prison(target.position(), color, previous != null ? previous.hadNoAi : hadNoAi, BrightestDayConfig.get().crystalPrisonTicks));
        if (target instanceof Mob mob) {
            if (previous == null && !hadNoAi) mob.addTag(PRISON_TAG);
            mob.setNoAi(true);
        }
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.5F, 0.8F);
    }

    private static void tickPrisons() {
        Iterator<Map.Entry<LivingEntity, Prison>> iterator = PRISONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Prison> entry = iterator.next();
            LivingEntity entity = entry.getKey();
            Prison prison = entry.getValue();
            if (!entity.isAlive() || entity.isRemoved() || --prison.ticks <= 0) {
                iterator.remove();
                free(entity, prison);
                continue;
            }

            if (entity instanceof ServerPlayer player) {
                boolean jumping = player.getLastClientInput().jump();
                if (jumping && !prison.jumping) prison.ticks -= MASH_TICKS;
                prison.jumping = jumping;
            }

            entity.teleportTo(prison.anchor.x, prison.anchor.y, prison.anchor.z);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.needsSync = true;
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 9, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 5, 4, false, false));
            if (prison.ticks % SHELL_INTERVAL == 0) shell(entity, prison.color);
        }
    }

    private static void shell(LivingEntity entity, int color) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        AABB box = entity.getBoundingBox().inflate(0.2);
        level.sendParticles(new DustParticleOptions(color, 1.6F), box.getCenter().x, box.getCenter().y, box.getCenter().z, 20,
                box.getXsize() * 0.4, box.getYsize() * 0.4, box.getZsize() * 0.4, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, box.getCenter().x, box.maxY, box.getCenter().z, 1, 0.2, 0.0, 0.2, 0.0);
    }

    private static void release(LivingEntity entity) {
        Prison prison = PRISONS.remove(entity);
        if (prison != null) free(entity, prison);
    }

    private static void free(LivingEntity entity, Prison prison) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(prison.hadNoAi);
            mob.removeTag(PRISON_TAG);
        }
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 1.0F);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(prison.color, 2.0F), entity.getX(), entity.getY(0.5), entity.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
        }
    }

    private CrystalManager() {}
}
