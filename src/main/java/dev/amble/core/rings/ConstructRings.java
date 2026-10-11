package dev.amble.core.rings;

import com.mojang.serialization.Codec;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.RingOffers;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ConstructRings {
    public static final int MINUTES_PER_STEP = 5;
    public static final int MAX_STEPS = 12;
    public static final float WEAK_DAMAGE = 0.5F;
    private static final long MINUTE_MILLIS = 60_000L;
    private static final int UPKEEP_PER_SECOND = 2;

    private record Live(UUID creator, LanternCorps corps, long created, long expires) {}

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    public static final AttachmentType<Map<UUID, Long>> DISMISSED =
            AttachmentRegistry.<Map<UUID, Long>>builder()
                    .initializer(Map::of)
                    .persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG))
                    .buildAndRegister(BrightestDay.id("dismissed_construct_rings"));

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ConstructRings::tick);
    }

    public static boolean isConstruct(ItemStack stack) {
        return stack.has(BrightestDayComponents.CONSTRUCT_RING);
    }

    public static boolean temporary(ItemStack stack) {
        return stack.has(BrightestDayComponents.CONSTRUCT_RING) || stack.has(BrightestDayComponents.FUSED_WHITE);
    }

    public static boolean refuseRecharge(Player player, ItemStack ring) {
        if (!temporary(ring)) return false;
        player.sendOverlayMessage(Component.translatable("message.brightestday.construct_ring.no_recharge").withStyle(ChatFormatting.GRAY));
        return true;
    }

    public static void create(ServerPlayer creator, int steps) {
        ItemStack main = BrightestDayAttachments.getRing(creator);
        LanternCorps corps = PowerRingItem.getCorps(main).orElse(null);
        if (corps == null || temporary(main)) {
            creator.sendOverlayMessage(Component.translatable("message.brightestday.construct_ring.needs_main").withStyle(ChatFormatting.GRAY));
            return;
        }
        int clamped = Math.max(1, Math.min(MAX_STEPS, steps));
        long now = System.currentTimeMillis();
        UUID id = UUID.randomUUID();
        long expires = now + clamped * MINUTES_PER_STEP * MINUTE_MILLIS;
        ItemStack ring = RingOffers.forge(corps, creator);
        ring.set(BrightestDayComponents.POWER_TYPE, PowerRingItem.getRingPower(main));
        ring.set(BrightestDayComponents.CONSTRUCT_RING, new BrightestDayComponents.ConstructRing(id, creator.getUUID(), creator.getScoreboardName(), expires));
        LIVE.put(id, new Live(creator.getUUID(), corps, creator.level().getGameTime(), expires));

        if (!creator.addItem(ring)) creator.drop(ring, false, Prediction.SERVER_ONLY);
        creator.level().sendParticles(ParticleTypes.END_ROD, creator.getX(), creator.getY(1.0), creator.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
        creator.level().playSound(null, creator.getX(), creator.getY(), creator.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
        creator.sendOverlayMessage(Component.translatable("message.brightestday.construct_ring.created", clamped * MINUTES_PER_STEP).withColor(corps.color()));
    }

    public static @Nullable ServerPlayer creator(ServerPlayer wearer, ItemStack ring) {
        BrightestDayComponents.ConstructRing data = ring.get(BrightestDayComponents.CONSTRUCT_RING);
        return data == null ? null : wearer.level().getServer().getPlayerList().getPlayer(data.creator());
    }

    public static long latestCreatedAt(ServerPlayer creator) {
        long latest = Long.MIN_VALUE;
        for (Live live : LIVE.values()) {
            if (live.creator().equals(creator.getUUID())) latest = Math.max(latest, live.created());
        }
        return latest;
    }

    public static void dismissLatest(ServerPlayer creator) {
        UUID newest = null;
        long created = Long.MIN_VALUE;
        for (Map.Entry<UUID, Live> entry : LIVE.entrySet()) {
            if (entry.getValue().creator().equals(creator.getUUID()) && entry.getValue().created() > created) {
                created = entry.getValue().created();
                newest = entry.getKey();
            }
        }
        if (newest == null) return;
        Live live = LIVE.remove(newest);
        MinecraftServer server = creator.level().getServer();
        Map<UUID, Long> dismissed = new HashMap<>(server.overworld().getAttachedOrElse(DISMISSED, Map.of()));
        dismissed.put(newest, live.expires());
        server.overworld().setAttached(DISMISSED, Map.copyOf(dismissed));
        creator.level().playSound(null, creator.getX(), creator.getY(), creator.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
        creator.sendOverlayMessage(Component.translatable("message.brightestday.construct_ring.dismissed").withColor(live.corps().color()));
    }

    public static boolean valid(MinecraftServer server, ItemStack ring) {
        BrightestDayComponents.ConstructRing data = ring.get(BrightestDayComponents.CONSTRUCT_RING);
        LanternCorps corps = PowerRingItem.getCorps(ring).orElse(null);
        return data != null && corps != null && valid(server, data.id(), data.creator(), corps, data.expires());
    }

    private static boolean valid(MinecraftServer server, UUID id, UUID creatorId, LanternCorps corps, long expires) {
        if (System.currentTimeMillis() >= expires) return false;
        if (server.overworld().getAttachedOrElse(DISMISSED, Map.of()).containsKey(id)) return false;
        ServerPlayer creator = server.getPlayerList().getPlayer(creatorId);
        if (creator == null) return false;
        ItemStack main = BrightestDayAttachments.getRing(creator);
        if (temporary(main) || PowerRingItem.getCorps(main).orElse(null) != corps) return false;
        return creator.hasInfiniteMaterials() || PowerRingItem.getRingPower(main) > 0;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;

        LIVE.entrySet().removeIf(entry -> {
            Live live = entry.getValue();
            if (!valid(server, entry.getKey(), live.creator(), live.corps(), live.expires())) return true;
            ServerPlayer creator = server.getPlayerList().getPlayer(live.creator());
            if (creator != null && !creator.hasInfiniteMaterials()) {
                ItemStack main = BrightestDayAttachments.getRing(creator);
                PowerRingItem.drainRing(main, UPKEEP_PER_SECOND);
                BrightestDayAttachments.setRing(creator, main);
            }
            return false;
        });

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack worn = BrightestDayAttachments.getRing(player);
            if (temporary(worn)) {
                if (!inspect(server, player, worn)) BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
                else if (mirror(server, worn)) BrightestDayAttachments.setRing(player, worn);
            }
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!temporary(stack)) continue;
                if (!inspect(server, player, stack)) inventory.setItem(slot, ItemStack.EMPTY);
                else mirror(server, stack);
            }
        }

        Map<UUID, Long> dismissed = server.overworld().getAttachedOrElse(DISMISSED, Map.of());
        if (!dismissed.isEmpty() && server.getTickCount() % 1200 == 0) {
            long now = System.currentTimeMillis();
            Map<UUID, Long> pruned = new HashMap<>(dismissed);
            if (pruned.values().removeIf(expires -> expires < now)) server.overworld().setAttached(DISMISSED, Map.copyOf(pruned));
        }
    }

    private static boolean inspect(MinecraftServer server, ServerPlayer holder, ItemStack stack) {
        if (stack.has(BrightestDayComponents.FUSED_WHITE)) {
            if (WhiteFusion.valid(server, stack)) return true;
            holder.sendSystemMessage(Component.translatable("message.brightestday.white_fusion.faded").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
            return false;
        }
        BrightestDayComponents.ConstructRing data = stack.get(BrightestDayComponents.CONSTRUCT_RING);
        LanternCorps corps = PowerRingItem.getCorps(stack).orElse(null);
        if (data == null || corps == null) return false;
        if (LIVE.containsKey(data.id())) return true;
        if (valid(server, data.id(), data.creator(), corps, data.expires())) {
            LIVE.put(data.id(), new Live(data.creator(), corps, server.overworld().getGameTime(), data.expires()));
            return true;
        }
        holder.sendOverlayMessage(Component.translatable("message.brightestday.construct_ring.faded").withColor(corps.color()));
        return false;
    }

    private static boolean mirror(MinecraftServer server, ItemStack stack) {
        BrightestDayComponents.ConstructRing data = stack.get(BrightestDayComponents.CONSTRUCT_RING);
        if (data == null) return false;
        ServerPlayer creator = server.getPlayerList().getPlayer(data.creator());
        if (creator == null) return false;
        int power = PowerRingItem.getRingPower(BrightestDayAttachments.getRing(creator));
        if (stack.getOrDefault(BrightestDayComponents.POWER_TYPE, 0) == power) return false;
        stack.set(BrightestDayComponents.POWER_TYPE, power);
        return true;
    }

    public static boolean liveOnGround(MinecraftServer server, ItemStack stack) {
        if (stack.has(BrightestDayComponents.FUSED_WHITE)) return WhiteFusion.valid(server, stack);
        BrightestDayComponents.ConstructRing data = stack.get(BrightestDayComponents.CONSTRUCT_RING);
        return data == null || LIVE.containsKey(data.id()) || valid(server, stack);
    }

    public static void consumed(ItemStack stack) {
        BrightestDayComponents.ConstructRing data = stack.get(BrightestDayComponents.CONSTRUCT_RING);
        if (data != null) LIVE.remove(data.id());
    }

    private ConstructRings() {}
}
