package io.github.kunosayo.simplepathfinder.data;

import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class LevelNavDataSavedData extends SavedData {
    private static final StreamCodec<ByteBuf, LevelNavData> SAVE_CODEC = LevelNavData.STREAM_CODEC;
    public LevelNavData levelNavData = new LevelNavData();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {

        var buffer = Unpooled.buffer();
        SAVE_CODEC.encode(buffer, this.levelNavData);
        var data = new byte[buffer.writerIndex()];
        buffer.readBytes(data);
        tag.putByteArray("simple_path_finder_data", data);
        return tag;
    }

    public static LevelNavDataSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        if (tag != null && tag.contains("simple_path_finder_data")) {
            byte[] data = tag.getByteArray("simple_path_finder_data");
            return new LevelNavDataSavedData(SAVE_CODEC.decode(Unpooled.wrappedBuffer(data)));
        }
        return new LevelNavDataSavedData();
    }

    public LevelNavDataSavedData() {
    }

    public LevelNavDataSavedData(LevelNavData levelNavData) {
        this.levelNavData = levelNavData;
    }

    public static LevelNavDataSavedData loadFromLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(LevelNavDataSavedData::new, LevelNavDataSavedData::load),
                "simple_path_finder_data");
    }
}
