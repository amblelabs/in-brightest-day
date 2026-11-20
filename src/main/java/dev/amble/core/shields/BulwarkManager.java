package dev.amble.core.shields;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.BulwarkS2CPayload;
import dev.amble.core.networking.payloads.s2c.HardLightCracksS2CPayload;
import dev.amble.core.poses.Poses;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BulwarkManager {
    private static final Identifier SLOW_MODIFIER = BrightestDay.id("bulwark");
    private static final double BASE_SLOW = 0.35;
    private static final double SLOW_PER_SIZE = 0.1;
    private static final int DRAIN_BASE = 8;
    private static final int DRAIN_PER_SIZE = 4;
    private static final double MELEE_REACH = 4.5;
    private static final double REPEL = 0.5;
    private static final int STAGES = 9;
    private static final int BROKEN = HardLightCracksS2CPayload.BROKEN;
    private static final int SHATTER_LOCKOUT = 100;

    private static final Map<ServerPlayer, Bulwark> BULWARKS = new HashMap<>();
    private static final Map<UUID, Long> SHATTERED_AT = new HashMap<>();

    private static final class Bulwark {
        final int color;
        int size;
        float[] damage;
        long[] hitAt;
        byte[] stages;

        Bulwark(int color, int size) {
            this.color = color;
            this.size = size;
            int cells = BulwarkArc.damageCells(size);
            this.damage = new float[cells];
            this.hitAt = new long[cells];
            this.stages = new byte[cells];
        }

        // resample the old grid at each new cell's centre so cracks stay roughly where they were
        void resize(int size) {
            int columns = BulwarkArc.damageColumns(size);
            int cells = BulwarkArc.damageCells(size);
            float[] damage = new float[cells];
            long[] hitAt = new long[cells];
            byte[] stages = new byte[cells];
            for (int i = 0; i < cells; i++) {
                double fraction = ((i % columns) + 0.5) / columns * 2.0 - 1.0;
                double height = ((i / columns) + 0.5) / BulwarkArc.DAMAGE_ROWS;
                int old = BulwarkArc.damageCell(this.size, fraction, height);
                damage[i] = this.damage[old];
                hitAt[i] = this.hitAt[old];
                stages[i] = this.stages[old];
            }
            this.size = size;
            this.damage = damage;
            this.hitAt = hitAt;
            this.stages = stages;
        }

        float brokenFraction() {
            int broken = 0;
            for (byte stage : this.stages) if (stage >= BROKEN) broken++;
            return broken / (float) this.stages.length;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BulwarkManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BULWARKS.clear();
            SHATTERED_AT.clear();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !block(entity, source, amount));
        EntityTrackingEvents.START_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayer player && BULWARKS.get(player) instanceof Bulwark bulwark) {
                ServerPlayNetworking.send(watcher, payload(player, bulwark, true));
            }
        });
    }

    public static void start(ServerPlayer player, int color, int size) {
        if (BULWARKS.containsKey(player)) return;
        Long shattered = SHATTERED_AT.get(player.getUUID());
        if (shattered != null && player.level().getGameTime() - shattered < SHATTER_LOCKOUT) return;
        Bulwark bulwark = new Bulwark(color, Mth.clamp(size, BulwarkArc.MIN_SIZE, BulwarkArc.MAX_SIZE));
        BULWARKS.put(player, bulwark);
        slow(player, bulwark.size);
        broadcast(player, bulwark, true);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    public static void resize(ServerPlayer player, int size) {
        Bulwark bulwark = BULWARKS.get(player);
        int clamped = RingPowerRegistry.BULWARK.clampSize(player, size);
        if (bulwark == null || bulwark.size == clamped) return;
        bulwark.resize(clamped);
        slow(player, clamped);
        broadcast(player, bulwark, true);
    }

    public static void stop(ServerPlayer player) {
        Bulwark bulwark = BULWARKS.remove(player);
        if (bulwark == null) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_MODIFIER);
        broadcast(player, bulwark, false);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public static boolean isRaised(Entity entity) {
        return entity instanceof ServerPlayer player && BULWARKS.containsKey(player);
    }

    private static void slow(ServerPlayer player, int size) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOW_MODIFIER);
        speed.addTransientModifier(new AttributeModifier(SLOW_MODIFIER, -(BASE_SLOW + SLOW_PER_SIZE * (size - BulwarkArc.MIN_SIZE)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static boolean block(LivingEntity victim, DamageSource source, float amount) {
        if (BULWARKS.isEmpty()) return false;
        Vec3 origin = source.getSourcePosition();
        if (origin == null) return false;
        for (Map.Entry<ServerPlayer, Bulwark> entry : BULWARKS.entrySet()) {
            ServerPlayer holder = entry.getKey();
            Bulwark bulwark = entry.getValue();
            if (holder.level() != victim.level() || source.getEntity() == holder) continue;
            BulwarkArc arc = BulwarkArc.of(holder, holder.position(), holder.getYRot(), bulwark.size);
            if (!arc.shelters(victim.position(), victim.getBbHeight(), origin)) continue;

            Vec3 impact = arc.crossing(origin, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0));
            int cell = BulwarkArc.damageCell(bulwark.size, arc.fractionOf(impact), arc.heightOf(impact.y));
            // a broken cell is a hole: the hit goes through unless another bulwark catches it
            if (bulwark.stages[cell] >= BROKEN) continue;

            victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.9F + victim.getRandom().nextFloat() * 0.2F);
            if (source.getDirectEntity() instanceof LivingEntity attacker && attacker.distanceTo(victim) <= MELEE_REACH) {
                Vec3 away = attacker.position().subtract(arc.center()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    attacker.push(away.normalize().scale(REPEL).add(0.0, 0.1, 0.0));
                    attacker.needsSync = true;
                }
            }
            if (damage(holder, bulwark, cell, amount, impact)) shatter(holder, bulwark);
            return true;
        }
        return false;
    }

    // returns true when the bulwark has lost enough cells to shatter
    private static boolean damage(ServerPlayer holder, Bulwark bulwark, int cell, float amount, Vec3 impact) {
        if (amount <= 0.0F) return false;
        BrightestDayConfig config = BrightestDayConfig.get();
        ServerLevel level = holder.level();
        float health = config.hardLightHealth;
        bulwark.damage[cell] += amount;
        bulwark.hitAt[cell] = level.getGameTime();

        if (bulwark.damage[cell] >= health) {
            bulwark.stages[cell] = BROKEN;
            level.sendParticles(new DustParticleOptions(bulwark.color, 1.4F), impact.x, impact.y, impact.z, 14, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.3F);
            if (bulwark.brokenFraction() >= config.domeShatterFraction) return true;
            broadcast(holder, bulwark, true);
            return false;
        }

        int stage = Mth.clamp(Mth.ceil(bulwark.damage[cell] / health * STAGES), 1, STAGES);
        if (stage != bulwark.stages[cell]) {
            bulwark.stages[cell] = (byte) stage;
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.7F, 0.6F + stage * 0.08F);
            broadcast(holder, bulwark, true);
        }
        return false;
    }

    private static void shatter(ServerPlayer holder, Bulwark bulwark) {
        ServerLevel level = holder.level();
        BulwarkArc arc = BulwarkArc.of(holder, holder.position(), holder.getYRot(), bulwark.size);
        for (int i = -2; i <= 2; i++) {
            Vec3 point = arc.point(i / 2.0, 0.5);
            level.sendParticles(new DustParticleOptions(bulwark.color, 1.6F), point.x, point.y, point.z, 12, 0.3, 0.6, 0.3, 0.08);
        }
        level.playSound(null, holder.getX(), holder.getEyeY(), holder.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
        SHATTERED_AT.put(holder.getUUID(), level.getGameTime());
        stop(holder);
    }

    private static void tick(MinecraftServer server) {
        boolean drainTick = server.getTickCount() % 20 == 0;
        for (ServerPlayer player : new ArrayList<>(BULWARKS.keySet())) {
            Bulwark bulwark = BULWARKS.get(player);
            boolean selected = ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.BULWARK;
            boolean outOfCharge = !PowerRingItem.hasCharge(player)
                    || drainTick && !player.hasInfiniteMaterials() && !PowerRingItem.drainWorn(player, DRAIN_BASE + DRAIN_PER_SIZE * (bulwark.size - BulwarkArc.MIN_SIZE));
            if (player.isRemoved() || !player.isAlive() || !ArmedRingPower.isArmed(player) && !Poses.channeling(player) || !selected || outOfCharge) {
                stop(player);
                continue;
            }
            if (drainTick) mend(player, bulwark);
        }
    }

    // cracks mend like hard light walls; holes stay until the bulwark is lowered
    private static void mend(ServerPlayer player, Bulwark bulwark) {
        BrightestDayConfig config = BrightestDayConfig.get();
        long now = player.level().getGameTime();
        boolean changed = false;
        for (int i = 0; i < bulwark.stages.length; i++) {
            if (bulwark.stages[i] <= 0 || bulwark.stages[i] >= BROKEN || now - bulwark.hitAt[i] < config.hardLightMendDelay) continue;
            bulwark.damage[i] = Math.max(0.0F, bulwark.damage[i] - config.hardLightMendPerSecond);
            int stage = bulwark.damage[i] <= 0.0F ? 0 : Mth.clamp(Mth.ceil(bulwark.damage[i] / config.hardLightHealth * STAGES), 1, STAGES);
            if (stage == bulwark.stages[i]) continue;
            bulwark.stages[i] = (byte) stage;
            changed = true;
        }
        if (changed) broadcast(player, bulwark, true);
    }

    private static BulwarkS2CPayload payload(ServerPlayer player, Bulwark bulwark, boolean active) {
        return new BulwarkS2CPayload(player.getId(), active, bulwark.size, bulwark.color, active ? bulwark.stages.clone() : new byte[0]);
    }

    private static void broadcast(ServerPlayer player, Bulwark bulwark, boolean active) {
        BulwarkS2CPayload payload = payload(player, bulwark, active);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private BulwarkManager() {}
}
