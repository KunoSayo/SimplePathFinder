package io.github.kunosayo.simplepathfinder.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 导航物品的模式枚举
 */
public enum NavigationMode {
    /**
     * 默认显示模式 - 显示导航路径
     */
    DEFAULT(0, "item.navigation_mode.default", "item.navigation_mode.default.desc"),

    /**
     * 添加导航模式 - 在点击位置添加导航点
     */
    ADD_NAV(1, "item.navigation_mode.add_nav", "item.navigation_mode.add_nav.desc"),

    /**
     * 移除导航模式 - 移除导航路径
     */
    REMOVE_NAV(2, "item.navigation_mode.remove_nav", "item.navigation_mode.remove_nav.desc"),

    /**
     * 添加导航链接模式 - 右键两个点创建导航链接
     */
    ADD_LINK(3, "item.navigation_mode.add_link", "item.navigation_mode.add_link.desc");

    private final int id;
    private final String translationKey;
    private final String descriptionKey;

    /**
     * Gets Id -> Enum mapping
     */
    public static final IntFunction<NavigationMode> BY_ID = ByIdMap.continuous(
            NavigationMode::getId,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );

    NavigationMode(int id, String translationKey, String descriptionKey) {
        this.id = id;
        this.translationKey = translationKey;
        this.descriptionKey = descriptionKey;
    }

    public int getId() {
        return id;
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

    /**
     * Codec for serialization
     */
    public static final Codec<NavigationMode> CODEC = Codec.STRING.xmap(s -> {
        try {
            return NavigationMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }, NavigationMode::name);

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, NavigationMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, NavigationMode::getId);

    /**
     * Get NavigationMode from ID
     */
    public static NavigationMode fromId(int id) {
        return BY_ID.apply(id);
    }
}