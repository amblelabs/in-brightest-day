package dev.amble.core.sync;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class RingSync {
    public static final float DEGRADED = 0.5F;
    public static final float UNSTABLE = 0.25F;
    private static final float STUTTER_TICKS = 2.0F;
    private static final float STUTTER_CHANCE = 0.6F;
    private static final float STUTTER_DIM = 0.15F;
    private static final float MISFIRE_CHANCE = 0.15F;
    private static final float CHARGE_PENALTY = 1.5F;
    private static final int TICK_INTERVAL = 20;
    private static final int LOCKED_REMINDER_TICKS = 400;
    private static final float DAY_TICKS = 24000.0F;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RingSync::tick);
    }

    public static boolean drifts(LanternCorps corps) {
        return CentralPowerBattery.required(corps) || corps == LanternCorps.ORANGE;
    }

    public static float stutter(float sync, float time) {
        if (sync >= DEGRADED) return 1.0F;
        float severity = (DEGRADED - Math.max(0.0F, sync)) / DEGRADED;
        int bucket = Mth.floor(time / STUTTER_TICKS);
        float noise = (Mth.murmurHash3Mixer(bucket) & 0xFFFF) / 65535.0F;
        return noise < severity * STUTTER_CHANCE ? STUTTER_DIM : 1.0F;
    }

    public static float sync(ItemStack ring) {
        return ring.getOrDefault(BrightestDayComponents.RING_SYNC, 1.0F);
    }

    public static float sync(Player player) {
        ItemStack ring = BrightestDayAttachments.getRing(player);
        LanternCorps corps = PowerRingItem.getCorps(ring).orElse(null);
        if (corps == null || !drifts(corps) || PowerRingItem.isDormant(ring)) return 1.0F;
        return sync(ring);
    }

    public static boolean locked(Player player) {
        return sync(player) <= 0.0F;
    }

    public static int chargeTicks(Player player, int base) {
        return sync(player) < DEGRADED ? Math.round(base * CHARGE_PENALTY) : base;
    }

    public static void restore(ItemStack ring) {
        ring.remove(BrightestDayComponents.RING_SYNC);
    }

    public static int percent(float sync) {
        return Math.round(sync * 100.0F);
    }

    public static boolean misfire(ServerPlayer player, ConstructRingPower construct) {
        if (construct == RingPowerRegistry.RING_COMPASS) return false;
        float sync = sync(player);
        if (sync >= UNSTABLE || player.getRandom().nextFloat() >= MISFIRE_CHANCE) return false;
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.8));
        player.level().sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 12, 0.15, 0.15, 0.15, 0.02);
        player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 8, 0.2, 0.2, 0.2, 0.1);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.6F);
        player.sendOverlayMessage(Component.translatable("message.brightestday.sync.misfire", percent(sync), home(player)).withColor(0xFFFF7A5A));
        return true;
    }

    private static Component home(Player player) {
        boolean orange = PowerRingItem.getWornCorps(player).orElse(null) == LanternCorps.ORANGE;
        return Component.translatable(orange ? "message.brightestday.sync.home.lantern" : "message.brightestday.sync.home.battery");
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICK_INTERVAL != 0) return;
        float drift = TICK_INTERVAL / (Math.max(1, BrightestDayConfig.get().ringSyncDays) * DAY_TICKS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack ring = BrightestDayAttachments.getRing(player);
            LanternCorps corps = PowerRingItem.getCorps(ring).orElse(null);
            if (corps == null || !drifts(corps) || PowerRingItem.isDormant(ring) || player.hasInfiniteMaterials()) continue;
            if (CentralPowerBattery.required(corps) && !CentralPowerBattery.active(server, corps)) continue;

            float before = sync(ring);
            if (before <= 0.0F) {
                if (server.getTickCount() % LOCKED_REMINDER_TICKS == 0) warn(player, "message.brightestday.sync.locked", 0.0F);
                continue;
            }
            float after = Math.max(0.0F, before - drift);
            ring.set(BrightestDayComponents.RING_SYNC, after);
            BrightestDayAttachments.setRing(player, ring);
            if (before >= DEGRADED && after < DEGRADED) warn(player, "message.brightestday.sync.degraded", after);
            else if (before >= UNSTABLE && after < UNSTABLE) warn(player, "message.brightestday.sync.unstable", after);
            else if (after <= 0.0F) warn(player, "message.brightestday.sync.locked", after);
        }
    }

    private static void warn(ServerPlayer player, String key, float sync) {
        player.sendSystemMessage(Component.translatable(key, percent(sync), home(player)).withStyle(ChatFormatting.ITALIC).withColor(0xFFFFB347));
    }

    private RingSync() {}
}
