package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.client.event.NavigationRenderTriggerEvent;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NavigationItem extends Item {
    public NavigationItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            // 如果是服务端，处理物品功能
            handleNavigationItem(level, (ServerPlayer) player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
    }

    private void handleNavigationItem(Level level, ServerPlayer player) {

    }
}