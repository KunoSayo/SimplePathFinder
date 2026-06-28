package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * 方块战利品表数据提供者
 * 为模组中的方块生成战利品表（如方块掉落自己）
 */
public class BlocksLootSubProvider extends BlockLootSubProvider {

    protected BlocksLootSubProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, registries);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries()
                .stream()
                .map(blockDeferredHolder -> (Block) blockDeferredHolder.value())
                .toList();
    }

    @Override
    protected void generate() {
        // PathFinderBlock 掉落自己
        dropSelf(ModBlocks.PATH_FINDER_BLOCK.get());
    }
}
