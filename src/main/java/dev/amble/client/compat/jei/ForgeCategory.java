package dev.amble.client.compat.jei;

import dev.amble.core.forge.ForgeHammer;
import dev.amble.core.progression.Emotion;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class ForgeCategory extends AbstractRecipeCategory<ForgeDisplay> {
    private static final int WIDTH = 160;
    private static final int SLOT = 18;
    private static final int INPUT_COLUMNS = 4;
    private static final int OUTPUT_X = 108;
    private static final int OUTPUT_SLOT = 26;
    private static final int TOP = 5;
    private static final int TEXT_Y = 32;
    private static final int LINE_HEIGHT = 10;
    private static final int LINES = 4;
    private static final int TEXT_COLOR = 0xFF404040;

    public ForgeCategory(IRecipeType<ForgeDisplay> type, Block station, IGuiHelper guiHelper) {
        super(type, station.getName(), guiHelper.createDrawableItemLike(station), WIDTH, TEXT_Y + LINES * LINE_HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ForgeDisplay display, IFocusGroup focuses) {
        List<ItemStack> inputs = display.recipe().inputs();
        for (int i = 0; i < inputs.size(); i++) {
            builder.addInputSlot(1 + (i % INPUT_COLUMNS) * SLOT, TOP + (i / INPUT_COLUMNS) * SLOT)
                    .setStandardSlotBackground()
                    .add(inputs.get(i));
        }
        for (int i = 0; i < display.outputs().size(); i++) {
            builder.addOutputSlot(OUTPUT_X + i * OUTPUT_SLOT, TOP)
                    .setOutputSlotBackground()
                    .add(display.outputs().get(i));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ForgeDisplay display, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(INPUT_COLUMNS * SLOT + 6, TOP);

        List<FormattedText> lines = new ArrayList<>();
        if (display.fuse()) {
            lines.add(Component.translatable("jei.brightestday.forge.requires_both", Emotion.GATE, emotion(Emotion.HOPE), emotion(Emotion.LOVE)));
            lines.add(Component.translatable("jei.brightestday.forge.singleplayer"));
        } else {
            lines.add(Component.translatable("jei.brightestday.forge.requires", Emotion.GATE, emotion(display.emotion())));
        }
        if (display.lava() && display.recipe().lava() > 0) lines.add(Component.translatable("jei.brightestday.forge.lava", display.recipe().lava()));
        if (display.emotion() == Emotion.FEAR) lines.add(Component.translatable("jei.brightestday.forge.darkness"));
        lines.add(Component.translatable("jei.brightestday.forge.hammer", display.recipe().strikes()));

        int y = TEXT_Y;
        for (FormattedText line : lines.subList(0, Math.min(lines.size(), LINES))) {
            builder.addText(line, WIDTH, LINE_HEIGHT).setPosition(0, y).setColor(TEXT_COLOR).setShadow(false);
            y += LINE_HEIGHT;
        }
    }

    private static Component emotion(Emotion emotion) {
        return Component.translatable("emotion.brightestday." + emotion.getSerializedName()).withColor(emotion.corps().color());
    }
}
