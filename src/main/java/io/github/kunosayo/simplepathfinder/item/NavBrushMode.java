package io.github.kunosayo.simplepathfinder.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * Brush mode for navigation brush tool.
 * Determines how the brush applies operations to navigation edges.
 */
public enum NavBrushMode {
    /**
     * All edges mode - affects all navigation edges from a position
     * This includes all 4 cardinal directions and any nav links
     */
    ALL_EDGES(0, "item.simple_path_finder.nav_brush.mode.all_edges"),

    /**
     * Single edge mode - affects only the specific edge being targeted
     * Uses ray trace to determine which direction/edge to modify
     */
    SINGLE_EDGE(1, "item.simple_path_finder.nav_brush.mode.single_edge");

    private final int id;
    private final String translationKey;

    /**
     * Gets Id -> Enum mapping
     */
    public static final IntFunction<NavBrushMode> BY_ID = ByIdMap.continuous(
            NavBrushMode::getId,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );

    NavBrushMode(int id, String translationKey) {
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
    public static final Codec<NavBrushMode> CODEC = Codec.STRING.xmap(s -> {
        try {
            return NavBrushMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ALL_EDGES;
        }
    }, NavBrushMode::name);

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, NavBrushMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, NavBrushMode::getId);

    /**
     * Get NavBrushMode from ID
     */
    public static NavBrushMode fromId(int id) {
        return BY_ID.apply(id);
    }
}
