package dev.amble.core.ringpowers;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class OrangeHoard {
    private static final Map<Item, Integer> WORTH = Map.ofEntries(
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

    public static int value(Player player) {
        int value = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Integer worth = WORTH.get(stack.getItem());
            if (worth != null) value += worth * stack.getCount();
        }
        return value;
    }

    public static float slowdown(Player player) {
        if (PowerRingItem.getWornCorps(player).orElse(null) != LanternCorps.ORANGE) return 0.0F;
        BrightestDayConfig config = BrightestDayConfig.get();
        if (config.orangeBurdenHoard <= 0) return 0.0F;
        return Mth.clamp((float) value(player) / config.orangeBurdenHoard, 0.0F, 1.0F) * config.orangeMaxSlowdown;
    }

    private OrangeHoard() {}
}
