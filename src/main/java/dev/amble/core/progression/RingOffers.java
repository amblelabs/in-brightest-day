package dev.amble.core.progression;

import dev.amble.core.loyalty.RingBonds;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.BrightestDayItems;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingLoyalty;
import dev.amble.core.networking.payloads.s2c.TintFlashS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RingOffers {
    private static final long RED_COOLDOWN = 24000;
    private static final int MINUTES_PER_DAY = 20;
    private static final int FLASH_TICKS = 60;
    private static final Map<Item, Integer> HOARD = Map.ofEntries(
            Map.entry(Items.GOLD_INGOT, 1),
            Map.entry(Items.GOLD_BLOCK, 9),
            Map.entry(Items.EMERALD, 2),
            Map.entry(Items.EMERALD_BLOCK, 18),
            Map.entry(Items.DIAMOND, 4),
            Map.entry(Items.DIAMOND_BLOCK, 36),
            Map.entry(Items.ANCIENT_DEBRIS, 8),
            Map.entry(Items.NETHERITE_SCRAP, 8),
            Map.entry(Items.NETHERITE_INGOT, 36),
            Map.entry(Items.NETHERITE_BLOCK, 324));

    private static final Map<UUID, Long> RED_REFUSED = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RingOffers::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RED_REFUSED.clear());
    }

    public static ItemStack forge(LanternCorps corps, Player bearer) {
        ItemStack ring = new ItemStack(BrightestDayItems.ring(corps));
        ring.set(BrightestDayComponents.POWER_TYPE, RingRanks.capacity(bearer, corps));
        return ring;
    }

    public static void bestow(ServerPlayer player, LanternCorps corps) {
        RingLoyalty.deliver(player, forge(corps, player));
    }

    private static boolean ringless(Player player) {
        return BrightestDayAttachments.getRing(player).isEmpty() && !player.isSpectator()
                && !(player instanceof ServerPlayer server && RingBonds.bonded(server));
    }

    private static void offer(ServerPlayer player, LanternCorps corps, String key) {
        if (corps != LanternCorps.ORANGE && !CorpsCaps.admits(player, corps)) return;
        if (RingLoyalty.awaiting(player)) return;
        player.sendSystemMessage(Component.translatable("message.brightestday.offer." + key).withStyle(ChatFormatting.ITALIC).withColor(corps.color()));
        RingLoyalty.offer(player, forge(corps, player), new RingLoyalty.Offer() {
            @Override
            public boolean accept(ServerPlayer bearer, ItemStack ring) {
                return accepted(bearer, corps);
            }

            @Override
            public void refuse(ServerPlayer bearer, ItemStack ring) {
                if (corps == LanternCorps.RED) RED_REFUSED.put(bearer.getUUID(), bearer.level().getGameTime());
                bearer.sendSystemMessage(Component.translatable("message.brightestday.offer.refused").withStyle(ChatFormatting.GRAY));
            }
        });
    }

    public static void forceOffer(ServerPlayer player, LanternCorps corps) {
        switch (corps) {
            case RED -> offer(player, corps, "red");
            case ORANGE -> offer(player, corps, "orange");
            default -> bestow(player, corps);
        }
    }

    private static boolean accepted(ServerPlayer player, LanternCorps corps) {
        MinecraftServer server = player.level().getServer();
        if (OrangeDuels.involved(server, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.brightestday.duel.pending").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!ringless(player)) {
            player.sendSystemMessage(Component.translatable("message.brightestday.offer.bound").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (corps == LanternCorps.ORANGE && !CorpsCaps.admits(player, LanternCorps.ORANGE)) {
            UUID holder = orangeHolder(server);
            if (holder != null && !holder.equals(player.getUUID())) OrangeDuels.challenge(player, holder);
            else player.sendSystemMessage(Component.translatable("message.brightestday.offer.absent").withStyle(ChatFormatting.GRAY));
            return false;
        }
        return CorpsCaps.check(player, corps);
    }

    public static void considerOrange(ServerPlayer player) {
        if (!ringless(player) || RingLoyalty.awaiting(player) || OrangeDuels.involved(player.level().getServer(), player.getUUID())) return;
        if (hoard(player) < BrightestDayConfig.get().orangeHoardValue) return;
        float chance = Math.min(0.5F, SpectrumMeters.get(player, Emotion.AVARICE) / 2000.0F);
        if (player.getRandom().nextFloat() >= chance) return;
        offer(player, LanternCorps.ORANGE, "orange");
    }

    public static int hoard(Player player) {
        int value = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Integer worth = HOARD.get(stack.getItem());
            if (worth != null) value += worth * stack.getCount();
        }
        return value;
    }

    public static void considerGreen(ServerPlayer player) {
        if (!ringless(player) || RingLoyalty.inFlight(LanternCorps.GREEN) || !CorpsCaps.admits(player, LanternCorps.GREEN)) return;
        MinecraftServer server = player.level().getServer();
        BrightestDayConfig config = BrightestDayConfig.get();
        if (SpectrumMeters.get(player, Emotion.WILL) < config.greenRingWill) return;

        int online = server.getPlayerList().getPlayerCount();
        int slots = Math.max(1, online / Math.max(1, config.greenPlayersPerRing));
        int bearers = CorpsCaps.bearers(server, LanternCorps.GREEN).size();
        if (bearers >= slots) return;

        player.sendSystemMessage(Component.translatable("message.brightestday.offer.green").withStyle(ChatFormatting.ITALIC).withColor(LanternCorps.GREEN.color()));
        bestow(player, LanternCorps.GREEN);
    }

    private static @Nullable UUID orangeHolder(MinecraftServer server) {
        UUID offline = null;
        for (UUID bearer : CorpsCaps.bearers(server, LanternCorps.ORANGE)) {
            ServerPlayer online = server.getPlayerList().getPlayer(bearer);
            if (online != null && !online.isSpectator()) return bearer;
            if (offline == null) offline = bearer;
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (PowerRingItem.getCorps(BrightestDayAttachments.getRing(other)).orElse(null) == LanternCorps.ORANGE) return other.getUUID();
        }
        return offline;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 1200 != 0) return;
        long now = server.overworld().getGameTime();
        RED_REFUSED.values().removeIf(refused -> now - refused >= RED_COOLDOWN);
        BrightestDayConfig config = BrightestDayConfig.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ringless(player)) continue;
            considerGreen(player);
            if (RingLoyalty.awaiting(player) || SpectrumMeters.get(player, Emotion.RAGE) < config.redOfferRage) continue;
            Long refused = RED_REFUSED.get(player.getUUID());
            if (refused != null && now - refused < RED_COOLDOWN) continue;
            if (player.getRandom().nextFloat() >= config.redOfferDailyChance / MINUTES_PER_DAY) continue;

            ServerPlayNetworking.send(player, new TintFlashS2CPayload(LanternCorps.RED.color(), FLASH_TICKS));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
            RED_REFUSED.put(player.getUUID(), now);
            offer(player, LanternCorps.RED, "red");
        }
    }

    private RingOffers() {}
}
