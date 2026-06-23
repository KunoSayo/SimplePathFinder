package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavResult;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
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
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        LocatorData data = getLocatorData(stack);

        if (data == null) {
            // 没有设置目标，按Shift写入玩家UUID
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide()) {
                    LocatorData newData = LocatorData.forPlayer(player.getUUID());
                    stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
                    player.sendSystemMessage(Component.translatable("item.simple_path_finder.locator.bound.player",
                            player.getName()));
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        }

        // 有目标数据：触发导航
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                // 服务端：处理目标并发送网络包
                handleLocatorNavigation(level, (ServerPlayer) player, data);
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.use(level, player, hand);
    }

    /**
     * 处理定位器导航
     */
    private void handleLocatorNavigation(Level level, ServerPlayer player, LocatorData data) {
        if (data.isPlayerBound()) {
            // 绑定到玩家：检查玩家是否在线
            UUID targetUuid = data.getPlayerUuid();
            var targetPlayer = level.getServer().getPlayerList().getPlayer(targetUuid);
            if (targetPlayer == null) {
                // 目标玩家不在线
                PacketDistributor.sendToPlayer(player, io.github.kunosayo.simplepathfinder.network.PlayerLocationPacket.offline());
            } else {
                // 目标玩家在线，发送位置
                net.minecraft.core.BlockPos targetPos = targetPlayer.blockPosition();
                String playerName = targetPlayer.getName().getString();
                PacketDistributor.sendToPlayer(player, io.github.kunosayo.simplepathfinder.network.PlayerLocationPacket.online(targetPos, playerName));
            }
        } else if (data.isPosBound()) {
            // 绑定到位置：触发客户端导航
            net.minecraft.core.BlockPos targetPos = data.getGlobalPos().pos();
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav.starting", targetPos.getX(), targetPos.getY(), targetPos.getZ()));

            // 在客户端触发导航
            if (level instanceof ServerLevel serverLevel) {
                // 发送一个简单的网络包通知客户端开始导航
                // 这里我们使用 PlayerLocationPacket 的格式，但带有位置信息
                PacketDistributor.sendToPlayer(player, io.github.kunosayo.simplepathfinder.network.PlayerLocationPacket.online(targetPos, "Location"));
            }
        }
    }

    @Override
    public void appendHoverText(@NonNull ItemStack itemStack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, @NonNull Consumer<Component> builder, @NonNull TooltipFlag tooltipFlag) {
        LocatorData data = getLocatorData(itemStack);

        if (data == null) {
            builder.accept(Component.translatable("tooltip.locator.unbound")
                    .withStyle(style -> style.withColor(0xFFFF00)));
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

        builder.accept(Component.translatable("tooltip.locator.usage")
                .withStyle(style -> style.withColor(0x7F7F7F)));
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

    public static void setGlobalPos(ItemStack stack, net.minecraft.core.GlobalPos pos) {
        LocatorData newData = LocatorData.forPosition(pos);
        stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
    }
}
