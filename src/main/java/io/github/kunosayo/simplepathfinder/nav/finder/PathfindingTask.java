package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
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
public record PathfindingTask(WeakReference<MinecraftServer> server, UUID player, BlockPos targetPos,
                              String targetDesc, NavNotificationConfig config,
                              long submissionTime) implements Runnable {
    @Override
    public void run() {
        var task = this;
        var server = task.server.get();
        if (server == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(task.player);
        if (player == null) {
            return;
        }
        try {

            BlockPos targetPos = task.targetPos;
            String targetDesc = task.targetDesc;

            // Get player's current position
            BlockPos startPos = player.blockPosition();

            // Get nav data from server
            var level = player.level();
            var data = LevelNavDataSavedData.loadFromLevel(level);

            // Execute pathfinding (no timeout - let it run as long as needed)
            Optional<NavResult> result = data.levelNavData.findNav(startPos, targetPos);

            ServerPathfindingManager.sendPathfindingResult(player, targetPos, targetDesc, result, task.config);

        } catch (Exception e) {
            // Error during pathfinding
            SimplePathFinder.LOGGER.error("Error during pathfinding for player {}", task.player, e);

            PacketDistributor.sendToPlayer(player, new PathfindingResultPacket("simple_path_finder.nav.no_path"));
        }

    }
}
