package io.github.kunosayo.simplepathfinder.nav;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

public class ChunkInnerPos {
    public final int x;
    public final int z;
    private static final ChunkInnerPos[] POSES = new ChunkInnerPos[16 * 16];

    static {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                POSES[(x << 4) | z] = new ChunkInnerPos(x, z);
            }
        }
    }

    /**
     * Stream codec for ChunkInnerPos serialization
     */
    public static final StreamCodec<ByteBuf, ChunkInnerPos> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            pos -> (byte) pos.x,
            ByteBufCodecs.BYTE,
            pos -> (byte) pos.z,
            ChunkInnerPos::get
    );

    private ChunkInnerPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static ChunkInnerPos get(BlockPos pos) {
        return POSES[((pos.getX() & 15) << 4) | (pos.getZ() & 15)];
    }

    public static ChunkInnerPos get(byte x, byte z) {
        return POSES[(x << 4) | z];
    }

    public static ChunkInnerPos get(int x, int z) {
        return POSES[(x << 4) | z];
    }

    public static ChunkInnerPos getWithModulo(int x, int z) {
        x &= 15;
        z &= 15;
        return POSES[(x << 4) | z];
    }

    public BlockPos toBlockPos(int y, ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getBlockX(x), y, chunkPos.getBlockZ(z));
    }

    public static int getInnerPos(int value) {
        return value & 15;
    }

    // We do not override equals. We consider it is the same object when equals.

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + z;
        return result;
    }
}
