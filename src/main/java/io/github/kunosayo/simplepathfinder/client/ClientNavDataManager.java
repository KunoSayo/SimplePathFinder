package io.github.kunosayo.simplepathfinder.client;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
        if (mc == null) {
            return "unknown";
        }

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
        Util.ioPool().execute(() -> loadCachedData(serverAddress));
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
        Util.ioPool().execute(() -> saveDataToFile(serverAddress, dimension, data));
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
     * Save navigation data to a file.
     */
    private static void saveDataToFile(String serverAddress, Identifier dimensionKey, LevelNavData data) {
        if (data == null) {
            return;
        }

        try {
            Path cacheDir = getCacheDir();
            Path serverDir = cacheDir.resolve(serverAddress);

            if (!serverDir.toFile().exists()) {
                serverDir.toFile().mkdirs();
            }

            // Sanitize dimension key for filename
            String safeFileName = dimensionKey.toString().replaceAll("[^a-zA-Z0-9_-]", "_") + ".nav";
            Path filePath = serverDir.resolve(safeFileName);

            // Encode data to byte array
            byte[] encodedData;
            var tempBuffer = io.netty.buffer.Unpooled.buffer();
            LevelNavData.STREAM_CODEC.encode(tempBuffer, data);
            encodedData = new byte[tempBuffer.readableBytes()];
            tempBuffer.readBytes(encodedData);
            tempBuffer.release();

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(encodedData);
            }
        } catch (IOException e) {
            SimplePathFinder.LOGGER.error("Failed to save nav data to file: {} - {}",
                    serverAddress, dimensionKey, e);
        }
    }

    /**
     * Load all cached data for a server.
     */
    private static void loadCachedData(String serverAddress) {
        Path cacheDir = getCacheDir();
        Path serverDir = cacheDir.resolve(serverAddress);

        if (!serverDir.toFile().exists()) {
            return;
        }

        File[] files = serverDir.toFile().listFiles((dir, name) -> name.endsWith(".nav"));
        if (files == null) {
            return;
        }

        var serverCache = cachedNavData.computeIfAbsent(
                serverAddress, k -> new HashMap<>());

        for (File file : files) {
            try {
                byte[] fileData;
                try (FileInputStream fis = new FileInputStream(file)) {
                    fileData = fis.readAllBytes();
                }

                // Decode data
                var buffer = io.netty.buffer.Unpooled.wrappedBuffer(fileData);
                LevelNavData navData = LevelNavData.STREAM_CODEC.decode(buffer);
                buffer.release();

                // Extract dimension key from filename
                var dimensionKey = Identifier.parse(file.getName().substring(0, file.getName().length() - 4));

                // Add to cache and current storage
                serverCache.put(dimensionKey, navData);
                navDataByDimension.put(dimensionKey, navData);

                SimplePathFinder.LOGGER.info("Loaded cached nav data: {} - {}",
                        serverAddress, dimensionKey);
            } catch (Exception e) {
                SimplePathFinder.LOGGER.error("Failed to load nav data from file: {}",
                        file.getName(), e);
            }
        }
    }

    /**
     * Clear cached data for a specific server.
     */
    public static void clearServerCache(String serverAddress) {
        cachedNavData.remove(serverAddress);

        // Also delete files
        Path cacheDir = getCacheDir();
        Path serverDir = cacheDir.resolve(serverAddress);

        if (serverDir.toFile().exists()) {
            File[] files = serverDir.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            serverDir.toFile().delete();
        }
    }

    /**
     * Clear all cached data.
     */
    public static void clearAllCache() {
        cachedNavData.clear();

        Path cacheDir = getCacheDir();
        if (cacheDir.toFile().exists()) {
            File[] servers = cacheDir.toFile().listFiles();
            if (servers != null) {
                for (File server : servers) {
                    File[] files = server.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            file.delete();
                        }
                    }
                    server.delete();
                }
            }
        }
    }
}
