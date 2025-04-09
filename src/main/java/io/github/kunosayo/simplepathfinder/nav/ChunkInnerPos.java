package io.github.kunosayo.simplepathfinder.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public class ChunkInnerPos {
    public final int x;
    public final int z;

    public ChunkInnerPos(int x, int z) {
        this.x = Mth.positiveModulo(x, 16);
        this.z = Mth.positiveModulo(z, 16);
    }

    public ChunkInnerPos(BlockPos pos) {
        this.x = Mth.positiveModulo(pos.getX(), 16);
        this.z = Mth.positiveModulo(pos.getZ(), 16);
    }

    public BlockPos toBlockPos(int y, ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getBlockX(x), y, chunkPos.getBlockZ(z));
    }
}
