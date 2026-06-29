package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk.*;

class AsyncSolver {

    private static final int VISITED_SIZE = Math.ceilDiv(LevelNavData.CHUNK_AREA, 64);
    private static final CompletableFuture<?> COMPLETED = CompletableFuture.completedFuture(null);
    private static final ExecutorService ASYNC_REQUESTER = Executors.newSingleThreadExecutor(it -> new Thread(it, "SimplePathFinder async chunk source"));
    private static final TicketType TYPE_SOLVER = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
    private static final Ticket TICKET = new Ticket(TYPE_SOLVER, ChunkLevel.byStatus(ChunkStatus.FULL));

    private final Level levelIn;
    private final LayeredNavChunk chunkIn;
    private final int cx, cz;
    private final BlockPos trustedCenter;

    private final LongList q1 = new LongArrayList(31), q2 = new LongArrayList(31);
    private final long[] visited = new long[VISITED_SIZE];

    AsyncSolver(Level levelIn, LayeredNavChunk chunkIn, BlockPos trustedCenter) {
        this.levelIn = levelIn;
        this.chunkIn = chunkIn;
        this.trustedCenter = trustedCenter;

        cx = SectionPos.blockToSectionCoord(trustedCenter.getX());
        cz = SectionPos.blockToSectionCoord(trustedCenter.getZ());
    }

    int blockX(int x) {
        return SectionPos.sectionToBlockCoord(cx, x);
    }

    int blockZ(int z) {
        return SectionPos.sectionToBlockCoord(cz, z);
    }

    void markVisited(int idx) {
        visited[idx >>> 6] |= 1L << (idx & 63);
    }

    boolean isVisited(int idx) {
        return (visited[idx >>> 6] & (1L << (idx & 63))) != 0;
    }

    void markDistance(int x, int z, int tx, int tz, int distance) {
        boolean isZ = z != tz;
        int fromX = Math.min(x, tx);
        int fromZ = Math.min(z, tz);
        chunkIn.distances[getDistanceIdx(fromX, fromZ, isZ)] = distance;
    }

    protected boolean detectDependencies() {
        if (levelIn.getServer() == null) return true;
        final var chunkSource = levelIn.getChunkSource();
        boolean hereLoaded = chunkSource.hasChunk(cx, cz);
        boolean xPlusOneLoaded = chunkSource.hasChunk(cx + 1, cz);
        boolean zPlusOneLoaded = chunkSource.hasChunk(cx, cz + 1);
        return hereLoaded && xPlusOneLoaded && zPlusOneLoaded;
    }

    CompletableFuture<?> loadChunkForAsyncProcessing() {
        final var chunkSource = (ServerChunkCache) levelIn.getChunkSource();
        chunkSource.addTicket(TICKET, new ChunkPos(cx, cz));
        chunkSource.addTicket(TICKET, new ChunkPos(cx + 1, cz));
        chunkSource.addTicket(TICKET, new ChunkPos(cx, cz + 1));
        return CompletableFuture.supplyAsync(() -> CompletableFuture.allOf(
                chunkSource.getChunkFuture(cx, cz, ChunkStatus.FULL, true),
                chunkSource.getChunkFuture(cx + 1, cz, ChunkStatus.FULL, true),
                chunkSource.getChunkFuture(cx, cz + 1, ChunkStatus.FULL, true)
        ), ASYNC_REQUESTER).thenCompose(Function.identity());
    }

    void releaseChunk() {
        final var chunkSource = (ServerChunkCache) levelIn.getChunkSource();
        chunkSource.removeTicketWithRadius(TYPE_SOLVER, new ChunkPos(cx, cz), 0);
        chunkSource.removeTicketWithRadius(TYPE_SOLVER, new ChunkPos(cx + 1, cz), 0);
        chunkSource.removeTicketWithRadius(TYPE_SOLVER, new ChunkPos(cx, cz + 1), 0);
    }

    boolean once(LongList queue, int x, int y, int z) {
        // the x, z in [0, 15]
        // the y is real world

        // InitAuther97: extract marking to constructor to avoid redundant marking
        for (int i = 0; i < 4; i++) {
            int tx = x + SEARCH_DX[i];
            int tz = z + SEARCH_DZ[i];
            if (tx < 0 || tz < 0) {
                continue;
            }

            long dResult = getDistance(levelIn, blockX(x), y, blockZ(z), blockX(tx), blockZ(tz));
            final int distance = unpackDistance(dResult);
            markDistance(x, z, tx, tz, distance);
            if (!canReachDistance(dResult) || tx >= 16 || tz >= 16) {
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
        return true;
    }

    void doWork() {
        LongList working = q2, toWork = q1;
        do {
            for (int i = 0; i < working.size(); i++ ) {
                var pos = working.getLong(i);
                once(toWork, BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
            }
            working.clear();
            LongList buffer = working;
            working = toWork;
            toWork = buffer;
        } while (!working.isEmpty());
    }

    CompletableFuture<?> begin() {
        final int startX = Mth.positiveModulo(trustedCenter.getX(), 16);
        final int startZ = Mth.positiveModulo(trustedCenter.getZ(), 16);
        final int idx = convertToIndex(startX, startZ);
        chunkIn.walkY[idx] = (short) trustedCenter.getY();
        // in fact, we run bfs
        q2.addLast(BlockPos.asLong(startX, trustedCenter.getY(), startZ));
        markVisited(idx);
        if (!detectDependencies()) {
            return loadChunkForAsyncProcessing()
                    .thenRunAsync(this::doWork, levelIn.getServer())
                    .thenRun(this::releaseChunk);
        }
        doWork();
        return COMPLETED;
    }
}