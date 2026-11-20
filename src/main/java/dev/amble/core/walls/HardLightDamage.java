package dev.amble.core.walls;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.networking.payloads.s2c.HardLightCracksS2CPayload;
import dev.amble.core.team.RingTargets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class HardLightDamage {
    private static final int STAGES = 9;
    private static final double EXPLOSION_REACH = 1.6;

    private static final Map<ServerLevel, Map<BlockPos, Crack>> CRACKS = new WeakHashMap<>();

    private static final class Crack {
        float damage;
        long hitAt;
        int stage;
        boolean broken;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(HardLightDamage::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CRACKS.clear());
    }

    public static boolean hit(ServerLevel level, HitResult hit, float amount, @Nullable Entity attacker) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && hit(level, block.getBlockPos(), amount, attacker);
    }

    public static boolean hit(ServerLevel level, BlockPos pos, float amount, @Nullable Entity attacker) {
        if (amount <= 0.0F || !WallManager.owns(level, pos) || friendly(level, pos, attacker)) return false;

        BlockPos cell = pos.immutable();
        Crack crack = CRACKS.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(cell, key -> new Crack());
        if (crack.broken) return false;
        crack.damage += amount;
        crack.hitAt = level.getGameTime();

        float health = BrightestDayConfig.get().hardLightHealth;
        Vec3 center = Vec3.atCenterOf(cell);
        if (crack.damage >= health) {
            crack.broken = true;
            crack.stage = HardLightCracksS2CPayload.BROKEN;
            int color = WallManager.colorAt(level, cell);
            broadcast(level, List.of(cell), new byte[]{HardLightCracksS2CPayload.BROKEN});
            WallManager.shatter(level, cell);
            level.sendParticles(new DustParticleOptions(color, 1.4F), center.x, center.y, center.z, 14, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.3F);
            return true;
        }

        int stage = Mth.clamp(Mth.ceil(crack.damage / health * STAGES), 1, STAGES);
        if (stage != crack.stage) {
            crack.stage = stage;
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.7F, 0.6F + stage * 0.08F);
            broadcast(level, List.of(cell), new byte[]{(byte) stage});
        }
        return true;
    }

    public static void explosion(ServerLevel level, Vec3 center, float power, @Nullable Entity attacker) {
        if (power <= 0.0F || !WallManager.any(level)) return;
        double reach = power * EXPLOSION_REACH;
        float peak = power * BrightestDayConfig.get().hardLightExplosionDamage;
        int span = Mth.ceil(reach);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-span, -span, -span), origin.offset(span, span, span))) {
            if (!WallManager.owns(level, pos)) continue;
            double distance = Vec3.atCenterOf(pos).distanceTo(center);
            if (distance > reach) continue;
            hit(level, pos, (float) (peak * (1.0 - distance / reach)), attacker);
        }
    }

    public static void burst(ServerLevel level, Vec3 center, double radius, float amount, @Nullable Entity attacker) {
        if (amount <= 0.0F || !WallManager.any(level)) return;
        int span = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-span, -span, -span), origin.offset(span, span, span))) {
            if (!WallManager.owns(level, pos)) continue;
            double distance = Vec3.atCenterOf(pos).distanceTo(center);
            if (distance > radius) continue;
            hit(level, pos, (float) (amount * Math.max(0.25, 1.0 - distance / radius)), attacker);
        }
    }

    private static boolean friendly(ServerLevel level, BlockPos pos, @Nullable Entity attacker) {
        if (attacker == null) return false;
        UUID caster = WallManager.casterAt(level, pos);
        if (caster == null) return false;
        Entity root = RingTargets.root(attacker);
        if (root.getUUID().equals(caster)) return true;
        Player owner = level.getServer().getPlayerList().getPlayer(caster);
        return owner != null && RingTargets.allied(root, owner);
    }

    public static void forget(ServerLevel level, Collection<BlockPos> cells) {
        Map<BlockPos, Crack> cracks = CRACKS.get(level);
        if (cracks == null || cracks.isEmpty()) return;
        List<BlockPos> cleared = new ArrayList<>();
        for (BlockPos pos : cells) {
            if (cracks.remove(pos) != null) cleared.add(pos);
        }
        if (!cleared.isEmpty()) broadcast(level, cleared, new byte[cleared.size()]);
    }

    public static void sendTo(ServerPlayer player, ServerLevel level, Collection<BlockPos> cells) {
        Map<BlockPos, Crack> cracks = CRACKS.get(level);
        if (cracks == null || cracks.isEmpty()) return;
        List<BlockPos> positions = new ArrayList<>();
        List<Byte> stages = new ArrayList<>();
        for (BlockPos pos : cells) {
            Crack crack = cracks.get(pos);
            if (crack == null) continue;
            positions.add(pos);
            stages.add((byte) crack.stage);
        }
        if (!positions.isEmpty()) ServerPlayNetworking.send(player, new HardLightCracksS2CPayload(positions, bytes(stages)));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        BrightestDayConfig config = BrightestDayConfig.get();
        float health = config.hardLightHealth;
        for (Map.Entry<ServerLevel, Map<BlockPos, Crack>> entry : CRACKS.entrySet()) {
            ServerLevel level = entry.getKey();
            long now = level.getGameTime();
            List<BlockPos> positions = new ArrayList<>();
            List<Byte> stages = new ArrayList<>();
            Iterator<Map.Entry<BlockPos, Crack>> iterator = entry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Crack> cell = iterator.next();
                Crack crack = cell.getValue();
                if (crack.broken || now - crack.hitAt < config.hardLightMendDelay) continue;
                crack.damage -= config.hardLightMendPerSecond;
                int stage = crack.damage <= 0.0F ? 0 : Mth.clamp(Mth.ceil(crack.damage / health * STAGES), 1, STAGES);
                if (stage == 0) iterator.remove();
                if (stage == crack.stage) continue;
                crack.stage = stage;
                positions.add(cell.getKey());
                stages.add((byte) stage);
            }
            if (!positions.isEmpty()) broadcast(level, positions, bytes(stages));
        }
    }

    private static void broadcast(ServerLevel level, List<BlockPos> cells, byte[] stages) {
        HardLightCracksS2CPayload payload = new HardLightCracksS2CPayload(cells, stages);
        for (ServerPlayer player : PlayerLookup.tracking(level, cells.getFirst())) ServerPlayNetworking.send(player, payload);
    }

    private static byte[] bytes(List<Byte> values) {
        byte[] out = new byte[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }

    private HardLightDamage() {}
}
