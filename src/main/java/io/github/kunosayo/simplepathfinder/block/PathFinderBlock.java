package io.github.kunosayo.simplepathfinder.block;

import com.mojang.serialization.MapCodec;
import io.github.kunosayo.simplepathfinder.block.entity.PathFinderBlockEntity;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.item.LocatorItem;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
import io.github.kunosayo.simplepathfinder.nav.NavigationService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public class PathFinderBlock extends BaseEntityBlock {

    public static final MapCodec<PathFinderBlock> CODEC = Block.simpleCodec(PathFinderBlock::new);

    /**
     * 激活状态属性 - 方块是否有有效的定位数据
     */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public PathFinderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
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
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack itemStack, @NonNull BlockState state, @NonNull Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查是否手持定位器
        if (stack.getItem() instanceof LocatorItem) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PathFinderBlockEntity be) {
                // 只在方块没有数据时允许写入
                var blockData = be.getBlockLocatorData();
                if (blockData.hasLocator()) {
                    player.sendSystemMessage(Component.translatable("block.simple_path_finder.path_finder_block.already_has_data"));
                    return InteractionResult.FAIL;
                }

                // 读取定位器数据
                LocatorData locatorData = LocatorItem.getLocatorData(stack);

                if (locatorData != null) {
                    // 将定位器数据写入方块实体
                    // 注意：LocatorData 现在总是包含一个有效的 target（Either.left 或 Either.right）
                    be.setLocatorData(locatorData);

                    // 更新方块状态为激活状态
                    level.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);

                    // 发送确认消息
                    if (locatorData.isPosBound()) {
                        var posData = locatorData.getGlobalPos();
                        player.sendSystemMessage(Component.translatable(
                                "block.simple_path_finder.path_finder_block.wrote.pos",
                                posData.pos().getX(), posData.pos().getY(), posData.pos().getZ()
                        ));
                    }
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        // 空手或非定位器物品：触发导航
        if (stack.isEmpty()) {
            if (level.getBlockEntity(pos) instanceof PathFinderBlockEntity be) {
                var blockData = be.getBlockLocatorData();
                if (blockData.hasLocator()) {
                    LocatorData locatorData = blockData.getLocatorData();
                    if (level.isClientSide()) {
                        // 客户端：使用导航服务处理导航
                        NavigationService.navigate(locatorData, NavNotificationConfig.all());
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity by, @NonNull ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.getBlockEntity(pos) instanceof PathFinderBlockEntity be) {
            if (be.getBlockLocatorData().hasLocator() && state.getBlock() == this) {
                if (!state.getValue(ACTIVE)) {
                    level.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
                }
            }
        }
    }
}
