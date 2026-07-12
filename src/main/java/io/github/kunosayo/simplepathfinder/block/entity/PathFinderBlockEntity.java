package io.github.kunosayo.simplepathfinder.block.entity;

import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.data.LocatorDataAttachment;
import io.github.kunosayo.simplepathfinder.init.ModAttachments;
import io.github.kunosayo.simplepathfinder.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    /**
     * 设置定位器数据
     * 使用 setData 会自动标记方块实体为已修改并同步到客户端
     */
    public void setLocatorData(LocatorData data) {
        setData(ModAttachments.LOCATOR_DATA.get(), LocatorDataAttachment.of(data));
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
