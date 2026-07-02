package io.github.kunosayo.simplepathfinder.network;

import com.mojang.datafixers.util.Either;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.nav.finder.ModNavResult;
import io.github.kunosayo.simplepathfinder.nav.finder.NavResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network packet from server to client with pathfinding result.
 * Contains either a successful path or a failure message.
 */
public class PathfindingResultPacket implements CustomPacketPayload {
    public static final Type<PathfindingResultPacket> NETWORK_TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "pathfinding_result"));

    public static final StreamCodec<ByteBuf, PathfindingResultPacket> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.either(ModNavResult.STREAM_CODEC, ByteBufCodecs.STRING_UTF8),
                    x -> x.result, PathfindingResultPacket::new);

    private final Either<ModNavResult, String> result;

    public PathfindingResultPacket(Either<ModNavResult, String> result) {
        this.result = result;
    }

    /**
     * Creates a successful result packet.
     *
     * @param modNavResult The pathfinding result
     */
    public PathfindingResultPacket(ModNavResult modNavResult) {
        this(Either.left(modNavResult));
    }

    /**
     * Creates a failed result packet.
     *
     * @param errorMessage The error message describing why pathfinding failed
     */
    public PathfindingResultPacket(String errorMessage) {
        this(Either.right(errorMessage));
    }


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }

    /**
     * Client-side handler for the packet.
     * Processes the pathfinding result and stores it for rendering.
     */
    public static void clientHandler(final PathfindingResultPacket packet, final IPayloadContext context) {
        packet.result.left().ifPresent(modNavResult -> {
            // Store the result for rendering
            NavResult navResult = new NavResult(modNavResult);
            ClientNavDataManager.handleServerPathfindingResult(navResult);
        });
        packet.result.right().ifPresent(s -> context.enqueueWork(() -> {
            // KunoSayo: we send message by packet for some future callback?
            context.player().sendSystemMessage(Component.translatable(s));
        }));

    }
}
