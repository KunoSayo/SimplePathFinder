package io.github.kunosayo.simplepathfinder.nav;

/**
 * 导航通知配置
 * 用于控制导航操作时是否向玩家发送提示消息
 */
public record NavNotificationConfig(
        /**
         * 寻路成功时是否提示玩家
         */
        boolean notifyOnSuccess,
        /**
         * 寻路失败时是否提示玩家
         */
        boolean notifyOnFailure,
        /**
         * 当已有寻路任务进行中时是否提示玩家
         */
        boolean notifyOnBusy
) {
    /**
     * 创建一个启用所有通知的配置
     */
    public static NavNotificationConfig all() {
        return new NavNotificationConfig(true, true, true);
    }

    /**
     * 创建一个禁用所有通知的配置
     */
    public static NavNotificationConfig none() {
        return new NavNotificationConfig(false, false, false);
    }

    /**
     * 创建仅成功通知的配置
     */
    public static NavNotificationConfig successOnly() {
        return new NavNotificationConfig(true, false, false);
    }

    /**
     * 创建仅失败通知的配置
     */
    public static NavNotificationConfig failureOnly() {
        return new NavNotificationConfig(false, true, false);
    }

    /**
     * 创建仅忙时通知的配置
     */
    public static NavNotificationConfig busyOnly() {
        return new NavNotificationConfig(false, false, true);
    }

    /**
     * 兼容旧版本的两参数构造函数
     */
    public NavNotificationConfig(boolean notifyOnSuccess, boolean notifyOnFailure) {
        this(notifyOnSuccess, notifyOnFailure, false);
    }
}
