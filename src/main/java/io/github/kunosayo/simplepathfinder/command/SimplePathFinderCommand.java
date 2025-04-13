package io.github.kunosayo.simplepathfinder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.network.SyncLevelNavDataPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SimplePathFinderCommand {
    private static final IntegerArgumentType LAYER_ARG = IntegerArgumentType.integer(Byte.MIN_VALUE, Byte.MAX_VALUE);

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("spf")
                .then(Commands.literal("admin")
                        .requires(commandSourceStack -> commandSourceStack.hasPermission(2))

                        .then(Commands.literal("stats")
                                .executes(context -> {
                                    var src = context.getSource().source;
                                    if (src instanceof Player player) {
                                        if (player.level() instanceof ServerLevel sl) {
                                            var data = LevelNavDataSavedData.loadFromLevel(sl);
                                            long total = data.levelNavData.getTotalLayers();
                                            long chunks = data.levelNavData.getTotalNavChunks();
                                            long bytes = data.levelNavData.getEncodedBytes();
                                            src.sendSystemMessage(Component.literal(String.format("[SPF][NavData] Chunks: %d, Layers: %d\nBytes: %d", chunks, total, bytes)));
                                        }
                                    }

                                    return 1;
                                }))
                        .then(Commands.literal("nav")
                                .then(Commands.literal("remove")
                                        .then(Commands.literal("current").executes(context -> {
                                            if (context.getSource().source instanceof Player player) {
                                                var level = player.level();
                                                if (level instanceof ServerLevel sl) {
                                                    var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                    if (data.levelNavData.removeNavChunk(player)) {
                                                        data.setDirty();
                                                        player.sendSystemMessage(Component.translatable("simple_path_finder.remove.current.success"));
                                                        if (player instanceof ServerPlayer sp && !level.isClientSide) {
                                                            PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(data.levelNavData));
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
                                                .then(Commands.argument("dx", IntegerArgumentType.integer(0, 15))
                                                        .then(Commands.argument("dz", IntegerArgumentType.integer(0, 15))
                                                                .executes(context -> {
                                                                    byte layer = context.getArgument("layer", Integer.class).byteValue();
                                                                    int dx = context.getArgument("dx", Integer.class);
                                                                    int dz = context.getArgument("dz", Integer.class);

                                                                    if (context.getSource().source instanceof Player player) {
                                                                        var level = player.level();
                                                                        if (level instanceof ServerLevel sl) {
                                                                            var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                                            var cp = new ChunkPos(player.blockPosition());
                                                                            if (data.levelNavData.buildForPlayer(player, layer)) {
                                                                                data.setDirty();

                                                                            }
                                                                            for (int x = 0; x <= dx; x++) {
                                                                                for (int z = 0; z <= dz; z++) {
                                                                                    if (x == 0 && z == 0) {
                                                                                        continue;
                                                                                    }
                                                                                    var acp = new ChunkPos(x + cp.x, z + cp.z);
                                                                                    if (data.levelNavData.buildFromLayerStart(level, data.levelNavData, layer, acp)) {
                                                                                        data.setDirty();
                                                                                    }
                                                                                }
                                                                            }
                                                                            if (data.isDirty()) {
                                                                                if (player instanceof ServerPlayer sp && !level.isClientSide) {
                                                                                    PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(data.levelNavData));
                                                                                }
                                                                            }
                                                                        }
                                                                    }

                                                                    return 0;
                                                                }))))
                                        .then(Commands.literal("current")
                                                .then(Commands.argument("layer", LAYER_ARG)
                                                        .executes(context -> {
                                                            byte layer = context.getArgument("layer", Integer.class).byteValue();
                                                            if (context.getSource().source instanceof Player player) {
                                                                var level = player.level();
                                                                if (level instanceof ServerLevel sl) {
                                                                    var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                                    if (data.levelNavData.buildForPlayer(player, layer)) {
                                                                        data.setDirty();
                                                                        if (player instanceof ServerPlayer sp && !level.isClientSide) {
                                                                            PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(data.levelNavData));
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            return 0;
                                                        }))
                                                .executes(context -> {

                                                    if (context.getSource().source instanceof Player player) {
                                                        var level = player.level();
                                                        if (level instanceof ServerLevel sl) {
                                                            var data = LevelNavDataSavedData.loadFromLevel(sl);
                                                            if (data.levelNavData.buildForPlayer(player, (byte) 0)) {
                                                                data.setDirty();
                                                                if (player instanceof ServerPlayer sp && !level.isClientSide) {
                                                                    PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(data.levelNavData));
                                                                }
                                                            }
                                                        }
                                                    }
                                                    return 0;
                                                }))
                                ))));
        dispatcher.register(Commands.literal("simple_path_finder").redirect(root));
    }
}
