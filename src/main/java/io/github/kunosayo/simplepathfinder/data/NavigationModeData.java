package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

/**
 * 导航模式数据
 * 用于存储物品的当前导航模式
 */
public record NavigationModeData(NavigationMode mode) {
    public static final Codec<NavigationModeData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("mode").forGetter(data -> data.mode.name())
            ).apply(instance, NavigationModeData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NavigationModeData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull NavigationModeData decode(RegistryFriendlyByteBuf buf) {
            String modeName = buf.readUtf();
            NavigationMode mode = NavigationMode.valueOf(modeName);
            return new NavigationModeData(mode);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, NavigationModeData data) {
            buf.writeUtf(data.mode.name());
        }
    };

    public NavigationModeData() {
        this(NavigationMode.DEFAULT);
    }

    public NavigationModeData(String modeName) {
        this(NavigationMode.valueOf(modeName));
    }

    public NavigationModeData withMode(NavigationMode mode) {
        return new NavigationModeData(mode);
    }
}