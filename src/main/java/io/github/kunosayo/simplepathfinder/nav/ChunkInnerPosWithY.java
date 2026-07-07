package io.github.kunosayo.simplepathfinder.nav;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

public class ChunkInnerPosWithY {
    public final short y;
    public final byte x;
    public final byte z;

    /**
     * Stream codec for ChunkInnerPos serialization
     */
    public static final StreamCodec<ByteBuf, ChunkInnerPosWithY> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.SHORT,
            pos -> pos.y,
            ByteBufCodecs.BYTE,
            pos -> pos.x,
            ByteBufCodecs.BYTE,
            pos -> pos.z,
            ChunkInnerPosWithY::new
    );

    public ChunkInnerPosWithY(short y, byte x, byte z) {
        this.y = y;
        this.x = x;
        this.z = z;
    }

    public static ChunkInnerPosWithY unpack(int integer) {
        return new ChunkInnerPosWithY((short) ((integer >> 16) & 0xffff), (byte) ((integer >> 8) & 15), (byte) (integer & 15));
    }

    public static int pack(int x, int y, int z) {
        return ((y & 0xffff) << 16) | ((x & 15) << 8) | (z & 15);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ChunkInnerPosWithY that)) return false;

        return y == that.y && x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        int result = y;
        result = 31 * result + x;
        result = 31 * result + z;
        return result;
    }

    public int pack() {
        return (((int) y & 0xffff) << 16) | ((int) x << 8) | (z);
    }
}
