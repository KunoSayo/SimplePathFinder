package io.github.kunosayo.simplepathfinder.client.listener;

import com.mojang.brigadier.tree.CommandNode;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.event.NavigationRenderTriggerEvent;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavResult;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT, modid = SimplePathFinder.MOD_ID)
public class ClientListener {
    private static LevelNavData getNavData(Player player) {
        if (player.level() instanceof ServerLevel sl) {
            return LevelNavDataSavedData.loadFromLevel(sl).levelNavData;
        }
        return SimplePathFinder.clientNavData;
    }

    private static void doNav(Player player, BlockPos target) {
        var data = getNavData(player);
        if (data == null) {
            return;
        }
        long startTime = System.currentTimeMillis();
        data.findNav(player.blockPosition(), target).ifPresent(navResult -> {
            SimplePathFinder.clientNavResult = navResult;
        });
        long endTime = System.currentTimeMillis();
        LogManager.getLogger().info("nav in " + (endTime - startTime) + "ms");
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        CommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("spf").then(
                Commands.literal("nav")
                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                .executes(context -> {
                                    var target = BlockPosArgument.getBlockPos(context, "target");
                                    if (context.getSource().source instanceof Player player) {
                                        doNav(player, target);
                                    }
                                    return 0;
                                }))
        ));

        dispatcher.register(Commands.literal("nav").redirect(root.getChild("nav")));
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null) {
            return;
        }

        // 检查玩家是否按住了Shift键
        if (!player.isShiftKeyDown()) {
            return;
        }

        // 检查玩家手中是否有导航物品
        var mainHandItem = player.getMainHandItem();
        if (mainHandItem.is(ModItems.NAVIGATION) || mainHandItem.is(ModItems.DEBUG_NAV)) {
            // 取消默认的滚动行为
            event.setCanceled(true);

            // 根据滚动方向切换模式
            double scrollDelta = event.getScrollDeltaY();
            boolean forward = scrollDelta > 0; // 向下滚动切换到下一个模式
            NavigationItem.switchNavigationMode(mainHandItem, forward);

            // 更新玩家物品栏
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent
    public static void onTick(RenderLevelStageEvent event) {
        var state = event.getStage();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (player.getMainHandItem().is(ModItems.DEBUG_NAV) || player.getMainHandItem().is(ModItems.NAVIGATION)) {
            var renderNavEvent = new NavigationRenderTriggerEvent(player);
            if (NeoForge.EVENT_BUS.post(renderNavEvent).isCanceled()) {
                return;
            }
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
                    NavResult clientNavResult = SimplePathFinder.clientNavResult;
                    if (amount == 64) {
                        if (clientNavResult != null) {
                            clientNavResult.render(event.getLevelRenderer(), player);
                        }
                        return;
                    }
                    if (amount == 63) {
                        if (clientNavResult != null) {
                            doNav(player, clientNavResult.getNavTarget());
                            clientNavResult.render(event.getLevelRenderer(), player);
                            return;
                        }
                    }
                    if (amount >= 62) {
                        if (clientNavResult != null) {
                            clientNavResult.render(event.getLevelRenderer(), player);
                        }
                    }
                    int layerRangeLeft;
                    int layerRangeRight = Integer.MAX_VALUE;
                    if (amount > 16 && amount <= 48) {
                        layerRangeLeft = layerRangeRight = amount - 32;
                    } else {
                        layerRangeLeft = Integer.MIN_VALUE;
                    }
                    amount = Math.min(Math.max(amount, 3), 16);
                    var currentChunkPos = new ChunkPos(player.blockPosition());

                    for (int offsetX = -amount; offsetX <= amount; offsetX++) {
                        for (int offsetZ = -amount; offsetZ <= amount; offsetZ++) {
                            final int dis = Math.abs(offsetX) + Math.abs(offsetZ);
                            if (dis < amount) {

                                var chunkPos = new ChunkPos(currentChunkPos.x + offsetX, currentChunkPos.z + offsetZ);
                                int finalLayerRangeRight = layerRangeRight;
                                data.getNavChunk(chunkPos, false)
                                        .ifPresent(navChunk -> {
                                            for (var layer : navChunk.layers) {
                                                if (layer.getLayer() > finalLayerRangeRight || layer.getLayer() < layerRangeLeft) {
                                                    continue;
                                                }
                                                for (int x = 0; x < 16; x++) {
                                                    for (int z = 0; z < 16; z++) {
                                                        int y = layer.getWalkY(x, z);

                                                        var blockPos = new BlockPos(chunkPos.getBlockX(x), y, chunkPos.getBlockZ(z));
                                                        if (layer.isWalkYValid(y)) {
                                                            if (layer.getDistance(x, z, false) < 0) {
                                                                lr.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.0f, 0.0f, 0.0f),
                                                                        true, blockPos.getX() + 1.0, blockPos.getY(), blockPos.getZ() + 0.5, 0.0, 0.0, 0.0);
                                                            }
                                                            if (layer.getDistance(x, z, true) < 0) {
                                                                lr.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.0f, 0.0f, 0.0f),
                                                                        true, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 1.0, 0.0, 0.0, 0.0);
                                                            }
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
