package io.github.kunosayo.simplepathfinder.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.nav.layered.BatchScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class SimplePathFinderCommand {
    private static final IntegerArgumentType LAYER_ARG = IntegerArgumentType.integer(Byte.MIN_VALUE, Byte.MAX_VALUE);

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("spf")
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

                        .then(Commands.literal("config")
                                .then(Commands.literal("maxConcurrentTasks")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                                                .executes(SimplePathFinderCommand::adjustMaxConcurrentTasks))))

                        .then(Commands.literal("stats")
                                .executes(SimplePathFinderCommand::requestStats))

                        .then(Commands.literal("nav")
                                .then(Commands.literal("remove")
                                        .then(Commands.literal("current")
                                                .executes(SimplePathFinderCommand::removeNavigation)))

                                .then(Commands.literal("build")
                                        .then(Commands.argument("layer", LAYER_ARG)
                                                .then(Commands.argument("dx", IntegerArgumentType.integer(0, 255))
                                                        .then(Commands.argument("dz", IntegerArgumentType.integer(0, 255))
                                                                .executes(SimplePathFinderCommand::buildAtLayerBatched))))

                                        .then(Commands.literal("current")
                                                .executes(SimplePathFinderCommand::buildAtZero))

                                        .then(Commands.argument("layer", LAYER_ARG)
                                                .executes(SimplePathFinderCommand::buildAtLayer))
                                ))));
        dispatcher.register(Commands.literal("simple_path_finder").redirect(root));
    }

    private static void trap(ServerLevel level) {
        if (level.isClientSide()) {
            SimplePathFinder.LOGGER.warn("TRAP: Didn't expect ServerLevel#isClientSide() to return true");
        }
    }

    private static int requestStats(CommandContext<CommandSourceStack> context) {
        var src = context.getSource().getEntity();
        if (src instanceof Player player && player.level() instanceof ServerLevel sl) {
            var data = LevelNavDataSavedData.loadFromLevel(sl);
            long total = data.levelNavData.getTotalLayers();
            long chunks = data.levelNavData.getTotalNavChunks();
            long bytes = data.levelNavData.getEncodedBytes();
            long compressed = data.levelNavData.getEncodedCompressedBytes();
            context.getSource().source.sendSystemMessage(Component.literal(String.format("[SPF][NavData] Chunks: %d, Layers: %d\nBytes: %d (Compressed %d)",
                    chunks, total, bytes, compressed)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeNavigation(CommandContext<CommandSourceStack> context) {
        final var player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        var level = player.level();
        var data = LevelNavDataSavedData.loadFromLevel(level);
        if (!data.levelNavData.removeNavChunk(player)) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.failed.not_found"));
            return 0;
        }
        data.setDirty();
        player.sendSystemMessage(Component.translatable("simple_path_finder.remove.current.success"));
        trap(level);
        SimplePathFinder.playerMadeServerNavDirty(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int buildAtZero(CommandContext<CommandSourceStack> context) {
        final var player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        buildAtLayer(player, (byte) 0);
        return Command.SINGLE_SUCCESS;
    }

    private static int buildAtLayer(CommandContext<CommandSourceStack> context) {
        byte layer = (byte) IntegerArgumentType.getInteger(context, "layer");
        final var player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        buildAtLayer(player, layer);
        return Command.SINGLE_SUCCESS;
    }

    private static void buildAtLayer(ServerPlayer player, byte layer) {
        var level = player.level();
        var data = LevelNavDataSavedData.loadFromLevel(level);
        data.levelNavData.buildForPlayer(player, layer);
        data.setDirty();
        trap(level);
        SimplePathFinder.playerMadeServerNavDirty(player);
    }

    private static int buildAtLayerBatched(CommandContext<CommandSourceStack> context) {
        byte layer = (byte) IntegerArgumentType.getInteger(context, "layer");
        int dx = IntegerArgumentType.getInteger(context, "dx");
        int dz = IntegerArgumentType.getInteger(context, "dz");

        final var player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final var sl = player.level();
        var playerUUID = player.getUUID();
        var data = LevelNavDataSavedData.loadFromLevel(sl);
        int chunkX = SectionPos.blockToSectionCoord(player.getX());
        int chunkZ = SectionPos.blockToSectionCoord(player.getZ());
        data.levelNavData.buildForPlayer(player, layer);
        data.setDirty();
        new BatchScheduler(data, sl, playerUUID, layer, chunkX, chunkZ, dx, dz).fire();
        return Command.SINGLE_SUCCESS;
    }

    private static int adjustMaxConcurrentTasks(CommandContext<CommandSourceStack> context) {
        int concurrency = IntegerArgumentType.getInteger(context, "value");
        NavConfig.NAV_CONFIG.getLeft().maxConcurrentTasks.set(concurrency);
        return Command.SINGLE_SUCCESS;
    }
}
