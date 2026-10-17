package dev.amble.core.space;

import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.RedRage;
import dev.amble.core.sync.RingSync;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SolarHeat {
    public static final double WARM = 3.0;
    private static final double SCORCH = 2.0;
    private static final double CORONA = 1.45;
    private static final double SURFACE = 1.05;
    private static final int INTERVAL = 10;
    private static final int WARM_DRAIN = 6;
    private static final int SCORCH_DRAIN = 15;
    private static final int CORONA_DRAIN = 30;
    private static final float SCORCH_DAMAGE = 1.0F;
    private static final float CORONA_DAMAGE = 3.0F;
    private static final float SURFACE_DAMAGE = 8.0F;
    private static final float RED_RESIST = 0.5F;
    private static final double SURFACE_BOUNCE = 1.6;
    private static final long REWARD_COOLDOWN = 24000L;
    private static final int SOLAR = 0xFFFFD866;

    private static final Map<UUID, Long> REWARDED = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SolarHeat::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> REWARDED.clear());
    }

    public static Cosmos.@Nullable Star nearest(ResourceKey<Level> sector, Vec3 position, double clock) {
        Cosmos.Star best = null;
        double closest = Double.MAX_VALUE;
        for (Cosmos.Star star : Cosmos.stars(sector, clock)) {
            double reach = position.distanceTo(star.center()) / star.half();
            if (reach < closest) {
                closest = reach;
                best = star;
            }
        }
        return best;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) return;
        double clock = CosmosClock.now(server);
        for (Cosmos.Sector info : Cosmos.SECTORS) {
            ServerLevel sector = server.getLevel(info.key());
            if (sector != null && !sector.players().isEmpty()) heat(sector, clock);
        }
    }

    private static void heat(ServerLevel sector, double clock) {
        long now = sector.getGameTime();
        for (ServerPlayer player : sector.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            Vec3 body = player.getBoundingBox().getCenter();
            Cosmos.Star star = nearest(sector.dimension(), body, clock);
            if (star == null) continue;
            double reach = body.distanceTo(star.center()) / star.half();
            if (reach > WARM) continue;
            boolean red = RedRage.wearsRed(player);
            float resist = red ? RED_RESIST : 1.0F;

            if (reach <= SURFACE) {
                Vec3 out = body.subtract(star.center());
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
                player.setDeltaMovement(out.scale(SURFACE_BOUNCE));
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.hurtServer(sector, sector.damageSources().inFire(), SURFACE_DAMAGE * resist);
                player.igniteForSeconds(5.0F);
                drain(player, CORONA_DRAIN * 2);
                player.sendOverlayMessage(Component.translatable("message.brightestday.sun.surface").withColor(SOLAR));
                continue;
            }
            if (reach <= CORONA) {
                reward(player, now);
                player.hurtServer(sector, sector.damageSources().onFire(), CORONA_DAMAGE * resist);
                player.igniteForSeconds(3.0F);
                drain(player, CORONA_DRAIN);
                flare(sector, player);
                continue;
            }
            if (reach <= SCORCH) {
                player.hurtServer(sector, sector.damageSources().onFire(), SCORCH_DAMAGE * resist);
                drain(player, SCORCH_DRAIN);
                player.sendOverlayMessage(Component.translatable("message.brightestday.sun.scorch").withColor(SOLAR));
                continue;
            }
            drain(player, WARM_DRAIN);
            player.sendOverlayMessage(Component.translatable("message.brightestday.sun.heat").withColor(SOLAR));
        }
    }

    private static void drain(ServerPlayer player, int amount) {
        if (!PowerRingItem.getWornRing(player).isEmpty()) PowerRingItem.drainWorn(player, amount);
    }

    private static void reward(ServerPlayer player, long now) {
        Long last = REWARDED.get(player.getUUID());
        if (last != null && now - last < REWARD_COOLDOWN) return;
        ItemStack ring = PowerRingItem.getWornRing(player);
        if (ring.isEmpty()) return;
        REWARDED.put(player.getUUID(), now);
        PowerRingItem.chargeRing(ring, BrightestDayComponents.MAX_POWER);
        RingSync.restore(ring);
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
        player.sendSystemMessage(Component.translatable("message.brightestday.sun.touched").withStyle(ChatFormatting.BOLD).withColor(SOLAR));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 1.4F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    private static void flare(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(1.0), player.getZ(), 12, 0.6, 0.8, 0.6, 0.02);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY(0.5), player.getZ(), 2, 0.4, 0.4, 0.4, 0.0);
    }

    private SolarHeat() {}
}
