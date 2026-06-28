package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 双语语言提供者，通过一个add方法同时添加中英文翻译
 */
public class LangGen {

    private static class TranslationEntry {
        String key;
        String english;
        String chinese;

        TranslationEntry(String key, String english, String chinese) {
            this.key = key;
            this.english = english;
            this.chinese = chinese;
        }
    }

    private final List<TranslationEntry> translations = new ArrayList<>();
    private final PackOutput output;

    public LangGen(PackOutput output) {
        this.output = output;
    }

    /**
     * 添加双语翻译
     */
    public void add(String key, String chinese, String english) {
        translations.add(new TranslationEntry(key, english, chinese));
    }

    /**
     * 获取英文语言提供者
     */
    public LanguageProvider getEnglishProvider() {
        return new LanguageProvider(output, SimplePathFinder.MOD_ID, "en_us") {
            @Override
            protected void addTranslations() {
                for (TranslationEntry entry : translations) {
                    add(entry.key, entry.english);
                }
            }
        };
    }

    /**
     * 获取中文语言提供者
     */
    public LanguageProvider getChineseProvider() {
        return new LanguageProvider(output, SimplePathFinder.MOD_ID, "zh_cn") {
            @Override
            protected void addTranslations() {
                for (TranslationEntry entry : translations) {
                    add(entry.key, entry.chinese);
                }
            }
        };
    }

    /**
     * 快速添加物品双语翻译
     */
    public void addItem(DeferredItem<Item> item, String chineseName, String englishName) {
        add(item.get().getDescriptionId(), chineseName, englishName);
    }

    /**
     * 添加物品双语翻译（接受Item类型）
     */
    public void addItem(Item item, String chineseName, String englishName) {
        add(item.getDescriptionId(), chineseName, englishName);
    }

    /**
     * 添加带描述的物品双语翻译
     */
    public void addItem(Item item, String chineseName, String englishName, String chineseDesc, String englishDesc) {
        addItem(item, chineseName, englishName);
        // 对于物品描述，使用翻译键
        String descKey = item.getDescriptionId();
        if (!chineseDesc.isEmpty() && !englishDesc.isEmpty()) {
            add(descKey, englishDesc, chineseDesc);
        }
    }

    /**
     * 添加方块双语翻译
     */
    public void addBlock(net.minecraft.world.level.block.Block block, String chineseName, String englishName) {
        add(block.getDescriptionId(), chineseName, englishName);
    }

    /**
     * 初始化所有翻译
     */
    public void initializeTranslations() {
        // 模组名称
        add(SimplePathFinder.MOD_ID, "简单路径查找器", "Simple Path Finder");

        // 物品名称
        addItem(ModItems.NAVIGATION, "导航", "Navigation");
        addItem(ModItems.DEBUG_NAV, "导航调试棍", "Debug Navigation Stick");
        addItem(ModItems.LOCATOR, "定位器", "Locator");
        addItem(ModItems.NAV_BRUSH, "导航笔刷", "Navigation Brush");

        // 方块名称
        addBlock(ModBlocks.PATH_FINDER_BLOCK.get(), "路径查找方块", "Path Finder Block");

        // 导航模式
        add("item.navigation_mode.default", "默认显示", "Default Display");
        add("item.navigation_mode.add_nav", "添加导航", "Add Navigation");
        add("item.navigation_mode.remove_nav", "移除导航", "Remove Navigation");
        add("item.navigation_mode.add_link", "添加导航链接", "Add Navigation Link");

        // 导航模式描述
        add("item.navigation_mode.default.desc", "显示导航路径", "Displays navigation path");
        add("item.navigation_mode.add_nav.desc", "在点击位置添加导航点", "Add navigation at clicked position");
        add("item.navigation_mode.remove_nav.desc", "移除导航路径", "Remove navigation path");
        add("item.navigation_mode.add_link.desc", "右键两个点创建导航链接", "Right-click two positions to create navigation link");

        // 控制提示
        add("tooltip.navigation.switch_mode", "按住Shift + 滚动鼠标滚轮切换模式", "Hold Shift + Scroll to switch mode");
        add("tooltip.navigation.open_settings", "Shift+右键打开设置界面", "Shift+Right-click to open settings");
        add("tooltip.navigation.current_mode", "当前模式：", "Current Mode:");

        // 定位器提示
        add("tooltip.locator.bound.player", "已绑定到玩家", "Bound to Player");
        add("tooltip.locator.bound.pos", "已绑定到位置", "Bound to Position");
        add("tooltip.locator.unbound", "未绑定", "Unbound");
        add("tooltip.locator.usage", "按住Shift + 右键绑定当前玩家，右键方块绑定位置", "Hold Shift + Right-click to bind current player, Right-click block to bind location.");

        // 定位器系统消息
        add("item.simple_path_finder.locator.bound.player", "已将定位器绑定到玩家：", "Locator bound to player: ");


        // 路径查找方块消息
        add("block.simple_path_finder.path_finder_block.wrote.player", "已将玩家定位数据写入方块", "Wrote player locator data to block");
        add("block.simple_path_finder.path_finder_block.wrote.pos", "已将位置数据写入方块：", "Wrote position data to block: ");
        add("block.simple_path_finder.path_finder_block.empty_locator", "定位器为空，无法写入", "Locator is empty, cannot write");
        add("block.simple_path_finder.path_finder_block.already_has_data", "方块已有数据，无法写入", "Block already has data, cannot write");

        // 系统消息
        add("simple_path_finder.build.nav.success", "成功构建导航区块", "Successfully built navigation chunk");
        add("simple_path_finder.build.nav.batch_success", "成功构建导航区块 %s/%s", "Successfully built navigation chunk %s/%s");
        add("simple_path_finder.build.nav.limited", "当前导航区块数量超过上限", "Current navigation chunk count exceeds limit");
        add("simple_path_finder.build.nav.failed", "当前环境不易于构造寻路", "Current environment is not suitable for pathfinding");
        add("simple_path_finder.remove.current.success", "成功移除当前所在区块导航", "Successfully removed navigation in current chunk");
        add("simple_path_finder.failed.not_found", "未能找到导航区块", "Navigation chunk not found");


        add("item_group.simple_path_finder.name", "简单路径查找器", "Simple Path Finder");

        // 导航笔刷模式
        add("item.simple_path_finder.nav_brush.mode.all_edges", "所有边", "All Edges");
        add("item.simple_path_finder.nav_brush.mode.single_edge", "单边", "Single Edge");

        // 导航笔刷操作
        add("item.simple_path_finder.nav_brush.operation.delete", "删除", "Delete");
        add("item.simple_path_finder.nav_brush.operation.add", "添加", "Add");
        add("item.simple_path_finder.nav_brush.operation.adjust_weight", "调整权重", "Adjust Weight");

        // 导航笔刷提示
        add("tooltip.nav_brush.mode", "笔刷模式：", "Brush Mode: ");
        add("tooltip.nav_brush.operation", "笔刷操作：", "Brush Operation: ");
        add("tooltip.nav_brush.weight_value", "权重值： %s", "Weight Value: %s");
        add("tooltip.nav_brush.controls", "Shift+右键打开设置界面，右键方块应用操作", "Shift+Right-click to open settings, right-click block to apply");

        // 导航笔刷GUI
        add("gui.simple_path_finder.nav_brush.title", "导航笔刷设置", "Navigation Brush Settings");
        add("gui.simple_path_finder.nav_brush.mode.all_edges", "所有边", "All Edges");
        add("gui.simple_path_finder.nav_brush.mode.single_edge", "单边", "Single Edge");
        add("gui.simple_path_finder.nav_brush.operation.delete", "删除", "Delete");
        add("gui.simple_path_finder.nav_brush.operation.add", "添加", "Add");
        add("gui.simple_path_finder.nav_brush.operation.adjust_weight", "调整权重", "Adjust Weight");
        add("gui.simple_path_finder.nav_brush.weight", "权重值:", "Weight:");
        add("gui.simple_path_finder.nav_brush.save", "保存", "Save");
        add("gui.simple_path_finder.nav_brush.cancel", "取消", "Cancel");

        // 导航点GUI
        add("gui.simple_path_finder.navigation.title", "导航点设置", "Navigation Point Settings");
        add("gui.simple_path_finder.navigation.mode.default", "默认", "Default");
        add("gui.simple_path_finder.navigation.mode.add_nav", "添加导航", "Add Nav");
        add("gui.simple_path_finder.navigation.mode.remove_nav", "移除导航", "Remove Nav");
        add("gui.simple_path_finder.navigation.mode.add_link", "添加链接", "Add Link");
        add("gui.simple_path_finder.navigation.layer", "层级值:", "Layer:");
        add("gui.simple_path_finder.navigation.save", "保存", "Save");
        add("gui.simple_path_finder.navigation.cancel", "取消", "Cancel");

        // 导航笔刷系统消息
        add("simple_path_finder.nav_brush.creative_required", "需要创造模式才能使用导航笔刷", "Creative mode required to use navigation brush");
        add("simple_path_finder.nav_brush.no_nav_data", "此位置没有导航数据", "No navigation data at this position");
        add("simple_path_finder.nav_brush.no_layer_at_pos", "此位置没有导航层", "No navigation layer at this position");
        add("simple_path_finder.nav_brush.modified_edges", "已修改 %s 条边", "Modified %s edges");
        add("simple_path_finder.nav_brush.edge_deleted", "已删除 %s 方向的边", "Deleted edge in %s direction");
        add("simple_path_finder.nav_brush.edge_added", "已添加 %s 方向的边", "Added edge in %s direction");
        add("simple_path_finder.nav_brush.edge_weight_set", "已设置 %s 方向的权重为 %s", "Set %s direction weight to %s");

        // 导航层级系统消息（补充）
        add("simple_path_finder.nav.creative_required", "需要创造模式才能使用导航物品功能", "Creative mode required for navigation item features");
        add("simple_path_finder.nav.layer_limit", "层级必须在 -128 到 %d 之间", "Layer must be between -128 and %d");
        add("simple_path_finder.nav.layer_exists", "该区块已存在层级 %d", "This chunk already has layer %d");
        add("simple_path_finder.nav.layer_created", "在 %2$s, %3$s, %4$s 创建导航层级 %1$d", "Created navigation layer %d at %s, %s, %s");
        add("simple_path_finder.nav.layer_removed", "已移除导航层级 %d", "Removed navigation layer %d");
        add("simple_path_finder.nav.chunk_not_found", "此位置未找到导航区块", "Navigation chunk not found at this position");
        add("simple_path_finder.nav.no_layer_at_pos", "此位置未找到导航层级", "No navigation layer found at this position");
        add("simple_path_finder.nav.no_data", "没有可用的导航数据", "No navigation data available");
        add("simple_path_finder.nav.success", "开始寻路", "Starting pathfinding");
        add("simple_path_finder.nav.starting", "开始寻路至 %s, %s, %s", "Starting pathfinding to %s, %s, %s");
        add("simple_path_finder.nav.to_player", "开始寻路至玩家： %s", "Starting pathfinding to player: %s");

        // 导航链接系统消息
        add("simple_path_finder.nav.link.start_pos_set", "已设置起始位置： %s, %s, %s，请右键点击目标位置", "Start position set: %s, %s, %s, now right-click target position");
        add("simple_path_finder.nav.link.no_start_nav", "起始位置没有导航数据", "No navigation data at start position");
        add("simple_path_finder.nav.link.created", "已创建导航链接：(%s, %s, %s) → (%s, %s, %s)", "Created navigation link: (%s, %s, %s) → (%s, %s, %s)");

        add("tooltip.navigation.current_layer", "层级： %s", "Layer: %s");
        // Layer switching tooltip removed - functionality not implemented

        // 定位器系统消息（补充）
        add("simple_path_finder.locator.no_target", "定位器未设置目标", "Locator has no target set");
        add("simple_path_finder.locator.player_offline", "目标玩家离线", "Target player is offline");
    }
}
