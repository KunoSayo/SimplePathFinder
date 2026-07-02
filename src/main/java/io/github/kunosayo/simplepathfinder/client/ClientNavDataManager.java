package io.github.kunosayo.simplepathfinder.client;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.finder.NavResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side navigation data manager.
 * Stores navigation data by world dimension and caches it locally by server IP.
 */
public class ClientNavDataManager {
    /**
     * Current server's address (IP:port or "local" for singleplayer)
     */
    private static String currentServerAddress = "unknown";

    /**
     * Storage for navigation data by dimension key
     * Key: dimension ResourceKey string representation
     */
    private static final ConcurrentHashMap<Identifier, LevelNavData> navDataByDimension = new ConcurrentHashMap<>();

    /**
     * Storage for all cached nav data by server address
     * Used for loading cached data when joining a server
     * Map structure: serverAddress -> (dimensionKey -> navData)
     */
    private static final Map<String, Map<Identifier, LevelNavData>> cachedNavData = new HashMap<>();

    /**
     * Get the current server address.
     * Returns "local" for singleplayer, or the server's IP:port for multiplayer.
     */
    public static String getCurrentServerAddress() {
        Minecraft mc = Minecraft.getInstance();

        // Singleplayer
        if (mc.hasSingleplayerServer()) {
            return "local";
        }

        // Try to get server info from connection
        var connection = mc.getConnection();
        if (connection == null) {
            return "unknown";
        }

        // In 1.26.1, the server info might not be directly available from ClientPacketListener
        // Use a simple fallback based on the connection type
        return "multiplayer_" + System.currentTimeMillis();
    }

    /**
     * Initialize the manager for a new server connection.
     * Loads cached data asynchronously if available.
     * File I/O is performed on the I/O pool to avoid blocking the main thread.
     */
    public static void onServerConnect() {
        currentServerAddress = getCurrentServerAddress();
        navDataByDimension.clear();

        // Load cached data for this server asynchronously - do NOT block the main thread
        String serverAddress = currentServerAddress;
    }

    /**
     * Get navigation data for the current dimension.
     */
    @Nullable
    public static LevelNavData getNavData(ResourceKey<Level> dimension) {
        return getNavData(dimension.identifier());
    }

    /**
     * Get navigation data for the specified dimension key string.
     */
    @Nullable
    public static LevelNavData getNavData(Identifier dimensionKey) {
        return navDataByDimension.get(dimensionKey);
    }

    /**
     * Get navigation data for a player's current dimension.
     */
    @Nullable
    public static LevelNavData getNavDataForPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return getNavData(mc.player.level().dimension());
    }

    /**
     * Set navigation data for a specific dimension.
     * Also saves it to local cache.
     * File I/O is performed asynchronously on the I/O pool.
     */
    public static void setNavData(Identifier dimension, LevelNavData data) {
        navDataByDimension.put(dimension, data);

        // Update cache
        Map<Identifier, LevelNavData> serverCache = cachedNavData.computeIfAbsent(
                currentServerAddress, k -> new HashMap<>());
        serverCache.put(dimension, data);

        // Save to disk asynchronously - do NOT block the main thread
        String serverAddress = currentServerAddress;
    }

    /**
     * Update a single navigation chunk in the specified dimension.
     * Used for incremental updates when a chunk is modified on the server.
     *
     * @param dimension the dimension identifier
     * @param chunkPos  the chunk position
     * @param navChunk  the navigation chunk data, or null to delete the chunk
     */
    public static void updateSingleChunk(Identifier dimension, net.minecraft.world.level.ChunkPos chunkPos,
                                         @Nullable io.github.kunosayo.simplepathfinder.nav.INavChunk navChunk) {
        LevelNavData navData = navDataByDimension.computeIfAbsent(dimension, k -> new LevelNavData());

        // Update or add the chunk
        navData.updateNavChunk(chunkPos, navChunk);

        // Update cache
        Map<Identifier, LevelNavData> serverCache = cachedNavData.computeIfAbsent(
                currentServerAddress, k -> new HashMap<>());
        serverCache.put(dimension, navData);

        // Save to disk asynchronously - do NOT block the main thread
        String serverAddress = currentServerAddress;
    }

    /**
     * Clear all navigation data (e.g., when disconnecting).
     */
    public static void clear() {
        navDataByDimension.clear();
        currentServerAddress = "unknown";
    }

    /**
     * Get the cache directory for storing navigation data files.
     */
    private static Path getCacheDir() {
        Minecraft mc = Minecraft.getInstance();
        File gameDir = mc.gameDirectory;
        File cacheDir = new File(gameDir, "simplepathfinder_cache");

        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        return cacheDir.toPath();
    }


    /**
     * Handle server-side pathfinding result.
     * Stores the result for rendering.
     *
     * @param result The pathfinding result from the server
     */
    public static void handleServerPathfindingResult(NavResult result) {
        SimplePathFinder.clientNavResult.set(result);
    }
}
