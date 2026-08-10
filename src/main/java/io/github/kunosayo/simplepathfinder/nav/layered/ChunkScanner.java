package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceData;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Chunk scanner that scans entire chunk in Y, Y+1, Y-1, Y+2, Y-2... pattern.
 * For each XZ position, creates multiple layers based on different Y heights.
 */
public class ChunkScanner {

    /**
     * Scan the entire chunk and build multiple navigation layers.
     *
     * @param level        The level
     * @param chunk        The nav chunk to populate
     * @param centerX      Center X position (world coordinates)
     * @param centerY      Starting Y position (world coordinates)
     * @param centerZ      Center Z position (world coordinates)
     * @param distanceData Player-specific block distance configuration
     * @return ScanResult containing layer information
     */
    public static ScanResult scanChunk(Level level, io.github.kunosayo.simplepathfinder.nav.INavChunk chunk, int centerX, int centerY, int centerZ, @Nullable PlayerBlockDistanceData distanceData) {
        // Clear existing layers
        chunk.removeAllLayers();

        int chunkX = SectionPos.blockToSectionCoord(centerX);
        int chunkZ = SectionPos.blockToSectionCoord(centerZ);
        int baseY = centerY;

        // Track for each XZ: its baseY (first discovered Y) and all discovered Y levels
        // Map<xz, BaseYData>
        Map<Integer, BaseYData> xzBaseData = new HashMap<>();

        // Collect all (layer, x, z, y) positions to create
        // Key = layer index, Value = list of (x, z, y) for that layer
        Map<Integer, List<PosData>> layerPositions = new HashMap<>();

        // Scan in Y, Y+1, Y-1, Y+2, Y-2, Y+3, Y-3... pattern
        int offset = 0;
        boolean goingUp = true;

        while (offset <= 200) {
            int currentY;

            if (offset == 0) {
                currentY = baseY;
                offset = 1;
            } else {
                currentY = goingUp ? baseY + offset : baseY - offset;
                goingUp = !goingUp;
                // Only increment offset after both up and down
                if (goingUp) {
                    offset++;
                }
            }

            // Check Y bounds (Minecraft Y: -64 to 320)
            if (currentY < -64 || currentY > 320) {
                continue;
            }

            // Scan all XZ positions at this Y level
            scanLevel(level, chunkX, chunkZ, currentY, xzBaseData, layerPositions);
        }

        // Check max layers limit
        var maxLayers = io.github.kunosayo.simplepathfinder.config.NavConfig.NAV_CONFIG.getLeft().maxLayers.get();
        if (layerPositions.size() > maxLayers) {
            return ScanResult.failed();
        }

        // Create the layers
        int minLayer = Integer.MAX_VALUE;
        int maxLayer = Integer.MIN_VALUE;

        for (Map.Entry<Integer, List<PosData>> entry : layerPositions.entrySet()) {
            int layerIndex = entry.getKey();
            byte layerByte = (byte) layerIndex;

            // Create or get layer
            var optionalLayered = chunk.getLayer(layerByte, LayeredNavChunk::getDefault);
            if (optionalLayered.isEmpty()) {
                continue;
            }
            minLayer = Math.min(minLayer, layerIndex);
            maxLayer = Math.max(maxLayer, layerIndex);

            var layeredChunk = (LayeredNavChunk) optionalLayered.get();
            layeredChunk.setParentChunk(chunk);
            layeredChunk.setLayer(layerByte);

            // Populate walkY for this layer
            for (PosData posData : entry.getValue()) {
                int idx = AbstractLayeredNavChunk.convertToIndex(posData.x & 15, posData.z & 15);
                layeredChunk.walkY[idx] = (short) posData.y;
            }

            // Mark distances for adjacent positions
            markAdjacentDistances(layeredChunk, entry.getValue(), level, chunkX, chunkZ, distanceData);
            layeredChunk.updateChunkData();
        }

        int layerCount = layerPositions.size();
        if (minLayer == Integer.MAX_VALUE) {
            return new ScanResult(0, 0, 0, true);
        }
        return new ScanResult(layerCount, minLayer, maxLayer, true);
    }

    /**
     * Scan all XZ positions at a specific Y level.
     */
    private static void scanLevel(Level level, int chunkX, int chunkZ, int y,
                                  Map<Integer, BaseYData> xzBaseData,
                                  Map<Integer, List<PosData>> layerPositions) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = SectionPos.sectionToBlockCoord(chunkX, x);
                int worldZ = SectionPos.sectionToBlockCoord(chunkZ, z);

                // Check if this position is walkable
                if (!isWalkableAt(level, worldX, y, worldZ)) {
                    continue;
                }

                int xzKey = (x << 8) | z;
                BaseYData baseData = xzBaseData.get(xzKey);

                if (baseData == null) {
                    // First discovery of this XZ - this becomes baseY (layer 0)
                    baseData = new BaseYData(y);
                    xzBaseData.put(xzKey, baseData);
                    addLayerPosition(layerPositions, 0, x, z, y);
                } else {
                    // XZ already has a baseY
                    // Layer index by discovery order: 1,2,3... for up, -1,-2,-3... for down
                    int layerIndex = baseData.getLayerIndex(y);

                    // Check if this Y level was already recorded for this XZ
                    if (!baseData.recordedYLevels.contains(y)) {
                        baseData.recordedYLevels.add(y);
                        addLayerPosition(layerPositions, layerIndex, x, z, y);
                    }
                }
            }
        }
    }

    /**
     * Add a position to the layer positions map.
     */
    private static void addLayerPosition(Map<Integer, List<PosData>> layerPositions, int layerIndex, int x, int z, int y) {
        layerPositions.computeIfAbsent(layerIndex, k -> new ArrayList<>())
                .add(new PosData(x, z, y));
    }

    /**
     * Check if position is walkable (y is air, y-1 is solid ground).
     */
    private static boolean isWalkableAt(Level level, int x, int y, int z) {

        var mutable = new BlockPos.MutableBlockPos(x, y, z);


        // Check if y+1 is air/passable
        mutable.move(0, 1, 0);
        if (!NavUtil.considerSafeCross(level, mutable)) {
            return false;
        }
        mutable.move(0, -1, 0);
        // Check if y is air/passable
        if (!NavUtil.considerSafeCross(level, mutable)) {
            return false;
        }

        // Check if y-1 is solid ground
        mutable.move(0, -1, 0);
        var groundBlock = level.getBlockState(mutable);
        return NavUtil.considerSafeGround(level, mutable, groundBlock);
    }

    /**
     * Mark distances between adjacent walkable positions in the same layer.
     * Uses LayeredNavChunk.getDistance to calculate proper distance values.
     */
    private static void markAdjacentDistances(LayeredNavChunk chunk, List<PosData> positions, Level level, int chunkX, int chunkZ, @Nullable PlayerBlockDistanceData distanceData) {
        for (PosData posData : positions) {
            int x = posData.x & 15;
            int z = posData.z & 15;
            int y = posData.y;

            // Check +X direction
            {

                int dist = calculateDistance(level, chunkX, chunkZ, x, y, z, x + 1, z, distanceData);
                chunk.distances[LayeredNavChunk.getDistanceIdx(x, z, false)] = dist;
            }

            // Check +Z direction
            {

                int dist = calculateDistance(level, chunkX, chunkZ, x, y, z, x, z + 1, distanceData);
                chunk.distances[LayeredNavChunk.getDistanceIdx(x, z, true)] = dist;

            }
        }
    }

    /**
     * Calculate distance between two positions using LayeredNavChunk.getDistance logic.
     * This delegates to the existing distance calculation method.
     */
    private static int calculateDistance(Level level, int chunkX, int chunkZ,
                                         int sx, int sy, int sz, int tx, int tz,
                                         @Nullable PlayerBlockDistanceData distanceData) {
        // Use the existing getDistance method from LayeredNavChunk
        long dResult = LayeredNavChunk.getDistance(
                level,
                SectionPos.sectionToBlockCoord(chunkX, sx),
                sy,
                SectionPos.sectionToBlockCoord(chunkZ, sz),
                SectionPos.sectionToBlockCoord(chunkX, tx),
                SectionPos.sectionToBlockCoord(chunkZ, tz),
                distanceData
        );

        // Extract distance from the packed result
        return LayeredNavChunk.unpackDistance(dResult);
    }

    /**
     * Data for base Y of an XZ position.
     * Tracks layer indices by discovery order: up=1,2,3... down=-1,-2,-3...
     */
    private static class BaseYData {
        final int baseY;
        final Set<Integer> recordedYLevels = new HashSet<>();
        int nextLayerUp = 1;   // Next layer index for Y > baseY
        int nextLayerDown = -1; // Next layer index for Y < baseY

        BaseYData(int baseY) {
            this.baseY = baseY;
            recordedYLevels.add(baseY);
        }

        /**
         * Get the layer index for a given Y level by discovery order.
         * Returns positive incrementing sequence for Y > baseY: 1, 2, 3...
         * Returns negative decrementing sequence for Y < baseY: -1, -2, -3...
         */
        int getLayerIndex(int y) {
            if (y == baseY) {
                return 0;
            } else if (y > baseY) {
                // Assign and return next positive layer index
                int layer = nextLayerUp;
                nextLayerUp++;
                return layer;
            } else {
                // Assign and return next negative layer index
                int layer = nextLayerDown;
                nextLayerDown--;
                return layer;
            }
        }
    }

    /**
     * Position data within a layer.
     */
    private static class PosData {
        final int x;
        final int z;
        final int y;

        PosData(int x, int z, int y) {
            this.x = x;
            this.z = z;
            this.y = y;
        }
    }
}
