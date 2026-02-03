package io.github.kunosayo.simplepathfinder.datagen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = "simple_path_finder", bus = EventBusSubscriber.Bus.MOD)
public class DataGen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        // 创建双语生成器
        LangGen bilingualProvider = new LangGen(event.getGenerator().getPackOutput());

        // 初始化所有翻译
        bilingualProvider.initializeTranslations();

        // 注册英文和中文语言提供者
        event.getGenerator().addProvider(true, bilingualProvider.getEnglishProvider());
        event.getGenerator().addProvider(true, bilingualProvider.getChineseProvider());
    }
}
