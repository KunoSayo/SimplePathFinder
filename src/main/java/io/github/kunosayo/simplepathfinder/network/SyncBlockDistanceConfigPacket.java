package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceAttachment;
import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceData;
import io.github.kunosayo.simplepathfinder.init.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network packet to sync player-specific block distance configuration bidirectionally.
 * Used for both client→server (player updates config) and server→client (sync current config to GUI).
 */
public class SyncBlockDistanceConfigPacket implements CustomPacketPayload {
    public static final Type<SyncBlockDistanceConfigPacket> NETWORK_TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "sync_block_distance_config"));

    public static final StreamCodec<ByteBuf, SyncBlockDistanceConfigPacket> STREAM_CODEC = StreamCodec.composite(
            PlayerBlockDistanceData.STREAM_CODEC,
            SyncBlockDistanceConfigPacket::data,
            SyncBlockDistanceConfigPacket::new
    );

    private final PlayerBlockDistanceData data;

    /**
     * Constructor with block distance data
     */
    public SyncBlockDistanceConfigPacket(PlayerBlockDistanceData data) {
        this.data = data;
    }

    /**
     * Get the block distance data
     */
    public PlayerBlockDistanceData data() {
        return data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }

    /**
     * Server-side handler for the packet.
     * Updates the player's block distance attachment with the received configuration
     * and echoes it back to the client for GUI display.
     */
    public static void serverHandler(final SyncBlockDistanceConfigPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var attachment = player.getData(ModAttachments.PLAYER_BLOCK_DISTANCE.get());
                attachment.setData(packet.data);

                // Echo the updated data back to client for GUI display
                PacketDistributor.sendToPlayer(player, new SyncBlockDistanceConfigPacket(packet.data));
            }
        });
    }

    /**
     * Client-side handler for the packet.
     * Stores the received block distance data for use in the GUI.
     */
    public static void clientHandler(final SyncBlockDistanceConfigPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            context.player().setData(ModAttachments.PLAYER_BLOCK_DISTANCE, new PlayerBlockDistanceAttachment(packet.data));
        });
    }
}
