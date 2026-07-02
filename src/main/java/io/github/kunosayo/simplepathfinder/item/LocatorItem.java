package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
import io.github.kunosayo.simplepathfinder.nav.NavigationService;
import io.github.kunosayo.simplepathfinder.network.PathfindingRequestPacket;
import io.github.kunosayo.simplepathfinder.network.PlayerLocationPacket;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 定位器物品
 */
public class LocatorItem extends Item {
    public LocatorItem(Identifier id) {
        var key = ResourceKey.create(Registries.ITEM, id);
        super(new Properties().setId(key).stacksTo(1));
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        LocatorData data = getLocatorData(stack);


        if (data == null) {
            // 没有设置目标，按Shift写入玩家UUID
            if (player.isCrouching()) {
                if (!level.isClientSide()) {
                    LocatorData newData = LocatorData.forPlayer(player.getUUID());
                    stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
                    player.sendSystemMessage(Component.translatable("item.simple_path_finder.locator.bound.player",
                            player.getName()));
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            return super.use(level, player, hand);
        }

        // 有目标数据：触发导航
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                if (data.isPosBound()) {
                    // 客户端：检查是否有导航数据可用
                    var config = NavNotificationConfig.all();
                    if (SimplePathFinder.isServerSidePathfindingEnabled()) {
                        ClientPacketDistributor.sendToServer(new PathfindingRequestPacket(
                                data.getGlobalPos().pos(), "", config));
                    } else {
                        // 有导航数据，使用导航服务处理导航
                        NavigationService.navigate(data, config);
                    }
                }
            } else {
                if (data.isPlayerBound()) {
                    // 服务端：发送网络包告诉客户端目标位置
                    sendNavigationPacket((ServerLevel) level, (ServerPlayer) player, data);
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.use(level, player, hand);
    }

    @Override
    public @NonNull InteractionResult onItemUseFirst(@NonNull ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) {
            return super.onItemUseFirst(stack, context);
        }
        var level = context.getLevel();
        LocatorData data = getLocatorData(stack);

        if (data == null) {
            // 没有设置目标，按Shift写入玩家UUID
            if (player.isCrouching()) {
                if (!level.isClientSide()) {
                    LocatorData newData = LocatorData.forPlayer(player.getUUID());
                    stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
                    player.sendSystemMessage(Component.translatable("item.simple_path_finder.locator.bound.player",
                            player.getName()));
                }
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.onItemUseFirst(stack, context);
    }

    /**
     * 发送导航网络包到客户端
     *
     * @param level       服务端世界
     * @param player      目标玩家
     * @param locatorData 定位器数据
     */
    private void sendNavigationPacket(ServerLevel level, ServerPlayer player, LocatorData locatorData) {
        if (locatorData.isPlayerBound()) {
            // 绑定到玩家：检查玩家是否在线
            UUID targetUuid = locatorData.getPlayerUuid();
            var targetPlayer = level.getServer().getPlayerList().getPlayer(targetUuid);
            if (targetPlayer == null) {
                // 目标玩家不在线
                PacketDistributor.sendToPlayer(player, PlayerLocationPacket.offline());
            } else {
                // 目标玩家在线，发送位置
                BlockPos targetPos = targetPlayer.blockPosition();
                String playerName = targetPlayer.getName().getString();
                PacketDistributor.sendToPlayer(player, PlayerLocationPacket.online(targetPos, playerName));
            }
        } else if (locatorData.isPosBound()) {
            // 绑定到位置：检查维度后发送位置
            var globalPos = locatorData.getGlobalPos();
            ResourceKey<Level> targetDimension = globalPos.dimension();
            ResourceKey<Level> currentDimension = level.dimension();

            if (!targetDimension.equals(currentDimension)) {
                // 维度不匹配
                player.sendSystemMessage(Component.translatable("simple_path_finder.nav.wrong_dimension"));
                return;
            }

            BlockPos targetPos = globalPos.pos();
            PacketDistributor.sendToPlayer(player, PlayerLocationPacket.online(targetPos, ""));
        }
    }

    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        LocatorData data = getLocatorData(stack);
        if (data == null && !context.getLevel().isClientSide()) {
            var result = context.getClickedPos().relative(context.getClickedFace());
            stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), new LocatorData(GlobalPos.of(context.getLevel().dimension(), result)));
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(@NonNull ItemStack itemStack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, @NonNull Consumer<Component> builder, @NonNull TooltipFlag tooltipFlag) {
        LocatorData data = getLocatorData(itemStack);

        if (data == null) {
            builder.accept(Component.translatable("tooltip.locator.unbound")
                    .withStyle(style -> style.withColor(0xFFFF00)));
            builder.accept(Component.translatable("tooltip.locator.usage")
                    .withStyle(style -> style.withColor(0x7F7F7F)));
        } else if (data.isPlayerBound()) {
            builder.accept(Component.translatable("tooltip.locator.bound.player")
                    .withStyle(style -> style.withColor(0x00FF00)));
            builder.accept(Component.literal("UUID: " + data.getPlayerUuid().toString())
                    .withStyle(style -> style.withColor(0x7F7F7F)));
        } else {
            builder.accept(Component.translatable("tooltip.locator.bound.pos")
                    .withStyle(style -> style.withColor(0x00FFFF)));
            var pos = data.getGlobalPos();
            // 使用 dimension().toString() 获取维度字符串表示
            builder.accept(Component.literal("%s: %s".formatted(
                    pos.dimension(),
                    pos.pos()
            )).withStyle(style -> style.withColor(0x7F7F7F)));
        }

    }

    /**
     * 获取定位器数据
     */
    public static @Nullable LocatorData getLocatorData(ItemStack stack) {
        return stack.get(ModDataComponents.LOCATOR_COMPONENT.get());

    }

    public static void setPlayerUuid(ItemStack stack, UUID uuid) {
        LocatorData newData = LocatorData.forPlayer(uuid);
        stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
    }

    public static void setGlobalPos(ItemStack stack, GlobalPos pos) {
        LocatorData newData = LocatorData.forPosition(pos);
        stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
    }
}
