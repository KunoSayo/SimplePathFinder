package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.data.PlayerLocatorData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 玩家定位器物品
 * 按住Shift + 右键点击时，会写入当前玩家的UUID
 */
public class PlayerLocatorItem extends Item {
    public PlayerLocatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player.isShiftKeyDown()) {
            // 按住Shift + 右键，写入玩家UUID
            setPlayerUuid(stack, player.getUUID());
            player.sendSystemMessage(Component.translatable("item.simple_path_finder.player_locator.bound",
                    player.getName()));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        PlayerLocatorData data = getPlayerLocatorData(stack);

        if (data.hasPlayer()) {
            tooltip.add(Component.translatable("tooltip.player_locator.bound")
                    .withStyle(style -> style.withColor(0x00FF00)));
            tooltip.add(Component.literal("UUID: " + data.playerUuid().toString())
                    .withStyle(style -> style.withColor(0x7F7F7F)));
        } else {
            tooltip.add(Component.translatable("tooltip.player_locator.unbound")
                    .withStyle(style -> style.withColor(0xFFFF00)));
        }

        tooltip.add(Component.translatable("tooltip.player_locator.usage")
                .withStyle(style -> style.withColor(0x7F7F7F)));
    }

    /**
     * 获取玩家定位器数据
     */
    public static PlayerLocatorData getPlayerLocatorData(ItemStack stack) {
        var c = stack.get(ModDataComponents.PLAYER_LOCATOR_COMPONENT.get());
        if (c != null) {
            return c;
        }
        return new PlayerLocatorData();
    }

    /**
     * 设置玩家UUID
     */
    public static void setPlayerUuid(ItemStack stack, UUID uuid) {
        PlayerLocatorData newData = new PlayerLocatorData(uuid);
        stack.set(ModDataComponents.PLAYER_LOCATOR_COMPONENT.get(), newData);
    }
}
