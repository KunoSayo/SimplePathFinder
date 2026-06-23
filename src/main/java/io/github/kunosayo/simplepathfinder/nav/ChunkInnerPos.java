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
    public static final StreamCodec<ByteBuf, ChunkInnerPos> STREAM_CODEC = StreamCodec.of(
            (buf, pos) -> {
                buf.writeByte(pos.x);
                buf.writeByte(pos.z);
            },
            (buf) -> {
                int x = buf.readUnsignedByte();
                int z = buf.readUnsignedByte();
                return new ChunkInnerPos(x, z);
            }
    );

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

    public static int getInnerPos(int value) {
        return Mth.positiveModulo(value, 16);
    }
}
