package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.block.entity.PathFinderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体类型注册
 */
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SimplePathFinder.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PathFinderBlockEntity>> PATH_FINDER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("path_finder_block", () -> new BlockEntityType<>(
                    PathFinderBlockEntity::new,
                    true,
                    ModBlocks.PATH_FINDER_BLOCK.get()
            ));
}
