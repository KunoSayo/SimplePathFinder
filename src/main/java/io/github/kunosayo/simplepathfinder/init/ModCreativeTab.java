package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimplePathFinder.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TABS = TABS.register(SimplePathFinder.MOD_ID, () -> CreativeModeTab.builder()
            .title(Component.translatable("item_group." + SimplePathFinder.MOD_ID + ".name"))
            .icon(() -> ModItems.NAVIGATION.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.NAVIGATION);
                output.accept(ModItems.LOCATOR);
            }).build());
}