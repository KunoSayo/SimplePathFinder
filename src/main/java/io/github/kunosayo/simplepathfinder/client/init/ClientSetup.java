package io.github.kunosayo.simplepathfinder.client.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 客户端初始化
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = SimplePathFinder.MOD_ID)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterItems(RegisterEvent event) {
        // 只在物品注册时处理
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }

        // 为导航物品注册模型属性
        ItemProperties.register(
            ModItems.NAVIGATION.get(),
            ResourceLocation.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation_mode"),
            (stack, level, entity, seed) -> {
                // 从 ItemStack 的数据组件中获取导航模式
                var modeData = stack.get(ModDataComponents.NAV_MODE_COMPONENT.get());
                if (modeData != null) {
                    return modeData.mode().ordinal();
                }
                // 默认返回 DEFAULT模式的序号
                return NavigationMode.DEFAULT.ordinal();
            }
        );
    }
}