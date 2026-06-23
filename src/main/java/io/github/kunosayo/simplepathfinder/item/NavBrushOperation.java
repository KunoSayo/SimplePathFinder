package io.github.kunosayo.simplepathfinder.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * Brush operation for navigation brush tool.
 * Determines what action the brush performs on navigation edges.
 */
public enum NavBrushOperation {
    /**
     * Delete operation - removes navigation edges
     * For nav chunks: sets distance to -1 (blocked)
     * For nav links: removes the link
     */
    DELETE(0, "item.simple_path_finder.nav_brush.operation.delete"),

    /**
     * Add operation - adds navigation edges with default weight
     * For nav chunks: sets distance to 1 (fully walkable)
     * For nav links: adds a new nav link
     */
    ADD(1, "item.simple_path_finder.nav_brush.operation.add"),

    /**
     * Adjust weight operation - modifies edge weights
     * For nav chunks: adjusts the distance value
     * For nav links: not applicable (nav links use fixed type-based costs)
     */
    ADJUST_WEIGHT(2, "item.simple_path_finder.nav_brush.operation.adjust_weight");

    private final int id;
    private final String translationKey;

    /**
     * Gets Id -> Enum mapping
     */
    public static final IntFunction<NavBrushOperation> BY_ID = ByIdMap.continuous(
            NavBrushOperation::getId,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );

    NavBrushOperation(int id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public int getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * Codec for serialization
     */
    public static final Codec<NavBrushOperation> CODEC = Codec.STRING.xmap(s -> {
        try {
            return NavBrushOperation.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ADD;
        }
    }, NavBrushOperation::name);

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, NavBrushOperation> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, NavBrushOperation::getId);

    /**
     * Get NavBrushOperation from ID
     */
    public static NavBrushOperation fromId(int id) {
        return BY_ID.apply(id);
    }
}
