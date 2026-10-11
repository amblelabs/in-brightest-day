package dev.amble.core.progression;

import dev.amble.config.BrightestDayConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Terror {
    private static final long DAY = 24000L;
    private static final float DIMINISH = 0.7F;
    private static final float FLOOR = 0.1F;
    private static final long GRACE_TICKS = 6000L;
    private static final int DECAY_INTERVAL = 300;
    private static final int DECAY_AMOUNT = 5;
    private static final long AMBUSH_COOLDOWN = 200L;
    private static final long WOUND_MEMORY = 600L;
    private static final float LOW_HEALTH = 0.3F;
    private static final int DARKNESS = 4;
    private static final double FLEE_SPEED = 0.02;

    private record Strikes(long since, int count) {}

    private record Wound(UUID hunter, long at) {}

    private static final Map<UUID, Map<String, Strikes>> STRIKES = new HashMap<>();
    private static final Map<String, Long> AMBUSHED = new HashMap<>();
    private static final Map<UUID, Wound> WOUNDED = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Terror::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STRIKES.remove(handler.player.getUUID()));
    }

    public static void add(ServerPlayer player, Entity source, int amount) {
        if (amount <= 0) return;
        String key = source instanceof Player target ? "player:" + target.getUUID() : "type:" + BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        long now = player.level().getGameTime();
        Map<String, Strikes> strikes = STRIKES.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Strikes previous = strikes.get(key);
        int count = previous == null || now - previous.since() >= DAY ? 0 : previous.count();
        strikes.put(key, new Strikes(previous == null || count == 0 ? now : previous.since(), count + 1));
        float factor = Math.max(FLOOR, (float) Math.pow(DIMINISH, count));
        SpectrumMeters.add(player, Emotion.FEAR, Math.max(1, Math.round(amount * factor)));
    }

    public static void onHurt(LivingEntity victim, ServerPlayer attacker) {
        long now = attacker.level().getGameTime();
        if (victim.getHealth() < victim.getMaxHealth() * LOW_HEALTH) WOUNDED.putIfAbsent(victim.getUUID(), new Wound(attacker.getUUID(), now));
        if (!ambush(victim, attacker)) return;

        String pair = attacker.getUUID() + ":" + victim.getUUID();
        Long last = AMBUSHED.get(pair);
        if (last != null && now - last < AMBUSH_COOLDOWN) return;
        AMBUSHED.put(pair, now);

        boolean unseen = attacker.isInvisible() || attacker.level().getMaxLocalRawBrightness(attacker.blockPosition()) <= DARKNESS;
        int amount = BrightestDayConfig.get().fearAmbush;
        add(attacker, victim, unseen ? amount * 2 : amount);
    }

    private static boolean ambush(LivingEntity victim, ServerPlayer attacker) {
        if (victim instanceof Mob mob) return mob.getTarget() != attacker;
        if (!(victim instanceof Player)) return false;
        Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1.0, 0.0, 1.0);
        Vec3 facing = victim.getLookAngle().multiply(1.0, 0.0, 1.0);
        return toAttacker.lengthSqr() > 1.0E-4 && facing.lengthSqr() > 1.0E-4 && toAttacker.normalize().dot(facing.normalize()) < 0.0;
    }

    public static void onKill(LivingEntity victim, ServerPlayer killer) {
        Wound wound = WOUNDED.remove(victim.getUUID());
        long now = killer.level().getGameTime();
        boolean hunted = wound != null && wound.hunter().equals(killer.getUUID()) && now - wound.at() <= WOUND_MEMORY;
        boolean panicked = victim instanceof PathfinderMob mob && mob.isPanicking();
        Vec3 step = victim.position().subtract(victim.xo, victim.yo, victim.zo).multiply(1.0, 0.0, 1.0);
        Vec3 away = victim.position().subtract(killer.position()).multiply(1.0, 0.0, 1.0);
        boolean running = away.lengthSqr() > 1.0E-4 && step.dot(away.normalize()) > FLEE_SPEED;
        if (!hunted && !panicked && !running) return;

        BrightestDayConfig config = BrightestDayConfig.get();
        add(killer, victim, victim instanceof Player ? config.fearFleeingKillPlayer : config.fearFleeingKill);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % DECAY_INTERVAL != 0) return;
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int fear = SpectrumMeters.get(player, Emotion.FEAR);
            int floor = SpectrumMeters.idleFloor(fear);
            if (fear <= floor || SpectrumMeters.sustained(player, Emotion.FEAR) || SpectrumMeters.atBattery(player)) continue;
            if (now - SpectrumMeters.lastFed(player, Emotion.FEAR) < GRACE_TICKS) continue;
            SpectrumMeters.drain(player, Emotion.FEAR, Math.min(DECAY_AMOUNT, fear - floor));
        }
        if (server.getTickCount() % (DECAY_INTERVAL * 20) != 0) return;
        AMBUSHED.values().removeIf(time -> now - time > AMBUSH_COOLDOWN);
        WOUNDED.values().removeIf(wound -> now - wound.at() > WOUND_MEMORY);
    }

    private Terror() {}
}
