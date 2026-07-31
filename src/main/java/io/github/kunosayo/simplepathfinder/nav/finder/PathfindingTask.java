package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
import io.github.kunosayo.simplepathfinder.nav.progress.PathfindingContext;
import io.github.kunosayo.simplepathfinder.network.PathfindingResultPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a pathfinding task.
 */
public record PathfindingTask(WeakReference<MinecraftServer> server, UUID player, LevelNavDataSavedData data,
                              BlockPos startPos, BlockPos targetPos,
                              String targetDesc, NavNotificationConfig config,
                              long submissionTime) implements Runnable {
    @Override
    public void run() {
        var task = this;
        var server = task.server.get();
        if (server == null) {
            return;
        }
        try {

            BlockPos targetPos = task.targetPos;
            String targetDesc = task.targetDesc;

            // Execute pathfinding with progress tracking
            var ctx = new PathfindingContext(player);
            ServerPathfindingManager.startProgress(ctx);
            long startTime = System.nanoTime();
            Optional<NavResult> result = data.levelNavData.findNav(startPos, targetPos, ctx);
            long endTime = System.nanoTime();
            long dur = endTime - startTime;
            long durMs = dur / 1_000_000;
            if (durMs >= 1000) {
                SimplePathFinder.LOGGER.debug("Player {} found nav in {} ms", player, durMs);
            }

            var _ = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(this.player);
                if (player != null) {
                    ServerPathfindingManager.sendPathfindingResult(player, targetPos, targetDesc, result, task.config);
                }
            });

        } catch (Exception e) {
            // Error during pathfinding
            SimplePathFinder.LOGGER.error("Error during pathfinding for player {}", task.player, e);

            var _ = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(this.player);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new PathfindingResultPacket("simple_path_finder.nav.no_path"));
                }
            });
        }

    }
}
