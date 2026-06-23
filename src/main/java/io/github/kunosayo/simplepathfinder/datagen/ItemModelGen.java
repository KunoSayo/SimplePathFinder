package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

/**
 * 物品模型生成器
 * 生成物品的JSON模型文件
 */
public class ItemModelGen extends ModelProvider {

    public ItemModelGen(PackOutput output) {
        super(output, SimplePathFinder.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 调试导航棍 - 使用原木棍纹理
        itemModels.itemModelOutput.accept(ModItems.DEBUG_NAV.get(), ItemModelUtils.plainModel(Identifier.withDefaultNamespace("item/stick")));

        // 导航物品
        itemModels.generateFlatItem(ModItems.NAVIGATION.get(), ModelTemplates.FLAT_ITEM);
        // 定位器
        itemModels.generateFlatItem(ModItems.LOCATOR.get(), ModelTemplates.FLAT_ITEM);
        // 导航笔刷
        itemModels.generateFlatItem(ModItems.NAV_BRUSH.get(), ModelTemplates.FLAT_ITEM);

        // 方块物品模型 - 这会自动生成方块模型和状态
        itemModels.generateFlatItem(ModBlocks.PATH_FINDER_BLOCK.get().asItem(), ModelTemplates.FLAT_ITEM);
        // 生成方块的模型和状态
        blockModels.createTrivialCube(ModBlocks.PATH_FINDER_BLOCK.get());
    }

    /**
     * 获取导航模式属性的资源位置
     */
    private Identifier getNavigationModePredicate() {
        return Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation_mode");
    }
}
