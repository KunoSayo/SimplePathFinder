package io.github.kunosayo.simplepathfinder.block.entity;

import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.data.LocatorDataAttachment;
import io.github.kunosayo.simplepathfinder.init.ModAttachments;
import io.github.kunosayo.simplepathfinder.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * 路径查找方块实体
 * 使用数据附件系统存储定位器数据
 */
public class PathFinderBlockEntity extends BlockEntity {

    public PathFinderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PATH_FINDER_BLOCK_ENTITY.get(), pos, blockState);
    }

    /**
     * 获取方块定位器数据容器
     */
    public LocatorDataAttachment getBlockLocatorData() {
        return getData(ModAttachments.LOCATOR_DATA.get());
    }

    /**
     * 设置定位器数据
     * 使用 setData 会自动标记方块实体为已修改并同步到客户端
     */
    public void setLocatorData(LocatorData data) {
        setData(ModAttachments.LOCATOR_DATA.get(), LocatorDataAttachment.of(data));
    }

    /**
     * 检查是否有有效的定位数据
     */
    public boolean hasValidTarget() {
        LocatorDataAttachment blockData = getBlockLocatorData();
        if (!blockData.hasLocator()) {
            return false;
        }

        LocatorData data = blockData.getLocatorData();
        if (data.isPlayerBound()) {
            // 检查玩家是否在线
            if (level instanceof ServerLevel serverLevel) {
                var player = serverLevel.getServer().getPlayerList().getPlayer(data.getPlayerUuid());
                return player != null;
            }
            return false;
        } else {
            return data.isPosBound();
        }
    }

    /**
     * 获取目标玩家名称（如果绑定到玩家）
     */
    public Optional<String> getTargetPlayerName() {
        LocatorDataAttachment blockData = getBlockLocatorData();
        if (!blockData.hasLocator()) {
            return Optional.empty();
        }
        LocatorData data = blockData.getLocatorData();
        if (data.isPlayerBound() && level instanceof ServerLevel serverLevel) {
            var player = serverLevel.getServer().getPlayerList().getPlayer(data.getPlayerUuid());
            return Optional.ofNullable(player).map(p -> p.getName().getString());
        }
        return Optional.empty();
    }
}
