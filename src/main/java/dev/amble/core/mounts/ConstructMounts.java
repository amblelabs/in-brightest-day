package dev.amble.core.mounts;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.ActiveConstructs;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ConstructMounts {
    public static final String TAG = "brightestday_construct_mount";
    private static final double HORSE_SPEED = 0.3375;
    private static final double HORSE_JUMP = 1.0;
    private static final double HORSE_HEALTH = 30.0;
    private static final float DOWN_PITCH = 60.0F;
    private static final double PLACE_RANGE = 24.0;
    private static final double PLACE_BACKOFF = 0.8;

    public enum Kind {
        HORSE,
        BOAT
    }

    public static final AttachmentType<Integer> COLOR =
            AttachmentRegistry.<Integer>builder()
                    .syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.all())
                    .buildAndRegister(BrightestDay.id("construct_mount"));

    private static final class Mount {
        final Entity entity;
        final ServerPlayer owner;
        final Kind kind;
        final long createdAt;
        final boolean placed;
        boolean ridden;

        Mount(Entity entity, ServerPlayer owner, Kind kind, long createdAt, boolean placed) {
            this.entity = entity;
            this.owner = owner;
            this.kind = kind;
            this.createdAt = createdAt;
            this.placed = placed;
        }
    }

    private static final Map<UUID, Mount> MOUNTS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ConstructMounts::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !MOUNTS.containsKey(entity.getUUID())) entity.discard();
        });
    }

    public static boolean isConstruct(@Nullable Entity entity) {
        return entity != null && entity.hasAttached(COLOR);
    }

    public static boolean rideableBy(Entity entity, Player player) {
        Mount mount = MOUNTS.get(entity.getUUID());
        return mount != null && (mount.placed || mount.owner == player);
    }

    public static void summon(ServerPlayer player, Kind kind, int color) {
        dismiss(player.getUUID(), kind);
        boolean placed = player.getXRot() < DOWN_PITCH;
        if (!placed && player.isPassenger()) player.stopRiding();
        ServerLevel level = player.level();
        Entity entity = kind == Kind.HORSE ? horse(level, player) : boat(level, player);
        if (entity == null) return;

        if (placed) {
            Vec3 look = player.getLookAngle();
            ClipContext.Fluid fluid = kind == Kind.BOAT ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
            Vec3 at = ConstructRingPower.aim(player, PLACE_RANGE, fluid).end().subtract(look.scale(PLACE_BACKOFF));
            if (kind == Kind.BOAT) at = surface(level, at);
            entity.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
        } else {
            Vec3 at = kind == Kind.BOAT ? surface(level, player.position()) : player.position();
            entity.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
        }
        entity.addTag(TAG);
        entity.setPermanentlyInvulnerable(true);
        entity.setAttached(COLOR, color);
        MOUNTS.put(entity.getUUID(), new Mount(entity, player, kind, level.getGameTime(), placed));
        if (!level.addFreshEntity(entity)) {
            MOUNTS.remove(entity.getUUID());
            return;
        }
        if (!placed) player.startRiding(entity, true, true);
        ActiveConstructs.track(player, entity);

        burst(level, entity, color);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.2F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), kind == Kind.HORSE ? SoundEvents.HORSE_SADDLE.value() : SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static @Nullable Entity horse(ServerLevel level, ServerPlayer player) {
        Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (horse == null) return null;
        horse.setTamed(true);
        horse.setOwner(player);
        horse.setPersistenceRequired();
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HORSE_HEALTH);
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HORSE_SPEED);
        horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(HORSE_JUMP);
        horse.setHealth((float) HORSE_HEALTH);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setDropChance(EquipmentSlot.SADDLE, 0.0F);
        return horse;
    }

    private static Vec3 surface(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        if (!water(level, pos) && water(level, pos.below())) pos = pos.below();
        if (!water(level, pos)) return at;
        while (water(level, pos.above())) pos = pos.above();
        return new Vec3(at.x, pos.getY() + level.getFluidState(pos).getHeight(level, pos), at.z);
    }

    private static boolean water(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static @Nullable Entity boat(ServerLevel level, ServerPlayer player) {
        return EntityTypes.BIRCH_BOAT.create(level, EntitySpawnReason.MOB_SUMMONED);
    }

    public static long latestCreatedAt(UUID owner) {
        return MOUNTS.values().stream().filter(mount -> mount.owner.getUUID().equals(owner)).mapToLong(mount -> mount.createdAt).max().orElse(Long.MIN_VALUE);
    }

    public static void dismissLatest(UUID owner) {
        MOUNTS.values().stream().filter(mount -> mount.owner.getUUID().equals(owner))
                .max((a, b) -> Long.compare(a.createdAt, b.createdAt))
                .ifPresent(ConstructMounts::dissolve);
    }

    public static void dismissAll(UUID owner) {
        for (Mount mount : new ArrayList<>(MOUNTS.values())) {
            if (mount.owner.getUUID().equals(owner)) dissolve(mount);
        }
    }

    private static void dismiss(UUID owner, Kind kind) {
        for (Mount mount : new ArrayList<>(MOUNTS.values())) {
            if (mount.owner.getUUID().equals(owner) && mount.kind == kind) dissolve(mount);
        }
    }

    private static void dissolve(Mount mount) {
        MOUNTS.remove(mount.entity.getUUID());
        ActiveConstructs.untrack(mount.owner.getUUID(), mount.entity);
        Entity entity = mount.entity;
        if (entity.isRemoved()) return;
        if (entity.level() instanceof ServerLevel level) {
            burst(level, entity, entity.getAttachedOrElse(COLOR, 0xFFFFFF));
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        entity.ejectPassengers();
        entity.discard();
    }

    private static void burst(ServerLevel level, Entity entity, int color) {
        Vec3 center = entity.getBoundingBox().getCenter();
        level.sendParticles(new DustParticleOptions(color, 1.6F), center.x, center.y, center.z, 30,
                entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.4, 0.0);
    }

    private static void tick(MinecraftServer server) {
        if (MOUNTS.isEmpty()) return;
        boolean drainTick = server.getTickCount() % 20 == 0;
        BrightestDayConfig config = BrightestDayConfig.get();
        for (Mount mount : new ArrayList<>(MOUNTS.values())) {
            ServerPlayer owner = mount.owner;
            boolean riding = owner.getVehicle() == mount.entity;
            if (riding) mount.ridden = true;
            boolean abandoned = !mount.placed && (mount.ridden && !riding || !mount.ridden && server.overworld().getGameTime() - mount.createdAt > 20);
            if (mount.entity.isRemoved() || owner.isRemoved() || !owner.isAlive() || abandoned) {
                dissolve(mount);
                continue;
            }
            if (!drainTick || owner.hasInfiniteMaterials()) continue;
            int drain = mount.kind == Kind.HORSE ? config.horseDrainPerSecond : config.boatDrainPerSecond;
            if (drain > 0 && !PowerRingItem.drainWorn(owner, drain)) {
                owner.sendOverlayMessage(Component.translatable("message.brightestday.mount.faded"));
                dissolve(mount);
            }
        }
    }

    private ConstructMounts() {}
}
