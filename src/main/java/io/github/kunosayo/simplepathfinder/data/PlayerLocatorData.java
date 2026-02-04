package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * 玩家定位器数据
 * 用于存储定位器绑定的玩家UUID
 */
public record PlayerLocatorData(UUID playerUuid) {
    public static final Codec<PlayerLocatorData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("uuid").forGetter(data -> Optional.of(data.playerUuid.toString()))
            ).apply(instance, uuidOpt -> new PlayerLocatorData(UUID.fromString(uuidOpt.orElse(""))))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerLocatorData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PlayerLocatorData decode(RegistryFriendlyByteBuf buf) {
            String uuidStr = buf.readUtf();
            UUID uuid = UUID.fromString(uuidStr);
            return new PlayerLocatorData(uuid);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerLocatorData data) {
            buf.writeUtf(data.playerUuid.toString());
        }
    };

    public PlayerLocatorData() {
        this(new UUID(0, 0));
    }

    public PlayerLocatorData(String uuidString) {
        this(UUID.fromString(uuidString));
    }

    public boolean hasPlayer() {
        return playerUuid.getMostSignificantBits() != 0 || playerUuid.getLeastSignificantBits() != 0;
    }

    public PlayerLocatorData withUuid(UUID uuid) {
        return new PlayerLocatorData(uuid);
    }
}
