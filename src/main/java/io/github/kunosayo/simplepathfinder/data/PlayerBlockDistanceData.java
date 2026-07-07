package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Player-specific block distance configuration.
 * Stores custom distance costs for blocks per player.
 * Supports both block IDs and block tags.
 */
public record PlayerBlockDistanceData(
        Map<BlockDistanceKey, Integer> distanceMap,
        int defaultDistance
) {
    /**
     * Default configuration with empty map and default distance of 10.
     */
    public static final PlayerBlockDistanceData DEFAULT = new PlayerBlockDistanceData(Map.of(), 10);

    /**
     * Codec for serialization
     */
    public static final Codec<PlayerBlockDistanceData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.unboundedMap(BlockDistanceKey.CODEC, Codec.INT)
                            .optionalFieldOf("distance_map", Map.of())
                            .forGetter(PlayerBlockDistanceData::distanceMap),
                    Codec.INT.optionalFieldOf("default_distance", 10)
                            .forGetter(PlayerBlockDistanceData::defaultDistance)
            ).apply(instance, PlayerBlockDistanceData::new)
    );

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, PlayerBlockDistanceData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                // Encode the map size first
                ByteBufCodecs.VAR_INT.encode(buf, data.distanceMap.size());
                // Encode each entry
                for (Map.Entry<BlockDistanceKey, Integer> entry : data.distanceMap.entrySet()) {
                    BlockDistanceKey.STREAM_CODEC.encode(buf, entry.getKey());
                    ByteBufCodecs.VAR_INT.encode(buf, entry.getValue());
                }
                // Encode default distance
                ByteBufCodecs.VAR_INT.encode(buf, data.defaultDistance);
            },
            (buf) -> {
                // Decode map size
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                Map<BlockDistanceKey, Integer> map = new HashMap<>();
                // Decode each entry
                for (int i = 0; i < size; i++) {
                    BlockDistanceKey key = BlockDistanceKey.STREAM_CODEC.decode(buf);
                    Integer value = ByteBufCodecs.VAR_INT.decode(buf);
                    map.put(key, value);
                }
                // Decode default distance
                int defaultDistance = ByteBufCodecs.VAR_INT.decode(buf);
                return new PlayerBlockDistanceData(map, defaultDistance);
            }
    );

    /**
     * Get the distance for a specific block.
     * Checks both specific block IDs and tags. Returns the custom distance if configured,
     * otherwise returns the default distance.
     *
     * @param block the block to get distance for
     * @return the distance value for this block
     */
    public int getDistance(Block block) {
        // First, check for exact block ID match
        for (Map.Entry<BlockDistanceKey, Integer> entry : distanceMap.entrySet()) {
            if (entry.getKey().matches(block)) {
                return Math.min(entry.getValue(), Integer.MAX_VALUE >> 1);
            }
        }
        return defaultDistance;
    }

    /**
     * Create a new configuration with updated block/tag distance.
     *
     * @param key      the block or tag key
     * @param distance the distance value
     * @return a new PlayerBlockDistanceData with the updated value
     */
    public PlayerBlockDistanceData withDistance(BlockDistanceKey key, int distance) {
        Map<BlockDistanceKey, Integer> newMap = new HashMap<>(this.distanceMap);
        newMap.put(key, distance);
        return new PlayerBlockDistanceData(newMap, this.defaultDistance);
    }

    /**
     * Remove a distance entry by key.
     *
     * @param key the block or tag key to remove
     * @return a new PlayerBlockDistanceData without the entry
     */
    public PlayerBlockDistanceData removeDistance(BlockDistanceKey key) {
        Map<BlockDistanceKey, Integer> newMap = new HashMap<>(this.distanceMap);
        newMap.remove(key);
        return new PlayerBlockDistanceData(newMap, this.defaultDistance);
    }

    /**
     * Create a new configuration with updated default distance.
     *
     * @param defaultDistance the new default distance
     * @return a new PlayerBlockDistanceData with the updated default
     */
    public PlayerBlockDistanceData withDefaultDistance(int defaultDistance) {
        return new PlayerBlockDistanceData(this.distanceMap, defaultDistance);
    }
}
