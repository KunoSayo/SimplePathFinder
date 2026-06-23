package io.github.kunosayo.simplepathfinder.nav;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * Type of navigation link between positions.
 * Determines how a player can travel from one position to another.
 */
public enum NavLinkType {
    /**
     * Normal walking - player can walk between positions
     */
    NORMAL(0, "item.nav_link_type.normal"),

    /**
     * Teleport - requires teleportation (e.g., nether portal, end portal, command)
     */
    TELEPORT(1, "item.nav_link_type.teleport"),

    /**
     * Vehicle - requires a vehicle (e.g., boat, minecart, horse)
     */
    VEHICLE(2, "item.nav_link_type.vehicle");

    private final int id;
    private final String translationKey;

    /**
     * Gets Id -> Enum mapping
     */
    public static final IntFunction<NavLinkType> BY_ID = ByIdMap.continuous(
            NavLinkType::getId,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );

    NavLinkType(int id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public int getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * Codec for serialization
     */
    public static final Codec<NavLinkType> CODEC = Codec.STRING.xmap(s -> {
        try {
            return NavLinkType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }, NavLinkType::name);

    /**
     * Stream codec for network serialization
     */
    public static final StreamCodec<ByteBuf, NavLinkType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, NavLinkType::getId);

    /**
     * Get NavLinkType from ID
     */
    public static NavLinkType fromId(int id) {
        return BY_ID.apply(id);
    }
}

