package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.finder.ServerPathfindingManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network packet from client to server requesting pathfinding.
 * Sent when client wants to perform server-side pathfinding.
 */
public class PathfindingRequestPacket implements CustomPacketPayload {
    public static final Type<PathfindingRequestPacket> NETWORK_TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "pathfinding_request"));

    public static final StreamCodec<ByteBuf, PathfindingRequestPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            PathfindingRequestPacket::targetPos,
            ByteBufCodecs.STRING_UTF8,
            PathfindingRequestPacket::targetDesc,
            PathfindingRequestPacket::new
    );

    private final BlockPos targetPos;
    private final String targetDesc;

    /**
     * Creates a pathfinding request packet.
     *
     * @param targetPos  The target position to pathfind to
     * @param targetDesc Optional description of the target (e.g., player name)
     */
    public PathfindingRequestPacket(BlockPos targetPos, String targetDesc) {
        this.targetPos = targetPos;
        this.targetDesc = targetDesc;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public String targetDesc() {
        return targetDesc;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }

    /**
     * Server-side handler for the packet.
     * Submits the pathfinding request to the server-side queue.
     */
    public static void serverHandler(final PathfindingRequestPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            // Submit to server pathfinding manager
            ServerPathfindingManager.submitRequest(serverPlayer, packet.targetPos, packet.targetDesc);
        });
    }
}
