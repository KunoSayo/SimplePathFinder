package io.github.kunosayo.simplepathfinder.client.listener;

import com.mojang.brigadier.tree.CommandNode;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.client.event.NavigationRenderTriggerEvent;
import io.github.kunosayo.simplepathfinder.client.property.LocatorModelProperty;
import io.github.kunosayo.simplepathfinder.client.property.NavBrushModelProperty;
import io.github.kunosayo.simplepathfinder.client.property.NavigationModelProperty;
import io.github.kunosayo.simplepathfinder.client.rendering.NavRenderingSupport;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.finder.NavResult;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@EventBusSubscriber(value = Dist.CLIENT, modid = SimplePathFinder.MOD_ID)
public class ClientListener {
    private static final Logger LOGGER = LogManager.getLogger(ClientListener.class);

    /**
     * Initialize navigation data manager when connecting to a server.
     * Loads cached data if available.
     */
    @SubscribeEvent
    public static void onServerConnect(PlayerEvent.PlayerLoggedInEvent event) {

        ClientNavDataManager.onServerConnect();

    }

    /**
     * Clear navigation data when disconnecting from a server.
     */
    @SubscribeEvent
    public static void onServerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientNavDataManager.clear();
        SimplePathFinder.clientNavResult.set(null);
    }

    private static LevelNavData getNavData(Player player) {
        if (player.level() instanceof ServerLevel sl) {
            return LevelNavDataSavedData.loadFromLevel(sl).levelNavData;
        }
        return ClientNavDataManager.getNavDataForPlayer();
    }

    /**
     * Perform pathfinding asynchronously to avoid blocking the main thread.
     * The A* pathfinding algorithm is computationally expensive and runs on a background thread.
     */
    private static void doNav(Player player, BlockPos target) {
        var data = getNavData(player);
        if (data == null) {
            return;
        }
        BlockPos start = player.blockPosition();

        // Run pathfinding asynchronously on the background executor to avoid blocking the main thread
        Util.backgroundExecutor().execute(() -> data.findNav(start, target).ifPresent(navResult -> {
            SimplePathFinder.clientNavResult.set(navResult);
        }));
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        CommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("spf").then(
                Commands.literal("nav")
                        .then(Commands.literal("clear")
                                .executes(commandContext -> {
                                    SimplePathFinder.clientNavResult.set(null);
                                    return 0;
                                }))
                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                .executes(context -> {
                                    var target = BlockPosArgument.getBlockPos(context, "target");
                                    if (context.getSource().getEntity() instanceof Player player) {
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
        if (mainHandItem.is(ModItems.NAVIGATION)) {
            // 取消默认的滚动行为
            event.setCanceled(true);

            // 根据滚动方向切换模式
            double scrollDelta = event.getScrollDeltaY();
            boolean forward = scrollDelta < 0; // 向下滚动切换到下一个模式

            // 获取当前模式
            NavigationMode currentMode = NavigationItem.getNavigationMode(mainHandItem);
            NavigationMode newMode = forward ? currentMode.next() : currentMode.previous();

            // 使用同步方法更新模式和层设置
            byte currentLayer = NavigationItem.getNavigationLayer(mainHandItem);
            NavigationItem.setNavigationModeDataSync(mainHandItem, InteractionHand.MAIN_HAND, newMode, currentLayer);
        }
    }

    @SubscribeEvent
    public static void on(RenderFrameEvent.Pre event) {
        NavRenderingSupport.INSTANCE.prepare();
    }

    @SubscribeEvent
    public static void on(SubmitCustomGeometryEvent event) {
        NavRenderingSupport.INSTANCE.submit(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState);
    }

    @SubscribeEvent
    public static void onTick(RenderLevelStageEvent.AfterSky event) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var result = SimplePathFinder.clientNavResult.get();
        if (result != null) {
            var pos = result.getNavTarget();
            if (pos.distManhattan(player.blockPosition()) <= 1) {
                SimplePathFinder.clientNavResult.compareAndSet(result, null);
            }
        }
        if (!player.getMainHandItem().is(ModItems.DEBUG_NAV)) {
            return;
        }
        var renderNavEvent = new NavigationRenderTriggerEvent(player);
        if (NeoForge.EVENT_BUS.post(renderNavEvent).isCanceled()) {
            return;
        }
        var data = getNavData(player);
        if (data == null) {
            return;
        }
        int amount = player.getMainHandItem().getCount();
        if (amount == 64) {
            return;
        }
        if (amount == 63) {
            NavResult clientNavResult = SimplePathFinder.clientNavResult.get();
            if (clientNavResult != null) {
                doNav(player, clientNavResult.getNavTarget());
            }
        }
    }

    /**
     * Register navigation item model properties
     */
    @SubscribeEvent
    public static void onRegisterRangeSelectItemModelProperty(RegisterRangeSelectItemModelPropertyEvent event) {
        NavigationModelProperty.register(event);
        LocatorModelProperty.register(event);
    }

    /**
     * Register nav brush item model properties
     */
    @SubscribeEvent
    public static void onRegisterConditionalItemModelProperty(RegisterConditionalItemModelPropertyEvent event) {
        NavBrushModelProperty.register(event);
    }

}
