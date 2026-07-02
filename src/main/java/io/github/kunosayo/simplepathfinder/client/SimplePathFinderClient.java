package io.github.kunosayo.simplepathfinder.client;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SimplePathFinder.MOD_ID, dist = Dist.CLIENT)
public class SimplePathFinderClient {
    public SimplePathFinderClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
