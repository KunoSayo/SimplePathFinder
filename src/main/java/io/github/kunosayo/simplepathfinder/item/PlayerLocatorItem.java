package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.data.PlayerLocatorData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 玩家定位器物品
 * 按住Shift + 右键点击时，会写入当前玩家的UUID
 */
public class PlayerLocatorItem extends Item {
    public PlayerLocatorItem(Identifier id) {
        var key = ResourceKey.create(Registries.ITEM, id);
        super(new Properties().setId(key).stacksTo(1));
    }


    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player.isShiftKeyDown()) {
            // 按住Shift + 右键，写入玩家UUID
            setPlayerUuid(stack, player.getUUID());
            player.sendSystemMessage(Component.translatable("item.simple_path_finder.player_locator.bound",
                    player.getName()));
        }

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        PlayerLocatorData data = getPlayerLocatorData(itemStack);

        if (data.hasPlayer()) {
            builder.accept(Component.translatable("tooltip.player_locator.bound")
                    .withStyle(style -> style.withColor(0x00FF00)));
            builder.accept(Component.literal("UUID: " + data.playerUuid().toString())
                    .withStyle(style -> style.withColor(0x7F7F7F)));
        } else {
            builder.accept(Component.translatable("tooltip.player_locator.unbound")
                    .withStyle(style -> style.withColor(0xFFFF00)));
        }

        builder.accept(Component.translatable("tooltip.player_locator.usage")
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
