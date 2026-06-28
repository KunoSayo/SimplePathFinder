package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data for storing the first clicked position when creating a navigation link.
 */
public record LinkCreationData(GlobalPos startPos) {

    /**
     * Codec for serialization
     */
    public static final Codec<LinkCreationData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    GlobalPos.CODEC.fieldOf("startPos").forGetter(LinkCreationData::startPos)
            ).apply(instance, LinkCreationData::new)
    );

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, LinkCreationData> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC,
            LinkCreationData::startPos,
            LinkCreationData::new
    );

    /**
     * Create a new link creation data with the given start position
     */
    public static LinkCreationData of(GlobalPos startPos) {
        return new LinkCreationData(startPos);
    }

    /**
     * Create a new link creation data from level and block position
     */
    public static LinkCreationData of(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return new LinkCreationData(GlobalPos.of(level.dimension(), pos));
    }
}
