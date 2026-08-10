package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.codec.ArrayCodecs;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceData;
import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import io.github.kunosayo.simplepathfinder.nav.finder.EdgeConsumer;
import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static io.github.kunosayo.simplepathfinder.util.NavUtil.considerSafeCross;
import static io.github.kunosayo.simplepathfinder.util.NavUtil.considerSafeGround;

/**
 * The nav data in chunks
 */
public final class LayeredNavChunk extends AbstractLayeredNavChunk {
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
        return getPosSituation(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    public static int getPosSituation(int ax, int az, int bx, int bz) {
        if (ax == bx) {
            if (az < bz) {
                // a+z
                return 1;
            }
            // b+z
            return 3;
        }
        if (ax < bx) {
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
    int[][] extraVisited = new int[NavPathFinder.VISIT_CACHE_SIZE][256];
    byte layer = 0;
    // Assign in different threads.
    List<NavRectCell> cellList = new ArrayList<>();
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
        updateChunkData();
    }

    LayeredNavChunk(short[] walkY, int[] distances, byte layer) {
        this.walkY = walkY;
        this.distances = distances;
        this.layer = layer;
        updateChunkData();
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
        var inner = ChunkInnerPos.get(pos);
        return distances[getDistanceIdx(inner.x, inner.z, isZ)];
    }

    @Override
    public void setDistance(int x, int z, boolean isZ, short value) {
        distances[getDistanceIdx(x, z, isZ)] = value;
    }


    static int getDistanceIdx(int sx, int sz, boolean isZ) {
        return (convertToIndex(sx, sz) << 1) | (isZ ? 1 : 0);
    }

    @Override
    public int getPositiveDistanceX(int x, int z) {
        return distances[convertToIndex(x, z) << 1];
    }

    @Override
    public int getPositiveDistanceZ(int x, int z) {
        return distances[(convertToIndex(x, z) << 1) | 1];
    }

    /**
     * Get the distance cost for a block.
     * Uses player-specific configuration if provided, otherwise falls back to global config.
     *
     * @param block        the block to get distance for
     * @param distanceData the player-specific distance configuration, null to use global config
     * @return the distance cost for this block
     */
    private static int getDistanceResult(Block block, @Nullable PlayerBlockDistanceData distanceData) {
        // Try player-specific config first
        if (distanceData != null) {
            return distanceData.getDistance(block);
        }

        // Fallback to global config (uses block IDs only)
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return NavConfig.NAV_CONFIG.getLeft().blockDistanceMap.getOrDefault(
                blockId,
                NavConfig.NAV_CONFIG.getLeft().defaultBlockDistance.getDefault());
    }

    static long getDistance(Level level, int sx, int sy, int sz, int tx, int tz,
                            @Nullable PlayerBlockDistanceData distanceData) {
        final var mutable = new BlockPos.MutableBlockPos(tx, sy, tz);
        //   13
        //   .2
        //sy:.4
        //   #5
        //    6

        // Get self distance with fluid handling
        BlockState selfState = level.getBlockState(new BlockPos(sx, sy - 1, sz));
        int selfDistance = getDistanceWithFluid(selfState, distanceData);

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
            return packDistanceResult(Math.max(getDistanceWithFluid(upBaseBlock, distanceData), selfDistance), sy + 1);
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
            return packDistanceResult(Math.max(getDistanceWithFluid(sameBaseBlock, distanceData), selfDistance), sy);
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
            return packDistanceResult(Math.max(getDistanceWithFluid(downBase, distanceData), selfDistance), sy - 1);
        }

        return D_CANNOT_REACH;

    }

    /**
     * Get distance for a block state, handling fluids specially.
     */
    private static int getDistanceWithFluid(BlockState state, @Nullable PlayerBlockDistanceData distanceData) {
        var fluid = state.getFluidState();
        if (distanceData != null && !distanceData.distanceMap().isEmpty()) {
            return distanceData.getDistance(state.getBlock());
        }
        if (!fluid.isEmpty()) {
            if (fluid.getType().isSame(Fluids.WATER) || fluid.getType().isSame(Fluids.FLOWING_WATER)) {
                return 127;
            } else if (fluid.getType().isSame(Fluids.LAVA) || fluid.getType().isSame(Fluids.FLOWING_LAVA)) {
                return 12737;
            }
            return 30;
        }
        Block block = state.getBlock();
        return getDistanceResult(block, distanceData);
    }

    @Override
    public byte parse(Level level, BlockPos trustedCenter, @Nullable PlayerBlockDistanceData distanceData) {
        final var solver = Solver.acquire();
        try {
            return solver.solve(level, this, trustedCenter, distanceData);
        } finally {
            solver.unlock();
            updateChunkData();
        }
    }

    /// Fuck, it won't kill you to just return LayeredNavChunk
    /// I'd prefer to delete the whole ILayeredNavChunk if I can
    public static LayeredNavChunk getDefault() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distance = new int[LevelNavData.CHUNK_AREA << 1];
        Arrays.fill(distance, -1);
        Arrays.fill(walkY, (short) LayeredNavChunk.INVALID_WALK_Y);
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


    public void updateChunkData() {
        // we won't use rect cell.


//        var list = new ArrayList<NavRectCell>();
//
//        for (int x = 0; x < 15; x++) {
//            for (int z = 0; z < 15; z++) {
//                final int startX = x;
//                final int startZ = z;
//                short targetWalkY = this.walkY[convertToIndex(startX, startZ)];
//                int targetDistX = this.distances[getDistanceIdx(startX, startZ, false)];
//                int targetDistZ = this.distances[getDistanceIdx(startX, startZ, true)];
//
//                if (targetWalkY == INVALID_WALK_Y || targetDistX < 0 || targetDistX != targetDistZ) {
//                    continue;
//                }
//                // Find maximum rectangle with same values
//                int maxX = startX;
//                int maxZ = startZ;
//
//                // First, find max extent in X direction from start row
//                while (maxX + 1 < 16 &&
//                        this.walkY[convertToIndex(maxX + 1, startZ)] == targetWalkY &&
//                        this.distances[getDistanceIdx(maxX, startZ, false)] == targetDistX) {
//                    maxX++;
//                }
//
//                int minMaxX = maxX;
//                while (minMaxX != startX && maxZ + 1 < 16) {
//                    int curMin = minMaxX;
//                    // Check if all cells in the next row (z = maxZ + 1) have same values
//                    for (int cx = startX; cx <= minMaxX; cx++) {
//                        if (this.walkY[convertToIndex(cx, maxZ + 1)] != targetWalkY
//                                || this.distances[getDistanceIdx(cx, maxZ, true)] != targetDistZ
//                                || (cx > startX && this.distances[getDistanceIdx(cx - 1, maxZ + 1, false)] != targetDistX)) {
//                            curMin = Math.min(minMaxX, cx - 1);
//                            break;
//                        }
//                    }
//                    if (curMin < startX) {
//                        break;
//                    } else if (curMin != startX) {
//                        minMaxX = curMin;
//                        maxZ++;
//                    } else {
//                        break;
//                    }
//                }
//
//                // Check if this rectangle should be added
//                // Condition: neither edge has length 1 (so width > 1 and height > 1)
//                maxX = minMaxX;
//                int width = maxX - startX + 1;
//                int height = maxZ - startZ + 1;
//
//                if (width > 1 && height > 1) {
//                    // Check if this rectangle is a subset of any existing rectangle
//                    boolean isSubset = false;
//                    for (NavRectCell existing : list) {
//                        if (startX >= existing.minX && maxX <= existing.maxX &&
//                                startZ >= existing.minZ && maxZ <= existing.maxZ) {
//                            isSubset = true;
//                            break;
//                        }
//                    }
//
//                    if (!isSubset) {
//                        var cell = new NavRectCell();
//                        cell.minX = (byte) startX;
//                        cell.minZ = (byte) startZ;
//                        cell.maxX = (byte) maxX;
//                        cell.maxZ = (byte) maxZ;
//                        list.add(cell);
//                    }
//                }
//            }
//        }
//
//        this.cellList = list;
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

    @Override
    public void checkExtraPath(NavPathFinder finder, NavPathFinder.SearchNode node, EdgeConsumer edgeConsumer) {
        int ix = node.x & 15;
        int iz = node.z & 15;
        var list = this.cellList;
        int endChunkX = finder.getEnd().getX() >> 4;
        int endChunkZ = finder.getEnd().getZ() >> 4;
        int curChunkX = node.x >> 4;
        int curChunkZ = node.z >> 4;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < list.size(); i++) {
            var rect = list.get(i);
            if (rect.isInRegion(ix, iz)) {
                if (rect.markVisited(finder)) {
                    int dis = getDistance(ix, iz, false);
                    int y = getWalkY(ix, iz);
                    if ((endChunkX == curChunkX && endChunkZ == curChunkZ) || (rect.maxZ == rect.minZ || rect.maxX == rect.minX)) {
                        for (int tx = rect.minX; tx <= rect.maxX; tx++) {
                            for (int tz = rect.minZ; tz <= rect.maxZ; tz++) {
                                checkExtraPoint(finder, node, edgeConsumer, tx, tz, ix, iz, dis, y);
                            }
                        }
                    } else {
                        for (int tx = rect.minX; tx <= rect.maxX; tx++) {
                            for (int tz = rect.minZ; tz <= rect.maxZ; tz += rect.maxZ - rect.minZ) {
                                checkExtraPoint(finder, node, edgeConsumer, tx, tz, ix, iz, dis, y);
                            }
                        }

                        for (int tx = rect.minX; tx <= rect.maxX; tx += rect.maxX - rect.minX) {
                            for (int tz = rect.minZ; tz <= rect.maxZ; tz++) {
                                checkExtraPoint(finder, node, edgeConsumer, tx, tz, ix, iz, dis, y);
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkExtraPoint(NavPathFinder finder, NavPathFinder.SearchNode node, EdgeConsumer edgeConsumer, int tx, int tz, int ix, int iz, int dis, int y) {
        int idx = finder.getCacheIndex();
        if (idx != -1) {
            int cnt = finder.getCacheVisitCount();
            int pointIndex = convertToIndex(tx, tz);
            if (this.extraVisited[idx][pointIndex] == cnt) {
                return;
            }
            this.extraVisited[idx][pointIndex] = cnt;
        } else {
            var obj = finder.extraFinderData.computeIfAbsent(this, _ -> new HashSet<ChunkInnerPos>());
            if (obj instanceof HashSet<?> s) {
                //noinspection unchecked
                if (!((HashSet<Object>) s).add(ChunkInnerPos.get(ix, iz))) {
                    return;
                }
            }
        }
        if (tx == ix && tz == iz) {
            return;
        }
        int dx = tx - ix;
        int dz = tz - iz;
        if (Math.abs(dx) + Math.abs(dz) <= 1) {
            return;
        }
        int finalDis = Math.max(Math.abs(dx), Math.abs(dz)) * dis;
        edgeConsumer.acceptEdge(finalDis, dx + node.x, y, dz + node.z, this, NavLinkType.NORMAL);
    }
}
