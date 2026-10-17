package dev.amble.core.blocks;

import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.LanternRitualS2CPayload;
import dev.amble.core.oath.OathCharge;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LanternRitual {
    public static final int DURATION_TICKS = 140;
    private static final int FLOOR_INSERT_TICKS = 44;
    private static final int TOP_INSERT_TICKS = 48;
    private static final double MAX_DRIFT = 0.3;
    private static final int PARTICLE_INTERVAL = 3;
    private static final int SOUND_INTERVAL = 20;

    private static final class Session {
        final BlockPos pos;
        final LanternCorps corps;
        final Vec3 anchor;
        final int mode;
        final float yaw;
        final boolean oath;
        final int duration;
        int age;

        Session(BlockPos pos, LanternCorps corps, Vec3 anchor, int mode, float yaw, boolean oath, int duration) {
            this.oath = oath;
            this.duration = duration;
            this.pos = pos;
            this.corps = corps;
            this.anchor = anchor;
            this.mode = mode;
            this.yaw = yaw;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LanternRitual::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
    }

    public static boolean begin(ServerPlayer player, BlockPos pos, BlockState state, LanternCorps corps) {
        int height = pos.getY() - player.getBlockY();
        int mode = height == 0 ? LanternRitualS2CPayload.FLOOR : height == 1 ? LanternRitualS2CPayload.TOP : LanternRitualS2CPayload.NONE;
        if (mode == LanternRitualS2CPayload.NONE) return false;
        if (SESSIONS.containsKey(player.getUUID())) return true;

        float yaw = LanternBlock.facingYaw(state);
        Vec3 anchor = snap(player, pos, mode, yaw);
        boolean oath = OathCharge.begin(player, corps, true);
        SESSIONS.put(player.getUUID(), new Session(pos.immutable(), corps, anchor, mode, yaw, oath, OathCharge.timed(player, DURATION_TICKS)));
        broadcast(player, mode, yaw);
        return true;
    }

    public static boolean active(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }

            ItemStack ring = PowerRingItem.getWornRing(player);
            boolean silent = session.oath && !OathCharge.tick(player, session.corps.color());
            if (silent || interrupted(player, session, ring)) {
                iterator.remove();
                if (session.oath) OathCharge.end(player);
                broadcast(player, LanternRitualS2CPayload.NONE, session.yaw);
                player.sendOverlayMessage(Component.translatable(silent ? "message.brightestday.oath.silent" : "message.brightestday.ritual_broken").withColor(session.corps.color()));
                continue;
            }

            session.age++;
            boolean slotted = ring == BrightestDayAttachments.getRing(player);
            int insert = insertTick(session.mode);
            if (session.age == insert) LanternCharging.inserted(player.level(), session.pos);
            if (session.age >= insert) {
                float progress = session.oath ? OathCharge.progress(player) : (session.age - insert) / (float) (session.duration - insert);
                if (!session.oath) {
                    PowerRingItem.chargeRing(ring, Mth.ceil((float) BrightestDayComponents.MAX_POWER / (session.duration - insert)));
                    if (slotted) BrightestDayAttachments.setRing(player, ring);
                }
                effects(player.level(), session, insert, progress);
            }

            boolean done = session.oath ? session.age >= insert && OathCharge.complete(player) : session.age >= session.duration;
            if (!done) continue;
            iterator.remove();
            if (session.oath) OathCharge.end(player);
            broadcast(player, LanternRitualS2CPayload.COMPLETE, session.yaw);
            if (player.level().getBlockState(session.pos).getBlock() instanceof LanternBlock lantern) {
                lantern.complete(player.level(), session.pos, player, ring, slotted, !session.oath);
            }
        }
    }

    private static boolean interrupted(ServerPlayer player, Session session, ItemStack ring) {
        if (!player.isAlive() || player.hurtTime > 0) return true;
        if (PowerRingItem.getCorps(ring).orElse(null) != session.corps) return true;
        if (!(player.level().getBlockState(session.pos).getBlock() instanceof LanternBlock lantern) || lantern.corps() != session.corps) return true;
        Vec3 position = player.position();
        return Mth.lengthSquared(position.x - session.anchor.x, position.z - session.anchor.z) > MAX_DRIFT * MAX_DRIFT
                || Math.abs(position.y - session.anchor.y) > 1.0;
    }

    private static int insertTick(int mode) {
        return mode == LanternRitualS2CPayload.TOP ? TOP_INSERT_TICKS : FLOOR_INSERT_TICKS;
    }

    private static void effects(ServerLevel level, Session session, int insert, float progress) {
        int charged = session.age - insert;
        if (charged % PARTICLE_INTERVAL == 0) {
            DustParticleOptions dust = new DustParticleOptions(session.corps.color(), 1.0F);
            float angle = session.age * 0.4F;
            for (int strand = 0; strand < 2; strand++) {
                float a = angle + strand * Mth.PI;
                level.sendParticles(dust, session.pos.getX() + 0.5 + Mth.cos(a) * 0.6, session.pos.getY() + 0.3 + progress * 0.8,
                        session.pos.getZ() + 0.5 + Mth.sin(a) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (charged > 0 && charged % SOUND_INTERVAL == 0) LanternCharging.chime(level, session.pos, progress);
    }

    private static Vec3 snap(ServerPlayer player, BlockPos pos, int mode, float yaw) {
        float radians = yaw * Mth.DEG_TO_RAD;
        double feet = mode == LanternRitualS2CPayload.TOP ? pos.getY() - 1 : pos.getY();
        Vec3 target = new Vec3(pos.getX() + 0.5 + Mth.sin(radians), feet, pos.getZ() + 0.5 - Mth.cos(radians));
        if (!player.level().noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) return player.position();

        player.teleportTo(player.level(), target.x, target.y, target.z, Set.of(), yaw, player.getXRot(), false);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        return target;
    }

    private static void broadcast(ServerPlayer player, int mode, float yaw) {
        LanternRitualS2CPayload payload = new LanternRitualS2CPayload(player.getId(), mode, yaw);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private LanternRitual() {}
}
