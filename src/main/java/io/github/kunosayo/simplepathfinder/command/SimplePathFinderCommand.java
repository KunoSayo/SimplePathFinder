package io.github.kunosayo.simplepathfinder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

public final class SimplePathFinderCommand {
    private static final IntegerArgumentType LAYER_ARG = IntegerArgumentType.integer(Byte.MIN_VALUE, Byte.MAX_VALUE);

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("spf")
                .then(Commands.literal("admin")
                        .requires(commandSourceStack -> commandSourceStack.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                        .then(Commands.literal("stats")
                                .executes(context -> {
                                    var src = context.getSource().getEntity();
                                    if (src instanceof Player player) {
                                        if (player.level() instanceof ServerLevel sl) {
                                            var data = LevelNavDataSavedData.loadFromLevel(sl);
                                            long total = data.levelNavData.getTotalLayers();
                                            long chunks = data.levelNavData.getTotalNavChunks();
                                            long bytes = data.levelNavData.getEncodedBytes();
                                            long compressed = data.levelNavData.getEncodedCompressedBytes();
                                            context.getSource().source.sendSystemMessage(Component.literal(String.format("[SPF][NavData] Chunks: %d, Layers: %d\nBytes: %d (Compressed %d)",
                                                    chunks, total, bytes, compressed)));
                                        }
                                    }

                                    return 1;
                                }))
                        .then(Commands.literal("nav")
                                .then(Commands.literal("remove")
                                        .then(Commands.literal("current").executes(context -> {
                                            if (context.getSource().getEntity() instanceof Player player) {
                                                var level = player.level();
                                                if (level instanceof ServerLevel sl) {
                                                    var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                    if (data.levelNavData.removeNavChunk(player)) {
                                                        data.setDirty();
                                                        player.sendSystemMessage(Component.translatable("simple_path_finder.remove.current.success"));
                                                        if (player instanceof ServerPlayer sp && !level.isClientSide()) {
                                                            SimplePathFinder.playerMadeServerNavDirty(sp);
                                                        }
                                                        return 1;
                                                    } else {
                                                        player.sendSystemMessage(Component.translatable("simple_path_finder.failed.not_found"));
                                                    }
                                                }
                                            }
                                            return 0;
                                        })))

                                .then(Commands.literal("build")
                                        .then(Commands.argument("layer", LAYER_ARG)
                                                .then(Commands.argument("dx", IntegerArgumentType.integer(0, 255))
                                                        .then(Commands.argument("dz", IntegerArgumentType.integer(0, 255))
                                                                .executes(context -> {
                                                                    byte layer = context.getArgument("layer", Integer.class).byteValue();
                                                                    int dx = context.getArgument("dx", Integer.class);
                                                                    int dz = context.getArgument("dz", Integer.class);

                                                                    if (context.getSource().getEntity() instanceof Player player) {
                                                                        var level = player.level();
                                                                        if (level instanceof ServerLevel sl) {
                                                                            var server = sl.getServer();
                                                                            var playerUUID = player.getUUID();
                                                                            var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                                            var cp = ChunkPos.containing(player.blockPosition());
                                                                            data.levelNavData.buildForPlayer(player, layer).whenCompleteAsync((_, th) -> {
                                                                                data.setDirty();
                                                                            }, sl.getServer());
                                                                            class Runner implements Runnable {
                                                                                int x = 0;
                                                                                int z = 0;
                                                                                boolean dirty = false;
                                                                                int total = 0;
                                                                                int chunkDirty = 0;

                                                                                void done() {
                                                                                    if (dirty) {
                                                                                        data.setDirty();
                                                                                    }
                                                                                    if (data.isDirty()) {
                                                                                        var player = server.getPlayerList().getPlayer(playerUUID);
                                                                                        if (player instanceof ServerPlayer sp && !level.isClientSide()) {
                                                                                            player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.batch_success", chunkDirty, total));
                                                                                            SimplePathFinder.playerMadeServerNavDirty(sp);
                                                                                        }
                                                                                    }
                                                                                }

                                                                                private boolean runOnce() {
                                                                                    ++z;
                                                                                    if (z > dz) {
                                                                                        z = 0;
                                                                                        ++x;
                                                                                        var player = server.getPlayerList().getPlayer(playerUUID);
                                                                                        if (player != null) {
                                                                                            player.sendSystemMessage(Component.literal("[SPF] Built " + (x - 1) + " / " + dx));
                                                                                        }
                                                                                    }
                                                                                    if (x > dx) {
                                                                                        done();
                                                                                        return true;
                                                                                    }
                                                                                    var acp = new ChunkPos(x + cp.x(), z + cp.z());
                                                                                    ++total;
                                                                                    if (data.levelNavData.buildFromLayerStart(level, data.levelNavData, layer, acp)) {
                                                                                        dirty = true;
                                                                                        ++chunkDirty;
                                                                                    }
                                                                                    return false;
                                                                                }

                                                                                @Override
                                                                                public void run() {
                                                                                    long start = System.currentTimeMillis();
                                                                                    while (!runOnce()) {
                                                                                        long now = System.currentTimeMillis();
                                                                                        if (now - start >= NavConfig.NAV_CONFIG.getLeft().msPerTick.get()) {
                                                                                            sl.getServer().submitAsync(this);
                                                                                            break;
                                                                                        }
                                                                                    }

                                                                                }
                                                                            }
                                                                            new Runner().run();
                                                                        }
                                                                    }

                                                                    return 0;
                                                                }))))
                                        .then(Commands.literal("current")
                                                .then(Commands.argument("layer", LAYER_ARG)
                                                        .executes(context -> {
                                                            byte layer = context.getArgument("layer", Integer.class).byteValue();
                                                            if (context.getSource().getEntity() instanceof Player player) {
                                                                var level = player.level();
                                                                if (level instanceof ServerLevel sl) {
                                                                    var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                                    data.levelNavData.buildForPlayer(player, layer).whenCompleteAsync((_, th) -> {
                                                                        data.setDirty();
                                                                        if (player instanceof ServerPlayer sp && !level.isClientSide()) {
                                                                            SimplePathFinder.playerMadeServerNavDirty(sp);
                                                                        }
                                                                    }, sl.getServer());
                                                                }
                                                            }
                                                            return 0;
                                                        }))
                                                .executes(context -> {

                                                    if (context.getSource().getEntity() instanceof Player player) {
                                                        var level = player.level();
                                                        if (level instanceof ServerLevel sl) {
                                                            var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                            data.levelNavData.buildForPlayer(player, (byte) 0).whenCompleteAsync((_, th) -> {
                                                                data.setDirty();
                                                                if (player instanceof ServerPlayer sp && !level.isClientSide()) {
                                                                    SimplePathFinder.playerMadeServerNavDirty(sp);
                                                                }
                                                            }, sl.getServer());
                                                        }
                                                    }
                                                    return 0;
                                                }))
                                ))));
        dispatcher.register(Commands.literal("simple_path_finder").redirect(root));
    }
}
