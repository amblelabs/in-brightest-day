package dev.amble.core.ringpowers;

import com.mojang.serialization.Codec;
import dev.amble.BrightestDay;
import dev.amble.core.attacks.area.BarrageManager;
import dev.amble.core.attacks.weapon.TurretManager;
import dev.amble.core.shields.ShieldManager;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RedBlood {
    public static final int MAX = 100;
    private static final float PER_HEART = 8.0F;
    private static final int DRAIN_PER_SECOND = 2;
    private static final int FIRE_COST = 2;
    private static final long IDLE_TICKS = 20L * 60L * 5L;

    public static final AttachmentType<Integer> BLOOD =
            AttachmentRegistry.<Integer>builder()
                    .initializer(() -> 0)
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(BrightestDay.id("red_blood"));

    private static final Map<UUID, Long> LAST_FED = new HashMap<>();
    private static final Map<UUID, Float> REMAINDER = new HashMap<>();

    private static final List<RingPower<?>> BORROWED = List.of(RingPowerRegistry.PIERCING_LANCE, RingPowerRegistry.CHAIN_BOLT, RingPowerRegistry.BOOMERANG_DISC, RingPowerRegistry.RAPID_BARRAGE,
            RingPowerRegistry.NOVA_BURST, RingPowerRegistry.GROUND_SLAM, RingPowerRegistry.SENTRY_TURRET, RingPowerRegistry.ENTITY_SHIELD, RingPowerRegistry.AREA_SHIELD,
            RingPowerRegistry.DRILL);

    public static List<RingPower<?>> borrowed() {
        return BORROWED;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RedBlood::tick);
    }

    public static int get(Player player) {
        return player.getAttachedOrElse(BLOOD, 0);
    }

    public static boolean flowing(Player player) {
        return get(player) > 0;
    }

    public static boolean isBorrowed(RingPower<?> power) {
        return borrowed().contains(power);
    }

    public static void onDealt(ServerPlayer player, float amount) {
        if (amount <= 0.0F || !RedRage.wearsRed(player)) return;
        float gained = amount / 2.0F * PER_HEART + REMAINDER.getOrDefault(player.getUUID(), 0.0F);
        int whole = Mth.floor(gained);
        REMAINDER.put(player.getUUID(), gained - whole);
        LAST_FED.put(player.getUUID(), player.level().getGameTime());
        if (whole > 0) set(player, Math.min(MAX, get(player) + whole));
    }

    public static void onFired(ServerPlayer player, RingPower<?> power) {
        if (!isBorrowed(power) || !RedRage.wearsRed(player)) return;
        spend(player, FIRE_COST);
    }

    private static void spend(ServerPlayer player, int amount) {
        int next = Math.max(0, get(player) - amount);
        set(player, next);
        if (next == 0) shatter(player);
    }

    private static void set(ServerPlayer player, int amount) {
        if (amount == get(player)) return;
        if (amount <= 0) player.removeAttached(BLOOD);
        else player.setAttached(BLOOD, amount);
    }

    private static boolean sustaining(ServerPlayer player) {
        UUID id = player.getUUID();
        return ShieldManager.latestCreatedAt(id) != Long.MIN_VALUE || TurretManager.latestCreatedAt(id) != Long.MIN_VALUE || BarrageManager.isFiring(player);
    }

    private static void shatter(ServerPlayer player) {
        boolean any = sustaining(player);
        BarrageManager.stop(player);
        ShieldManager.dismissAll(player.getUUID());
        TurretManager.dismissAll(player.getUUID());
        REMAINDER.remove(player.getUUID());
        if (!any) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.sendOverlayMessage(Component.translatable("message.brightestday.blood.dry").withColor(LanternCorps.RED.color()));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!flowing(player)) continue;
            if (!RedRage.wearsRed(player)) {
                set(player, 0);
                shatter(player);
                continue;
            }
            long fed = LAST_FED.computeIfAbsent(player.getUUID(), id -> now);
            if (now - fed > IDLE_TICKS) {
                set(player, 0);
                shatter(player);
                continue;
            }
            if (sustaining(player)) spend(player, DRAIN_PER_SECOND);
        }
        LAST_FED.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private RedBlood() {}
}
