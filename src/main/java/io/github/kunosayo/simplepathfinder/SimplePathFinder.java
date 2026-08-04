package io.github.kunosayo.simplepathfinder;

import com.mojang.brigadier.CommandDispatcher;
import io.github.kunosayo.simplepathfinder.command.SimplePathFinderCommand;
import io.github.kunosayo.simplepathfinder.config.ClientConfig;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.*;
import io.github.kunosayo.simplepathfinder.nav.finder.NavResult;
import io.github.kunosayo.simplepathfinder.nav.finder.ServerPathfindingManager;
import io.github.kunosayo.simplepathfinder.nav.layered.BatchScheduler;
import io.github.kunosayo.simplepathfinder.nav.progress.PathfindingContext;
import io.github.kunosayo.simplepathfinder.network.SyncLevelNavDataPacket;
import io.github.kunosayo.simplepathfinder.network.SyncSingleChunkPacket;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Mod(SimplePathFinder.MOD_ID)
public final class SimplePathFinder {
    public static final Logger LOGGER = LogManager.getLogger(SimplePathFinder.MOD_ID);

    @NonNls
    public static AtomicReference<NavResult> clientNavResult = new AtomicReference<>();
    private static final HashMap<UUID, HashMap<Identifier, HashSet<ChunkPos>>> playerGotNav = new HashMap<>();
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
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG.getRight());
    }

    public static void playerMadeServerNavDirty(ServerPlayer sp) {
        LevelNavDataSavedData.loadFromLevel(sp.level()).setDirty();
        syncAllPlayerNav(sp);
    }

    /**
     * Check if server-side pathfinding is enabled.
     * When enabled, nav data sync is disabled.
     */
    public static boolean isServerSidePathfindingEnabled() {
        return NavConfig.NAV_CONFIG.getLeft().serverSidePathfinding.get();
    }

    public static Identifier location(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @SubscribeEvent
    public void onRegisterCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        SimplePathFinderCommand.registerCommand(dispatcher);
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // Only sync nav data if server-side pathfinding is disabled
            if (!isServerSidePathfindingEnabled()) {
                syncPlayerFullNav(sp);
            }

            // Sync player's block distance configuration
            syncPlayerBlockDistanceData(sp);
        }
    }

    /**
     * Sync player's block distance configuration from server to client.
     * This allows the client GUI to display the actual configuration.
     *
     * @param player the server player to sync
     */
    public static void syncPlayerBlockDistanceData(ServerPlayer player) {
        var attachment = player.getData(io.github.kunosayo.simplepathfinder.init.ModAttachments.PLAYER_BLOCK_DISTANCE.get());
        var data = attachment.getData();
        PacketDistributor.sendToPlayer(player, new io.github.kunosayo.simplepathfinder.network.SyncBlockDistanceConfigPacket(data));
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide()) {
            playerGotNav.clear();
        } else {
            playerGotNav.remove(event.getEntity().getUUID());
        }
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
        PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(dimension.identifier(), data.levelNavData));
    }

    public static void syncAllPlayerNav(ServerPlayer sp) {
        // Don't sync if server-side pathfinding is enabled
        if (isServerSidePathfindingEnabled()) {
            return;
        }

        var level = sp.level();
        var data = LevelNavDataSavedData.loadFromLevel(level);
        for (ServerPlayer player : sp.level().players()) {
            syncPlayerFullNav(player, data, level.dimension());
        }
    }

    /**
     * Synchronize a single navigation chunk to all players in the current dimension.
     * Used for incremental updates when a specific chunk is modified.
     *
     * @param level    the server level
     * @param chunkPos the chunk position to synchronize
     */
    public static void syncSingleChunk(ServerLevel level, ChunkPos chunkPos) {

        var data = LevelNavDataSavedData.loadFromLevel(level);
        var navChunkOpt = data.levelNavData.getNavChunkForSync(chunkPos);

        for (ServerPlayer player : level.players()) {
            if (navChunkOpt.isPresent()) {
                PacketDistributor.sendToPlayer(player,
                        new io.github.kunosayo.simplepathfinder.network.SyncSingleChunkPacket(
                                level.dimension().identifier(),
                                chunkPos,
                                navChunkOpt.get()));
            } else {
                // Send delete packet if chunk doesn't exist
                PacketDistributor.sendToPlayer(player,
                        io.github.kunosayo.simplepathfinder.network.SyncSingleChunkPacket.createDelete(
                                level.dimension().identifier(),
                                chunkPos));
            }
        }
    }

    @SubscribeEvent
    public void onLoadLevel(LevelEvent.Load load) {
        if (load.getLevel() instanceof ServerLevel l) {
            LevelNavDataSavedData.loadFromLevel(l);
        }
    }

    @SubscribeEvent
    public void onServerTick(PlayerTickEvent.Post event) {
        if (isServerSidePathfindingEnabled()) {
            if ((event.getEntity().tickCount & 0b1111) == 0) {
                if (event.getEntity() instanceof ServerPlayer sp) {
                    if (NavUtil.shouldShowNav(event.getEntity().getMainHandItem())) {
                        var cp = event.getEntity().chunkPosition();
                        if (!trySyncSingleForPlayer(sp, sp.level(), cp)) {
                            if (!trySyncSingleForPlayer(sp, sp.level(), new ChunkPos(cp.x(), cp.z() + 1))) {
                                if (!trySyncSingleForPlayer(sp, sp.level(), new ChunkPos(cp.x(), cp.z() - 1))) {
                                    if (!trySyncSingleForPlayer(sp, sp.level(), new ChunkPos(cp.x() + 1, cp.z()))) {
                                        trySyncSingleForPlayer(sp, sp.level(), new ChunkPos(cp.x() - 1, cp.z()));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (event.getEntity() instanceof ServerPlayer sp) {
                var ctx = ServerPathfindingManager.getProgressContext(sp.getUUID());
                if (ctx != null) {
                    if (ctx.isCompleted()) {
                        ServerPathfindingManager.removeProgressContext(sp.getUUID());
                        sp.sendSystemMessage(Component.translatable("simple_path_finder.nav.done").append(String.format(" [%d]", ctx.getNodes())), true);
                    } else {
                        sp.sendSystemMessage(createProgressBar(ctx), true);
                    }
                }
            }
        }
    }

    private static Component createProgressBar(PathfindingContext ctx) {
        int percent = ctx.getProgress();
        int filled = percent / 10;
        String bar = "[" + "=".repeat(Math.max(0, filled - 1)) + ">"
                + " ".repeat(Math.max(0, 9 - filled)) + "]";
        return Component.translatable("simple_path_finder.nav.progress", bar, percent).append(String.format(" [ %d | %d ]", ctx.getNodes(), ctx.getCurrentProgress()));
    }

    public static boolean trySyncSingleForPlayer(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        var data = LevelNavDataSavedData.loadFromLevel(level);

        return data.levelNavData.getNavChunk(pos, false).map(iNavChunk -> {
            var worldMap = playerGotNav.computeIfAbsent(player.getUUID(), (_) -> new HashMap<>());
            var set = worldMap.computeIfAbsent(level.dimension().identifier(), _ -> new HashSet<>());
            if (set.add(pos)) {
                var packet = new SyncSingleChunkPacket(level.dimension().identifier(), pos, iNavChunk);
                PacketDistributor.sendToPlayer(player, packet);
                return true;
            }
            return false;
        }).orElse(false);

    }


    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        playerGotNav.clear();
        BatchScheduler.initializeCallbackExecutor(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppedEvent event) {
        playerGotNav.clear();
        BatchScheduler.resetCallbackExecutor();
    }

}
