package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.network.SyncLevelNavDataPacket;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

record CachedData(@Nullable ByteBuf buf, int count) {

}

public class LevelNavData {
    public static final StreamCodec<ByteBuf, ChunkPos> CHUNK_POS_STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.VAR_LONG, ChunkPos::pack, ChunkPos::unpack);
    // Only write on server thread.
    public volatile int dirtyCount = 1;
    private final AtomicReference<CachedData> cachedBuf = new AtomicReference<>(new CachedData(null, 0));
    public static final StreamCodec<ByteBuf, LevelNavData> REAL_STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.map(HashMap::new, CHUNK_POS_STREAM_CODEC, INavChunk.TYPED_NAV_CHUNK_CODEC),
            levelNavData -> levelNavData.navChunks, LevelNavData::new);

    // Cached STREAM_CODEC for LevelNavData.
    public static final StreamCodec<ByteBuf, LevelNavData> STREAM_CODEC = StreamCodec.of((byteBuf, levelNavData) -> {
        var cached = levelNavData.cachedBuf.get();
        var buf = cached.buf();
        if (buf != null) {
            buf.retain();
            if (buf.refCnt() == 0) {
                buf = null;
            }
        }
        try {
            int cnt = cached.count();
            int gotDirty = levelNavData.dirtyCount;
            if (cnt != gotDirty || buf == null) {
                var newBuffer = Unpooled.buffer();
                REAL_STREAM_CODEC.encode(newBuffer, levelNavData);
                var newCached = new CachedData(newBuffer, gotDirty);
                byteBuf.writeBytes(newBuffer.slice());
                if (levelNavData.cachedBuf.compareAndSet(cached, newCached)) {
                    if (buf != null) {
                        buf.release();
                    }
                }
                return;
            }
            byteBuf.writeBytes(buf.slice());
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }, REAL_STREAM_CODEC);
    @ParametersAreNonnullByDefault
    public static final StreamCodec<ByteBuf, LevelNavData> VERSION_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull LevelNavData decode(ByteBuf buffer) {
            int version = VarInt.read(buffer);
            // we have no version yet.
            return STREAM_CODEC.decode(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, LevelNavData value) {
            VarInt.write(buffer, 0);
            STREAM_CODEC.encode(buffer, value);
        }
    };

    public static final int CHUNK_AREA = 16 * 16;

    private ConcurrentHashMap<ChunkPos, INavChunk> navChunks = new ConcurrentHashMap<>();

    public LevelNavData() {

    }

    public LevelNavData(Map<ChunkPos, INavChunk> chunkPosHashMap) {
        this.navChunks = new ConcurrentHashMap<>(chunkPosHashMap);
    }

    public Optional<INavChunk> getNavChunk(ChunkPos pos, boolean create) {
        return Optional.ofNullable(navChunks.computeIfAbsent(pos, chunkPos -> {
            if (!create || (navChunks.size() >= NavConfig.NAV_CONFIG.getLeft().maxNavChunks.get())) {
                return null;
            }
            return new NavChunk(pos);
        }));
    }

    public Optional<ILayeredNavChunk> getNavChunk(ChunkPos pos, int layer) {
        return Optional.ofNullable(navChunks.get(pos))
                .flatMap(navChunk -> navChunk.getLayers().filter(navChunk1 -> navChunk1.getLayer() == layer).findAny());
    }

    /**
     * Get a navigation chunk without creating it.
     * Used for synchronizing chunks to clients.
     *
     * @param pos the chunk position
     * @return optional containing the nav chunk if it exists
     */
    public Optional<INavChunk> getNavChunkForSync(ChunkPos pos) {
        return Optional.ofNullable(navChunks.get(pos));
    }

    /**
     * Update or add a navigation chunk.
     * Used for incremental updates from server.
     *
     * @param pos      the chunk position
     * @param navChunk the navigation chunk to set, or null to remove
     */
    public void updateNavChunk(ChunkPos pos, @Nullable INavChunk navChunk) {
        if (navChunk == null) {
            navChunks.remove(pos);
        } else {
            navChunks.put(pos, navChunk);
        }
    }

    public LevelNavData(HashMap<ChunkPos, INavChunk> navChunks) {
        this.navChunks = new ConcurrentHashMap<>(navChunks);
        navChunks.forEach((chunkPos, navChunk) -> navChunk.setChunkPos(chunkPos));
    }

    private static BlockPos getGroundPos(Level level, BlockPos groundPos) {
        while (groundPos.getY() >= -64 && NavUtil.considerSafeCross(level, groundPos)) {
            groundPos = groundPos.offset(0, -1, 0);
        }
        return groundPos;
    }

    public boolean buildForPlayer(Player player, byte layer) {
        var level = player.level();
        var groundPos = player.blockPosition();

        while (groundPos.getY() >= -64 && NavUtil.considerSafeCross(level, groundPos)) {
            groundPos = groundPos.offset(0, -1, 0);
        }


        if (NavUtil.isNoCollision(level, groundPos)) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.failed"));
            return false;
        }

        if (!NavUtil.isNoCollision(level, groundPos.offset(0, 1, 0))) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.failed"));
            return false;
        }
        if (!NavUtil.isNoCollision(level, groundPos.offset(0, 2, 0))) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.failed"));
            return false;
        }

        net.minecraft.core.BlockPos finalGroundPos = groundPos;
        boolean[] result = new boolean[]{false};
        getNavChunk(ChunkPos.containing(groundPos), true).ifPresentOrElse(navChunk -> navChunk
                .getLayer(layer, () -> (LayeredNavChunk) LayeredNavChunk.getDefault()).ifPresentOrElse(layeredNavChunk -> {
                    if (layeredNavChunk instanceof LayeredNavChunk) {
                        LayeredNavChunk chunk = (LayeredNavChunk) layeredNavChunk;
                        chunk.setParentChunk(navChunk);
                        chunk.setLayer(layer);
                        chunk.parse(level, finalGroundPos.offset(0, 1, 0));
                        player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.success"));
                        result[0] = true;
                    }
                }, () -> player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.limited"))), () -> player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.limited")));


        return result[0];
    }

    public Optional<NavResult> findNav(BlockPos from, BlockPos to) {
        var startChunk = ChunkPos.containing(from);
        var startNavChunk = this.navChunks.get(startChunk);
        if (startNavChunk == null) {
            return Optional.empty();
        }

        var finder = new NavPathFinder(this, from, to);

        return finder.search();
    }

    public boolean buildFromLayerStart(Level level, LevelNavData levelNavData, byte layer, ChunkPos acp) {

        boolean[] result = new boolean[]{false};
        getNavChunk(acp, true).ifPresent(navChunk -> navChunk
                .getLayer(layer, () -> (LayeredNavChunk) LayeredNavChunk.getDefault()).ifPresent(layeredNavChunk -> {
                    if (layeredNavChunk instanceof LayeredNavChunk chunk) {
                        chunk.setParentChunk(navChunk);
                        chunk.setLayer(layer);

                        levelNavData.getNavChunk(new ChunkPos(acp.x() - 1, acp.z()), layer)
                                .filter(navChunk1 -> navChunk1.canWalk(15, 0))
                                .ifPresentOrElse(navChunk1 -> {
                                    for (int i = 0; i < 16; i++) {
                                        int y = navChunk1.getWalkY(15, i);
                                        if (y != ILayeredNavChunk.INVALID_WALK_Y) {
                                            var blockPos = new BlockPos(acp.getBlockX(0), y + 2, acp.getBlockZ(i));
                                            var groundPos = getGroundPos(level, blockPos);
                                            layeredNavChunk.parse(level, groundPos.offset(0, 1, 0));
                                            result[0] = true;
                                            break;
                                        }
                                    }

                                }, () -> levelNavData.getNavChunk(new ChunkPos(acp.x(), acp.z() - 1), layer).ifPresent(navChunk1 -> {
                                    for (int i = 0; i < 16; i++) {
                                        int y = navChunk1.getWalkY(i, 15);
                                        if (y != ILayeredNavChunk.INVALID_WALK_Y) {
                                            var blockPos = new BlockPos(acp.getBlockX(i), y + 2, acp.getBlockZ(0));
                                            var groundPos = getGroundPos(level, blockPos);
                                            layeredNavChunk.parse(level, groundPos.offset(0, 1, 0));
                                            result[0] = true;
                                            break;
                                        }
                                    }

                                }));

                        if (!chunk.isAnyValid()) {
                            navChunk.removeNavChunk(chunk);
                        }
                    }
                }));


        return result[0];
    }

    public long getTotalLayers() {
        long totals = 0;
        for (INavChunk value : this.navChunks.values()) {
            totals += value.getLayerCount();
        }
        return totals;
    }

    public long getTotalNavChunks() {
        return this.navChunks.size();
    }

    public long getEncodedBytes() {
        var buffer = Unpooled.buffer();
        STREAM_CODEC.encode(buffer, this);
        int result = buffer.writerIndex();
        buffer.release();
        return result;
    }

    public long getEncodedCompressedBytes() {
        var buffer = Unpooled.buffer();
        SyncLevelNavDataPacket.STREAM_CODEC.encode(buffer, new SyncLevelNavDataPacket(this));
        int cnt = buffer.writerIndex();
        buffer.release();
        return cnt;
    }

    public boolean removeNavChunk(ChunkPos pos) {
        return this.navChunks.remove(pos) != null;
    }

    public boolean removeNavChunk(Player player) {
        return removeNavChunk(player.chunkPosition());
    }
}
