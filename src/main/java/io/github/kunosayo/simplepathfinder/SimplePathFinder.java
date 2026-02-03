package io.github.kunosayo.simplepathfinder;

import com.mojang.brigadier.CommandDispatcher;
import io.github.kunosayo.simplepathfinder.command.SimplePathFinderCommand;
import io.github.kunosayo.simplepathfinder.config.NavBuildConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.UUID;

@Mod(SimplePathFinder.MOD_ID)
public final class SimplePathFinder {
    @Nullable
    public static volatile LevelNavData clientNavData = null;
    @Nullable
    public static volatile NavResult clientNavResult = null;
    private static final HashSet<UUID> playerGotNav = new HashSet<>();
    public static final String MOD_ID = "simple_path_finder";


    public SimplePathFinder(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.SERVER, NavBuildConfig.NAV_BUILD_CONFIG.getRight());
    }

    @SubscribeEvent
    public void onRegisterCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        SimplePathFinderCommand.registerCommand(dispatcher);
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        playerGotNav.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onLoadLevel(LevelEvent.Load load) {
        if (load.getLevel() instanceof ServerLevel l) {
            LevelNavDataSavedData.loadFromLevel(l);
        }
    }

}
