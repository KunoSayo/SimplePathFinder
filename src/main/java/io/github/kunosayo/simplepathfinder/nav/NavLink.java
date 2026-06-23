package io.github.kunosayo.simplepathfinder.nav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Represents a navigation link between two positions.
 * Links allow the pathfinder to connect positions that may not be directly adjacent,
 * such as teleporters, vehicles, or other travel methods.
 */
public record NavLink(GlobalPos dest, NavLinkType type) {
    /**
     * Codec for serialization
     */
    public static final Codec<NavLink> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    GlobalPos.CODEC.fieldOf("dest").forGetter(NavLink::dest),
                    NavLinkType.CODEC.fieldOf("type").forGetter(NavLink::type)
            ).apply(instance, NavLink::new)
    );

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, NavLink> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC,
            NavLink::dest,
            NavLinkType.STREAM_CODEC,
            NavLink::type,
            NavLink::new
    );

    public NavLink {
    }

    /**
     * Create a normal walking link
     */
    public static NavLink normal(GlobalPos dest) {
        return new NavLink(dest, NavLinkType.NORMAL);
    }

    /**
     * Create a teleport link
     */
    public static NavLink teleport(GlobalPos dest) {
        return new NavLink(dest, NavLinkType.TELEPORT);
    }

    /**
     * Create a vehicle link
     */
    public static NavLink vehicle(GlobalPos dest) {
        return new NavLink(dest, NavLinkType.VEHICLE);
    }

    /**
     * Create a link in the current dimension
     */
    public static NavLink of(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, NavLinkType type) {
        return new NavLink(GlobalPos.of(level.dimension(), pos), type);
    }

    /**
     * Get the cost multiplier for this link type.
     * Higher values make the pathfinder avoid this link unless necessary.
     */
    public double getCostMultiplier() {
        return switch (type) {
            case NORMAL -> 1.0;
            case TELEPORT -> 50.0;  // Teleportation is expensive
            case VEHICLE -> 2.0;    // Vehicles are moderately expensive
        };
    }
}
