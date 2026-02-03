package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.client.event.NavigationRenderTriggerEvent;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NavigationItem extends Item {
    // 物品模式组件类型 - 简化实现，暂时不使用
    // public static final DataComponentType<CustomData> NAV_MODE_COMPONENT =
    //     DataComponentType.<CustomData>builder().codec(CustomData.CODEC).build();

    public NavigationItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            // 如果是服务端，处理物品功能
            handleNavigationItem(level, (ServerPlayer) player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        // 获取当前模式 - 简化实现
        NavigationMode currentMode = NavigationMode.DEFAULT;

        // 添加当前模式提示
        tooltip.add(Component.translatable("tooltip.navigation.current_mode")
            .append(Component.translatable(currentMode.getTranslationKey()).withColor(modeToColor(currentMode))));

        // 添加切换模式提示
        tooltip.add(Component.translatable("tooltip.navigation.switch_mode")
            .withStyle(style -> style.withColor(0x7F7F7F)));
    }

    /**
     * 获取导航模式 - 简化实现
     */
    public static NavigationMode getNavigationMode(ItemStack stack) {
        return NavigationMode.DEFAULT;
    }

    /**
     * 设置导航模式 - 简化实现
     */
    public static void setNavigationMode(ItemStack stack, NavigationMode mode) {
        // 暂时不实现
    }

    /**
     * 切换导航模式 - 简化实现
     */
    public static void switchNavigationMode(ItemStack stack, boolean forward) {
        NavigationMode currentMode = getNavigationMode(stack);
        NavigationMode newMode = forward ? currentMode.next() : currentMode.previous();
        setNavigationMode(stack, newMode);
    }

    /**
     * 根据模式获取对应的颜色
     */
    private static int modeToColor(NavigationMode mode) {
        return switch (mode) {
            case DEFAULT -> 0x00FF00; // 绿色
            case ADD_NAV -> 0x00FFFF; // 青色
            case REMOVE_NAV -> 0xFF0000; // 红色
        };
    }

    private void handleNavigationItem(Level level, ServerPlayer player, ItemStack stack) {
        NavigationMode mode = getNavigationMode(stack);

        // 这里可以根据不同模式执行不同的逻辑
        // 目前暂时只记录日志
        System.out.println("Navigation item used in mode: " + mode.getTranslationKey());
    }
}