package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.data.LocatorData;
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

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                // 按住Shift + 右键，写入玩家UUID
                LocatorData newData = LocatorData.forPlayer(player.getUUID());
                stack.set(ModDataComponents.LOCATOR_COMPONENT.get(), newData);
                player.sendSystemMessage(Component.translatable("item.simple_path_finder.locator.bound.player",
                        player.getName()));
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.use(level, player, hand);
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
