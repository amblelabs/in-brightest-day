package dev.amble.datagen.providers;

import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.BrightestDayItems;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import dev.amble.core.ringpowers.constructs.ConstructTool;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TexturedModel;

public class BrightestDayModelProvider extends FabricModelProvider {
    public BrightestDayModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        blockModelGenerators.createNonTemplateModelBlock(BrightestDayBlocks.HARD_LIGHT, Blocks.BARRIER);
        blockModelGenerators.createNonTemplateModelBlock(BrightestDayBlocks.CONSTRUCT_LIGHT, Blocks.BARRIER);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        itemModelGenerators.generateSpyglass(BrightestDayItems.GREEN_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.YELLOW_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.ORANGE_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.RED_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.INDIGO_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.STAR_SAPPHIRE_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.BLUE_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.WHITE_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayItems.BLACK_POWER_RING);
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.GREEN_LANTERN_BLOCK.asItem());
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.YELLOW_LANTERN_BLOCK.asItem());
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.RED_LANTERN_BLOCK.asItem());
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.ORANGE_LANTERN_BLOCK.asItem());
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.BLUE_LANTERN_BLOCK.asItem());
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.INDIGO_LANTERN_BLOCK.asItem());
        itemModelGenerators.generateSpyglass(BrightestDayBlocks.STAR_SAPPHIRE_LANTERN_BLOCK.asItem());

        itemModelGenerators.itemModelOutput.accept(BrightestDayItems.LORE_BOOK, ItemModelUtils.plainModel(BrightestDay.id("item/lore_book")));

        for (ConstructTool tool : ConstructTool.values()) {
            if (tool == ConstructTool.SPEAR) {
                itemModelGenerators.itemModelOutput.accept(tool.item(), ItemModelGenerators.createFlatModelDispatch(
                        constructModel(tool.vanillaModel()), constructModel(tool.vanillaModel() + "_in_hand")
                ), new ClientItem.Properties(true, false, 1.95F));
            } else if (tool == ConstructTool.SPYGLASS) {
                itemModelGenerators.itemModelOutput.accept(tool.item(), ItemModelGenerators.createFlatModelDispatch(
                        constructModel(tool.vanillaModel()), constructModel(tool.vanillaModel() + "_in_hand")
                ));
            } else {
                itemModelGenerators.itemModelOutput.accept(tool.item(), constructModel(tool.vanillaModel()));
            }
        }
    }

    private static ItemModel.Unbaked constructModel(String vanillaModel) {
        return ItemModelUtils.tintedModel(Identifier.withDefaultNamespace("item/" + vanillaModel), new Dye(-1));
    }
}
