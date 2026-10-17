package dev.amble.client.compat.jei;

import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.BrightestDayItems;
import dev.amble.core.forge.ForgeRecipes;
import dev.amble.core.progression.Emotion;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.constructs.ConstructTool;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BrightestDayJeiPlugin implements IModPlugin {
    public static final IRecipeType<ForgeDisplay> SPECTRUM_FORGE = IRecipeType.create(BrightestDay.id("spectrum_forge"), ForgeDisplay.class);
    public static final IRecipeType<ForgeDisplay> ZAMARONIAN_CRYSTAL = IRecipeType.create(BrightestDay.id("zamaronian_crystal"), ForgeDisplay.class);

    @Override
    public Identifier getPluginUid() {
        return BrightestDay.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (Item ring : BrightestDayItems.rings().values()) {
            registration.registerSubtypeInterpreter(ring, (stack, context) -> stack.getOrDefault(BrightestDayComponents.DORMANT, false));
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new ForgeCategory(SPECTRUM_FORGE, BrightestDayBlocks.SPECTRUM_FORGE, guiHelper),
                new ForgeCategory(ZAMARONIAN_CRYSTAL, BrightestDayBlocks.ZAMARONIAN_CRYSTAL, guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SPECTRUM_FORGE, ForgeRecipes.yellow().stream().map(recipe -> ForgeDisplay.of(recipe, Emotion.FEAR, true)).toList());
        registration.addRecipes(ZAMARONIAN_CRYSTAL, ForgeRecipes.sapphire().stream().map(recipe -> ForgeDisplay.of(recipe, Emotion.LOVE, false)).toList());

        for (LanternCorps corps : LanternCorps.values()) {
            Item ring = BrightestDayItems.ring(corps);
            if (ring == null) continue;
            String path = corps == LanternCorps.WHITE || corps == LanternCorps.BLACK ? "jei.brightestday.info.ring.unobtainable" : "gui.brightestday.spectrum.path." + corps.getSerializedName();
            registration.addIngredientInfo(ring, Component.translatable(path), Component.translatable("jei.brightestday.info.ring"));
            BrightestDayBlocks.lantern(corps).ifPresent(lantern -> registration.addIngredientInfo(lantern, Component.translatable("jei.brightestday.info.lantern")));
        }
        info(registration, BrightestDayBlocks.SPECTRUM_FORGE);
        info(registration, BrightestDayBlocks.ZAMARONIAN_CRYSTAL);
        info(registration, BrightestDayBlocks.YELLOW_BATTERY_CORE);
        info(registration, BrightestDayBlocks.SAPPHIRE_BATTERY_CORE);
        info(registration, BrightestDayBlocks.GREEN_BATTERY_CORE);
        info(registration, BrightestDayBlocks.RED_BATTERY_CORE);
        info(registration, BrightestDayBlocks.BLUE_BATTERY_CORE);
        info(registration, BrightestDayBlocks.INDIGO_BATTERY_CORE);
        registration.addIngredientInfo(BrightestDayItems.PARALLAX_SHARD, Component.translatable("jei.brightestday.info.parallax_shard"));
        registration.addIngredientInfo(BrightestDayItems.ZAMARON_CRYSTAL, Component.translatable("jei.brightestday.info.zamaron_crystal"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(SPECTRUM_FORGE, BrightestDayBlocks.SPECTRUM_FORGE);
        registration.addCraftingStation(ZAMARONIAN_CRYSTAL, BrightestDayBlocks.ZAMARONIAN_CRYSTAL);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> hidden = new ArrayList<>();
        hidden.add(new ItemStack(BrightestDayBlocks.BLUE_LANTERN_SHRINE));
        for (ConstructTool tool : ConstructTool.values()) hidden.add(new ItemStack(BrightestDayItems.constructTool(tool)));
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        addMissingCrafting(runtime);
    }

    private static void addMissingCrafting(IJeiRuntime runtime) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        Set<ResourceKey<Recipe<?>>> known = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).includeHidden().get()
                .map(RecipeHolder::id).collect(Collectors.toSet());
        List<RecipeHolder<CraftingRecipe>> missing = BundledRecipes.crafting(connection.registryAccess()).stream()
                .filter(recipe -> !known.contains(recipe.id())).toList();
        if (!missing.isEmpty()) runtime.getRecipeManager().addRecipes(RecipeTypes.CRAFTING, missing);
    }

    private static void info(IRecipeRegistration registration, Block block) {
        registration.addIngredientInfo(block, Component.translatable("jei.brightestday.info." + BuiltInRegistries.BLOCK.getKey(block).getPath()));
    }
}
