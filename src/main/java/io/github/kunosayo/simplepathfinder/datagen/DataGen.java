package io.github.kunosayo.simplepathfinder.datagen;

import net.minecraft.core.HolderLookup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "simple_path_finder")
public class DataGen implements IModBusEvent {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        // 创建双语生成器
        LangGen bilingualProvider = new LangGen(event.getGenerator().getPackOutput());

        // 初始化所有翻译
        bilingualProvider.initializeTranslations();

        // 注册英文和中文语言提供者
        event.getGenerator().addProvider(true, bilingualProvider.getEnglishProvider());
        event.getGenerator().addProvider(true, bilingualProvider.getChineseProvider());

        event.getGenerator().addProvider(true, new ItemModelGen(event.getGenerator().getPackOutput()));

        event.getGenerator().addProvider(true, new ModRecipeProvider.Runner(event.getGenerator().getPackOutput(), event.getLookupProvider()));

    }

    @SubscribeEvent
    public static void gatherServerData(GatherDataEvent.Server event) {
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // 注册战利品表生成器
        event.getGenerator().addProvider(true, new GenLootTable(event.getGenerator().getPackOutput(), lookupProvider));
    }
}
