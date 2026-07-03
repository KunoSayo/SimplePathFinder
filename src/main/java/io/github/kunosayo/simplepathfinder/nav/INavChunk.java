package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.nav.finder.EdgeConsumer;
import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;


/**
 * Interface for navigation chunk functionality
 */
public interface INavChunk {

    StreamCodec<ByteBuf, INavChunk> TYPED_NAV_CHUNK_CODEC = StreamCodec.of((buffer, value) -> {
        if (value instanceof NavChunk layeredNavChunk) {
            buffer.writeByte(0);
            NavChunk.STREAM_CODEC.encode(buffer, layeredNavChunk);
            return;
        }

        throw new IllegalArgumentException("Not supported nav chunk");
    }, buffer -> {
        // todo: use interface.
        byte type = buffer.readByte();
        if (type == 0) {
            return NavChunk.STREAM_CODEC.decode(buffer);
        }

        throw new IllegalArgumentException("Not supported nav chunk");
    });

    /**
     * Get the chunk position
     *
     * @return the chunk position
     */
    ChunkPos getChunkPos();

    /**
     * Set the chunk position
     */
    void setChunkPos(ChunkPos chunkPos);

    /**
     * Get a specific layer, creating it if it doesn't exist
     *
     * @param layer    the layer index
     * @param supplier supplier for creating new layers
     * @return optional containing the layer if it exists or was created. Empty if exceeded max layer or supplier return null.
     */
    Optional<ILayeredNavChunk> getLayer(int layer, java.util.function.Supplier<LayeredNavChunk> supplier);

    /**
     * Get navigation layer for a specific block position
     *
     * @param pos the block position
     * @return optional containing the layer that can walk to this position
     */
    Stream<ILayeredNavChunk> getLayerNav(BlockPos pos);

    /**
     * Get all layers that are within 1 block of the target Y position
     *
     * @param target the target block position
     * @return stream of matching layers
     */
    default Stream<ILayeredNavChunk> getLayers(BlockPos target) {
        var inner = ChunkInnerPos.get(target);
        return getLayers().filter(layer -> Math.abs(layer.getWalkY(inner.x, inner.z) - target.getY()) <= 1);
    }

    default Stream<ILayeredNavChunk> getLayers() {
        return getLayersCollection().stream();
    }

    Collection<ILayeredNavChunk> getLayersCollection();

    /**
     * Process all layers within 1 block of the target Y position with distance information
     *
     * @param target   the target block position
     * @param distance the current distance
     * @param consumer consumer for each edge info
     */
    default void getLayers(BlockPos target, int distance, EdgeConsumer consumer) {
        getLayers(target.getX(), target.getY(), target.getZ(), distance, consumer);
    }

    void getLayers(int x, int y, int z, int distance, EdgeConsumer consumer);

    /**
     * Get the nearest layer within 1 block of the specified Y position
     *
     * @param bx the block x coordinate
     * @param y  the y coordinate
     * @param bz the block z coordinate
     * @return optional containing the nearest layer
     */
    Optional<ILayeredNavChunk> getNearestLayer(int bx, int y, int bz);

    /**
     * Get the nearest walkable Y coordinate within 1 block of the specified Y position
     *
     * @param bx the block x coordinate
     * @param y  the y coordinate
     * @param bz the block z coordinate
     * @return optional int containing the nearest walk Y
     */
    java.util.OptionalInt getNearestWalkY(int bx, int y, int bz);

    /**
     * Get distance from position in specified direction
     *
     * @param pos the position to sample distance
     * @param isZ whether to sample in Z direction
     * @return the distance or -1 if not found
     */
    default int getDistance(BlockPos pos, boolean isZ) {
        return getDistance(pos.getX(), pos.getY(), pos.getZ(), isZ);
    }

    int getDistance(int x, int y, int z, boolean isZ);

    /**
     * Remove a layered navigation chunk
     *
     * @param layeredNavChunk the chunk to remove
     */
    void removeNavChunk(ILayeredNavChunk layeredNavChunk);

    /**
     * Get the number of layers in this navigation chunk
     *
     * @return the number of layers
     */
    int getLayerCount();

    /**
     * Get all navigation links from a specific position
     *
     * @param pos the chunk inner position
     * @return list of navigation links from this position
     */
    List<NavLink> getNavLinks(ChunkInnerPos pos);

    /**
     * Get all navigation links in this chunk
     *
     * @return map of position to navigation links
     */
    Map<ChunkInnerPos, List<NavLink>> getAllNavLinks();

    /**
     * Add a navigation link
     *
     * @param from the starting position
     * @param link the navigation link
     */
    void addNavLink(ChunkInnerPos from, NavLink link);

    /**
     * Remove all navigation links from a specific position
     *
     * @param pos the position to remove links from
     */
    void removeNavLinks(ChunkInnerPos pos);

    /**
     * Clear all navigation links
     */
    void clearNavLinks();
}