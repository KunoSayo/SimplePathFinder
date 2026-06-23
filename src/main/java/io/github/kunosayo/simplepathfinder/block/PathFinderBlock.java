package io.github.kunosayo.simplepathfinder.block;

import com.mojang.serialization.MapCodec;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.block.entity.PathFinderBlockEntity;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.item.LocatorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathFinderBlock extends BaseEntityBlock {

    public static final MapCodec<PathFinderBlock> CODEC = Block.simpleCodec(PathFinderBlock::new);

    public PathFinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PathFinderBlockEntity(pos, state);
    }

    @Override
    @Deprecated
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查是否手持定位器
        if (stack.getItem() instanceof LocatorItem) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PathFinderBlockEntity be) {
                // 读取定位器数据
                LocatorData locatorData = LocatorItem.getLocatorData(stack);

                // 将定位器数据写入方块实体
                // 注意：LocatorData 现在总是包含一个有效的 target（Either.left 或 Either.right）
                be.setLocatorData(locatorData);

                // 发送确认消息
                if (locatorData.isPlayerBound()) {
                    player.sendSystemMessage(Component.translatable(
                            "block.simple_path_finder.path_finder_block.wrote.player"));
                } else if (locatorData.isPosBound()) {
                    var posData = locatorData.getGlobalPos();
                    player.sendSystemMessage(Component.translatable(
                            "block.simple_path_finder.path_finder_block.wrote.pos",
                            posData.pos().getX(), posData.pos().getY(), posData.pos().getZ()
                    ));
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        // 空手或非定位器物品：触发导航
        if (stack.isEmpty() && !level.isClientSide() && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof PathFinderBlockEntity be) {
                var blockData = be.getBlockLocatorData();
                if (blockData.hasLocator()) {
                    LocatorData locatorData = blockData.getLocatorData();

                    if (locatorData.isPlayerBound()) {
                        // 绑定到玩家：发送网络包告诉客户端玩家位置
                        handlePlayerTarget(serverLevel, (ServerPlayer) player, locatorData.getPlayerUuid());
                    } else if (locatorData.isPosBound()) {
                        // 绑定到位置：发送网络包告诉客户端目标位置
                        handlePosTarget((ServerPlayer) player, locatorData.getGlobalPos().pos());
                    } else {
                        player.sendSystemMessage(Component.translatable("simple_path_finder.locator.no_target"));
                    }
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
        }

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 处理玩家目标导航
     */
    private void handlePlayerTarget(ServerLevel level, ServerPlayer requester, java.util.UUID targetUuid) {
        var targetPlayer = level.getServer().getPlayerList().getPlayer(targetUuid);
        if (targetPlayer == null) {
            // 目标玩家不在线
            PacketDistributor.sendToPlayer(requester, io.github.kunosayo.simplepathfinder.network.PlayerLocationPacket.offline());
        } else {
            // 目标玩家在线，发送位置
            BlockPos targetPos = targetPlayer.blockPosition();
            String playerName = targetPlayer.getName().getString();
            PacketDistributor.sendToPlayer(requester, io.github.kunosayo.simplepathfinder.network.PlayerLocationPacket.online(targetPos, playerName));
        }
    }

    /**
     * 处理位置目标导航
     */
    private void handlePosTarget(ServerPlayer requester, BlockPos targetPos) {
        // 对于位置目标，我们不需要额外的网络包
        // 客户端已经有了导航数据（通过 SyncLevelNavDataPacket）
        // 我们只需要设置客户端的目标位置
        requester.sendSystemMessage(Component.translatable("simple_path_finder.nav.starting", targetPos.getX(), targetPos.getY(), targetPos.getZ()));
    }
}
