package dev.amble.core;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
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

    public static final Block BATTERY_LIGHT = registerBlock("battery_light",
            properties -> new TransparentBlock(properties.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()
                    .lightLevel(_ -> 15).sound(SoundType.AMETHYST).pushReaction(PushReaction.IMMOVEABLE)
                    .isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false)));

    public static Optional<Block> lantern(LanternCorps corps) {
        return Optional.ofNullable(LANTERNS.get(corps));
    }

    private static Block registerLantern(String name, LanternCorps corps) {
        Block lantern = registerBlockWithItem(name,
                properties -> new LanternBlock(corps, properties.lightLevel(_ -> 12).mapColor(MapColor.METAL).forceSolidOn().strength(3.5F)
                        .sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.POPPED)),
                new Item.Properties().component(DataComponents.TOOLTIP_STYLE, BrightestDay.id("ring")));
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
