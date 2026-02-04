package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.data.PlayerLocatorData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlayerLocatorData>> PLAYER_LOCATOR_COMPONENT =
            register("player_locator",
                    builder -> builder.persistent(PlayerLocatorData.CODEC)
                            .networkSynchronized(PlayerLocatorData.STREAM_CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENTS.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }
}