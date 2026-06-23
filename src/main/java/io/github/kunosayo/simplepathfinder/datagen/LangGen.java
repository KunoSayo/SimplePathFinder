package io.github.kunosayo.simplepathfinder.datagen;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.common.data.LanguageProvider;

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

        // 导航模式描述
        add("item.navigation_mode.default.desc", "显示导航路径", "Displays navigation path");
        add("item.navigation_mode.add_nav.desc", "在点击位置添加导航点", "Add navigation at clicked position");
        add("item.navigation_mode.remove_nav.desc", "移除导航路径", "Remove navigation path");

        // 控制提示
        add("tooltip.navigation.switch_mode", "按住Shift + 滚动鼠标滚轮切换模式", "Hold Shift + Scroll to switch mode");
        add("tooltip.navigation.current_mode", "当前模式：", "Current Mode:");

        // 定位器提示
        add("tooltip.locator.bound.player", "已绑定到玩家", "Bound to Player");
        add("tooltip.locator.bound.pos", "已绑定到位置", "Bound to Position");
        add("tooltip.locator.unbound", "未绑定", "Unbound");
        add("tooltip.locator.usage", "按住Shift + 右键绑定当前位置", "Hold Shift + Right-click to bind current position");

        // 定位器系统消息
        add("item.simple_path_finder.locator.bound.player", "已将定位器绑定到玩家：", "Locator bound to player: ");


        // 路径查找方块消息
        add("block.simple_path_finder.path_finder_block.wrote.player", "已将玩家定位数据写入方块", "Wrote player locator data to block");
        add("block.simple_path_finder.path_finder_block.wrote.pos", "已将位置数据写入方块：", "Wrote position data to block: ");
        add("block.simple_path_finder.path_finder_block.empty_locator", "定位器为空，无法写入", "Locator is empty, cannot write");

        // 系统消息
        add("simple_path_finder.build.nav.success", "成功构建导航区块", "Successfully built navigation chunk");
        add("simple_path_finder.build.nav.limited", "当前导航区块数量超过上限", "Current navigation chunk count exceeds limit");
        add("simple_path_finder.build.nav.failed", "当前环境不易于构造寻路", "Current environment is not suitable for pathfinding");
        add("simple_path_finder.remove.current.success", "成功移除当前所在区块导航", "Successfully removed navigation in current chunk");
        add("simple_path_finder.failed.not_found", "未能找到导航区块", "Navigation chunk not found");


        add("item_group.simple_path_finder.name", "简单路径查找器", "Simple Path Finder");
    }
}
