package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.codec.ArrayCodecs;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.Arrays;

import static io.github.kunosayo.simplepathfinder.util.NavUtil.considerSafeCross;
import static io.github.kunosayo.simplepathfinder.util.NavUtil.considerSafeGround;

/**
 * The nav data in chunks
 */
public final class LayeredNavChunk implements ILayeredNavChunk {
    public static final StreamCodec<ByteBuf, LayeredNavChunk> STREAM_CODEC = StreamCodec
            .composite(ArrayCodecs.shortArrayCodec(LevelNavData.CHUNK_AREA),
                    layeredNavChunk -> layeredNavChunk.walkY,
                    ArrayCodecs.intArrayCodec(LevelNavData.CHUNK_AREA << 1),
                    layeredNavChunk -> layeredNavChunk.distances,
                    ByteBufCodecs.BYTE, layeredNavChunk -> layeredNavChunk.layer,
                    LayeredNavChunk::new);
    public static final int[] SEARCH_DX = {1, -1, 0, 0};
    public static final int[] SEARCH_DZ = {0, 0, 1, -1};

    /**
     * @param a the start point
     * @param b the end point
     * @return 0: a+x, 1: a+z, 2: b+x, 3: b+z
     */
    public static int getPosSituation(BlockPos a, BlockPos b) {
        if (a.getX() == b.getX()) {
            if (a.getZ() < b.getZ()) {
                // a+z
                return 1;
            }
            // b+z
            return 3;
        }
        if (a.getX() < b.getX()) {
            // a+x
            return 0;
        }
        // b+x
        return 2;
    }


    /**
     * The y is air, (y-1) is ground.
     */
    short[] walkY;

    // Store +x+z+x+z..
    int[] distances;
    byte layer = 0;
    public INavChunk parentChunk = null;

    @Override
    public INavChunk getParentChunk() {
        return parentChunk;
    }

    @Override
    public void setLayer(byte layer) {
        this.layer = layer;
    }

    @Override
    public void setParentChunk(INavChunk parentChunk) {
        this.parentChunk = parentChunk;
    }

    LayeredNavChunk(short[] walkY, int[] distances) {
        this.walkY = walkY;
        this.distances = distances;
    }

    LayeredNavChunk(short[] walkY, int[] distances, byte layer) {
        this.walkY = walkY;
        this.distances = distances;
        this.layer = layer;
    }

    /**
     * Return the walk y at location
     *
     * @param x in [0, 15]
     * @param z in [0, 15]
     * @return the walk y, or -9961 if cannot reach
     */
    @Override
    public int getWalkY(int x, int z) {
        return walkY[convertToIndex(x, z)];
    }

    @Override
    public int getDistance(int x, int z, boolean isZ) {
        return distances[getDistanceIdx(x, z, isZ)];
    }

    @Override
    public int getDistanceChecked(int x, int z, boolean isZ) {
        return canWalk(x, z) ? distances[getDistanceIdx(x, z, isZ)] : -1;
    }

    @Override
    public int getDistance(BlockPos pos, boolean isZ) {
        var inner = new ChunkInnerPos(pos);
        return distances[getDistanceIdx(inner.x, inner.z, isZ)];
    }

    @Override
    public void setDistance(int x, int z, boolean isZ, short value) {
        distances[getDistanceIdx(x, z, isZ)] = value;
    }

    static int convertToIndex(int x, int z) {
        return (x << 4) | z;
    }

    static int getDistanceIdx(int sx, int sz, boolean isZ) {
        return (convertToIndex(sx, sz) << 1) | (isZ ? 1 : 0);
    }


    private static int getDistanceResult(BlockState standBlock) {
        var fluid = standBlock.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.getType().isSame(Fluids.WATER) || fluid.getType().isSame(Fluids.FLOWING_WATER)) {
                return 127;
            } else if (fluid.getType().isSame(Fluids.LAVA) || fluid.getType().isSame(Fluids.FLOWING_LAVA)) {
                return 12737;
            }
            return 30;
        }
        return standBlock.typeHolder().unwrapKey()
                .map(blockResourceKey -> NavConfig.NAV_CONFIG.getLeft().blockDistanceMap
                        .get(blockResourceKey.identifier()))
                .orElse(NavConfig.NAV_CONFIG.getLeft().defaultBlockDistance.getDefault());
    }

    static long getDistance(Level level, int sx, int sy, int sz, int tx, int tz) {
        final var mutable = new BlockPos.MutableBlockPos(tx, sy, tz);
        //   13
        //   .2
        //sy:.4
        //   #5
        //    6

        mutable.move(0, 1, 0);
        if (!considerSafeCross(level, mutable)) {
            // check 2, blocked, no way!
            return D_CANNOT_REACH;
        }
        mutable.move(0, -1, 0);
        var upBaseBlock = level.getBlockState(mutable);
        if (considerSafeGround(level, mutable, upBaseBlock)) {
            // (4 is block)
            // we should go up

            //   13
            //   .2
            //sy:.#
            //   #

            mutable.set(sx, sy + 2, sz);
            if (!considerSafeCross(level, mutable)) {
                // checked 1
                // we cannot go up (blocked)
                return D_CANNOT_REACH;
            }
            mutable.set(tx, sy + 2, tz);
            if (!considerSafeCross(level, mutable)) {
                // checked 3
                // we cannot go up (blocked)
                return D_CANNOT_REACH;
            }
            return packDistanceResult(getDistanceResult(upBaseBlock), sy + 1);
        }
        //   13
        //   .2
        //sy:.4
        //   #5
        //    6
        // check are we going down
        // we checked 4, 2
        //   13
        //   ..
        //sy:..
        //   #5
        //    6

        // mutable is moved into sameGroundYPos
        final var sameGroundYPos = mutable.move(0, -1, 0);
        var sameBaseBlock = level.getBlockState(sameGroundYPos);
        if (considerSafeGround(level, sameGroundYPos, sameBaseBlock)) {
            // checked 5
            return packDistanceResult(getDistanceResult(sameBaseBlock), sy);
        }
        //   13
        //   ..
        //sy:..
        //   #5
        //    6
        // we just checked 5
        //   13
        //   ..
        //sy:..
        //   #.
        //    6

        // sameGroundYPos is moved into downGroundPos
        final var downGroundPos = sameGroundYPos.move(0, -1, 0);
        var downBase = level.getBlockState(downGroundPos);
        if (considerSafeGround(level, downGroundPos, downBase)) {
            // checked 6
            return packDistanceResult(getDistanceResult(downBase), sy - 1);
        }

        return D_CANNOT_REACH;

    }

    @Override
    public byte parse(Level level, BlockPos trustedCenter) {
        final var solver = Solver.acquire();
        try {
            return solver.solve(level, this, trustedCenter);
        } finally {
            solver.unlock();
        }
    }

    /// Fuck, it won't kill you to just return LayeredNavChunk
    /// I'd prefer to delete the whole ILayeredNavChunk if I can
    public static LayeredNavChunk getDefault() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distance = new int[LevelNavData.CHUNK_AREA << 1];
        Arrays.fill(distance, -1);
        Arrays.fill(walkY, (short) -9961);
        return new LayeredNavChunk(walkY, distance);
    }

    @Override
    public byte getLayer() {
        return layer;
    }

    @Override
    public boolean canWalk(int x, int z) {
        return isWalkYValid(getWalkY(x, z));
    }

    private static final long D_CANNOT_REACH = packDistanceResult(-1, -1);

    private static long packDistanceResult(int distance, int walkY) {
        final long result = Integer.toUnsignedLong(distance) << 32 | Integer.toUnsignedLong(walkY);
        if (result < 0) {
            trap();
        }
        return result;
    }

    static void trap() {
        System.out.println("TRAP");
    }

    static int unpackDistance(long dResult) {
        return (int) (dResult >>> 32);
    }

    static int unpackWalkY(long dResult) {
        return (int) dResult;
    }

    static boolean canReachDistance(long dResult) {
        return dResult >= 0;
    }
}
