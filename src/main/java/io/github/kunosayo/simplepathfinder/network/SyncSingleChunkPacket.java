package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Network packet for synchronizing a single navigation chunk.
 * Used for incremental updates when a chunk is modified.
 */
public class SyncSingleChunkPacket implements CustomPacketPayload {
    public static final Type<SyncSingleChunkPacket> NETWORK_TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "sync_single_chunk"));

    /**
     * Stream codec for serializing/deserializing the packet.
     * Handles nullable INavChunk (null means delete the chunk).
     */
    public static final StreamCodec<ByteBuf, SyncSingleChunkPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buffer, SyncSingleChunkPacket value) {
            // Encode dimension
            Identifier.STREAM_CODEC.encode(buffer, value.dimension);

            // Encode chunk position
            ChunkPos.STREAM_CODEC.encode(buffer, value.chunkPos);

            // Encode whether navChunk is present
            buffer.writeBoolean(value.navChunk != null);

            // Encode navChunk if present
            if (value.navChunk != null) {
                INavChunk.TYPED_NAV_CHUNK_CODEC.encode(buffer, value.navChunk);
            }
        }

        @Override
        public SyncSingleChunkPacket decode(ByteBuf buffer) {
            // Decode dimension
            var dimension = Identifier.STREAM_CODEC.decode(buffer);

            // Decode chunk position
            var chunkPos = ChunkPos.STREAM_CODEC.decode(buffer);

            // Decode whether navChunk is present
            boolean hasNavChunk = buffer.readBoolean();

            // Decode navChunk if present
            INavChunk navChunk = null;
            if (hasNavChunk) {
                navChunk = INavChunk.TYPED_NAV_CHUNK_CODEC.decode(buffer);
            }

            return new SyncSingleChunkPacket(dimension, chunkPos, navChunk);
        }
    };

    private final Identifier dimension;
    private final ChunkPos chunkPos;
    @Nullable
    private final INavChunk navChunk;

    /**
     * Creates a packet for adding/updating a chunk.
     */
    public SyncSingleChunkPacket(Identifier dimension, ChunkPos chunkPos, INavChunk navChunk) {
        this.dimension = dimension;
        this.chunkPos = chunkPos;
        this.navChunk = navChunk;
    }

    /**
     * Creates a packet for deleting a chunk.
     */
    public static SyncSingleChunkPacket createDelete(Identifier dimension, ChunkPos chunkPos) {
        return new SyncSingleChunkPacket(dimension, chunkPos, null);
    }

    /**
     * Client-side handler for processing the packet.
     * Updates the local navigation data with the chunk information.
     */
    public static void clientHandler(final SyncSingleChunkPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Use packet dimension

            // Update single chunk in ClientNavDataManager
            ClientNavDataManager.updateSingleChunk(packet.dimension, packet.chunkPos, packet.navChunk);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }
}
