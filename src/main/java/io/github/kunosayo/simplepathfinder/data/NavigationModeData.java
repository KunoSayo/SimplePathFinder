package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

/**
 * 导航模式数据
 * 用于存储物品的当前导航模式和导航层设置
 */
public record NavigationModeData(NavigationMode mode, byte layer) {
    public static final Codec<NavigationModeData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("mode").forGetter(data -> data.mode.name()),
                    Codec.BYTE.fieldOf("layer").forGetter(NavigationModeData::layer)
            ).apply(instance, NavigationModeData::new)
    );

    public static final StreamCodec<ByteBuf, NavigationModeData> STREAM_CODEC = StreamCodec.composite(
            NavigationMode.STREAM_CODEC.cast(),
            NavigationModeData::mode,
            ByteBufCodecs.BYTE,
            NavigationModeData::layer,
            NavigationModeData::new
    );

    /**
     * 默认构造函数，使用默认模式和层0
     */
    public NavigationModeData() {
        this(NavigationMode.DEFAULT, (byte) 0);
    }

    /**
     * 只指定模式的构造函数，使用默认层0
     */
    public NavigationModeData(NavigationMode mode) {
        this(mode, (byte) 0);
    }

    /**
     * 从模式名称和层创建数据
     */
    public NavigationModeData(String modeName, byte layer) {
        this(NavigationMode.valueOf(modeName), layer);
    }

    /**
     * 创建新的模式数据，保持相同的层
     */
    public NavigationModeData withMode(NavigationMode mode) {
        return new NavigationModeData(mode, this.layer);
    }

    /**
     * 创建新的模式数据，使用指定的层
     */
    public NavigationModeData withLayer(byte layer) {
        return new NavigationModeData(this.mode, layer);
    }

    /**
     * 创建新的模式数据，同时指定模式和层
     */
    public NavigationModeData withModeAndLayer(NavigationMode mode, byte layer) {
        return new NavigationModeData(mode, layer);
    }
}