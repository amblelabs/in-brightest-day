package dev.amble.core.items;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ConstructMimics {
    private static final Map<Item, Item> MIMICS = new IdentityHashMap<>();

    public static void register(Item construct, Item vanilla) {
        MIMICS.put(construct, vanilla);
    }

    public static @Nullable Item of(Item item) {
        return MIMICS.isEmpty() ? null : MIMICS.get(item);
    }

    private ConstructMimics() {}
}
