package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavNotificationConfig;
import io.github.kunosayo.simplepathfinder.nav.NavigationService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network packet to send player location to client for navigation.
 * Used when a locator is bound to a player.
 */
public class PlayerLocationPacket implements CustomPacketPayload {
    public static final Type<PlayerLocationPacket> NETWORK_TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "player_location"));

    public static final StreamCodec<ByteBuf, PlayerLocationPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.BOOL.encode(buf, packet.online);
                if (packet.online) {
                    BlockPos.STREAM_CODEC.encode(buf, packet.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.playerName);
                }
            },
            (buf) -> {
                boolean online = ByteBufCodecs.BOOL.decode(buf);
                if (online) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String playerName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    return new PlayerLocationPacket(pos, playerName);
                }
                return PlayerLocationPacket.offline();
            }
    );

    private final boolean online;
    private final BlockPos pos;
    private final String playerName;

    /**
     * Constructor for online player with position.
     */
    public PlayerLocationPacket(BlockPos pos, String playerName) {
        this.online = true;
        this.pos = pos;
        this.playerName = playerName;
    }

    /**
     * Constructor for offline player.
     */
    public PlayerLocationPacket() {
        this.online = false;
        this.pos = null;
        this.playerName = null;
    }

    /**
     * Create a packet for online player.
     */
    public static PlayerLocationPacket online(BlockPos pos, String playerName) {
        return new PlayerLocationPacket(pos, playerName);
    }

    /**
     * Create a packet for offline player.
     */
    public static PlayerLocationPacket offline() {
        return new PlayerLocationPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }

    /**
     * Client-side handler for the packet.
     */
    public static void clientHandler(final PlayerLocationPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!packet.online) {
                // Player is offline
                player.sendSystemMessage(Component.translatable("simple_path_finder.locator.player_offline"));
                return;
            }

            // Player is online, start pathfinding
            LevelNavData navData = ClientNavDataManager.getNavDataForPlayer();
            if (navData == null) {
                player.sendSystemMessage(Component.translatable("simple_path_finder.nav.no_data"));
                return;
            }

            BlockPos targetPos = packet.pos;

            // 使用客户端导航服务执行寻路
            if (!packet.playerName.isEmpty()) {
                NavigationService.navigateToPosition(targetPos, packet.playerName, NavNotificationConfig.all());
            } else {
                NavigationService.navigateToPosition(targetPos, NavNotificationConfig.all());
            }
        });
    }
}
