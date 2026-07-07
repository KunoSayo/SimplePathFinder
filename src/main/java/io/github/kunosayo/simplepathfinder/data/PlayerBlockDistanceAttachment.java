package io.github.kunosayo.simplepathfinder.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jspecify.annotations.NonNull;

/**
 * Player attachment for storing custom block distance configuration.
 * This allows each player to have their own block distance costs for navigation parsing.
 */
public class PlayerBlockDistanceAttachment implements ValueIOSerializable {

    private PlayerBlockDistanceData data;

    /**
     * Network sync stream codec
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerBlockDistanceAttachment> STREAM_CODEC = StreamCodec.composite(
            PlayerBlockDistanceData.STREAM_CODEC,
            PlayerBlockDistanceAttachment::getData,
            PlayerBlockDistanceAttachment::new
    );

    /**
     * Default constructor for serialization and attachment system
     */
    public PlayerBlockDistanceAttachment() {
        this.data = PlayerBlockDistanceData.DEFAULT;
    }

    /**
     * Constructor with data
     */
    public PlayerBlockDistanceAttachment(PlayerBlockDistanceData data) {
        this.data = data;
    }

    /**
     * Get the block distance data
     */
    public PlayerBlockDistanceData getData() {
        return data;
    }

    /**
     * Set the block distance data
     */
    public void setData(PlayerBlockDistanceData data) {
        this.data = data;
    }

    @Override
    public void serialize(@NonNull ValueOutput output) {
        output.store("data", PlayerBlockDistanceData.CODEC, data);
    }

    @Override
    public void deserialize(ValueInput input) {
        this.data = input.read("data", PlayerBlockDistanceData.CODEC).orElse(PlayerBlockDistanceData.DEFAULT);
    }
}
