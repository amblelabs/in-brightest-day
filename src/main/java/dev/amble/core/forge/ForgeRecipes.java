package dev.amble.core.forge;

import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.BrightestDayItems;
import dev.amble.core.progression.Emotion;
import dev.amble.core.progression.IndigoOne;
import dev.amble.core.progression.SpectrumMeters;
import dev.amble.core.progression.EmotionGates;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class ForgeRecipes {
    public static final String FUSE = "fuse";

    public static List<ForgeRecipe> yellow() {
        return List.of(
                new ForgeRecipe("ring", List.of(new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.GOLD_INGOT, 4), new ItemStack(BrightestDayItems.PARALLAX_SHARD)), 1,
                        player -> List.of(ring(LanternCorps.YELLOW))),
                new ForgeRecipe("lantern", List.of(new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.GOLD_INGOT, 8)), 2,
                        player -> List.of(lantern(LanternCorps.YELLOW))),
                new ForgeRecipe("core", List.of(new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.GOLD_BLOCK, 4), new ItemStack(BrightestDayItems.PARALLAX_SHARD)), 2,
                        player -> List.of(new ItemStack(BrightestDayBlocks.YELLOW_BATTERY_CORE))),
                new ForgeRecipe(FUSE, List.of(new ItemStack(BrightestDayItems.BLUE_POWER_RING), new ItemStack(BrightestDayItems.STAR_SAPPHIRE_POWER_RING)), 4,
                        player -> List.of(ring(LanternCorps.INDIGO), lantern(LanternCorps.INDIGO))));
    }

    public static List<ForgeRecipe> sapphire() {
        return List.of(
                new ForgeRecipe("ring", List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT, 4), new ItemStack(Items.AMETHYST_SHARD, 8), new ItemStack(BrightestDayItems.ZAMARON_CRYSTAL)), 0,
                        player -> List.of(ring(LanternCorps.STAR_SAPPHIRE))),
                new ForgeRecipe("lantern", List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT, 8), new ItemStack(Items.AMETHYST_BLOCK, 4)), 0,
                        player -> List.of(lantern(LanternCorps.STAR_SAPPHIRE))),
                new ForgeRecipe("core", List.of(new ItemStack(Items.DIAMOND_BLOCK), new ItemStack(Items.AMETHYST_BLOCK, 4), new ItemStack(Items.GOLD_BLOCK, 2), new ItemStack(BrightestDayItems.ZAMARON_CRYSTAL)), 0,
                        player -> List.of(new ItemStack(BrightestDayBlocks.SAPPHIRE_BATTERY_CORE))));
    }

    public static boolean enabled(ForgeRecipe recipe, Level level) {
        if (!recipe.key().equals(FUSE)) return true;
        return level instanceof ServerLevel server && !IndigoOne.multiplayer(server.getServer());
    }

    public static boolean worthy(ForgeRecipe recipe, Emotion emotion, Player player) {
        if (player.hasInfiniteMaterials()) return true;
        if (recipe.key().equals(FUSE)) return SpectrumMeters.passes(player, Emotion.HOPE) && SpectrumMeters.passes(player, Emotion.LOVE) && EmotionGates.allows(player, LanternCorps.INDIGO);
        return SpectrumMeters.passes(player, emotion) && EmotionGates.allows(player, emotion.corps());
    }

    private static ItemStack ring(LanternCorps corps) {
        ItemStack ring = new ItemStack(BrightestDayItems.ring(corps));
        ring.set(BrightestDayComponents.POWER_TYPE, 0);
        ring.set(BrightestDayComponents.DORMANT, true);
        return ring;
    }

    private static ItemStack lantern(LanternCorps corps) {
        return BrightestDayBlocks.lantern(corps).map(Block::asItem).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private ForgeRecipes() {}
}
