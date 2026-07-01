package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongStack;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class BatchScheduler implements Runnable {

    public static final ThreadLocal<BatchScheduler> THREAD_LOCAL = new ThreadLocal<>();
    private static final ExecutorService ASYNC_REQUESTER = Executors.newSingleThreadExecutor(it -> new Thread(it, "SimplePathFinder async chunk source"));
    private static final TicketType TYPE_SOLVER = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
    private static final Ticket TICKET = new Ticket(TYPE_SOLVER, ChunkLevel.byStatus(ChunkStatus.FULL));

    private static final int MAX_CONCURRENT_TASKS = 8;

    private final LevelNavDataSavedData data;
    private final ServerLevel level;
    private final UUID playerUUID;
    private final byte layer;
    private final int beginX, beginZ;
    private final int endX, endZ;
    private final int widthBits, heightBits;
    private final int intervalMask;
    private final int totalCount;

    private final Stack<ChunkTask> workStack = new ReferenceArrayList<>(MAX_CONCURRENT_TASKS);
    private final Stack<ChunkTask> pool = new ReferenceArrayList<>(MAX_CONCURRENT_TASKS);
    private final LongStack pending = new LongArrayList(MAX_CONCURRENT_TASKS);
    private final long[] bitset;
    private int current = 0, counter = 0;
    private int chunkDirty = 0;
    /// InitAuther97: we use 1 because player chunk build also invokes afterCompletion
    private int liveCounter = 1, failureCounter = 0;
    private boolean scheduled = false;

    static void trap(ServerLevel level) {
        if (level.isClientSide()) {
            SimplePathFinder.LOGGER.warn("TRAP: Didn't expect ServerLevel#isClientSide() to return true");
        }
    }

    public BatchScheduler(LevelNavDataSavedData data, ServerLevel level, UUID playerUUID, byte layer, int beginX, int beginZ, int deltaX, int deltaZ) {
        this.data = data;
        this.level = level;
        this.playerUUID = playerUUID;
        this.layer = layer;
        this.beginX = beginX;
        this.beginZ = beginZ;
        this.endX = beginX + deltaX;
        this.endZ = beginZ + deltaZ;
        // Calculate bits used to represent width/height
        // This saves us from multiplication into bit shift for bitset bit operation.
        this.widthBits = 32 - Integer.numberOfLeadingZeros(deltaX);
        this.heightBits = 32 - Integer.numberOfLeadingZeros(deltaZ);
        final int size = 1 << widthBits + heightBits;
        final int length = ((size - 1) >> 6) + 1;
        this.totalCount = (deltaX + 1) * (deltaZ + 1);
        intervalMask = Integer.highestOneBit((int) Math.sqrt(deltaX * deltaZ)) - 1;
        this.bitset = new long[length];
        fill(this.pool);
    }

    public boolean isInRange(ChunkPos p) {
        final int x = p.x();
        final int z = p.z();
        return x >= beginX && x <= endX && z >= beginZ && z <= endZ;
    }

    void fill(Stack<ChunkTask> pool) {
        for (int i = 0; i < MAX_CONCURRENT_TASKS; i++) {
            pool.push(new ChunkTask());
        }
    }

    void markVisited(int idx) {
        bitset[idx >>> 6] |= 1L << (idx & 63);
    }

    boolean isVisited(int idx) {
        return (bitset[idx >>> 6] & (1L << (idx & 63))) != 0;
    }

    int makeIdx(int x, int z) {
        return (x - beginX) << heightBits | (z - beginZ);
    }

    void markVisited(int x, int z) {
        markVisited(makeIdx(x, z));
    }

    boolean isVisited(int x, int z) {
        return isVisited(makeIdx(x, z));
    }

    private ServerPlayer getPlayer() {
        return level.getServer().getPlayerList().getPlayer(playerUUID);
    }

    /// Final step of BatchScheduler lifecycle, everything done
    void done() {
        // THREAD_LOCAL.remove();
        if (chunkDirty != 0) {
            data.setDirty();
        }
        trap(level);
        final var server = level.getServer();
        var player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.batch_success", chunkDirty, failureCounter, current, totalCount));
        SimplePathFinder.playerMadeServerNavDirty(player);
    }

    /// Run on server thread, final step of a chunk, schedule neighbors & return chunks
    private boolean afterCompletion(int x, int z, byte result) {
        ++current;
        if ((counter = ((counter + 1) & intervalMask)) == 0) {
            var player = getPlayer();
            if (player != null) {
                player.sendSystemMessage(Component.literal("[SPF] Built " + current + " / " + totalCount));
            }
        }
        if (result > 0) {
            if ((result & Solver.R_X) != 0 && x < endX) {
                // Kick-start calculation on x+ if we need to
                schedule(x + 1, z);
            }
            if ((result & Solver.R_Z) != 0 && z < endZ) {
                // Kick-start calculation on z+ if we need to
                schedule(x, z + 1);
            }
            ++chunkDirty;
        } else {
            ++failureCounter;
        }
        releaseChunk(x, z);
        // InitAuther97: do not reorder this above schedule
        // otherwise, live counter is down to zero too early
        if (--this.liveCounter == 0) {
            done();
            return true;
        }
        return true;
    }

    /// Run on server thread, utility, try to schedule a chunk, taking one free task to occupy
    private void schedule(int x, int z) {
        if (isVisited(x, z)) {
            return;
        }
        ++this.liveCounter;
        markVisited(x, z);
        pending.push(ChunkPos.pack(x, z));
        scheduleIfNeeded();
    }

    /// Run by server thread, step 4 for a chunk, {@link net.minecraft.util.thread.ConsecutiveExecutor}
    @Override
    public void run() {
        // Try to run tasks continuously until no task is available
        // When task is available / free slot is available, it is scheduled again
        if (!workStack.isEmpty()) {
            final var task = workStack.pop();
            solve(task);
            level.getServer().executeIfPossible(this);
        } else {
            scheduled = false;
        }
        // Try to assign all free slots to pending tasks
        while (!this.pending.isEmpty() && !this.pool.isEmpty()) {
            final var coord = pending.pop();
            final var task = pool.pop();
            task.x = ChunkPos.getX(coord);
            task.z = ChunkPos.getZ(coord);
            level.getServer().executeIfPossible(task);
        }
    }

    /// Run by command on server thread, before everything, begin execution
    public void fire() {
        // THREAD_LOCAL.set(this);
        afterCompletion(beginX, beginZ, (byte) 3);
    }

    /// Run on server thread, utility, schedule {@link BatchScheduler} if needed
    private void scheduleIfNeeded() {
        if (scheduled) return;
        scheduled = true;
        level.getServer().executeIfPossible(this);
    }

    /// Run on server thread, utility, return the occupied {@link ChunkTask}
    private void returnTask(ChunkTask task) {
        task.x = task.z = Integer.MIN_VALUE;
        task.state = ChunkTask.STATE_TICKET;
        pool.push(task);
        scheduleIfNeeded();
    }

    /// Run on server thread, add ticket, step 1 for a chunk, ensure chunk load
    private void acquireChunk(int x, int z) {
        final var chunkSource = level.getChunkSource();
        chunkSource.addTicket(TICKET, new ChunkPos(x, z));
        chunkSource.addTicket(TICKET, new ChunkPos(x + 1, z));
        chunkSource.addTicket(TICKET, new ChunkPos(x, z + 1));
    }

    /// Run on server thread, step 4 for a chunk, solve the chunk
    private void solve(ChunkTask task) {
        final int x = task.x, z = task.z;
        if (task.state != ChunkTask.STATE_SOLVE) {
            throw new IllegalStateException("ChunkTask is in incorrect state: cannot solve when state is " + task.state);
        }
        final byte result = data.levelNavData.buildFromLayerStart(level, data.levelNavData, layer, new ChunkPos(x, z));
        // the task can be returned
        returnTask(task);
        afterCompletion(x, z, result);
    }

    /// Run on server thread, final step for a chunk, release chunk load requirement
    private void releaseChunk(int cx, int cz) {
        final var chunkSource = level.getChunkSource();
        chunkSource.removeTicketWithRadius(TYPE_SOLVER, new ChunkPos(cx, cz), 0);
        chunkSource.removeTicketWithRadius(TYPE_SOLVER, new ChunkPos(cx + 1, cz), 0);
        chunkSource.removeTicketWithRadius(TYPE_SOLVER, new ChunkPos(cx, cz + 1), 0);
    }

    class ChunkTask implements Runnable, Supplier<CompletableFuture<Void>>, BiConsumer<Void, Throwable> {

        static final byte STATE_TICKET = 0, STATE_OBTAIN_FUTURE = 1, STATE_ENQUEUE = 2, STATE_SOLVE = 3;

        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        byte state;

        /// Run on server thread, step 1 for a chunk, begin async solve logic
        @Override
        public void run() {
            if (state != STATE_TICKET) {
                throw new IllegalStateException("ChunkTask is in incorrect state: cannot begin lifecycle when state is " + state);
            }
            acquireChunk(x, z);
            this.state = STATE_OBTAIN_FUTURE;
        }

        /// Run on async worker, step 2 for a chunk, request chunk future
        @Override
        public CompletableFuture<Void> get() {
            if (state != STATE_OBTAIN_FUTURE) {
                throw new IllegalStateException("ChunkTask is in incorrect state: cannot obtain future when state is " + state);
            }
            state = STATE_ENQUEUE;
            final var chunkSource = level.getChunkSource();
            return CompletableFuture.allOf(
                    chunkSource.getChunkFuture(x, z, ChunkStatus.FULL, true),
                    chunkSource.getChunkFuture(x + 1, z, ChunkStatus.FULL, true),
                    chunkSource.getChunkFuture(x, z + 1, ChunkStatus.FULL, true)
            );
        }

        /// Run on server thread, step 3 for a chunk, ready for execution
        @Override
        public void accept(Void unused, Throwable throwable) {
            if (state != STATE_ENQUEUE) {
                throw new IllegalStateException("ChunkTask is in incorrect state: cannot enqueue when state is " + state);
            }
            if (throwable != null) {
                SimplePathFinder.LOGGER.warn("Failed to load chunk in {} at [{}, {}]", level, x, z, throwable);
                releaseChunk(x, z);
                return;
            }
            this.state = STATE_SOLVE;
            workStack.push(this);
            scheduleIfNeeded();
        }
    }
}
