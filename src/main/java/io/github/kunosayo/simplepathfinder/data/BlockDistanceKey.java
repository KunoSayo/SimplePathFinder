package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Key type for block distance configuration.
 * Can be either a block ID or a block tag.
 */
public sealed interface BlockDistanceKey permits BlockDistanceKey.BlockIdKey, BlockDistanceKey.TagKey {
    /**
     * Creates a key from a block ID.
     */
    static BlockDistanceKey block(Identifier id) {
        return new BlockIdKey(id);
    }

    /**
     * Creates a key from a tag identifier.
     */
    static BlockDistanceKey tag(Identifier id) {
        return new TagKey(id);
    }

    /**
     * Key representing a specific block ID.
     */
    record BlockIdKey(Identifier id) implements BlockDistanceKey {
        @Override
        public boolean matches(Block block) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            return blockId.equals(id);
        }
    }

    /**
     * Key representing a block tag.
     * Uses TagKey and Holder.is() for runtime tag checking.
     */
    record TagKey(Identifier id) implements BlockDistanceKey {
        @Override
        public boolean matches(Block block) {
            try {
                // Create TagKey using the constructor with registry key and tag location
                // Note: Direct constructor usage is necessary for dynamic tag creation from user input
                net.minecraft.tags.TagKey<Block> tag = new net.minecraft.tags.TagKey<>(Registries.BLOCK, id);
                // Use builtInRegistryHolder().is() to check if block has this tag
                Holder<Block> holder = block.builtInRegistryHolder();
                return holder.is(tag);
            } catch (Exception e) {
                // If tag checking fails for any reason (invalid tag, etc.), return false
                return false;
            }
        }
    }

    /**
     * Check if this key matches the given block.
     */
    boolean matches(Block block);

    /**
     * Codec for BlockDistanceKey.
     * Format: "block:id" for block IDs, "tag:id" for tags
     */
    Codec<BlockDistanceKey> CODEC = Codec.STRING.xmap(
            s -> {
                String[] parts = s.split(":", 2);
                if (parts.length != 2) {
                    return BlockDistanceKey.block(Identifier.tryParse(s));
                }
                return switch (parts[0]) {
                    case "block" -> BlockDistanceKey.block(Identifier.tryParse(parts[1]));
                    case "tag" -> BlockDistanceKey.tag(Identifier.tryParse(parts[1]));
                    default -> BlockDistanceKey.block(Identifier.tryParse(s));
                };
            },
            key -> {
                if (key instanceof BlockIdKey bk) {
                    return "block:" + bk.id();
                } else if (key instanceof TagKey tk) {
                    return "tag:" + tk.id();
                }
                // This should never happen since we have sealed interface
                return "block:unknown";
            }
    );

    /**
     * Stream codec for BlockDistanceKey.
     */
    StreamCodec<ByteBuf, BlockDistanceKey> STREAM_CODEC = StreamCodec.of(
            (buf, key) -> {
                // Write type: 0 = block, 1 = tag
                if (key instanceof BlockIdKey(Identifier id)) {
                    ByteBufCodecs.BYTE.encode(buf, (byte) 0);
                    Identifier.STREAM_CODEC.encode(buf, id);
                } else if (key instanceof TagKey(Identifier id)) {
                    ByteBufCodecs.BYTE.encode(buf, (byte) 1);
                    Identifier.STREAM_CODEC.encode(buf, id);
                }
            },
            (buf) -> {
                byte type = ByteBufCodecs.BYTE.decode(buf);
                Identifier id = Identifier.STREAM_CODEC.decode(buf);
                return switch (type) {
                    case 0 -> BlockDistanceKey.block(id);
                    case 1 -> BlockDistanceKey.tag(id);
                    default -> BlockDistanceKey.block(id);
                };
            }
    );
}
