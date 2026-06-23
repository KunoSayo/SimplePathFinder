package io.github.kunosayo.simplepathfinder.block;

import com.mojang.serialization.MapCodec;
import io.github.kunosayo.simplepathfinder.block.entity.PathFinderBlockEntity;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.item.LocatorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

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
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack itemStack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
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

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }
}
