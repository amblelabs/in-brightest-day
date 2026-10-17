package dev.amble.core.ringpowers.constructs;

import dev.amble.core.BrightestDayComponents;
import dev.amble.core.BrightestDayItems;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.CorpsColors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;


public final class ConstructTools {
    public static final int LIFETIME_TICKS = 1200;
    private static final int DRAIN_PER_SECOND = 2;

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && isConstruct(item.getItem())) {
                level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
                item.discard();
            }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                isConstruct(player.getItemInHand(hand)) && (entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof Allay) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!isConstruct(player.getItemInHand(hand))) return InteractionResult.PASS;
            BlockEntity target = level.getBlockEntity(hit.getBlockPos());
            return target instanceof DecoratedPotBlockEntity || target instanceof ShelfBlockEntity ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    public static boolean isConstruct(ItemStack stack) {
        return stack.has(BrightestDayComponents.CONSTRUCT_TOOL) || BrightestDayItems.isConstructTool(stack.getItem());
    }

    public static ItemStack create(ServerPlayer player, ConstructTool tool) {
        int color = CorpsColors.of(player);
        ItemStack stack = new ItemStack(tool.item());
        stack.set(BrightestDayComponents.CONSTRUCT_TOOL, new ConstructToolData(player.getUUID(), player.level().getGameTime() + LIFETIME_TICKS));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(tool.translationKey()).withColor(color));
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.DYED_COLOR, true));
        return stack;
    }

    public static long latestCreatedAt(ServerPlayer player) {
        long latest = Long.MIN_VALUE;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ConstructToolData data = inventory.getItem(slot).get(BrightestDayComponents.CONSTRUCT_TOOL);
            if (data != null && data.owner().equals(player.getUUID())) latest = Math.max(latest, data.expiresAt() - LIFETIME_TICKS);
        }
        return latest;
    }

    public static void dissolveAll(ServerPlayer player) {
        clear(player.getInventory());
        clear(player.getEnderChestInventory());
        if (isConstruct(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    private static void clear(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (isConstruct(container.getItem(slot))) container.setItem(slot, ItemStack.EMPTY);
        }
    }

    private static void purgeOpenContainer(ServerPlayer player) {
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != player.getInventory() && isConstruct(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
    }

    public static void tick(ServerPlayer player, int serverTick) {
        Inventory inventory = player.getInventory();
        long now = player.level().getGameTime();
        boolean charged = PowerRingItem.hasCharge(player);
        boolean carrying = false;
        boolean dissolved = false;
        purgeOpenContainer(player);

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ConstructToolData data = stack.get(BrightestDayComponents.CONSTRUCT_TOOL);
            if (data == null && !isConstruct(stack)) continue;

            if (data == null || !charged || now >= data.expiresAt() || !data.owner().equals(player.getUUID())) {
                inventory.setItem(slot, ItemStack.EMPTY);
                dissolved = true;
            } else {
                carrying = true;
            }
        }

        if (dissolved) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        if (carrying && serverTick % 20 == 0 && !player.hasInfiniteMaterials()) {
            PowerRingItem.drainWorn(player, DRAIN_PER_SECOND);
        }
    }

    private ConstructTools() {}
}
