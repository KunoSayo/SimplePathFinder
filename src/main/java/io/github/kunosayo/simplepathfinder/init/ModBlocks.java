package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.block.NavigationBarrierBlock;
import io.github.kunosayo.simplepathfinder.block.PathFinderBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimplePathFinder.MOD_ID);

    public static final DeferredBlock<Block> PATH_FINDER_BLOCK = BLOCKS.registerBlock("path_finder_block",
            PathFinderBlock::new,
            props -> props.strength(1.5f).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredBlock<NavigationBarrierBlock> NAVIGATION_BARRIER_BLOCK = BLOCKS.registerBlock("navigation_barrier",
            NavigationBarrierBlock::new,
            props -> props
                    .noOcclusion()
                    .isValidSpawn(Blocks::never)
                    .noTerrainParticles()
                    .mapColor(MapColor.NONE)
                    .isSuffocating((_, _, _) -> false)
                    .isViewBlocking((_, _, _) -> false));
}
