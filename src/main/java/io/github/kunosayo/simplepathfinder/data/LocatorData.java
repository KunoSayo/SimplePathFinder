package io.github.kunosayo.simplepathfinder.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 定位器数据
 * 用于存储定位器绑定的玩家UUID或位置
 */
public record LocatorData(Either<UUID, GlobalPos> target) {
    public static final Codec<LocatorData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.either(
                            UUIDUtil.CODEC,
                            GlobalPos.CODEC
                    ).fieldOf("target").forGetter(LocatorData::target)
            ).apply(instance, LocatorData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LocatorData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.either(UUIDUtil.STREAM_CODEC, GlobalPos.STREAM_CODEC),
            LocatorData::target,
            LocatorData::new
    );


    public LocatorData(UUID uuid) {
        this(Either.left(uuid));
    }

    public LocatorData(GlobalPos pos) {
        this(Either.right(pos));
    }

    /**
     * 检查是否绑定了玩家
     */
    public boolean isPlayerBound() {
        return target.left().isPresent();
    }

    /**
     * 检查是否绑定了位置
     */
    public boolean isPosBound() {
        return target.right().isPresent();
    }

    /**
     * 创建绑定到玩家的定位器数据
     */
    public static LocatorData forPlayer(UUID uuid) {
        return new LocatorData(Either.left(uuid));
    }

    /**
     * 创建绑定到位置的定位器数据
     */
    public static LocatorData forPosition(GlobalPos pos) {
        return new LocatorData(Either.right(pos));
    }

    /**
     * 创建绑定到位置的定位器数据
     */
    public static LocatorData forPosition(ResourceKey<Level> dimension, net.minecraft.core.BlockPos pos) {
        return forPosition(new GlobalPos(dimension, pos));
    }

    /**
     * 获取玩家UUID（如果存在）
     */
    public UUID getPlayerUuid() {
        return target.left().orElse(null);
    }

    /**
     * 获取全局位置（如果存在）
     */
    public GlobalPos getGlobalPos() {
        return target.right().orElse(null);
    }
}
