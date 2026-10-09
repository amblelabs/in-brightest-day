package dev.amble.mixin;

import dev.amble.core.items.ConstructMimics;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
public abstract class ConstructItemStackMixin {
    @Shadow
    public abstract Holder<Item> typeHolder();

    public boolean is(Object rawType) {
        Item item = this.typeHolder().value();
        return item == rawType || rawType != null && ConstructMimics.of(item) == rawType;
    }

    public boolean is(HolderSet<Item> set) {
        Holder<Item> holder = this.typeHolder();
        if (set.contains(holder)) return true;
        Item mimicked = ConstructMimics.of(holder.value());
        return mimicked != null && set.contains(mimicked.builtInRegistryHolder());
    }
}
