package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.NavChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
    void setParentChunk(NavChunk parentChunk);

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
     */
    void parse(Level level, BlockPos trustedCenter);

    /**
     * Check if walk Y coordinate is valid
     *
     * @param y the Y coordinate to check
     * @return true if valid, false otherwise
     */
    default boolean isWalkYValid(int y) {
        return y != INVALID_WALK_Y;
    }
}