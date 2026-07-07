package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.StreamCodec;

/**
 * Represents a navigation link between two positions.
 * Links allow the pathfinder to connect positions that may not be directly adjacent,
 * such as teleporters, vehicles, or other travel methods.
 */
public record NavLink(BlockPos dest, NavLinkType type) {

    private static final StreamCodec<ByteBuf, NavLink> STREAM_CODEC_V1 = StreamCodec.composite(
            GlobalPos.STREAM_CODEC,
            _ -> {
                throw new UnsupportedOperationException();
            },
            NavLinkType.STREAM_CODEC,
            NavLink::type,
            (d, p) -> {
                if (d.dimension().identifier().getPath().trim().isEmpty()) {
                    throw new UnsupportedOperationException();
                }
                return new NavLink(d.pos(), p);
            }

    );
    /**
     * Stream codec for network serialization
     */
    private static final StreamCodec<ByteBuf, NavLink> CURRENT_STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            NavLink::dest,
            NavLinkType.STREAM_CODEC,
            NavLink::type,
            NavLink::new
    );

    public static final StreamCodec<ByteBuf, NavLink> STREAM_CODEC = StreamCodec.of(CURRENT_STREAM_CODEC, byteBuf -> {
        int reader = byteBuf.readerIndex();
        try {
            return STREAM_CODEC_V1.decode(byteBuf);
        } catch (Throwable t) {
            SimplePathFinder.LOGGER.warn(t);
        }
        byteBuf.readerIndex(reader);
        return CURRENT_STREAM_CODEC.decode(byteBuf);
    });

    public NavLink {
    }

    /**
     * Create a normal walking link
     */
    public static NavLink normal(GlobalPos dest) {
        return new NavLink(dest.pos(), NavLinkType.NORMAL);
    }

    /**
     * Create a teleport link
     */
    public static NavLink teleport(GlobalPos dest) {
        return new NavLink(dest.pos(), NavLinkType.TELEPORT);
    }

    /**
     * Create a vehicle link
     */
    public static NavLink vehicle(GlobalPos dest) {
        return new NavLink(dest.pos(), NavLinkType.VEHICLE);
    }

    /**
     * Create a link in the current dimension
     */
    public static NavLink of(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, NavLinkType type) {
        return new NavLink(pos, type);
    }
}
