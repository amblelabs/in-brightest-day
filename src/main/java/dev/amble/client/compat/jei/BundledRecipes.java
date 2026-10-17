package dev.amble.client.compat.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.amble.BrightestDay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class BundledRecipes {
    private static final String FOLDER = "data/" + BrightestDay.MOD_ID + "/recipe";

    static List<RecipeHolder<CraftingRecipe>> crafting(HolderLookup.Provider registries) {
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        Path folder = FabricLoader.getInstance().getModContainer(BrightestDay.MOD_ID).flatMap(mod -> mod.findPath(FOLDER)).orElse(null);
        if (folder == null || !Files.isDirectory(folder)) return recipes;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String name = folder.relativize(file).toString().replace('\\', '/');
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, BrightestDay.id(name.substring(0, name.length() - ".json".length())));
                try (Reader reader = Files.newBufferedReader(file)) {
                    Recipe.CODEC.parse(ops, JsonParser.parseReader(reader))
                            .resultOrPartial(error -> BrightestDay.LOGGER.warn("Skipping bundled recipe {} for JEI: {}", key.identifier(), error))
                            .ifPresent(recipe -> {
                                if (recipe instanceof CraftingRecipe crafting) recipes.add(new RecipeHolder<>(key, crafting));
                            });
                }
            }
        } catch (IOException e) {
            BrightestDay.LOGGER.warn("Could not read bundled recipes for JEI", e);
        }
        return recipes;
    }

    private BundledRecipes() {}
}
