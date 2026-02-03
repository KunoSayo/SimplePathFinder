package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

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
public class ItemModelGen extends ItemModelProvider {

    public ItemModelGen(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, SimplePathFinder.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // 调试导航棍 - 使用原木棍纹理
        withExistingParent("debug_nav", mcLoc("item/generated"))
                .texture("layer0", mcLoc("item/stick"));

        // 首先创建每个模式的模型（需要在主模型中引用它们）
        // 默认模式模型 - 使用指南针12点钟方向作为占位符
        withExistingParent("navigation_default", mcLoc("item/handheld"))
                .texture("layer0", mcLoc("item/compass_12"));

        // 添加导航模式模型 - 使用指南针其他角度作为占位符
        withExistingParent("navigation_add", mcLoc("item/handheld"))
                .texture("layer0", mcLoc("item/compass_08"));

        // 移除导航模式模型 - 使用指南针其他角度作为占位符
        withExistingParent("navigation_remove", mcLoc("item/handheld"))
                .texture("layer0", mcLoc("item/compass_16"));

        // 导航罗盘 - 使用自定义纹理，并配置模型覆盖
        withExistingParent("navigation", mcLoc("item/handheld"))
                .texture("layer0", mcLoc("item/compass_12"))
                .override()
                .predicate(getNavigationModePredicate(), NavigationMode.DEFAULT.ordinal())
                .model(getExistingFile(modLoc("item/navigation_default")))
                .end()
                .override()
                .predicate(getNavigationModePredicate(), NavigationMode.ADD_NAV.ordinal())
                .model(getExistingFile(modLoc("item/navigation_add")))
                .end()
                .override()
                .predicate(getNavigationModePredicate(), NavigationMode.REMOVE_NAV.ordinal())
                .model(getExistingFile(modLoc("item/navigation_remove")))
                .end();
    }

    /**
     * 获取导航模式属性的资源位置
     */
    private ResourceLocation getNavigationModePredicate() {
        return ResourceLocation.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation_mode");
    }
}
