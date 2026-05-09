package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

/**
 * 物品模型生成器
 * 生成物品的JSON模型文件
 * <p>
 * 注意：当前使用原版指南针纹理作为占位符。
 * 你可以稍后替换为自定义纹理文件：
 * - src/main/resources/assets/simple_path_finder/textures/item/navigation_default.png
 * - src/main/resources/assets/simple_path_finder/textures/item/navigation_add.png
 * - src/main/resources/assets/simple_path_finder/textures/item/navigation_remove.png
 */
public class ItemModelGen extends ModelProvider {

    public ItemModelGen(PackOutput output) {
        super(output, SimplePathFinder.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 调试导航棍 - 使用原木棍纹理
        itemModels.itemModelOutput.accept(ModItems.DEBUG_NAV.get(), ItemModelUtils.plainModel(Identifier.withDefaultNamespace("item/stick")));

    }

    /**
     * 获取导航模式属性的资源位置
     */
    private Identifier getNavigationModePredicate() {
        return Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation_mode");
    }
}
