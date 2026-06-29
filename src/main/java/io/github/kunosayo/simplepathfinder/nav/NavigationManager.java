package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 导航管理器
 * 统一管理所有寻路任务，确保同一时间只有一个寻路任务在后台执行
 */
public class NavigationManager {

    /**
     * 是否有寻路任务正在执行
     */
    private static final AtomicBoolean isPathfinding = new AtomicBoolean(false);

    /**
     * 当前寻路任务的起始位置（用于任务取消判断）
     */
    private static volatile BlockPos currentTaskStartPos;

    /**
     * 当前寻路任务的目标位置（用于任务取消判断）
     */
    private static volatile BlockPos currentTaskTargetPos;

    /**
     * 请求执行导航
     * 如果当前有任务在执行，根据配置决定是否通知玩家
     *
     * @param startPos   起始位置
     * @param targetPos  目标位置
     * @param targetDesc 目标描述（如玩家名称）
     * @param config     通知配置
     * @return 是否成功提交任务
     */
    public static boolean requestNavigation(BlockPos startPos, BlockPos targetPos, String targetDesc, NavNotificationConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }

        // 尝试设置寻路状态
        if (!isPathfinding.compareAndSet(false, true)) {
            // 已有任务在执行
            if (config.notifyOnBusy()) {
                mc.player.sendSystemMessage(Component.translatable("simple_path_finder.nav.already_pathfinding"));
            }
            return false;
        }

        // 记录当前任务信息
        currentTaskStartPos = startPos;
        currentTaskTargetPos = targetPos;

        // 异步执行寻路
        executePathfindingAsync(startPos, targetPos, targetDesc, config);
        return true;
    }

    /**
     * 取消当前正在执行的寻路任务
     */
    public static void cancelCurrentTask() {
        isPathfinding.set(false);
        currentTaskStartPos = null;
        currentTaskTargetPos = null;
    }

    /**
     * 检查是否有寻路任务正在执行
     *
     * @return 是否有任务在执行
     */
    public static boolean isPathfinding() {
        return isPathfinding.get();
    }

    /**
     * 获取当前任务信息
     *
     * @return 当前任务信息字符串，如果没有任务则返回空字符串
     */
    public static String getCurrentTaskInfo() {
        if (!isPathfinding.get()) {
            return "";
        }

        BlockPos start = currentTaskStartPos;
        BlockPos target = currentTaskTargetPos;

        if (start == null || target == null) {
            return "";
        }

        return String.format("From (%d, %d, %d) to (%d, %d, %d)",
                start.getX(), start.getY(), start.getZ(),
                target.getX(), target.getY(), target.getZ());
    }

    /**
     * 异步执行寻路计算
     *
     * @param startPos   起始位置
     * @param targetPos  目标位置
     * @param targetDesc 目标描述
     * @param config     通知配置
     */
    private static void executePathfindingAsync(BlockPos startPos, BlockPos targetPos, String targetDesc, NavNotificationConfig config) {
        LevelNavData navData = ClientNavDataManager.getNavDataForPlayer();

        if (navData == null) {
            Minecraft mc = Minecraft.getInstance();
            mc.submitAsync(() -> {
                if (mc.player != null && config.notifyOnFailure()) {
                    mc.player.sendSystemMessage(Component.translatable("simple_path_finder.nav.no_data"));
                }
            });
            isPathfinding.set(false);
            return;
        }

        // 在后台线程执行寻路
        Util.backgroundExecutor().execute(() -> {
            try {
                navData.findNav(startPos, targetPos).ifPresentOrElse(
                        navResult -> {
                            // 寻路成功
                            SimplePathFinder.clientNavResult = navResult;
                            if (config.notifyOnSuccess()) {
                                Minecraft mc = Minecraft.getInstance();
                                mc.submitAsync(() -> {
                                    if (mc.player != null) {
                                        mc.player.sendSystemMessage(Component.translatable("simple_path_finder.nav.starting",
                                                targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                                        if (!targetDesc.isEmpty()) {
                                            mc.player.sendSystemMessage(Component.translatable("simple_path_finder.nav.to_player", targetDesc));
                                        }
                                    }
                                });

                            }
                        },
                        () -> {
                            // 寻路失败
                            if (config.notifyOnFailure()) {
                                Minecraft mc = Minecraft.getInstance();
                                mc.submitAsync(() -> {
                                    if (mc.player != null) {
                                        mc.player.sendSystemMessage(Component.translatable("simple_path_finder.nav.chunk_not_found"));
                                    }
                                });
                            }
                        }
                );
            } finally {
                // 任务完成，重置状态
                isPathfinding.set(false);
            }
        });
    }

    /**
     * 清除当前导航结果（但不影响正在执行的任务）
     */
    public static void clearNavigationResult() {
        SimplePathFinder.clientNavResult = null;
    }

    /**
     * 获取当前导航结果
     *
     * @return 当前导航结果，可能为null
     */
    public static NavResult getCurrentNavigationResult() {
        return SimplePathFinder.clientNavResult;
    }

    /**
     * 检查是否有活动的导航（有结果显示）
     *
     * @return 是否有活动的导航
     */
    public static boolean hasActiveNavigation() {
        return SimplePathFinder.clientNavResult != null;
    }
}
