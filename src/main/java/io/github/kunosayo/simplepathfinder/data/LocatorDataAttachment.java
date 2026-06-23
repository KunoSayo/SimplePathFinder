package io.github.kunosayo.simplepathfinder.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class LocatorDataAttachment implements ValueIOSerializable {

    private Optional<LocatorData> data;

    /**
     * 网络同步编解码器
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, LocatorDataAttachment> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(LocatorData.STREAM_CODEC),
            LocatorDataAttachment::getData,
            LocatorDataAttachment::new
    );

    /**
     * 无参构造函数，用于序列化和附件系统
     */
    public LocatorDataAttachment() {
        this.data = Optional.empty();
    }

    /**
     * 带参数的构造函数
     */
    public LocatorDataAttachment(Optional<LocatorData> data) {
        this.data = data;
    }


    /**
     * 创建有定位器数据的实例
     */
    public static LocatorDataAttachment of(LocatorData locatorData) {
        return new LocatorDataAttachment(Optional.ofNullable(locatorData));
    }

    /**
     * 检查是否有定位器数据
     */
    public boolean hasLocator() {
        return data.isPresent();
    }

    /**
     * 获取定位器数据（如果存在）
     */
    public LocatorData getLocatorData() {
        return data.orElse(null);
    }

    /**
     * 获取 Optional 包装的数据
     */
    public Optional<LocatorData> getData() {
        return data;
    }

    @Override
    public void serialize(@NonNull ValueOutput output) {
        this.data.ifPresent(locatorData -> output.store("data", LocatorData.CODEC, locatorData));
    }

    @Override
    public void deserialize(ValueInput input) {
        this.data = input.read("data", LocatorData.CODEC);
    }
}
