package dev.amble.core.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record ForgeRecipe(String key, List<ItemStack> inputs, int lava, Function<Player, List<ItemStack>> outputs) {

    public int strikes() {
        return switch (this.key) {
            case "lantern" -> 3;
            case ForgeRecipes.FUSE -> 7;
            default -> 5;
        };
    }

    public boolean affordable(Player player) {
        if (player.hasInfiniteMaterials()) return true;
        for (ItemStack input : this.inputs) {
            if (player.getInventory().countItem(input.getItem()) < input.getCount()) return false;
        }
        return true;
    }

    public List<ItemStack> consume(Player player) {
        List<ItemStack> taken = new ArrayList<>();
        if (player.hasInfiniteMaterials()) return taken;
        for (ItemStack input : this.inputs) {
            Item item = input.getItem();
            int remaining = input.getCount();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(item)) continue;
                ItemStack part = stack.split(Math.min(remaining, stack.getCount()));
                remaining -= part.getCount();
                taken.add(part);
            }
        }
        return taken;
    }

    public Component describe() {
        MutableComponent text = Component.translatable("forge.brightestday.recipe." + this.key).append(": ");
        for (int i = 0; i < this.inputs.size(); i++) {
            ItemStack input = this.inputs.get(i);
            if (i > 0) text.append(", ");
            text.append(input.getCount() + "× ").append(input.getHoverName());
        }
        if (this.lava > 0) text.append(", ").append(Component.translatable("forge.brightestday.lava", this.lava));
        return text;
    }
}
