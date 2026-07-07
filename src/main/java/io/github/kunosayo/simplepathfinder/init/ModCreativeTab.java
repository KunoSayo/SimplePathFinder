package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimplePathFinder.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TABS = TABS.register(SimplePathFinder.MOD_ID, () -> CreativeModeTab.builder()
            .title(Component.translatable("item_group." + SimplePathFinder.MOD_ID + ".name"))
            .icon(() -> ModItems.NAVIGATION.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // 物品
                output.accept(ModItems.NAVIGATION);
                output.accept(ModItems.NAV_BRUSH);
                output.accept(ModItems.LOCATOR);
                output.accept(ModItems.DEBUG_NAV);
                // 方块
                output.accept(new ItemStack(ModBlocks.PATH_FINDER_BLOCK.get(), 1));
                output.accept(new ItemStack(ModBlocks.NAVIGATION_BARRIER_BLOCK.get(), 1));
            }).build());
}
