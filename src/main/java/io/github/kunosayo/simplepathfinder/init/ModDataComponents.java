package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LinkCreationData;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.data.NavBrushData;
import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

/**
 * 数据组件类型注册
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SimplePathFinder.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NavigationModeData>> NAV_MODE_COMPONENT =
            register("navigation_mode",
                    builder -> builder.persistent(NavigationModeData.CODEC)
                            .networkSynchronized(NavigationModeData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LocatorData>> LOCATOR_COMPONENT =
            register("locator",
                    builder -> builder.persistent(LocatorData.CODEC)
                            .networkSynchronized(LocatorData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NavBrushData>> NAV_BRUSH_COMPONENT =
            register("nav_brush",
                    builder -> builder.persistent(NavBrushData.CODEC)
                            .networkSynchronized(NavBrushData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LinkCreationData>> LINK_CREATION_COMPONENT =
            register("link_creation",
                    builder -> builder.persistent(LinkCreationData.CODEC)
                            .networkSynchronized(LinkCreationData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NavLinkType>> LINK_TYPE_COMPONENT =
            register("link_type",
                    builder -> builder.persistent(NavLinkType.CODEC)
                            .networkSynchronized(NavLinkType.STREAM_CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENTS.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }
}