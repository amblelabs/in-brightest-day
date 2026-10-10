package dev.amble.core.items;

import dev.amble.core.heart.RedHeart;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.core.progression.CorpsCaps;
import dev.amble.core.ringpowers.CorpsSynergy;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.team.LanternTeams;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

public class PowerRingItem extends Item {
    public PowerRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive()) return super.use(level, player, hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack ring = player.getItemInHand(hand);
        ItemStack previous = BrightestDayAttachments.getRing(player);
        if (!RedHeart.mayRemove(player, previous) || !CorpsCaps.check(player, ring)) return InteractionResult.FAIL;
        BrightestDayAttachments.setRing(player, ring);
        player.setItemInHand(hand, previous.copy());
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        player.sendOverlayMessage(Component.translatable("message.brightestday.ring_equipped", getCorps(ring).map(LanternCorps::displayName).orElse(ring.getHoverName()))
                .withColor(getCorps(ring).orElse(LanternCorps.GREEN).color()));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(itemStack, level, owner, slot);
        if (owner instanceof Player player && CorpsSynergy.empoweredByHope(player)) return;
        PowerRingItem.tickCharge(itemStack, level);
    }

    public static boolean tickCharge(ItemStack ring, ServerLevel level) {
        if (level.getServer().getTickCount() % (20 * 60) * 20 != 0) return false;

        int before = getRingPower(ring);
        drainRing(ring, 1);
        return getRingPower(ring) != before;
    }

    public static Optional<LanternCorps> getCorps(ItemStack ring) {
        if (!(ring.getItem() instanceof PowerRingItem)) return Optional.empty();
        return Optional.ofNullable(ring.get(BrightestDayComponents.LANTERN_CORPS));
    }

    public static ItemStack getWornRing(Player player) {
        ItemStack slotted = BrightestDayAttachments.getRing(player);
        if (slotted.getItem() instanceof PowerRingItem) return slotted;
        if (player.getMainHandItem().getItem() instanceof PowerRingItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof PowerRingItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static boolean usesPower(ItemStack ring) {
        return getCorps(ring).map(LanternCorps::hasRingPower).orElse(true);
    }

    public static Optional<LanternCorps> getWornCorps(Player player) {
        return getCorps(getWornRing(player));
    }

    public static float getChargeFraction(ItemStack ring) {
        return (float) getRingPower(ring) / BrightestDayComponents.MAX_POWER;
    }

    public static void refund(Player player, int amount) {
        amount = CorpsSynergy.scaleCost(player, amount);
        ItemStack ring = getWornRing(player);
        if (ring.isEmpty() || player.hasInfiniteMaterials()) return;

        chargeRing(ring, amount);
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
    }

    public static boolean hasCharge(Player player) {
        return player.hasInfiniteMaterials() || player.isCreative() || getRingPower(getWornRing(player)) > 0;
    }

    public static boolean drainWorn(Player player, int amount) {
        amount = CorpsSynergy.scaleCost(player, amount);
        ItemStack ring = getWornRing(player);
        if (ring.isEmpty()) return false;

        boolean paid = getRingPower(ring) >= amount;
        drainRing(ring, amount);
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
        return paid;
    }

    public static boolean consumeCharge(Player player, int amount) {
        amount = CorpsSynergy.scaleCost(player, amount);
        ItemStack ring = getWornRing(player);
        if (ring.isEmpty() || getRingPower(ring) < amount) return false;

        drainRing(ring, amount);
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
        return true;
    }

    public static int getRingPower(ItemStack ring) {
        if (!(ring.getItem() instanceof PowerRingItem)) return 0;
        if (!usesPower(ring)) return BrightestDayComponents.MAX_POWER;
        return ring.getOrDefault(BrightestDayComponents.POWER_TYPE, 0);
    }

    public static void drainRing(ItemStack ring, int amount) {
        if (!(ring.getItem() instanceof PowerRingItem) || !usesPower(ring)) return;
        int current = ring.getOrDefault(BrightestDayComponents.POWER_TYPE, 0);
        if (current <= 0) return;

        ring.set(BrightestDayComponents.POWER_TYPE, Math.max(current - amount, 0));
    }

    public static void chargeRing(ItemStack ring, int amount) {
        if (!(ring.getItem() instanceof PowerRingItem) || !usesPower(ring)) return;
        int current = ring.getOrDefault(BrightestDayComponents.POWER_TYPE, BrightestDayComponents.MAX_POWER);
        if (current >= BrightestDayComponents.MAX_POWER) return;

        ring.set(BrightestDayComponents.POWER_TYPE, Math.min(current + amount, BrightestDayComponents.MAX_POWER));
    }

    public static void setMaxPower(ItemStack ring) {
        if (!(ring.getItem() instanceof PowerRingItem) || !usesPower(ring)) return;
        ring.set(BrightestDayComponents.POWER_TYPE, BrightestDayComponents.MAX_POWER);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, context, display, builder, tooltipFlag);
        if (!usesPower(itemStack)) return;

        double percentage = ((double) PowerRingItem.getRingPower(itemStack) / BrightestDayComponents.MAX_POWER) * 100;

        int color = PowerRingItem.getCorps(itemStack).orElse(LanternCorps.GREEN).color();
        Component component = Component.literal(String.format("%.0f%%", percentage)).withStyle(ChatFormatting.BOLD).withColor(color);

        builder.accept(component);
        BrightestDayComponents.ChargeCap cap = itemStack.get(BrightestDayComponents.CHARGE_CAP);
        if (cap != null) {
            int capPercent = Math.round(cap.capacity() * 100.0F / BrightestDayComponents.MAX_POWER);
            builder.accept(Component.translatable("tooltip.brightestday.ring.cap", capPercent, cap.rank()).withStyle(ChatFormatting.GRAY));
            if (cap.battery() < 100) {
                Component corps = PowerRingItem.getCorps(itemStack).map(LanternCorps::displayName).orElse(Component.empty());
                builder.accept(Component.translatable("tooltip.brightestday.ring.cap_battery", corps, cap.battery()).withStyle(ChatFormatting.GRAY));
            }
        }
        String successor = itemStack.get(BrightestDayComponents.SUCCESSOR);
        if (successor != null) builder.accept(Component.translatable("tooltip.brightestday.ring.successor", successor).withStyle(ChatFormatting.GRAY));
        BrightestDayComponents.Sworn sworn = itemStack.get(BrightestDayComponents.SWORN_TO);
        if (sworn != null) builder.accept(Component.translatable("tooltip.brightestday.ring.sworn", sworn.name()).withStyle(ChatFormatting.GRAY));
        if (isDormant(itemStack)) {
            Component lantern = PowerRingItem.getCorps(itemStack).map(LanternCorps::displayName).orElse(Component.empty());
            builder.accept(Component.translatable("tooltip.brightestday.ring.dormant", lantern).withStyle(ChatFormatting.ITALIC).withColor(color));
        }
    }

    public static void swear(Player player, ItemStack ring) {
        BrightestDayComponents.Sworn previous = ring.get(BrightestDayComponents.SWORN_TO);
        if (previous != null && previous.owner().equals(player.getUUID())) {
            if (player instanceof ServerPlayer server && !ring.has(BrightestDayComponents.RING_BOND)) RingBonds.bind(server, ring);
            return;
        }
        ring.set(BrightestDayComponents.SWORN_TO, new BrightestDayComponents.Sworn(player.getUUID(), player.getScoreboardName()));
        if (player instanceof ServerPlayer server) {
            LanternCorps former = RingBonds.owned(server.level().getServer(), server.getUUID()).map(entry -> entry.getValue().corps()).orElse(null);
            RingBonds.bind(server, ring);
            getCorps(ring).ifPresent(corps -> align(server, former, corps));
        }
        int color = getCorps(ring).orElse(LanternCorps.GREEN).color();
        player.sendSystemMessage(Component.translatable(previous == null ? "message.brightestday.ring.sworn" : "message.brightestday.ring.reclaimed", previous == null ? "" : previous.name())
                .withStyle(ChatFormatting.ITALIC).withColor(color));
    }

    private static void align(ServerPlayer player, @Nullable LanternCorps former, LanternCorps corps) {
        if (corps == LanternCorps.INDIGO) LanternTeams.joinTribe(player);
        else LanternTeams.leaveTribe(player);
        if (former == null || former == corps) return;
        player.sendSystemMessage(Component.translatable("message.brightestday.ring.forsaken", former.displayName(), corps.displayName())
                .withStyle(ChatFormatting.ITALIC).withColor(corps.color()));
    }

    public static boolean isDormant(ItemStack ring) {
        return ring.getOrDefault(BrightestDayComponents.DORMANT, false);
    }

    public static void awaken(Player player, ItemStack ring) {
        if (!isDormant(ring) || !CorpsCaps.check(player, ring)) return;
        ring.remove(BrightestDayComponents.DORMANT);
        int color = getCorps(ring).orElse(LanternCorps.GREEN).color();
        player.sendSystemMessage(Component.translatable("message.brightestday.ring.awakened").withStyle(ChatFormatting.BOLD).withColor(color));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.4F);
    }
}
