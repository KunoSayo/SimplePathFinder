package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.network.UpdateItemPropertiesPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class NavigationItem extends Item {
    public NavigationItem(Identifier id) {
        var key = ResourceKey.create(Registries.ITEM, id);
        super(new Properties().setId(key).stacksTo(1));
    }

    @Override
    public InteractionResult useOn(@NonNull UseOnContext context) {
        var player = context.getPlayer();
        if (player != null) {
            var hand = context.getHand();
            var level = player.level();
            ItemStack stack = player.getItemInHand(hand);
            var clickedPos = context.getClickedPos().relative(context.getClickedFace());

            if (!level.isClientSide()) {
                // 如果是服务端，处理物品功能
                if (handleNavigationItem(level, (ServerPlayer) player, stack, clickedPos)) {
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Only open GUI if player is holding Shift
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            // Open GUI on client
            io.github.kunosayo.simplepathfinder.client.gui.NavigationScreen.open(stack, hand);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(@NonNull ItemStack itemStack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> builder, @NonNull TooltipFlag tooltipFlag) {
        // 获取当前模式数据
        NavigationModeData modeData = getNavigationModeData(itemStack);
        NavigationMode currentMode = modeData != null ? modeData.mode() : NavigationMode.DEFAULT;
        byte layer = modeData != null ? modeData.layer() : 0;

        // 添加当前模式提示
        builder.accept(Component.translatable("tooltip.navigation.current_mode")
                .append(Component.translatable(currentMode.getTranslationKey()).withColor(modeToColor(currentMode))));

        // 添加层设置提示
        builder.accept(Component.translatable("tooltip.navigation.current_layer", layer)
                .withStyle(style -> style.withColor(0x7F7F7F)));

        // 添加切换模式提示
        builder.accept(Component.translatable("tooltip.navigation.switch_mode")
                .withStyle(style -> style.withColor(0x7F7F7F)));

        // 添加打开设置提示
        builder.accept(Component.translatable("tooltip.navigation.open_settings")
                .withStyle(style -> style.withColor(0x7F7F7F)));
    }


    /**
     * 获取导航模式数据
     */
    public static NavigationModeData getNavigationModeData(ItemStack stack) {
        return stack.get(ModDataComponents.NAV_MODE_COMPONENT.get());
    }

    /**
     * 获取导航模式
     */
    public static NavigationMode getNavigationMode(ItemStack stack) {
        var c = getNavigationModeData(stack);
        if (c != null) {
            return c.mode();
        }
        return NavigationMode.DEFAULT;
    }

    /**
     * 获取导航层设置
     */
    public static byte getNavigationLayer(ItemStack stack) {
        var c = getNavigationModeData(stack);
        if (c != null) {
            return c.layer();
        }
        return 0;
    }

    /**
     * 设置导航模式（保留当前层设置）
     */
    public static void setNavigationMode(ItemStack stack, NavigationMode mode) {
        NavigationModeData currentData = getNavigationModeData(stack);
        byte currentLayer = currentData != null ? currentData.layer() : 0;
        // 创建新的导航模式数据，保留层设置
        NavigationModeData newData = new NavigationModeData(mode, currentLayer);
        // 设置数据组件
        stack.set(ModDataComponents.NAV_MODE_COMPONENT.get(), newData);
    }

    /**
     * 设置导航层
     */
    public static void setNavigationLayer(ItemStack stack, byte layer) {
        NavigationModeData currentData = getNavigationModeData(stack);
        NavigationMode currentMode = currentData != null ? currentData.mode() : NavigationMode.DEFAULT;
        // 创建新的导航模式数据，保留模式设置
        NavigationModeData newData = new NavigationModeData(currentMode, layer);
        stack.set(ModDataComponents.NAV_MODE_COMPONENT.get(), newData);
    }

    /**
     * 切换导航模式
     */
    public static void switchNavigationMode(ItemStack stack, boolean forward) {
        NavigationMode currentMode = getNavigationMode(stack);
        NavigationMode newMode = forward ? currentMode.next() : currentMode.previous();
        setNavigationMode(stack, newMode);
    }

    /**
     * 切换导航层（仅在添加导航模式时有效）
     */
    public static void switchNavigationLayer(ItemStack stack, boolean forward) {
        NavigationMode currentMode = getNavigationMode(stack);
        if (currentMode == NavigationMode.ADD_NAV) {
            byte currentLayer = getNavigationLayer(stack);
            // 切换层范围: -128 到 127，但实际使用建议 0-9
            byte newLayer = (byte) (forward ? currentLayer + 1 : currentLayer - 1);
            setNavigationLayer(stack, newLayer);
        }
    }

    /**
     * 根据模式获取对应的颜色
     */
    private static int modeToColor(NavigationMode mode) {
        return switch (mode) {
            case DEFAULT -> 0x00FF00; // 绿色
            case ADD_NAV -> 0x00FFFF; // 青色
            case REMOVE_NAV -> 0xFF0000; // 红色
            case ADD_LINK -> 0xFF00FF; // 紫色
        };
    }

    /**
     * 检查玩家是否可以使用导航物品功能
     */
    private static boolean canUseNavigationItem(ServerPlayer player) {
        if (!NavConfig.NAV_CONFIG.getLeft().requireCreativeMode.get()) {
            return true;
        }
        return player.isCreative();
    }

    /**
     * 处理客户端的寻路触发
     */
    private void handleClientPathfinding(BlockPos targetPos) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            return;
        }

        // 获取导航数据
        LevelNavData navData = ClientNavDataManager.getNavDataForPlayer();
        if (navData == null) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav.no_data"));
            return;
        }

        // 异步执行寻路
        net.minecraft.util.Util.backgroundExecutor().execute(() -> {
            long startTime = System.currentTimeMillis();
            navData.findNav(player.blockPosition(), targetPos).ifPresent(navResult -> {
                SimplePathFinder.clientNavResult = navResult;
                mc.execute(() -> {
                    player.sendSystemMessage(Component.translatable("simple_path_finder.nav.success"));
                });
            });
            long endTime = System.currentTimeMillis();
            SimplePathFinder.LOGGER.info("Client pathfinding completed in {}ms", endTime - startTime);
        });
    }

    private boolean handleNavigationItem(Level level, ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        NavigationMode mode = getNavigationMode(stack);

        // 检查是否可以使用导航物品
        if (mode != NavigationMode.DEFAULT && !canUseNavigationItem(player)) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav.creative_required"));
            return false;
        }

        switch (mode) {
            case ADD_NAV -> {
                // 添加导航模式 - 在点击位置构建导航层
                return handleAddNav((ServerLevel) level, player, stack, clickedPos);
            }
            case REMOVE_NAV -> {
                // 移除导航模式 - 移除点击位置的导航层
                return handleRemoveNav((ServerLevel) level, player, clickedPos);
            }
            case ADD_LINK -> {
                // 添加导航链接模式 - 在两个位置之间创建导航链接
                return handleAddLink((ServerLevel) level, player, stack, clickedPos);
            }
            default -> {
                // 默认模式不做任何服务端处理
                return false;
            }
        }
    }

    /**
     * 处理添加导航逻辑
     */
    private boolean handleAddNav(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        byte layer = getNavigationLayer(stack);

        var data = LevelNavDataSavedData.loadFromLevel(level);
        var chunkPos = ChunkPos.containing(clickedPos);

        // 检查是否达到最大层数限制
        var maxLayers = io.github.kunosayo.simplepathfinder.config.NavConfig.NAV_CONFIG.getLeft().maxLayers.get();


        data.levelNavData.getNavChunk(chunkPos, true).ifPresentOrElse(navChunk -> {

            // 获取或创建导航层
            navChunk.getLayer(layer, () -> (io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk)
                    io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk.getDefault()).ifPresentOrElse(layeredNavChunk -> {
                if (layeredNavChunk instanceof io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk chunk) {
                    chunk.setParentChunk(navChunk);
                    chunk.setLayer(layer);
                    chunk.parse(level, clickedPos);
                    player.sendSystemMessage(Component.translatable("simple_path_finder.nav.layer_created", layer, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));

                    // 标记导航数据需要同步
                    SimplePathFinder.playerMadeServerNavDirty(player);
                }
            }, () -> player.sendSystemMessage(Component.translatable("simple_path_finder.nav.layer_limit", maxLayers - 1)));
        }, () -> player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.limited")));

        return true;
    }

    /**
     * 处理移除导航逻辑
     */
    private boolean handleRemoveNav(ServerLevel level, ServerPlayer player, BlockPos clickedPos) {
        var chunkPos = ChunkPos.containing(clickedPos);
        var data = LevelNavDataSavedData.loadFromLevel(level);

        // 获取该chunk的所有层
        data.levelNavData.getNavChunk(chunkPos, false).ifPresentOrElse(navChunk -> {
            // 获取点击位置对应的层 - 查找最接近点击位置的层
            var chunkInnerPos = new io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos(
                    clickedPos.getX() & 15, clickedPos.getZ() & 15);

            navChunk.getLayers()
                    .filter(layer -> {
                        // 检查该位置是否可以行走
                        int walkY = layer.getWalkY(chunkInnerPos.x, chunkInnerPos.z);
                        // 如果该位置可以行走且点击位置接近该层的Y值
                        return walkY != io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk.INVALID_WALK_Y
                                && Math.abs(clickedPos.getY() - walkY) <= 3;
                    })
                    .findFirst()
                    .ifPresentOrElse(layeredNavChunk -> {
                        byte layer = layeredNavChunk.getLayer();
                        navChunk.removeNavChunk(layeredNavChunk);
                        player.sendSystemMessage(Component.translatable("simple_path_finder.nav.layer_removed", layer));

                        // 标记导航数据需要同步
                        SimplePathFinder.playerMadeServerNavDirty(player);
                    }, () -> {
                        player.sendSystemMessage(Component.translatable("simple_path_finder.nav.no_layer_at_pos"));
                    });
        }, () -> {
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav.chunk_not_found"));
        });

        return true;
    }

    /**
     * 处理添加导航链接逻辑
     */
    private boolean handleAddLink(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        var linkData = getLinkCreationData(stack);

        if (linkData == null) {
            // 第一步：记录起始位置
            setLinkCreationData(stack, io.github.kunosayo.simplepathfinder.data.LinkCreationData.of(level, clickedPos));
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav.link.start_pos_set",
                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
            return true;
        } else {
            // 第二步：创建从起始位置到当前位置的链接
            var startPos = linkData.startPos();
            var startChunkPos = ChunkPos.containing(startPos.pos());
            var data = LevelNavDataSavedData.loadFromLevel(level);

            // 获取起始位置的导航区块
            var navChunkOpt = data.levelNavData.getNavChunk(startChunkPos, false);
            if (navChunkOpt.isEmpty()) {
                player.sendSystemMessage(Component.translatable("simple_path_finder.nav.link.no_start_nav"));
                clearLinkCreationData(stack);
                return false;
            }

            var navChunk = navChunkOpt.get();
            var chunkInnerPos = new io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos(startPos.pos());

            // 创建链接
            var destPos = GlobalPos.of(level.dimension(), clickedPos);
            var link = io.github.kunosayo.simplepathfinder.nav.NavLink.normal(destPos);

            // 添加链接到导航区块
            navChunk.addNavLink(chunkInnerPos, link);

            player.sendSystemMessage(Component.translatable("simple_path_finder.nav.link.created",
                    startPos.pos().getX(), startPos.pos().getY(), startPos.pos().getZ(),
                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));

            // 清除起始位置数据
            clearLinkCreationData(stack);

            // 标记导航数据需要同步
            SimplePathFinder.playerMadeServerNavDirty(player);

            return true;
        }
    }

    /**
     * 获取链接创建数据
     */
    public static io.github.kunosayo.simplepathfinder.data.LinkCreationData getLinkCreationData(ItemStack stack) {
        return stack.get(ModDataComponents.LINK_CREATION_COMPONENT.get());
    }

    /**
     * 设置链接创建数据
     */
    public static void setLinkCreationData(ItemStack stack, io.github.kunosayo.simplepathfinder.data.LinkCreationData data) {
        stack.set(ModDataComponents.LINK_CREATION_COMPONENT.get(), data);
    }

    /**
     * 清除链接创建数据
     */
    public static void clearLinkCreationData(ItemStack stack) {
        stack.remove(ModDataComponents.LINK_CREATION_COMPONENT.get());
    }

    /**
     * Set navigation mode data and sync with server.
     * This method updates the local item data and sends a packet to the server.
     *
     * @param stack The item stack to update
     * @param hand  The hand holding the item
     * @param mode  The new navigation mode
     * @param layer The new navigation layer
     */
    public static void setNavigationModeDataSync(ItemStack stack, InteractionHand hand, NavigationMode mode, byte layer) {
        // Update local item data
        setNavigationMode(stack, mode);
        setNavigationLayer(stack, layer);

        // Send packet to server
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            var packet = new UpdateItemPropertiesPacket(hand, new NavigationModeData(mode, layer));
            net.minecraft.client.Minecraft.getInstance().getConnection().send(packet);
        }
    }

    /**
     * Set navigation mode and sync with server.
     *
     * @param stack The item stack to update
     * @param hand  The hand holding the item
     * @param mode  The new navigation mode
     */
    public static void setNavigationModeSync(ItemStack stack, InteractionHand hand, NavigationMode mode) {
        byte currentLayer = getNavigationLayer(stack);
        setNavigationModeDataSync(stack, hand, mode, currentLayer);
    }

    /**
     * Set navigation layer and sync with server.
     *
     * @param stack The item stack to update
     * @param hand  The hand holding the item
     * @param layer The new navigation layer
     */
    public static void setNavigationLayerSync(ItemStack stack, InteractionHand hand, byte layer) {
        NavigationMode currentMode = getNavigationMode(stack);
        setNavigationModeDataSync(stack, hand, currentMode, layer);
    }
}