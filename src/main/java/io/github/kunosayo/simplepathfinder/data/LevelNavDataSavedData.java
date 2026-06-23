package io.github.kunosayo.simplepathfinder.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.ByteBuffer;

public class LevelNavDataSavedData extends SavedData {
    private static final StreamCodec<ByteBuf, LevelNavData> SAVE_CODEC = LevelNavData.STREAM_CODEC;
    public LevelNavData levelNavData = new LevelNavData();
    private static final Codec<LevelNavDataSavedData> SAVED_DATA_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BYTE_BUFFER.fieldOf("simple_path_finder_data").forGetter(data -> {
                        var buffer = Unpooled.buffer();
                        SAVE_CODEC.encode(buffer, data.levelNavData);
                        var bytes = new byte[buffer.writerIndex()];
                        buffer.readBytes(bytes);
                        return ByteBuffer.wrap(bytes);
                    })
            ).apply(instance, byteBuffer -> {
                var data = SAVE_CODEC.decode(Unpooled.wrappedBuffer(byteBuffer.array()));
                return new LevelNavDataSavedData(data);
            }));

    public LevelNavDataSavedData(ServerLevel sl) {
    }

    public LevelNavDataSavedData(LevelNavData levelNavData) {
        this.levelNavData = levelNavData;
    }

    public static LevelNavDataSavedData loadFromLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedDataType<>(Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "simple_path_finder_data"),
                        LevelNavDataSavedData::new, _ -> SAVED_DATA_CODEC));
    }
}
