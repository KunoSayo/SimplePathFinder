package io.github.kunosayo.simplepathfinder.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 战利品表数据生成器
 */
public class GenLootTable extends LootTableProvider {
    public GenLootTable(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(), List.of(new SubProviderEntry(BlocksLootSubProvider::new, LootContextParamSets.BLOCK)), registries);
    }
}
