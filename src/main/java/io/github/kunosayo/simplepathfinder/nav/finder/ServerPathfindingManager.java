package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
import io.github.kunosayo.simplepathfinder.nav.progress.PathfindingContext;
import io.github.kunosayo.simplepathfinder.network.PathfindingResultPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side pathfinding manager with global queue system.
 * Ensures the server has at most 2 executing and 8 queued pathfinding requests total.
 */
public class ServerPathfindingManager {
    /**
     * Maximum number of concurrent executing pathfinding requests
     */
    private static final int MAX_CONCURRENT_EXECUTION = 2;

    /**
     * Maximum number of queued pathfinding requests (server-wide)
     */
    private static final int MAX_QUEUED_REQUESTS = 8;

    /**
     * Thread pool for executing pathfinding tasks
     */
    private static final ExecutorService executorService;

    /**
     * Queue for pending pathfinding requests with their submission time
     */
    private static final ConcurrentLinkedQueue<PathfindingTask> taskQueue;
    private static final Map<UUID, Boolean> queuePlayer = new ConcurrentHashMap<>();

    private static final AtomicInteger executingCount = new AtomicInteger(0);

    private static final ConcurrentHashMap<UUID, PathfindingContext> activeCtxs = new ConcurrentHashMap<>();


    static {
        // Create a fixed thread pool for pathfinding (at least 2 concurrent)
        int threads = Math.max(MAX_CONCURRENT_EXECUTION, Runtime.getRuntime().availableProcessors());
        executorService = new ThreadPoolExecutor(
                MAX_CONCURRENT_EXECUTION,
                threads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS),
                r -> {
                    Thread t = new Thread(r, "Pathfinding-Worker");
                    t.setDaemon(true);
                    return t;
                }
        );

        taskQueue = new ConcurrentLinkedQueue<>();
    }


    /**
     * Submit a pathfinding request.
     *
     * @param player     The player requesting pathfinding
     * @param targetPos  The target position to pathfind to
     * @param targetDesc Optional description of the target (e.g., player name)
     * @param config     Notification config for this request
     * @return true if request was accepted, false if queue is full
     */
    public static boolean submitRequest(ServerPlayer player, BlockPos targetPos, String targetDesc, NavNotificationConfig config) {
        // Check if server queue is full
        if (taskQueue.size() >= MAX_QUEUED_REQUESTS) {
            // Queue is full, send rejection message
            PacketDistributor.sendToPlayer(player, new PathfindingResultPacket("simple_path_finder.nav.server_busy"));
            return false;
        }

        if (queuePlayer.containsKey(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, new PathfindingResultPacket("simple_path_finder.nav.already_pathfinding"));
            return false;
        }

        var data = LevelNavDataSavedData.loadFromLevel(player.level());

        // Create and add task to queue
        PathfindingTask task = new PathfindingTask(new WeakReference<>(player.level().getServer()), player.getUUID(),
                data, player.blockPosition(), targetPos, targetDesc, config, System.currentTimeMillis());
        queuePlayer.put(player.getUUID(), Boolean.TRUE);
        if (!taskQueue.offer(task)) {
            // Failed to add to queue (shouldn't happen with LinkedBlockingQueue, but just in case)
            PacketDistributor.sendToPlayer(player, new PathfindingResultPacket("simple_path_finder.nav.already_pathfinding"));
            queuePlayer.remove(player.getUUID());
            return false;
        }


        // Try to process the queue
        tryProcessQueue();
        return true;
    }

    /**
     * Try to process queued tasks if there's available capacity.
     */
    private static void tryProcessQueue() {
        while (executingCount.get() < MAX_CONCURRENT_EXECUTION && !taskQueue.isEmpty()) {
            PathfindingTask task = taskQueue.poll();
            if (task != null) {
                executingCount.incrementAndGet();
                executorService.submit(() -> {
                    try {
                        task.run();
                    } finally {
                        executingCount.decrementAndGet();
                        queuePlayer.remove(task.player());
                    }
                    tryProcessQueue();
                });
            }
        }
    }

    /**
     * Helper method to send pathfinding result to player.
     * Must be called on the main thread.
     */
    public static void sendPathfindingResult(ServerPlayer player, BlockPos targetPos, String targetDesc,
                                             Optional<NavResult> result, NavNotificationConfig config) {
        if (result.isPresent() && result.get().modNavResult != null) {
            // Success - send path result
            PacketDistributor.sendToPlayer(player, new PathfindingResultPacket(result.get().modNavResult));

            // Also send success message if config allows
            if (config.notifyOnSuccess()) {
                player.sendSystemMessage(Component.translatable("simple_path_finder.nav.starting",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                if (!targetDesc.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("simple_path_finder.nav.to_player", targetDesc));
                }
            }
        } else {
            PacketDistributor.sendToPlayer(player, new PathfindingResultPacket(
                    "simple_path_finder.nav.no_path"));
        }
    }

    public static void startProgress(PathfindingContext ctx) {
        if (ctx != PathfindingContext.DUMMY) {
            if (ctx.getPlayerId() != null) {
                activeCtxs.put(ctx.getPlayerId(), ctx);
            }
        }
    }

    public static PathfindingContext getProgressContext(UUID playerId) {
        return activeCtxs.get(playerId);
    }

    public static void removeProgressContext(UUID playerId) {
        activeCtxs.remove(playerId);
    }
}