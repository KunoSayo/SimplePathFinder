package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.block.PathFinderBlock;
import io.github.kunosayo.simplepathfinder.client.property.LocatorModelProperty;
import io.github.kunosayo.simplepathfinder.client.property.NavBrushModelProperty;
import io.github.kunosayo.simplepathfinder.client.property.NavigationModelProperty;
import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.item.ConditionalItemModel;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 物品和方块模型生成器
 * 生成物品和方块的JSON模型文件
 */
public class ItemModelGen extends ModelProvider {

    public ItemModelGen(PackOutput output) {
        super(output, SimplePathFinder.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 调试导航棍 - 使用原木棍纹理
        itemModels.itemModelOutput.accept(ModItems.DEBUG_NAV.get(), ItemModelUtils.plainModel(Identifier.withDefaultNamespace("item/stick")));

        // 导航物品 - 基于模式切换模型
        registerNavigationItemModels(itemModels);

        // 定位器 - 基于绑定状态切换模型
        registerLocatorItemModels(itemModels);

        // 导航笔刷 - 基于模式切换模型
        registerNavBrushItemModels(itemModels);

        // PathFinderBlock - 基于激活状态切换模型
        registerPathFinderBlock(blockModels, itemModels);
    }

    /**
     * 注册导航物品的条件模型
     * 根据导航模式切换不同的模型：
     * 0 = DEFAULT (默认显示) - 使用无后缀贴图
     * 1 = ADD_NAV (添加导航) - 使用 _1
     * 2 = REMOVE_NAV (移除导航) - 使用 _2
     * 3 = ADD_LINK (添加链接) - 使用 _3
     */
    private void registerNavigationItemModels(ItemModelGenerators itemModels) {
        List<RangeSelectItemModel.Entry> entries = new ArrayList<>();

        // 模式0使用无后缀贴图
        var defaultModel = ItemModelUtils.plainModel(
                itemModels.createFlatItemModel(ModItems.NAVIGATION.get(), "", ModelTemplates.FLAT_ITEM)
        );
        entries.add(new RangeSelectItemModel.Entry(0.0f, defaultModel));

        // 模式1-3使用带后缀贴图
        for (int i = 1; i < 4; i++) {
            var model = ItemModelUtils.plainModel(
                    itemModels.createFlatItemModel(ModItems.NAVIGATION.get(), "_" + i, ModelTemplates.FLAT_ITEM)
            );
            entries.add(new RangeSelectItemModel.Entry((float) i, model));
        }

        itemModels.itemModelOutput.accept(ModItems.NAVIGATION.get(),
                new RangeSelectItemModel.Unbaked(
                        Optional.empty(),
                        new NavigationModelProperty(),
                        1,
                        entries,
                        Optional.of(defaultModel)
                ));
    }

    /**
     * 注册导航笔刷的条件模型
     * 根据笔刷模式切换不同的模型：
     * 0 = ALL_EDGES (所有边) - 使用无后缀贴图
     * 1 = SINGLE_EDGE (单边) - 使用 _single
     */
    private void registerNavBrushItemModels(ItemModelGenerators itemModels) {
        // 两个模式的模型
        var allEdgesModel = ItemModelUtils.plainModel(
                itemModels.createFlatItemModel(ModItems.NAV_BRUSH.get(), "", ModelTemplates.FLAT_ITEM)
        );
        var singleEdgeModel = ItemModelUtils.plainModel(
                itemModels.createFlatItemModel(ModItems.NAV_BRUSH.get(), "_single", ModelTemplates.FLAT_ITEM)
        );

        itemModels.itemModelOutput.accept(ModItems.NAV_BRUSH.get(),
                new ConditionalItemModel.Unbaked(
                        Optional.empty(),
                        new NavBrushModelProperty(),
                        singleEdgeModel,  // onTrue - when SINGLE_EDGE (ordinal 1)
                        allEdgesModel     // onFalse - when ALL_EDGES (ordinal 0)
                ));
    }

    /**
     * 注册定位器的条件模型
     * 根据绑定状态切换不同的模型：
     * 0 = unbound (未绑定) - 使用无后缀贴图
     * 1 = player bound (绑定玩家) - 使用 _player
     * 2 = position bound (绑定位置) - 使用 _pos
     */
    private void registerLocatorItemModels(ItemModelGenerators itemModels) {
        List<RangeSelectItemModel.Entry> entries = new ArrayList<>();

        // 状态0使用无后缀贴图
        var unboundModel = ItemModelUtils.plainModel(
                itemModels.createFlatItemModel(ModItems.LOCATOR.get(), "", ModelTemplates.FLAT_ITEM)
        );
        entries.add(new RangeSelectItemModel.Entry(0.0f, unboundModel));

        // 状态1: 绑定玩家
        var playerBoundModel = ItemModelUtils.plainModel(
                itemModels.createFlatItemModel(ModItems.LOCATOR.get(), "_player", ModelTemplates.FLAT_ITEM)
        );
        entries.add(new RangeSelectItemModel.Entry(1.0f, playerBoundModel));

        // 状态2: 绑定位置
        var posBoundModel = ItemModelUtils.plainModel(
                itemModels.createFlatItemModel(ModItems.LOCATOR.get(), "_pos", ModelTemplates.FLAT_ITEM)
        );
        entries.add(new RangeSelectItemModel.Entry(2.0f, posBoundModel));

        itemModels.itemModelOutput.accept(ModItems.LOCATOR.get(),
                new RangeSelectItemModel.Unbaked(
                        Optional.empty(),
                        new LocatorModelProperty(),
                        1,
                        entries,
                        Optional.of(unboundModel)
                ));
    }

    /**
     * 注册 PathFinderBlock 的模型和方块状态
     * 根据激活状态使用不同的侧面纹理：
     * - 激活状态（有数据）：使用 path_finder_block 侧面
     * - 未激活状态（无数据）：使用 path_finder_block_unactive 侧面
     */
    private void registerPathFinderBlock(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        var block = ModBlocks.PATH_FINDER_BLOCK.get();

        // 定义纹理（使用 Material）
        var topTexture = new Material(modLoc("block/path_finder_block_top"));
        var bottomTexture = new Material(modLoc("block/path_finder_block_bottom"));
        var activeSideTexture = new Material(modLoc("block/path_finder_block"));
        var inactiveSideTexture = new Material(modLoc("block/path_finder_block_inactive"));

        // 创建激活模型（有数据时）
        var activeTextureMapping = new TextureMapping()
                .put(TextureSlot.UP, topTexture)
                .put(TextureSlot.DOWN, bottomTexture)
                .put(TextureSlot.SIDE, activeSideTexture)
                .copySlot(TextureSlot.SIDE, TextureSlot.PARTICLE);

        var activeModel = ModelTemplates.CUBE.create(
                modLoc("block/path_finder_block_active"),
                activeTextureMapping,
                blockModels.modelOutput
        );

        // 创建未激活模型（无数据时）
        var inactiveTextureMapping = new TextureMapping()
                .put(TextureSlot.UP, topTexture)
                .put(TextureSlot.DOWN, bottomTexture)
                .put(TextureSlot.SIDE, inactiveSideTexture)
                .copySlot(TextureSlot.SIDE, TextureSlot.PARTICLE);

        var inactiveModel = ModelTemplates.CUBE.create(
                modLoc("block/path_finder_block_inactive"),
                inactiveTextureMapping,
                blockModels.modelOutput
        );

        // 使用 plainVariant 包装模型
        var activeVariant = BlockModelGenerators.plainVariant(activeModel);
        var inactiveVariant = BlockModelGenerators.plainVariant(inactiveModel);

        // 注册方块状态 - 根据 active 属性切换模型
        // 使用 MultiVariantGenerator.dispatch() 创建调度
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(BlockModelGenerators.createBooleanModelDispatch(
                        PathFinderBlock.ACTIVE,
                        activeVariant,
                        inactiveVariant
                )));

        // 生成物品模型 - 使用方块模型作为父模型（3D方块外观）
        // 方块物品应该显示为3D方块，而不是平面贴图
        var itemModelIdentifier = modLoc("block/path_finder_block_active");
        itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(itemModelIdentifier));
    }

    private Identifier modLoc(String path) {
        return Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, path);
    }
}
