package dev.amble.core;

import dev.amble.core.sync.RingSync;
import com.mojang.serialization.Codec;
import dev.amble.BrightestDay;
import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.comms.Comms;
import dev.amble.core.progression.IndigoOne;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.visuals.InsigniaAnchor;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.CorpsMimicry;
import dev.amble.core.ringpowers.CorpsSynergy;
import dev.amble.core.ringpowers.EyePaint;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import dev.amble.core.ringpowers.RingPowerInstance;
import dev.amble.core.ringpowers.RingPowerRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BrightestDayAttachments {
    public static final AttachmentType<List<RingPowerInstance<?>>> POWERS =
            AttachmentRegistry.<List<RingPowerInstance<?>>>builder()
                    .initializer(List::of)
                    .persistent(RingPowerInstance.CODEC.listOf())
                    .copyOnDeath()
                    .syncWith(RingPowerInstance.LIST_STREAM_CODEC, AttachmentSyncPredicate.all())
                    .buildAndRegister(BrightestDay.id("powers"));

    public static final AttachmentType<ItemStack> RING =
            AttachmentRegistry.<ItemStack>builder()
                    .initializer(() -> ItemStack.EMPTY)
                    .persistent(ItemStack.OPTIONAL_CODEC)
                    .copyOnDeath()
                    .syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all())
                    .buildAndRegister(BrightestDay.id("ring"));

    public static final AttachmentType<ColorTweak> COLOR_TWEAK =
            AttachmentRegistry.<ColorTweak>builder()
                    .initializer(() -> ColorTweak.NONE)
                    .persistent(ColorTweak.CODEC)
                    .copyOnDeath()
                    .syncWith(ColorTweak.STREAM_CODEC, AttachmentSyncPredicate.all())
                    .buildAndRegister(BrightestDay.id("color_tweak"));

    public static final AttachmentType<EyePaint> EYES =
            AttachmentRegistry.<EyePaint>builder()
                    .initializer(() -> EyePaint.EMPTY)
                    .persistent(EyePaint.CODEC)
                    .copyOnDeath()
                    .syncWith(EyePaint.STREAM_CODEC, AttachmentSyncPredicate.all())
                    .buildAndRegister(BrightestDay.id("eyes"));

    public static final AttachmentType<InsigniaAnchor> INSIGNIA_ANCHOR =
            AttachmentRegistry.<InsigniaAnchor>builder()
                    .initializer(() -> InsigniaAnchor.DEFAULT)
                    .persistent(InsigniaAnchor.CODEC)
                    .copyOnDeath()
                    .syncWith(InsigniaAnchor.STREAM_CODEC, AttachmentSyncPredicate.all())
                    .buildAndRegister(BrightestDay.id("insignia_anchor"));

    public static InsigniaAnchor getInsigniaAnchor(Player player) {
        return player.getAttachedOrElse(INSIGNIA_ANCHOR, InsigniaAnchor.DEFAULT);
    }

    public static void setInsigniaAnchor(Player player, InsigniaAnchor anchor) {
        player.setAttached(INSIGNIA_ANCHOR, anchor.sanitized());
    }

    public static EyePaint getEyes(Player player) {
        return player.getAttachedOrElse(BrightestDayAttachments.EYES, EyePaint.EMPTY);
    }

    public static void setEyes(Player player, EyePaint eyes) {
        player.setAttached(BrightestDayAttachments.EYES, eyes.sanitized());
    }

    public static ColorTweak getColorTweak(Player player) {
        return player.getAttachedOrElse(BrightestDayAttachments.COLOR_TWEAK, ColorTweak.NONE);
    }

    public static void setColorTweak(Player player, ColorTweak tweak) {
        player.setAttached(BrightestDayAttachments.COLOR_TWEAK, tweak.clamped());
    }

    public static ItemStack getRing(Player player) {
        return player.getAttachedOrElse(BrightestDayAttachments.RING, ItemStack.EMPTY);
    }

    public static void setRing(Player player, ItemStack ring) {
        player.setAttached(BrightestDayAttachments.RING, ring.isEmpty() ? ItemStack.EMPTY : ring.copy());
    }

    public static List<RingPowerInstance<?>> get(Player player) {
        return player.getAttachedOrElse(BrightestDayAttachments.POWERS, List.of());
    }

    @SuppressWarnings("unchecked")
    public static <D> Optional<RingPowerInstance<D>> get(Player player, RingPower<D> power) {
        for (RingPowerInstance<?> instance : get(player)) {
            if (instance.is(power)) return Optional.of((RingPowerInstance<D>) instance);
        }
        return Optional.empty();
    }

    public static List<RingPowerInstance<?>> constructs(Player player) {
        return get(player).stream()
                .filter(instance -> instance.power().category() == RingPowerCategory.CONSTRUCT)
                .toList();
    }

    public static boolean has(Player player, RingPower<?> power) {
        return get(player, power).isPresent();
    }

    public static <D> void setData(Player player, RingPower<D> power, D data) {
        List<RingPowerInstance<?>> current = get(player);
        List<RingPowerInstance<?>> updated = new ArrayList<>(current.size());
        boolean found = false;
        for (RingPowerInstance<?> instance : current) {
            if (instance.is(power)) {
                updated.add(new RingPowerInstance<>(power, data));
                found = true;
            } else {
                updated.add(instance);
            }
        }
        if (found) player.setAttached(BrightestDayAttachments.POWERS, List.copyOf(updated));
    }

    public static void sync(ServerPlayer player, @Nullable LanternCorps corps) {
        List<RingPowerInstance<?>> current = get(player);
        List<RingPower<?>> available = corps == null
                ? List.of()
                : RingPowerRegistry.forCorps(corps, CorpsSynergy.borrowsWill(player) ? LanternCorps.GREEN : null, CorpsMimicry.mimicked(player, corps));
        if (corps != null && CentralPowerBattery.required(corps) && !CentralPowerBattery.active(player.level().getServer(), corps)) {
            available = List.of();
            if (player.tickCount % 200 == 0) player.sendOverlayMessage(Component.translatable("message.brightestday.battery.dormant").withColor(corps.color()));
        }
        if (corps != null) {
            Set<RingPower<?>> locked = RingRanks.locked(player, corps);
            if (!locked.isEmpty()) available = available.stream().filter(power -> !locked.contains(power)).toList();
            if (!IndigoOne.isIndigoOne(player)) available = available.stream().filter(power -> power != RingPowerRegistry.GATHER).toList();
            if (!Comms.available()) available = available.stream().filter(power -> power != RingPowerRegistry.COMMS).toList();
            if (PowerRingItem.isDormant(PowerRingItem.getWornRing(player))) available = available.stream().filter(power -> power == RingPowerRegistry.ARMED).toList();
            if (RingSync.locked(player)) available = available.stream().filter(power -> power == RingPowerRegistry.ARMED || power == RingPowerRegistry.COMMS || power == RingPowerRegistry.RING_COMPASS).toList();
        }

        if (current.size() == available.size()) {
            boolean unchanged = true;
            for (int i = 0; i < current.size() && unchanged; i++) {
                unchanged = current.get(i).is(available.get(i));
            }
            if (unchanged) return;
        }

        List<RingPowerInstance<?>> updated = new ArrayList<>(available.size());
        List<RingPowerInstance<?>> granted = new ArrayList<>();
        for (RingPower<?> power : available) {
            RingPowerInstance<?> instance = get(player, power).orElse(null);
            if (instance == null) {
                instance = power.createInstance();
                granted.add(instance);
            }
            updated.add(instance);
        }

        player.setAttached(BrightestDayAttachments.POWERS, List.copyOf(updated));

        for (RingPowerInstance<?> instance : current) {
            if (!updated.contains(instance)) revoked(player, instance);
        }
        for (RingPowerInstance<?> instance : granted) {
            granted(player, instance);
        }
    }

    private static <D> void granted(ServerPlayer player, RingPowerInstance<D> instance) {
        instance.power().onGranted(player, instance.data());
    }

    private static <D> void revoked(ServerPlayer player, RingPowerInstance<D> instance) {
        instance.power().onRevoked(player, instance.data());
    }

    public static void init() {}
}
