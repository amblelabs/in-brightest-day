package dev.amble.core.magnet;

import dev.amble.core.carry.Carry;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.mannequin.Mannequins;
import dev.amble.core.mounts.ConstructMounts;
import dev.amble.core.networking.payloads.s2c.CarriedS2CPayload;
import dev.amble.core.networking.payloads.s2c.MagnetS2CPayload;
import dev.amble.core.ringpowers.ActiveConstructs;
import dev.amble.core.ringpowers.CorpsCombat;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.sphere.ContainmentSphere;
import dev.amble.core.team.LanternTeams;
import dev.amble.core.team.RingTargets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MagnetManager {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 5;
    public static final int EMPOWERED_SIZE = 8;
    private static final double BASE_RANGE = 6.0;
    private static final double RANGE_PER_SIZE = 3.0;
    private static final double BASE_PULL = 0.08;
    private static final double PULL_PER_SIZE = 0.03;
    private static final double DAMPING = 0.85;
    private static final double FRONT = 0.2;
    private static final int DRAIN_PER_SIZE = 3;
    private static final int REFRESH_TICKS = 40;

    private static final class Magnet {
        final ServerLevel level;
        final int size;
        final int color;
        final long createdAt;
        List<Integer> sent = List.of();

        Magnet(ServerLevel level, int size, int color) {
            this.level = level;
            this.size = size;
            this.color = color;
            this.createdAt = level.getGameTime();
        }
    }

    private static final Map<UUID, Magnet> MAGNETS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MagnetManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MAGNETS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> MAGNETS.clear());
    }

    public static double range(int size) {
        return BASE_RANGE + RANGE_PER_SIZE * size;
    }

    public static double reach(int size) {
        return 1.4 + 0.18 * size;
    }

    public static Vec3 tips(Player player, int size, float partialTicks) {
        return player.getEyePosition(partialTicks).add(player.getViewVector(partialTicks).scale(reach(size)));
    }

    public static boolean isActive(ServerPlayer player) {
        return MAGNETS.containsKey(player.getUUID());
    }

    public static long latestCreatedAt(ServerPlayer player) {
        Magnet magnet = MAGNETS.get(player.getUUID());
        return magnet == null ? Long.MIN_VALUE : magnet.createdAt;
    }

    public static void start(ServerPlayer player, int size, int color) {
        stop(player);
        Magnet magnet = new Magnet(player.level(), size, color);
        MAGNETS.put(player.getUUID(), magnet);
        ActiveConstructs.track(player, magnet);
        broadcast(player, size, color);
        Vec3 at = tips(player, size, 1.0F);
        player.level().playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        player.level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    public static void stop(ServerPlayer player) {
        Magnet magnet = MAGNETS.remove(player.getUUID());
        if (magnet == null) return;
        ActiveConstructs.untrack(player.getUUID(), magnet);
        broadcast(player, 0, magnet.color);
        Carry.broadcast(player, CarriedS2CPayload.MAGNET, 0.0F, List.of());
        Vec3 at = tips(player, magnet.size, 1.0F);
        player.level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    private static void tick(MinecraftServer server) {
        if (MAGNETS.isEmpty()) return;
        boolean drainTick = server.getTickCount() % 20 == 0;
        boolean refresh = server.getTickCount() % REFRESH_TICKS == 0;

        for (UUID id : new ArrayList<>(MAGNETS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Magnet magnet = MAGNETS.get(id);
            if (player == null) {
                MAGNETS.remove(id);
                continue;
            }
            int drain = CorpsCombat.utilityCost(player, DRAIN_PER_SIZE * magnet.size);
            boolean outOfCharge = !PowerRingItem.hasCharge(player) || drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, drain);
            if (!player.isAlive() || player.level() != magnet.level || !ArmedRingPower.isArmed(player) || outOfCharge || ContainmentSphere.holds(player)) {
                stop(player);
                continue;
            }
            if (refresh) broadcast(player, magnet.size, magnet.color);
            List<Integer> held = pull(player, magnet);
            if (refresh || !held.equals(magnet.sent)) {
                magnet.sent = held;
                List<CarriedS2CPayload.Entry> carried = new ArrayList<>();
                for (int entityId : held) carried.add(new CarriedS2CPayload.Entry(entityId, new Vector3f()));
                Carry.broadcast(player, CarriedS2CPayload.MAGNET, (float) reach(magnet.size), carried);
            }
        }
    }

    private static boolean pullable(ServerPlayer owner, Entity entity) {
        if (entity == owner || entity.isSpectator() || !entity.isAlive() || entity.isPassenger() || entity == owner.getVehicle()) return false;
        if (Mannequins.isHologram(entity) || ConstructMounts.isConstruct(entity) || ContainmentSphere.holds(entity)) return false;
        if (entity instanceof Player player && (player.isCreative() || LanternTeams.areTeammates(owner, player))) return false;
        if (entity instanceof LivingEntity) return RingTargets.isTarget(owner, entity);
        return entity instanceof ItemEntity || entity instanceof ExperienceOrb;
    }

    private static List<Integer> pull(ServerPlayer owner, Magnet magnet) {
        List<Integer> held = new ArrayList<>();
        double range = range(magnet.size);
        double pull = BASE_PULL + PULL_PER_SIZE * magnet.size;
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 tips = tips(owner, magnet.size, 1.0F);
        Vec3 body = owner.getBoundingBox().getCenter();
        double hold = 0.8 + 0.2 * magnet.size;
        boolean sparks = magnet.level.getGameTime() % 4 == 0;
        DustParticleOptions dust = new DustParticleOptions(magnet.color, 1.0F);

        for (Entity entity : magnet.level.getEntities(owner, new AABB(eye, eye).inflate(range), entity -> pullable(owner, entity))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 fromEye = center.subtract(eye);
            double distance = fromEye.length();
            if (distance > range || distance < 1.0E-3 || fromEye.scale(1.0 / distance).dot(look) < FRONT) continue;

            boolean loot = entity instanceof ItemEntity || entity instanceof ExperienceOrb;
            Vec3 goal = loot ? body : tips;
            Vec3 to = goal.subtract(center);
            double gap = to.length();
            double falloff = 1.0 - Mth.clamp(distance / range, 0.0, 1.0);
            double weight = weight(entity);

            if (!loot && gap <= hold) {
                Carry.toward(entity, tips);
                held.add(entity.getId());
                continue;
            }
            if (gap > 1.0E-3) {
                Vec3 inward = to.scale(1.0 / gap);
                entity.setDeltaMovement(entity.getDeltaMovement().scale(DAMPING).add(inward.scale(pull * weight * (0.5 + falloff * 1.5))));
            }
            entity.resetFallDistance();
            entity.needsSync = true;
            if (entity instanceof Mob mob) mob.getNavigation().stop();
            if (sparks) {
                double t = magnet.level.getRandom().nextDouble();
                Vec3 spark = center.add(tips.subtract(center).scale(t));
                magnet.level.sendParticles(dust, spark.x, spark.y, spark.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        return held;
    }

    private static double weight(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 1.0;
        double bulk = 1.0 / (1.0 + 0.5 * entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight());
        double resist = 1.0 - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        return Math.max(0.15, bulk * (0.3 + 0.7 * resist)) * 1.6;
    }

    private static void broadcast(ServerPlayer player, int size, int color) {
        MagnetS2CPayload payload = new MagnetS2CPayload(player.getId(), size, color);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private MagnetManager() {}
}
