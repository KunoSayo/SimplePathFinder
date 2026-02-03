package io.github.kunosayo.simplepathfinder.item;

/**
 * 导航物品的模式枚举
 */
public enum NavigationMode {
    /**
     * 默认显示模式 - 显示导航路径
     */
    DEFAULT("item.navigation_mode.default", "item.navigation_mode.default.desc"),

    /**
     * 添加导航模式 - 在点击位置添加导航点
     */
    ADD_NAV("item.navigation_mode.add_nav", "item.navigation_mode.add_nav.desc"),

    /**
     * 移除导航模式 - 移除导航路径
     */
    REMOVE_NAV("item.navigation_mode.remove_nav", "item.navigation_mode.remove_nav.desc");

    private final String translationKey;
    private final String descriptionKey;

    NavigationMode(String translationKey, String descriptionKey) {
        this.translationKey = translationKey;
        this.descriptionKey = descriptionKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    /**
     * 获取下一个模式
     */
    public NavigationMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * 获取上一个模式
     */
    public NavigationMode previous() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }
}