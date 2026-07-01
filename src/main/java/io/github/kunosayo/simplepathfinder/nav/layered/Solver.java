package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import static io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk.*;

class Solver {
    private boolean occupied = false;
    private final LongList q1 = new LongArrayList(31), q2 = new LongArrayList(31);
    private final long[] visited = new long[VISITED_SIZE];

    public static final byte R_X = 1, R_Z = 2, R_XZ = 3, R_O = 0;

    private Solver lock() {
        if (this.occupied) {
            SimplePathFinder.LOGGER.error("Reentrant Solver acquisition found. This usually means the code is badly coded and chunks now solve recursively.", new Throwable());
            return new Solver();
        }
        this.occupied = true;
        return this;
    }

    public void unlock() {
        this.occupied = false;
    }

    void markVisited(int idx) {
        visited[idx >>> 6] |= 1L << (idx & 63);
    }

    boolean isVisited(int idx) {
        return (visited[idx >>> 6] & (1L << (idx & 63))) != 0;
    }

    byte once(LongList queue, Level levelIn, LayeredNavChunk chunkIn, int cx, int cz, int x, int y, int z) {
        // the x, z in [0, 15]
        // the y is real world

        byte res = 0;
        // InitAuther97: extract marking to constructor to avoid redundant marking
        for (int i = 0; i < 4; i++) {
            int tx = x + SEARCH_DX[i];
            int tz = z + SEARCH_DZ[i];
            if (tx < 0 || tz < 0) {
                continue;
            }

            long dResult = getDistance(
                    levelIn,
                    SectionPos.sectionToBlockCoord(cx, x),
                    y,
                    SectionPos.sectionToBlockCoord(cz, z),
                    SectionPos.sectionToBlockCoord(cx, tx),
                    SectionPos.sectionToBlockCoord(cz, tz)
            );
            final int distance = unpackDistance(dResult);
            markDistance(chunkIn, x, z, tx, tz, distance);
            if (!canReachDistance(dResult)) {
                continue;
            }
            if (tx >= 16) {
                res |= R_X;
                continue;
            }
            if (tz >= 16) {
                res |= R_Z;
                continue;
            }
            final int thatIdx = convertToIndex(tx, tz);
            if (isVisited(thatIdx)) {
                continue;
            }
            // Why bother doing this anyway
            final short walkY = (short) unpackWalkY(dResult);
            chunkIn.walkY[thatIdx] = walkY;
            // InitAuther97: mark early so it won't be queued for many times
            markVisited(thatIdx);
            queue.add(BlockPos.asLong(tx, walkY, tz));
        }
        return res;
    }

    byte doWork(Level levelIn, LayeredNavChunk chunkIn, int cx, int cz) {
        LongList working = q2, toWork = q1;
        byte res = 0;
        do {
            for (int i = 0; i < working.size(); i++ ) {
                var pos = working.getLong(i);
                res |= once(toWork, levelIn, chunkIn, cx, cz, BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
            }
            working.clear();
            LongList buffer = working;
            working = toWork;
            toWork = buffer;
        } while (!working.isEmpty());
        clearVisited(this.visited);
        return res;
    }

    byte solve(
            final Level levelIn,
            final LayeredNavChunk chunkIn,
            final BlockPos trustedCenter
    ) {
        final int x = trustedCenter.getX();
        final int z = trustedCenter.getZ();
        final int cx = SectionPos.blockToSectionCoord(x);
        final int cz = SectionPos.blockToSectionCoord(z);
        final int startX = SectionPos.sectionRelative(x);
        final int startZ = SectionPos.sectionRelative(z);
        final int idx = convertToIndex(startX, startZ);
        chunkIn.walkY[idx] = (short) trustedCenter.getY();
        markVisited(idx);
        // in fact, we run bfs
        q2.addLast(BlockPos.asLong(startX, trustedCenter.getY(), startZ));
        return doWork(levelIn, chunkIn, cx, cz);
    }

    private static final int VISITED_SIZE = Math.ceilDiv(LevelNavData.CHUNK_AREA, 64);
    public static final ThreadLocal<Solver> SOLVER_CACHE = ThreadLocal.withInitial(Solver::new);

    static void clearVisited(long[] arr) {
        for (int i = 0; i < VISITED_SIZE; i++) {
            arr[i] = 0L;
        }
    }

    public static Solver acquire() {
        return SOLVER_CACHE.get().lock();
    }

    static void markDistance(LayeredNavChunk chunkIn, int x, int z, int tx, int tz, int distance) {
        boolean isZ = z != tz;
        int fromX = Math.min(x, tx);
        int fromZ = Math.min(z, tz);
        chunkIn.distances[getDistanceIdx(fromX, fromZ, isZ)] = distance;
    }
}