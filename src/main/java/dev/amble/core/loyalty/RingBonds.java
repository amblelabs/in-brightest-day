package dev.amble.core.loyalty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.RingOffers;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RingBonds {
    private static final int TRACK_INTERVAL = 20;
    private static final long SEEN_REFRESH_MILLIS = 60_000L;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    public record Entry(LanternCorps corps, UUID owner, String ownerName, int generation, long lastSeen, long recalledAt, Optional<ItemStack> snapshot) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LanternCorps.CODEC.fieldOf("corps").forGetter(Entry::corps),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Entry::ownerName),
                Codec.INT.optionalFieldOf("generation", 0).forGetter(Entry::generation),
                Codec.LONG.optionalFieldOf("last_seen", 0L).forGetter(Entry::lastSeen),
                Codec.LONG.optionalFieldOf("recalled_at", 0L).forGetter(Entry::recalledAt),
                ItemStack.CODEC.optionalFieldOf("snapshot").forGetter(Entry::snapshot)
        ).apply(instance, Entry::new));

        Entry seen(long now) {
            return new Entry(this.corps, this.owner, this.ownerName, this.generation, now, this.recalledAt, this.snapshot);
        }

        Entry withSnapshot(ItemStack ring) {
            return new Entry(this.corps, this.owner, this.ownerName, this.generation, this.lastSeen, this.recalledAt, Optional.of(ring.copy()));
        }
    }

    public record Status(Optional<LanternCorps> corps, boolean away, int cooldown) {
        public static final Status NONE = new Status(Optional.empty(), false, 0);

        public static final Codec<Status> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LanternCorps.CODEC.optionalFieldOf("corps").forGetter(Status::corps),
                Codec.BOOL.optionalFieldOf("away", false).forGetter(Status::away),
                Codec.INT.optionalFieldOf("cooldown", 0).forGetter(Status::cooldown)
        ).apply(instance, Status::new));
    }

    public static final AttachmentType<Map<UUID, Entry>> BONDS =
            AttachmentRegistry.<Map<UUID, Entry>>builder()
                    .initializer(Map::of)
                    .persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC))
                    .buildAndRegister(BrightestDay.id("ring_bonds"));

    public static final AttachmentType<Status> STATUS =
            AttachmentRegistry.<Status>builder()
                    .initializer(() -> Status.NONE)
                    .syncWith(ByteBufCodecs.fromCodec(Status.CODEC), AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(BrightestDay.id("ring_bond_status"));

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RingBonds::tick);
    }

    public static Map<UUID, Entry> all(MinecraftServer server) {
        return bonds(server);
    }

    private static Map<UUID, Entry> bonds(MinecraftServer server) {
        return server.overworld().getAttachedOrElse(BONDS, Map.of());
    }

    private static void save(MinecraftServer server, Map<UUID, Entry> bonds) {
        server.overworld().setAttached(BONDS, Map.copyOf(bonds));
    }

    public static Optional<Map.Entry<UUID, Entry>> owned(MinecraftServer server, UUID owner) {
        return bonds(server).entrySet().stream().filter(entry -> entry.getValue().owner().equals(owner)).findFirst();
    }

    public static boolean bonded(ServerPlayer player) {
        return owned(player.level().getServer(), player.getUUID()).isPresent();
    }

    public static List<UUID> bearers(MinecraftServer server, LanternCorps corps) {
        Set<UUID> owners = new LinkedHashSet<>();
        for (Entry entry : bonds(server).values()) {
            if (entry.corps() == corps) owners.add(entry.owner());
        }
        return List.copyOf(owners);
    }

    public static void bind(ServerPlayer player, ItemStack ring) {
        LanternCorps corps = PowerRingItem.getCorps(ring).orElse(null);
        if (corps == null || PowerRingItem.isDormant(ring)) return;
        MinecraftServer server = player.level().getServer();
        Map<UUID, Entry> bonds = new HashMap<>(bonds(server));
        BrightestDayComponents.Bond bond = ring.get(BrightestDayComponents.RING_BOND);
        Entry existing = bond == null ? null : bonds.get(bond.id());
        UUID id = existing != null ? bond.id() : UUID.randomUUID();
        int generation = existing != null ? existing.generation() : 0;
        long recalledAt = existing != null ? existing.recalledAt() : 0L;
        bonds.entrySet().removeIf(entry -> entry.getValue().owner().equals(player.getUUID()) && !entry.getKey().equals(id));
        ring.set(BrightestDayComponents.RING_BOND, new BrightestDayComponents.Bond(id, generation));
        bonds.put(id, new Entry(corps, player.getUUID(), player.getScoreboardName(), generation, System.currentTimeMillis(), recalledAt, Optional.of(ring.copy())));
        save(server, bonds);
    }

    public static void forget(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Map<UUID, Entry> bonds = new HashMap<>(bonds(server));
        if (bonds.entrySet().removeIf(entry -> entry.getValue().owner().equals(player.getUUID()))) save(server, bonds);
    }

    public static void release(MinecraftServer server, ItemStack ring) {
        BrightestDayComponents.Bond bond = ring.remove(BrightestDayComponents.RING_BOND);
        if (bond == null || !bonds(server).containsKey(bond.id())) return;
        Map<UUID, Entry> bonds = new HashMap<>(bonds(server));
        bonds.remove(bond.id());
        save(server, bonds);
    }

    public static void recall(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Map.Entry<UUID, Entry> owned = owned(server, player.getUUID()).orElse(null);
        if (owned == null) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.recall.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        UUID id = owned.getKey();
        Entry entry = owned.getValue();
        int color = entry.corps().color();
        if (carried(player, id, entry.generation()) != null) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.recall.with_you").withColor(color));
            return;
        }
        int cooldown = cooldown(entry, System.currentTimeMillis());
        if (cooldown > 0) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.recall.cooldown", cooldown).withColor(color));
            return;
        }

        int generation = entry.generation() + 1;
        ItemStack ring = entry.snapshot().map(ItemStack::copy).orElseGet(() -> RingOffers.forge(entry.corps(), player));
        ring.set(BrightestDayComponents.RING_BOND, new BrightestDayComponents.Bond(id, generation));
        Map<UUID, Entry> bonds = new HashMap<>(bonds(server));
        bonds.put(id, new Entry(entry.corps(), entry.owner(), player.getScoreboardName(), generation, System.currentTimeMillis(), System.currentTimeMillis(), Optional.of(ring.copy())));
        save(server, bonds);

        if (BrightestDayAttachments.getRing(player).isEmpty()) BrightestDayAttachments.setRing(player, ring);
        else if (!player.addItem(ring)) player.drop(ring, false, Prediction.SERVER_ONLY);
        player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.0), player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
        player.sendSystemMessage(Component.translatable("message.brightestday.recall.returned", entry.corps().displayName()).withStyle(ChatFormatting.ITALIC).withColor(color));
    }

    public static void relinquish(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Map.Entry<UUID, Entry> owned = owned(server, player.getUUID()).orElse(null);
        if (owned == null) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.recall.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        UUID id = owned.getKey();
        ItemStack worn = BrightestDayAttachments.getRing(player);
        if (bondId(worn).filter(id::equals).isPresent()) BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (bondId(inventory.getItem(slot)).filter(id::equals).isPresent()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        Map<UUID, Entry> bonds = new HashMap<>(bonds(server));
        bonds.remove(id);
        save(server, bonds);
        player.setAttached(STATUS, Status.NONE);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("message.brightestday.recall.relinquished", owned.getValue().corps().displayName())
                .withStyle(ChatFormatting.ITALIC).withColor(owned.getValue().corps().color()));
    }

    private static Optional<UUID> bondId(ItemStack stack) {
        return Optional.ofNullable(stack.get(BrightestDayComponents.RING_BOND)).map(BrightestDayComponents.Bond::id);
    }

    private static int cooldown(Entry entry, long now) {
        long ready = entry.recalledAt() + BrightestDayConfig.get().ringRecallCooldownSeconds * 1000L;
        return (int) Math.max(0L, (ready - now + 999L) / 1000L);
    }

    private static ItemStack carried(ServerPlayer player, UUID id, int generation) {
        ItemStack worn = BrightestDayAttachments.getRing(player);
        if (current(worn, id, generation)) return worn;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (current(stack, id, generation)) return stack;
        }
        return null;
    }

    private static boolean current(ItemStack stack, UUID id, int generation) {
        BrightestDayComponents.Bond bond = stack.get(BrightestDayComponents.RING_BOND);
        return bond != null && bond.id().equals(id) && bond.generation() == generation;
    }

    private static boolean inspect(ServerPlayer player, ItemStack stack, Map<UUID, Entry> bonds) {
        if (!(stack.getItem() instanceof PowerRingItem)) return false;
        BrightestDayComponents.Bond bond = stack.get(BrightestDayComponents.RING_BOND);
        if (bond == null) return false;
        Entry entry = bonds.get(bond.id());
        if (entry == null) {
            stack.remove(BrightestDayComponents.RING_BOND);
            return true;
        }
        if (bond.generation() < entry.generation()) {
            stack.setCount(0);
            player.sendOverlayMessage(Component.translatable("message.brightestday.recall.vanished").withColor(entry.corps().color()));
            return true;
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % TRACK_INTERVAL != 0) return;
        long now = System.currentTimeMillis();
        Map<UUID, Entry> original = bonds(server);
        Map<UUID, Entry> bonds = new HashMap<>(original);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack worn = BrightestDayAttachments.getRing(player);
            if (inspect(player, worn, bonds)) BrightestDayAttachments.setRing(player, worn.isEmpty() ? ItemStack.EMPTY : worn);
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) inspect(player, inventory.getItem(slot), bonds);

            worn = BrightestDayAttachments.getRing(player);
            if (!worn.isEmpty() && !worn.has(BrightestDayComponents.RING_BOND) && !PowerRingItem.isDormant(worn) && PowerRingItem.getCorps(worn).isPresent()) {
                bind(player, worn);
                BrightestDayAttachments.setRing(player, worn);
                bonds = new HashMap<>(bonds(server));
            }

            Status status = Status.NONE;
            for (Map.Entry<UUID, Entry> owned : new ArrayList<>(bonds.entrySet())) {
                Entry entry = owned.getValue();
                if (!entry.owner().equals(player.getUUID())) continue;
                if (now - entry.lastSeen() > SEEN_REFRESH_MILLIS) entry = entry.seen(now);
                ItemStack held = carried(player, owned.getKey(), entry.generation());
                if (held != null && (entry.snapshot().isEmpty() || !ItemStack.matches(entry.snapshot().get(), held))) entry = entry.withSnapshot(held);
                if (entry != owned.getValue()) bonds.put(owned.getKey(), entry);
                status = new Status(Optional.of(entry.corps()), held == null, cooldown(entry, now));
            }
            if (!status.equals(player.getAttachedOrElse(STATUS, Status.NONE))) player.setAttached(STATUS, status);
        }

        long expiry = BrightestDayConfig.get().ringBondExpiryDays * DAY_MILLIS;
        bonds.values().removeIf(entry -> server.getPlayerList().getPlayer(entry.owner()) == null && now - entry.lastSeen() > expiry);
        if (!Objects.equals(bonds, original)) save(server, bonds);
    }

    private RingBonds() {}
}
