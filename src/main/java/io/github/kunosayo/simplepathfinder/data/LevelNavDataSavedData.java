package io.github.kunosayo.simplepathfinder.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.ByteBuffer;

public class LevelNavDataSavedData extends SavedData {
    public LevelNavData levelNavData = new LevelNavData();

    private static final StreamCodec<ByteBuf, LevelNavData> SAVE_CODEC = LevelNavData.STREAM_CODEC;
    private static final Codec<LevelNavDataSavedData> SAVED_DATA_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BYTE_BUFFER.fieldOf("simple_path_finder_data").forGetter(data -> {
                        var buffer = Unpooled.buffer();
                        SAVE_CODEC.encode(buffer, data.levelNavData);
                        var bytes = new byte[buffer.writerIndex()];
                        buffer.readBytes(bytes);
                        buffer.release();
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

    @Override
    public void setDirty() {
        super.setDirty();
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) {
            ++levelNavData.dirtyCount;
        }
    }

    public static LevelNavDataSavedData loadFromLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedDataType<>(Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "simple_path_finder_data"),
                        LevelNavDataSavedData::new, _ -> SAVED_DATA_CODEC));
    }
}
