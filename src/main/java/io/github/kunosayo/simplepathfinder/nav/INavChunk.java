package io.github.kunosayo.simplepathfinder.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Interface for navigation chunk functionality
 */
public interface INavChunk {

    /**
     * Get the chunk position
     * @return the chunk position
     */
    ChunkPos getChunkPos();

    /**
     * Set the chunk position
     */
    void setChunkPos(ChunkPos chunkPos);

    /**
     * Get a specific layer, creating it if it doesn't exist
     * @param layer the layer index
     * @param supplier supplier for creating new layers
     * @return optional containing the layer if it exists or was created
     */
    Optional<ILayeredNavChunk> getLayer(int layer, java.util.function.Supplier<LayeredNavChunk> supplier);

    /**
     * Get navigation layer for a specific block position
     * @param pos the block position
     * @return optional containing the layer that can walk to this position
     */
    Optional<ILayeredNavChunk> getLayerNav(BlockPos pos);

    /**
     * Get all layers that are within 1 block of the target Y position
     * @param target the target block position
     * @return stream of matching layers
     */
    Stream<ILayeredNavChunk> getLayers(BlockPos target);

    /**
     * Process all layers within 1 block of the target Y position
     * @param target the target block position
     * @param consumer consumer for each matching layer
     */
    void getLayers(BlockPos target, Consumer<ILayeredNavChunk> consumer);

    /**
     * Process all layers within 1 block of the target Y position with distance information
     * @param target the target block position
     * @param distance the current distance
     * @param consumer consumer for each edge info
     */
    void getLayers(BlockPos target, int distance, Consumer<NavPathFinder.EdgeInfo> consumer);

    /**
     * Get the nearest layer within 1 block of the specified Y position
     * @param bx the block x coordinate
     * @param y the y coordinate
     * @param bz the block z coordinate
     * @return optional containing the nearest layer
     */
    Optional<ILayeredNavChunk> getNearestLayer(int bx, int y, int bz);

    /**
     * Get the nearest walkable Y coordinate within 1 block of the specified Y position
     * @param bx the block x coordinate
     * @param y the y coordinate
     * @param bz the block z coordinate
     * @return optional int containing the nearest walk Y
     */
    java.util.OptionalInt getNearestWalkY(int bx, int y, int bz);

    /**
     * Get distance from position in specified direction
     * @param pos the position to sample distance
     * @param isZ whether to sample in Z direction
     * @return the distance or -1 if not found
     */
    int getDistance(BlockPos pos, boolean isZ);

    /**
     * Remove a layered navigation chunk
     * @param layeredNavChunk the chunk to remove
     */
    void removeNavChunk(ILayeredNavChunk layeredNavChunk);

    /**
     * Get the number of layers in this navigation chunk
     * @return the number of layers
     */
    int getLayerCount();
}