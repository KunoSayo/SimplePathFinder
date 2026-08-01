package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceData;
import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.finder.EdgeConsumer;
import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for layered navigation chunk functionality
 */
public interface ILayeredNavChunk {
    int INVALID_WALK_Y = -9961;

    /**
     * Get the layer number
     *
     * @return the layer number
     */
    byte getLayer();

    /**
     * Get the parent navigation chunk
     *
     * @return the parent NavChunk
     */
    INavChunk getParentChunk();

    /**
     * Set the parent navigation chunk
     *
     * @param parentChunk the parent NavChunk
     */
    void setParentChunk(INavChunk parentChunk);

    /**
     * Set the layer number
     *
     * @param layer the layer number
     */
    void setLayer(byte layer);

    /**
     * Get walk Y coordinate at the specified position
     *
     * @param x the x coordinate in chunk [0, 15]
     * @param z the z coordinate in chunk [0, 15]
     * @return the walk Y coordinate, or -9961 if cannot reach
     */
    int getWalkY(int x, int z);

    /**
     * Get walk Y coordinate at the specified chunk position
     *
     * @param chunkInnerPos the chunk inner position
     * @return the walk Y coordinate
     */
    default int getWalkY(ChunkInnerPos chunkInnerPos) {
        return getWalkY(chunkInnerPos.x, chunkInnerPos.z);
    }

    /**
     * Get distance in specified direction
     *
     * @param x   the x coordinate in chunk [0, 15]
     * @param z   the z coordinate in chunk [0, 15]
     * @param isZ whether to get distance in Z direction
     * @return the distance
     */
    int getDistance(int x, int z, boolean isZ);

    /**
     * @param x   the x coordinate in chunk [0, 15]
     * @param z   the z coordinate in chunk [0, 15]
     * @return the distance walk to +x
     */
    int getPositiveDistanceX(int x, int z);

    /**
     * @param x   the x coordinate in chunk [0, 15]
     * @param z   the z coordinate in chunk [0, 15]
     * @return the distance walk to +z
     */
    int getPositiveDistanceZ(int x, int z);

    /**
     * Set distance in specified direction
     *
     * @param x     the x coordinate in chunk [0, 15]
     * @param z     the z coordinate in chunk [0, 15]
     * @param isZ   whether to set distance in Z direction (false for X direction)
     * @param value the distance value to set
     */
    void setDistance(int x, int z, boolean isZ, short value);

    /**
     * Get checked distance in specified direction
     *
     * @param x   the x coordinate in chunk [0, 15]
     * @param z   the z coordinate in chunk [0, 15]
     * @param isZ whether to get distance in Z direction
     * @return the distance or -1 if cannot walk
     */
    int getDistanceChecked(int x, int z, boolean isZ);

    /**
     * Get distance at block position in specified direction
     *
     * @param pos the block position
     * @param isZ whether to get distance in Z direction
     * @return the distance
     */
    int getDistance(BlockPos pos, boolean isZ);

    /**
     * Check if the position can be walked on
     *
     * @param x the x coordinate in chunk [0, 15]
     * @param z the z coordinate in chunk [0, 15]
     * @return true if can walk, false otherwise
     */
    boolean canWalk(int x, int z);

    /**
     * Check if this layer has any valid walkable positions
     *
     * @return true if any position is valid, false otherwise
     */
    default boolean isAnyValid() {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (canWalk(i, j)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parse navigation data for this layer
     *
     * @param level         the level to parse
     * @param trustedCenter the trusted center position
     * @param distanceData  the player-specific block distance configuration, null to use global config
     * @return A marker noting the directions neighbors are ready
     */
    byte parse(Level level, BlockPos trustedCenter, @Nullable PlayerBlockDistanceData distanceData);

    /**
     * Check if walk Y coordinate is valid
     *
     * @param y the Y coordinate to check
     * @return true if valid, false otherwise
     */
    default boolean isWalkYValid(int y) {
        return y != INVALID_WALK_Y;
    }

    default boolean markVisited(int cacheIndex, int cnt, BlockPos pos) {
        return markVisited(cacheIndex, cnt, pos.getX(), pos.getZ());
    }

    boolean markVisited(int cacheIndex, int cnt, int tx, int tz);

    default void checkExtraPath(NavPathFinder finder, NavPathFinder.SearchNode node, EdgeConsumer edgeConsumer) {
    }

    default NavPathFinder.SearchNode getSearchNode(NavPathFinder finder, int tx, int tz) {
        long vKey = NavPathFinder.SearchedPos.toLong(getLayer(), tx, tz);
        return finder.visitedNodes.get(vKey);
    }

    default void putSearchNode(NavPathFinder finder, NavPathFinder.SearchNode node) {
        long vKey = NavPathFinder.SearchedPos.toLong(getLayer(), node.x, node.z);
        finder.visitedNodes.put(vKey, node);
    }
}