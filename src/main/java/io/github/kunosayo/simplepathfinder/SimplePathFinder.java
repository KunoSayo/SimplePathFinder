package io.github.kunosayo.simplepathfinder;

import com.mojang.brigadier.CommandDispatcher;
import io.github.kunosayo.simplepathfinder.command.SimplePathFinderCommand;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.*;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavResult;
import io.github.kunosayo.simplepathfinder.network.SyncLevelNavDataPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SimplePathFinder.MOD_ID)
public final class SimplePathFinder {
    public static final Logger LOGGER = LogManager.getLogger(SimplePathFinder.MOD_ID);

    @Nullable
    public static volatile NavResult clientNavResult = null;
    private static final HashSet<UUID> playerGotNav = new HashSet<>();
    public static final String MOD_ID = "simple_path_finder";


    public SimplePathFinder(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        ModCreativeTab.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, NavConfig.NAV_CONFIG.getRight());
    }

    public static void playerMadeServerNavDirty(ServerPlayer sp) {
        syncAllPlayerNav(sp);
    }

    @SubscribeEvent
    public void onRegisterCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        SimplePathFinderCommand.registerCommand(dispatcher);
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncPlayerFullNav(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        playerGotNav.remove(event.getEntity().getUUID());
    }

    public static void syncPlayerFullNav(ServerPlayer sp) {
        var level = sp.level();
        var data = LevelNavDataSavedData.loadFromLevel(level);
        syncPlayerFullNav(sp, data, level.dimension());
    }

    public static void syncPlayerFullNav(ServerPlayer sp, LevelNavDataSavedData data) {
        syncPlayerFullNav(sp, data, sp.level().dimension());
    }

    public static void syncPlayerFullNav(ServerPlayer sp, LevelNavDataSavedData data, ResourceKey<Level> dimension) {
        PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(dimension, data.levelNavData));
    }

    public static void syncAllPlayerNav(ServerPlayer sp) {
        var level = sp.level();
        var data = LevelNavDataSavedData.loadFromLevel(level);
        for (ServerPlayer player : sp.level().players()) {
            syncPlayerFullNav(player, data, level.dimension());
        }
    }

    @SubscribeEvent
    public void onLoadLevel(LevelEvent.Load load) {
        if (load.getLevel() instanceof ServerLevel l) {
            LevelNavDataSavedData.loadFromLevel(l);
        }
    }
}
