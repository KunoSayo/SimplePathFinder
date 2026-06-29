package io.github.kunosayo.simplepathfinder.nav;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public class ChunkInnerPos {
    public final int x;
    public final int z;

    /**
     * Stream codec for ChunkInnerPos serialization
     */
    public static final StreamCodec<ByteBuf, ChunkInnerPos> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            pos -> (byte) pos.x,
            ByteBufCodecs.BYTE,
            pos -> (byte) pos.z,
            ChunkInnerPos::new
    );

    public ChunkInnerPos(int x, int z) {
        this.x = Mth.positiveModulo(x, 16);
        this.z = Mth.positiveModulo(z, 16);
    }

    public ChunkInnerPos(BlockPos pos) {
        this.x = Mth.positiveModulo(pos.getX(), 16);
        this.z = Mth.positiveModulo(pos.getZ(), 16);
    }

    private ChunkInnerPos(byte x, byte z) {
        this.x = x & 0xFF;
        this.z = z & 0xFF;
    }

    public BlockPos toBlockPos(int y, ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getBlockX(x), y, chunkPos.getBlockZ(z));
    }

    public static int getInnerPos(int value) {
        return Mth.positiveModulo(value, 16);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ChunkInnerPos that)) return false;

        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + z;
        return result;
    }
}
