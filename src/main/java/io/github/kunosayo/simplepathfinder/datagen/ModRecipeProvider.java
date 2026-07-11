package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import org.jspecify.annotations.NonNull;

public class ModRecipeProvider extends RecipeProvider {

    /**
     * Creates a new ModRecipeProvider.
     *
     * @param provider the registry provider
     * @param output   the recipe output
     */
    protected ModRecipeProvider(HolderLookup.Provider provider, RecipeOutput output) {
        super(provider, output);
    }

    @Override
    protected void buildRecipes() {
        // Unbind locator data
        ShapelessRecipeBuilder.shapeless(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.LOCATOR.get())
                .requires(ModItems.LOCATOR.get())
                .group(SimplePathFinder.MOD_ID)
                .unlockedBy("has_locator", has(ModItems.LOCATOR.get()))
                .save(output, "unbind_locator");
    }

    /**
     * Runner class for registering the recipe provider with the data generator.
     */
    public static class Runner extends RecipeProvider.Runner {

        /**
         * Creates a new Runner for the recipe provider.
         *
         * @param output         the pack output
         * @param lookupProvider the registry lookup provider
         */
        public Runner(net.minecraft.data.PackOutput output, java.util.concurrent.CompletableFuture<HolderLookup.Provider> lookupProvider) {
            super(output, lookupProvider);
        }

        @Override
        protected @NonNull RecipeProvider createRecipeProvider(HolderLookup.@NonNull Provider provider, @NonNull RecipeOutput output) {
            return new ModRecipeProvider(provider, output);
        }

        @Override
        public String getName() {
            return SimplePathFinder.MOD_ID;
        }
    }
}
