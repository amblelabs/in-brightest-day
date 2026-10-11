package dev.amble.core.ringpowers;

import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.Emotion;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.progression.SpectrumMeters;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RedRage {
    private static final int RED = LanternCorps.RED.color();
    private static final float RAGE_DAMAGE = 0.5F;
    private static final int CHARGE_PER_DAMAGE_DEALT = 4;
    private static final int CHARGE_PER_DAMAGE_TAKEN = 6;
    private static final int RETALIATION_BURN_SECONDS = 4;
    private static final int BOIL_THRESHOLD = 750;
    private static final int BOIL_INTERVAL = 8;
    public static final int BERSERK_TICKS = 200;
    private static final int BERSERK_SPEED_AMPLIFIER = 1;
    private static final float BERSERK_LIFESTEAL = 0.25F;
    private static final Identifier BERSERK_MODIFIER = BrightestDay.id("berserk");

    private static final Map<UUID, Long> BERSERK = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RedRage::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Player player && source.is(DamageTypeTags.IS_FIRE) && wearsRed(player)));
    }

    public static boolean wearsRed(Player player) {
        return PowerRingItem.getWornCorps(player).orElse(null) == LanternCorps.RED;
    }

    public static boolean isRed(Player player) {
        return wearsRed(player) && PowerRingItem.hasCharge(player);
    }

    public static boolean isBerserk(Player player) {
        return BERSERK.containsKey(player.getUUID());
    }

    public static float scale(LivingEntity victim, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim || !isRed(attacker)) return amount;
        float rage = isBerserk(attacker) ? 1.0F : SpectrumMeters.get(attacker, Emotion.RAGE) / (float) Emotion.MAX;
        float scaled = amount * (1.0F + RAGE_DAMAGE * rage);
        return BloodHunt.isPrey(attacker, victim) ? scaled * BloodHunt.DAMAGE_BONUS : scaled;
    }

    public static void onHurt(LivingEntity victim, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && wearsRed(attacker)) {
            fuel(attacker, Math.round(amount * CHARGE_PER_DAMAGE_DEALT));
            RedBlood.onDealt(attacker, amount);
            if (isBerserk(attacker)) attacker.heal(amount * BERSERK_LIFESTEAL);
        }
        if (!(victim instanceof ServerPlayer red) || !wearsRed(red)) return;

        fuel(red, Math.round(amount * CHARGE_PER_DAMAGE_TAKEN));
        if (source.getEntity() instanceof LivingEntity attacker && attacker != red && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_FIRE)) {
            attacker.igniteForSeconds(RETALIATION_BURN_SECONDS);
        }
    }

    private static void fuel(ServerPlayer player, int amount) {
        ItemStack ring = PowerRingItem.getWornRing(player);
        if (ring.isEmpty() || amount <= 0 || CentralPowerBattery.severed(player.level().getServer(), LanternCorps.RED)) return;
        int room = RingRanks.capacity(player, LanternCorps.RED) - PowerRingItem.getRingPower(ring);
        if (room <= 0) return;
        PowerRingItem.chargeRing(ring, Math.min(amount, room));
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
    }

    public static void berserk(ServerPlayer player) {
        if (!isRed(player) || isBerserk(player)) return;
        if (SpectrumMeters.get(player, Emotion.RAGE) < Emotion.MAX) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.berserk.not_ready").withColor(RED));
            return;
        }

        BERSERK.put(player.getUUID(), player.level().getGameTime() + BERSERK_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, BERSERK_TICKS, BERSERK_SPEED_AMPLIFIER, false, false, true));
        AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null && !knockback.hasModifier(BERSERK_MODIFIER)) {
            knockback.addTransientModifier(new AttributeModifier(BERSERK_MODIFIER, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
        player.sendOverlayMessage(Component.translatable("message.brightestday.berserk.start").withStyle(ChatFormatting.BOLD).withColor(RED));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    private static void endBerserk(ServerPlayer player) {
        AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.removeModifier(BERSERK_MODIFIER);
        SpectrumMeters.set(player, Emotion.RAGE, 0);
        player.sendOverlayMessage(Component.translatable("message.brightestday.berserk.end").withColor(RED));
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        BERSERK.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true;
            long remaining = entry.getValue() - now;
            if (remaining <= 0 || !player.isAlive() || !wearsRed(player)) {
                endBerserk(player);
                return true;
            }
            SpectrumMeters.set(player, Emotion.RAGE, Math.round(Emotion.MAX * remaining / (float) BERSERK_TICKS));
            if (remaining % 4 == 0) {
                player.level().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.35, 0.6, 0.35, 0.02);
            }
            return false;
        });

        if (server.getTickCount() % BOIL_INTERVAL != 0) return;
        DustParticleOptions blood = new DustParticleOptions(RED, 1.0F);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !isRed(player) || SpectrumMeters.get(player, Emotion.RAGE) < BOIL_THRESHOLD) continue;
            player.level().sendParticles(blood, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
            player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.6, player.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
        }
    }

    private RedRage() {}
}
