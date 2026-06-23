package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kunosayo.simplepathfinder.item.NavBrushMode;
import io.github.kunosayo.simplepathfinder.item.NavBrushOperation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component for navigation brush item.
 * Stores the current brush mode, operation, and related settings.
 */
public record NavBrushData(
        NavBrushMode mode,
        NavBrushOperation operation,
        int weightValue
) {
    /**
     * Codec for serialization
     */
    public static final Codec<NavBrushData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    NavBrushMode.CODEC.fieldOf("mode").forGetter(NavBrushData::mode),
                    NavBrushOperation.CODEC.fieldOf("operation").forGetter(NavBrushData::operation),
                    Codec.INT.fieldOf("weight_value").forGetter(NavBrushData::weightValue)
            ).apply(instance, NavBrushData::new)
    );

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, NavBrushData> STREAM_CODEC = StreamCodec.composite(
            NavBrushMode.STREAM_CODEC,
            NavBrushData::mode,
            NavBrushOperation.STREAM_CODEC,
            NavBrushData::operation,
            ByteBufCodecs.VAR_INT,
            NavBrushData::weightValue,
            NavBrushData::new
    );

    /**
     * Create a new brush data with default values
     */
    public static NavBrushData createDefault() {
        return new NavBrushData(NavBrushMode.ALL_EDGES, NavBrushOperation.ADD, 1);
    }

    /**
     * Create a new brush data with specified mode and operation
     */
    public static NavBrushData create(NavBrushMode mode, NavBrushOperation operation) {
        return new NavBrushData(mode, operation, 1);
    }

    /**
     * Create a new brush data with specified mode, operation, and weight value
     */
    public static NavBrushData create(NavBrushMode mode, NavBrushOperation operation, int weightValue) {
        return new NavBrushData(mode, operation, weightValue);
    }

    /**
     * Cycle to the next brush mode
     */
    public NavBrushData nextMode() {
        NavBrushMode[] modes = NavBrushMode.values();
        int nextIndex = (mode.ordinal() + 1) % modes.length;
        return new NavBrushData(modes[nextIndex], operation, weightValue);
    }

    /**
     * Cycle to the previous brush mode
     */
    public NavBrushData previousMode() {
        NavBrushMode[] modes = NavBrushMode.values();
        int prevIndex = (mode.ordinal() - 1 + modes.length) % modes.length;
        return new NavBrushData(modes[prevIndex], operation, weightValue);
    }

    /**
     * Cycle to the next operation
     */
    public NavBrushData nextOperation() {
        NavBrushOperation[] operations = NavBrushOperation.values();
        int nextIndex = (operation.ordinal() + 1) % operations.length;
        return new NavBrushData(mode, operations[nextIndex], weightValue);
    }

    /**
     * Cycle to the previous operation
     */
    public NavBrushData previousOperation() {
        NavBrushOperation[] operations = NavBrushOperation.values();
        int prevIndex = (operation.ordinal() - 1 + operations.length) % operations.length;
        return new NavBrushData(mode, operations[prevIndex], weightValue);
    }

    /**
     * Increase weight value
     */
    public NavBrushData increaseWeight() {
        return new NavBrushData(mode, operation, Math.min(weightValue + 1, 16));
    }

    /**
     * Decrease weight value
     */
    public NavBrushData decreaseWeight() {
        return new NavBrushData(mode, operation, Math.max(weightValue - 1, 1));
    }
}
