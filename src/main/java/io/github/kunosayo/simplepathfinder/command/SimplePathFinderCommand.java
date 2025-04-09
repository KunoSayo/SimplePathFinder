package io.github.kunosayo.simplepathfinder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.network.SyncLevelNavDataPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SimplePathFinderCommand {


    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("spf")
                .then(Commands.literal("admin")
                        .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                        .then(Commands.literal("nav")
                                .then(Commands.literal("build")
                                        .then(Commands.literal("current")
                                                .then(Commands.argument("layer", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            int layer = context.getArgument("layer", Integer.class);
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
                                                            if (data.levelNavData.buildForPlayer(player, 0)) {
                                                                data.setDirty();
                                                                if (player instanceof ServerPlayer sp && !level.isClientSide) {
                                                                    PacketDistributor.sendToPlayer(sp, new SyncLevelNavDataPacket(data.levelNavData));
                                                                }
                                                            }
                                                        }
                                                    }
                                                    return 0;
                                                }))

                                        .then(Commands.literal("c")).executes(context -> {
                                            return 0;
                                        })
                                )))
        );
    }
}
