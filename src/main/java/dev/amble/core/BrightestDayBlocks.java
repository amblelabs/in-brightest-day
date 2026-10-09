package dev.amble.core;

import dev.amble.core.blocks.BlueShrineBlock;
import dev.amble.core.forge.BatteryCoreBlock;
import dev.amble.core.forge.ForgeRecipes;
import dev.amble.core.forge.SpectrumForgeBlock;
import dev.amble.core.forge.BatteryFrameBlock;
import dev.amble.core.progression.Emotion;
import dev.amble.BrightestDay;
import dev.amble.core.blocks.ConstructLightBlock;
import dev.amble.core.blocks.HardLightBlock;
import dev.amble.core.blocks.LanternBlock;
import dev.amble.core.items.LanternBlockItem;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class BrightestDayBlocks {

    private static final Map<LanternCorps, Block> LANTERNS = new EnumMap<>(LanternCorps.class);

    public static final Block GREEN_LANTERN_BLOCK = registerLantern("green_lantern", LanternCorps.GREEN);
    public static final Block YELLOW_LANTERN_BLOCK = registerLantern("yellow_lantern", LanternCorps.YELLOW);
    public static final Block RED_LANTERN_BLOCK = registerLantern("red_lantern", LanternCorps.RED);
    public static final Block ORANGE_LANTERN_BLOCK = registerLantern("orange_lantern", LanternCorps.ORANGE);
    public static final Block BLUE_LANTERN_BLOCK = registerLantern("blue_lantern", LanternCorps.BLUE);
    public static final Block INDIGO_LANTERN_BLOCK = registerLantern("indigo_lantern", LanternCorps.INDIGO);
    public static final Block STAR_SAPPHIRE_LANTERN_BLOCK = registerLantern("sapphire_lantern", LanternCorps.STAR_SAPPHIRE);

    public static final Block HARD_LIGHT = registerBlock("hard_light",
            properties -> new HardLightBlock(properties.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()
                    .lightLevel(_ -> 8).sound(SoundType.AMETHYST).pushReaction(PushReaction.IMMOVEABLE)
                    .isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false)));

    public static final Block CONSTRUCT_LIGHT = registerBlock("construct_light",
            properties -> new ConstructLightBlock(properties.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().noCollision().replaceable()
                    .lightLevel(_ -> 15).pushReaction(PushReaction.POPPED).isValidSpawn((state, level, pos, type) -> false)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)));

    public static final Block BLUE_LANTERN_SHRINE = registerBlockWithItem("blue_lantern_shrine",
            properties -> new BlueShrineBlock(properties.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()
                    .lightLevel(_ -> 13).sound(SoundType.LANTERN).pushReaction(PushReaction.IMMOVEABLE).mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .isValidSpawn((state, level, pos, type) -> false)),
            new Item.Properties());

    public static final Block SPECTRUM_FORGE = registerBlockWithItem("spectrum_forge",
            properties -> new SpectrumForgeBlock(Emotion.FEAR, true, ForgeRecipes::yellow, properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK).lightLevel(state -> state.getValue(SpectrumForgeBlock.LAVA) * 3)),
            new Item.Properties());

    public static final Block ZAMARONIAN_CRYSTAL = registerBlockWithItem("zamaronian_crystal",
            properties -> new SpectrumForgeBlock(Emotion.LOVE, false, ForgeRecipes::sapphire, properties.strength(30.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(_ -> 10).mapColor(MapColor.COLOR_PINK)),
            new Item.Properties().fireResistant());

    public static final Block ZAMARON_CRYSTAL_CLUSTER = registerBlock("zamaron_crystal_cluster",
            properties -> new AmethystClusterBlock(7.0F, 3.0F, properties.strength(1.5F).requiresCorrectToolForDrops().noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER).lightLevel(_ -> 6).pushReaction(PushReaction.POPPED).mapColor(MapColor.COLOR_PINK)));

    public static final Block YELLOW_BATTERY_CORE = registerBlockWithItem("yellow_battery_core",
            properties -> new BatteryCoreBlock(LanternCorps.YELLOW, () -> Blocks.GOLD_BLOCK, properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(_ -> 15)),
            new Item.Properties());

    public static final Block BATTERY_FRAME = registerBlock("battery_frame",
            properties -> new BatteryFrameBlock(properties.strength(5.0F, 1200.0F).noOcclusion().sound(SoundType.METAL)
                    .isValidSpawn((state, level, pos, type) -> false).isViewBlocking((state, level, pos, box) -> false)));

    public static final Block SAPPHIRE_BATTERY_CORE = registerBlockWithItem("sapphire_battery_core",
            properties -> new BatteryCoreBlock(LanternCorps.STAR_SAPPHIRE, () -> Blocks.AMETHYST_BLOCK, properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(_ -> 15)),
            new Item.Properties());

    public static final Block GREEN_BATTERY_CORE = registerBlockWithItem("green_battery_core",
            properties -> new BatteryCoreBlock(LanternCorps.GREEN, () -> Blocks.COPPER_BLOCK.weathering().oxidized(), properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(_ -> 15)),
            new Item.Properties());

    public static final Block RED_BATTERY_CORE = registerBlockWithItem("red_battery_core",
            properties -> new BatteryCoreBlock(LanternCorps.RED, () -> Blocks.REDSTONE_BLOCK, properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(_ -> 15)),
            new Item.Properties());

    public static final Block BLUE_BATTERY_CORE = registerBlockWithItem("blue_battery_core",
            properties -> new BatteryCoreBlock(LanternCorps.BLUE, () -> Blocks.LAPIS_BLOCK, properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).lightLevel(_ -> 15)),
            new Item.Properties());

    public static final Block INDIGO_BATTERY_CORE = registerBlockWithItem("indigo_battery_core",
            properties -> new BatteryCoreBlock(LanternCorps.INDIGO, () -> Blocks.OBSIDIAN, properties.strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).lightLevel(_ -> 15)),
            new Item.Properties());

    public static Optional<Block> lantern(LanternCorps corps) {
        return Optional.ofNullable(LANTERNS.get(corps));
    }

    private static Block registerLantern(String name, LanternCorps corps) {
        Block lantern = registerBlockWithItem(name,
                properties -> new LanternBlock(corps, properties.lightLevel(_ -> 12).mapColor(MapColor.METAL).forceSolidOn().strength(3.5F)
                        .sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.POPPED)),
                new Item.Properties().component(DataComponents.TOOLTIP_STYLE, BrightestDay.id("ring")).stacksTo(1));
        LANTERNS.put(corps, lantern);
        return lantern;
    }

    private static <T extends Block> T registerBlockWithItem(String name, Function<BlockBehaviour.Properties, T> function, Item.Properties itemProperties) {
        T block = registerBlock(name, function);

        registerBlockItem(name, block, itemProperties);

        return block;
    }

    private static <T extends Block> T registerBlock(String name, Function<BlockBehaviour.Properties, T> function) {
        Identifier id = BrightestDay.id(name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);

        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(blockKey);
        T block = function.apply(properties);

        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        return block;
    }

    private static <T extends Block> void registerBlockItem(String name, T block, Item.Properties properties) {
        Identifier id = BrightestDay.id(name);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        Item.Properties props = properties.setId(itemKey).useBlockDescriptionPrefix();

        Registry.register(BuiltInRegistries.ITEM, itemKey, block instanceof LanternBlock ? new LanternBlockItem(block, props) : new BlockItem(block, props));
    }

    public static void init() {}
}
