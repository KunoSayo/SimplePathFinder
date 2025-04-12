package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.codec.ArrayCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.Arrays;

import static io.github.kunosayo.simplepathfinder.util.NavUtil.considerSafeCross;
import static io.github.kunosayo.simplepathfinder.util.NavUtil.considerSafeGround;

/**
 * The nav data in chunks
 */
public final class LayeredNavChunk {
    public static final StreamCodec<ByteBuf, LayeredNavChunk> STREAM_CODEC = StreamCodec
            .composite(ArrayCodecs.shortArrayCodec(LevelNavData.CHUNK_AREA),
                    layeredNavChunk -> layeredNavChunk.walkY,
                    ArrayCodecs.intArrayCodec(LevelNavData.CHUNK_AREA << 1),
                    layeredNavChunk -> layeredNavChunk.distances,
                    ByteBufCodecs.VAR_INT, layeredNavChunk -> layeredNavChunk.layer,
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
    int layer = 0;
    public NavChunk parentChunk = null;

    LayeredNavChunk(short[] walkY, int[] distances) {
        this.walkY = walkY;
        this.distances = distances;
    }

    LayeredNavChunk(short[] walkY, int[] distances, int layer) {
        this.walkY = walkY;
        this.distances = distances;
        this.layer = layer;
    }

    public static boolean isWalkYValid(int y) {
        return y != -9961;
    }

    /**
     * Return the walk y at location
     *
     * @param x in [0, 15]
     * @param z in [0, 15]
     * @return the walk y, or -9961 if cannot reach
     */
    public int getWalkY(int x, int z) {
        return walkY[convertToIndex(x, z)];
    }

    public int getDistance(int x, int z, boolean isZ) {
        return distances[getDistanceIdx(x, z, isZ)];
    }

    public int getDistanceChecked(int x, int z, boolean isZ) {
        return canWalk(x, z) ? distances[getDistanceIdx(x, z, isZ)] : -1;
    }

    public int getDistance(BlockPos pos, boolean isZ) {
        var inner = new ChunkInnerPos(pos);
        return distances[getDistanceIdx(inner.x, inner.z, isZ)];
    }

    private static int convertToIndex(int x, int z) {
        return (x << 4) | z;
    }

    private static int getDistanceIdx(int sx, int sz, boolean isZ) {
        return (convertToIndex(sx, sz) << 1) | (isZ ? 1 : 0);
    }


    private static DistanceResult getDistanceResult(BlockState standBlock, int walkY) {
        var fluid = standBlock.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.getType().isSame(Fluids.WATER) || fluid.getType().isSame(Fluids.FLOWING_WATER)) {
                return new DistanceResult(4, walkY);
            }
            return new DistanceResult(1, walkY);
        }
        return new DistanceResult(1, walkY);
    }

    private static DistanceResult getDistance(Level level, int sx, int sy, int sz, int tx, int tz) {
        var posTargetStartY = new BlockPos(tx, sy, tz);

        //   13
        //   .2
        //sy:.4
        //   #5
        //    6

        if (!considerSafeCross(level, posTargetStartY.offset(0, 1, 0))) {
            // check 2, blocked, no way!
            return DistanceResult.CANNOT_REACH;
        }
        var upBaseBlock = level.getBlockState(posTargetStartY);
        if (considerSafeGround(level, posTargetStartY, upBaseBlock)) {
            // (4 is block)
            // we should go up

            //   13
            //   .2
            //sy:.#
            //   #

            if (!considerSafeCross(level, new BlockPos(sx, sy + 2, sz))) {
                // checked 1
                // we cannot go up (blocked)
                return DistanceResult.CANNOT_REACH;
            }
            if (!considerSafeCross(level, posTargetStartY.offset(0, 2, 0))) {
                // checked 3
                // we cannot go up (blocked)
                return DistanceResult.CANNOT_REACH;
            }
            return getDistanceResult(upBaseBlock, posTargetStartY.getY() + 1);
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
        var sameGroundYPos = posTargetStartY.offset(0, -1, 0);
        var sameBaseBlock = level.getBlockState(sameGroundYPos);
        if (considerSafeGround(level, sameGroundYPos, sameBaseBlock)) {
            // checked 5
            return getDistanceResult(sameBaseBlock, posTargetStartY.getY());
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
        var downGroundPos = sameGroundYPos.offset(0, -1, 0);
        var downBase = level.getBlockState(downGroundPos);
        if (considerSafeGround(level, downGroundPos, downBase)) {
            // checked 6
            return getDistanceResult(downBase, sameGroundYPos.getY());
        }

        return DistanceResult.CANNOT_REACH;

    }

    public void parse(Level level, BlockPos trustedCenter) {
        boolean[] visited = new boolean[LevelNavData.CHUNK_AREA];
        final int startX = Mth.positiveModulo(trustedCenter.getX(), 16);
        final int startZ = Mth.positiveModulo(trustedCenter.getZ(), 16);
        walkY[convertToIndex(startX, startZ)] = (short) trustedCenter.getY();

        var chunkPos = new ChunkPos(trustedCenter);
        class Solver {
            final ArrayDeque<int[]> q = new ArrayDeque<>();

            void dfs(int x, int y, int z) {
                // in fact, we run bfs
                q.addLast(new int[]{x, y, z});
            }

            void markDistance(int x, int z, int tx, int tz, int distance) {
                boolean isZ = z != tz;
                int fromX = Math.min(x, tx);
                int fromZ = Math.min(z, tz);
                distances[getDistanceIdx(fromX, fromZ, isZ)] = distance;
            }

            void once(int x, int y, int z) {
                // the x, z in [0, 15]
                // the y is real world
                final int idx = convertToIndex(x, z);
                visited[idx] = true;

                for (int i = 0; i < 4; i++) {
                    int tx = x + SEARCH_DX[i];
                    int tz = z + SEARCH_DZ[i];
                    if (tx < 0 || tz < 0) {
                        continue;
                    }
                    var distance = getDistance(level,
                            chunkPos.getBlockX(x), y, chunkPos.getBlockZ(z),
                            chunkPos.getBlockX(tx), chunkPos.getBlockZ(tz));
                    markDistance(x, z, tx, tz, distance.distance);
                    if (distance.canReach()) {
                        if (tx >= 16 || tz >= 16) {
                            continue;
                        }
                        final int thatIdx = convertToIndex(tx, tz);
                        if (!visited[thatIdx]) {
                            walkY[thatIdx] = distance.walkY;
                            visited[thatIdx] = true;
                            dfs(tx, distance.walkY, tz);
                        }
                    }
                }
            }

            void run() {
                while (!q.isEmpty()) {
                    var pos = q.pollFirst();
                    once(pos[0], pos[1], pos[2]);
                }
            }
        }

        var solver = new Solver();
        solver.dfs(startX, trustedCenter.getY(), startZ);
        solver.run();
    }

    public static LayeredNavChunk getDefault() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distance = new int[LevelNavData.CHUNK_AREA << 1];
        Arrays.fill(distance, -1);
        Arrays.fill(walkY, (short) -9961);
        return new LayeredNavChunk(walkY, distance);
    }

    public int getLayer() {
        return layer;
    }

    public boolean canWalk(int x, int z) {
        return isWalkYValid(getWalkY(x, z));
    }

    public boolean isAnyValid() {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (canWalk(i, j)) {
                    return true;
                }
            }
        }
        return false;
    }

    private record DistanceResult(int distance, short walkY) {
        public static final DistanceResult CANNOT_REACH = new DistanceResult(-1, (short) -1);

        DistanceResult(int d, int y) {
            this(d, (short) y);
        }

        public boolean canReach() {
            return distance >= 0;
        }
    }
}
