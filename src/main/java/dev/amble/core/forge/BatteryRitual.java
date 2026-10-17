package dev.amble.core.forge;

import dev.amble.BrightestDay;
import dev.amble.core.blocks.LanternCharging;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.oath.OathCharge;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BatteryRitual {
    private static final int DURATION_TICKS = 60;
    private static final int HOLD_GRACE_TICKS = 14;
    private static final double MAX_DISTANCE = 8.0;
    private static final int PARTICLE_INTERVAL = 2;
    private static final int CHIME_INTERVAL = 20;

    private static final class Session {
        final BlockPos core;
        final LanternCorps corps;
        final boolean oath;
        final boolean ceremony;
        final int duration;
        long lastHeld;
        int age;

        Session(BlockPos core, LanternCorps corps, boolean oath, boolean ceremony, long lastHeld, int duration) {
            this.duration = duration;
            this.core = core;
            this.corps = corps;
            this.oath = oath;
            this.ceremony = ceremony;
            this.lastHeld = lastHeld;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static final AttachmentType<Unit> PERFORMING = AttachmentRegistry.<Unit>builder()
            .syncWith(StreamCodec.unit(Unit.INSTANCE), AttachmentSyncPredicate.all())
            .buildAndRegister(BrightestDay.id("battery_ritual"));

    public static boolean performing(Player player) {
        return player.hasAttached(PERFORMING);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BatteryRitual::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (SESSIONS.remove(handler.player.getUUID()) != null) OathCharge.end(handler.player);
        });
    }

    public static void hold(ServerPlayer player, BlockPos core, LanternCorps corps, boolean ceremony) {
        long now = player.level().getGameTime();
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.core.equals(core) && session.ceremony == ceremony) {
            session.lastHeld = now;
            return;
        }
        if (session != null && session.oath) OathCharge.end(player);
        boolean oath = OathCharge.begin(player, corps, true);
        if (!oath) player.sendSystemMessage(Component.translatable(corps.oathKey()).withStyle(ChatFormatting.BOLD).withColor(corps.color()));
        SESSIONS.put(player.getUUID(), new Session(core.immutable(), corps, oath, ceremony, now, OathCharge.timed(player, DURATION_TICKS)));
        player.setAttached(PERFORMING, Unit.INSTANCE);
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }

            boolean released = now - session.lastHeld > HOLD_GRACE_TICKS;
            boolean broken = !player.isAlive() || player.hurtTime > 0 || player.level() != server.overworld()
                    || PowerRingItem.getCorps(PowerRingItem.getWornRing(player)).orElse(null) != session.corps
                    || player.getEyePosition().distanceTo(Vec3.atCenterOf(session.core)) > MAX_DISTANCE;
            boolean silent = !released && !broken && session.oath && !OathCharge.tick(player, session.corps.color());
            if (released || broken || silent) {
                iterator.remove();
                player.removeAttached(PERFORMING);
                if (session.oath) OathCharge.end(player);
                player.sendOverlayMessage(Component.translatable(silent ? "message.brightestday.oath.silent" : "message.brightestday.battery.ritual_broken").withColor(session.corps.color()));
                continue;
            }

            session.age++;
            float progress = session.oath ? OathCharge.progress(player) : Math.min(1.0F, session.age / (float) session.duration);
            effects(player, session, progress);
            boolean done = session.oath ? OathCharge.complete(player) : session.age >= session.duration;
            if (!done) continue;
            iterator.remove();
            player.removeAttached(PERFORMING);
            if (session.oath) OathCharge.end(player);
            if (session.ceremony) CentralPowerBattery.sworn(player.level(), session.core, player);
            else CentralPowerBattery.resync(player.level(), session.core, player, session.corps);
        }
    }

    private static void effects(ServerPlayer player, Session session, float progress) {
        ServerLevel level = player.level();
        if (session.age % PARTICLE_INTERVAL == 0) {
            Vec3 from = Vec3.atCenterOf(session.core);
            Vec3 to = player.getEyePosition().subtract(0.0, 0.4, 0.0);
            DustParticleOptions dust = new DustParticleOptions(session.corps.color(), 1.0F + progress);
            for (int i = 0; i < 4; i++) {
                Vec3 point = from.lerp(to, player.getRandom().nextDouble());
                level.sendParticles(dust, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        if (session.age % CHIME_INTERVAL == 0) LanternCharging.chime(level, session.core, progress);
    }

    private BatteryRitual() {}
}
