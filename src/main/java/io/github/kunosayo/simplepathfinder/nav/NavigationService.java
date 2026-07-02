package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.network.PathfindingRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 客户端导航服务类
 * 统一处理客户端的所有导航相关操作
 */
public class NavigationService {

    /**
     * 执行导航到指定位置
     *
     * @param targetPos  目标位置
     * @param config     通知配置
     */
    public static void navigateToPosition(BlockPos targetPos, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 检查是否启用服务端寻路
        if (SimplePathFinder.isServerSidePathfindingEnabled()) {
            // 发送寻路请求到服务端
            ClientPacketDistributor.sendToServer(new PathfindingRequestPacket(targetPos, "", config));
        } else {
            // 客户端寻路
            BlockPos playerPos = mc.player.blockPosition();
            NavigationManager.requestNavigation(playerPos, targetPos, "", config);
        }
    }

    /**
     * 执行导航到指定位置（带描述）
     *
     * @param targetPos  目标位置
     * @param targetDesc 目标描述（如玩家名称）
     * @param config     通知配置
     */
    public static void navigateToPosition(BlockPos targetPos, String targetDesc, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 检查是否启用服务端寻路
        if (SimplePathFinder.isServerSidePathfindingEnabled()) {
            // 发送寻路请求到服务端
            ClientPacketDistributor.sendToServer(new PathfindingRequestPacket(targetPos, targetDesc, config));
        } else {
            // 客户端寻路
            BlockPos playerPos = mc.player.blockPosition();
            NavigationManager.requestNavigation(playerPos, targetPos, targetDesc, config);
        }
    }

    /**
     * 执行导航到指定玩家
     *
     * @param playerName 玩家名称
     * @param config     通知配置
     */
    public static void navigateToPlayer(String playerName, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 查找目标玩家
        Player targetPlayer = mc.level.players().stream()
                .filter(p -> p.getName().getString().equals(playerName))
                .findFirst()
                .orElse(null);

        if (targetPlayer == null) {
            if (config.notifyOnFailure()) {
                mc.player.sendSystemMessage(Component.translatable("simple_path_finder.locator.player_offline"));
            }
            return;
        }

        BlockPos targetPos = targetPlayer.blockPosition();
        navigateToPosition(targetPos, playerName, config);
    }

    /**
     * 执行导航（使用定位器数据）
     *
     * @param locatorData 定位器数据
     * @param config       通知配置
     */
    public static void navigate(LocatorData locatorData, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (locatorData.isPlayerBound()) {
            // 绑定到玩家：在客户端查找玩家
            navigateToPlayerByUuid(locatorData.getPlayerUuid(), config);
        } else if (locatorData.isPosBound()) {
            // 绑定到位置：检查维度后导航
            navigateToGlobalPosition(locatorData.getGlobalPos(), config);
        } else {
            if (config.notifyOnFailure()) {
                mc.player.sendSystemMessage(Component.translatable("simple_path_finder.locator.no_target"));
            }
        }
    }

    /**
     * 通过UUID导航到玩家
     *
     * @param targetUuid 目标玩家UUID
     * @param config     通知配置
     */
    private static void navigateToPlayerByUuid(java.util.UUID targetUuid, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 查找目标玩家
        Player targetPlayer = mc.level.players().stream()
                .filter(p -> p.getUUID().equals(targetUuid))
                .findFirst()
                .orElse(null);

        if (targetPlayer == null) {
            if (config.notifyOnFailure()) {
                mc.player.sendSystemMessage(Component.translatable("simple_path_finder.locator.player_offline"));
            }
            return;
        }

        BlockPos targetPos = targetPlayer.blockPosition();
        String playerName = targetPlayer.getName().getString();
        navigateToPosition(targetPos, playerName, config);
    }

    /**
     * 导航到全局位置（包含维度检查）
     *
     * @param globalPos 全局位置
     * @param config    通知配置
     */
    private static void navigateToGlobalPosition(net.minecraft.core.GlobalPos globalPos, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        ResourceKey<Level> targetDimension = globalPos.dimension();
        ResourceKey<Level> currentDimension = mc.player.level().dimension();

        if (!targetDimension.equals(currentDimension)) {
            if (config.notifyOnFailure()) {
                mc.player.sendSystemMessage(Component.translatable("simple_path_finder.nav.wrong_dimension"));
            }
            return;
        }

        BlockPos targetPos = globalPos.pos();
        navigateToPosition(targetPos, config);
    }

}
