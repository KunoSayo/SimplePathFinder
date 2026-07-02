package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
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
            NavNotificationConfig.STREAM_CODEC,
            PathfindingRequestPacket::config,
            PathfindingRequestPacket::new
    );

    private final BlockPos targetPos;
    private final String targetDesc;
    private final NavNotificationConfig config;

    /**
     * Creates a pathfinding request packet.
     *
     * @param targetPos  The target position to pathfind to
     * @param targetDesc Optional description of the target (e.g., player name)
     * @param config     Notification config for this request
     */
    public PathfindingRequestPacket(BlockPos targetPos, String targetDesc, NavNotificationConfig config) {
        this.targetPos = targetPos;
        this.targetDesc = targetDesc;
        this.config = config;
    }

    /**
     * Legacy constructor for compatibility.
     * Uses default notification config (all notifications enabled).
     */
    public PathfindingRequestPacket(BlockPos targetPos, String targetDesc) {
        this(targetPos, targetDesc, NavNotificationConfig.all());
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public String targetDesc() {
        return targetDesc;
    }

    public NavNotificationConfig config() {
        return config;
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
            // Submit to server pathfinding manager with config
            ServerPathfindingManager.submitRequest(serverPlayer, packet.targetPos, packet.targetDesc, packet.config);
        });
    }
}
