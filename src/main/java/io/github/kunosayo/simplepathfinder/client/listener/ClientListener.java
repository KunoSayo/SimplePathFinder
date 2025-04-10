package io.github.kunosayo.simplepathfinder.client.listener;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.nav.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT, modid = SimplePathFinder.MOD_ID)
public class ClientListener {
    private static LevelNavData getNavData(Player player) {
        if (player.level() instanceof ServerLevel sl) {
            return LevelNavDataSavedData.loadFromLevel(sl).levelNavData;
        }
        return SimplePathFinder.clientNavData;
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("spf").then(
                Commands.literal("nav")
                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                .executes(context -> {
                                    var target = BlockPosArgument.getBlockPos(context, "target");
                                    if (context.getSource().source instanceof Player player) {
                                        var data = getNavData(player);
                                        if (data == null) {
                                            return 0;
                                        }
                                        data.findNav(player.blockPosition(), target).ifPresent(navResult -> {
                                            SimplePathFinder.clientNavResult = navResult;
                                        });
                                    }
                                    return 0;
                                }))
        ));
    }


    @SubscribeEvent
    public static void onTick(RenderLevelStageEvent event) {
        var state = event.getStage();
        var player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().is(ModItems.DEBUG_NAV)) {
            if (state == RenderLevelStageEvent.Stage.AFTER_SKY) {
                var level = player.level();
                LevelNavData data;
                if (level instanceof ServerLevel sl) {
                    data = LevelNavDataSavedData.loadFromLevel(sl).levelNavData;
                } else {
                    data = SimplePathFinder.clientNavData;
                }
                var lr = event.getLevelRenderer();
                if (data != null) {
                    int amount = player.getMainHandItem().getCount();
                    if (amount == 64) {
                        if (SimplePathFinder.clientNavResult != null) {
                            SimplePathFinder.clientNavResult.render(event.getLevelRenderer(), player);
                        }
                        return;
                    }
                    amount = Math.min(amount, 16);
                    var currentChunkPos = new ChunkPos(player.blockPosition());

                    for (int offsetX = -amount; offsetX <= amount; offsetX++) {
                        for (int offsetZ = -amount; offsetZ <= amount; offsetZ++) {
                            final int dis = Math.abs(offsetX) + Math.abs(offsetZ);
                            if (dis < amount) {

                                var chunkPos = new ChunkPos(currentChunkPos.x + offsetX, currentChunkPos.z + offsetZ);
                                data.getNavChunk(chunkPos, false)
                                        .ifPresent(navChunk -> {
                                            for (LayeredNavChunk layer : navChunk.layers) {
                                                for (int x = 0; x < 16; x++) {
                                                    for (int z = 0; z < 16; z++) {
                                                        int y = layer.getWalkY(x, z);
                                                        var blockPos = new BlockPos(chunkPos.getBlockX(x), y, chunkPos.getBlockZ(z));
                                                        if (LayeredNavChunk.isWalkYValid(y)) {
                                                            if (layer.getLayer() >= 0) {
                                                                lr.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.125f + layer.getLayer() * 0.125f, 1.0f - layer.getLayer() * 0.125f, 0.0f),
                                                                        true, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, 0.0, 0.0, 0.0);
                                                            } else {
                                                                lr.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.125f - layer.getLayer() * 0.125f, 0.0f, 1.0f + layer.getLayer() * 0.125f),
                                                                        true, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, 0.0, 0.0, 0.0);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        });
                            }
                        }
                    }

                }

            }
        }

    }

}
