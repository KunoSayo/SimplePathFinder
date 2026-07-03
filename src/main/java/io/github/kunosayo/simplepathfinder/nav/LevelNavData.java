package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;
import io.github.kunosayo.simplepathfinder.nav.finder.NavResult;
import io.github.kunosayo.simplepathfinder.nav.layered.BatchScheduler;
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
                } else {
                    // We set failed, so we should release it.
                    newBuffer.release();
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

    static void trap() {
        SimplePathFinder.LOGGER.warn("TRAP: out of range nav chunk creation during batch generation");
    }

    static void checkBoundedAccess(ChunkPos pos, boolean create) {
        final var batch = BatchScheduler.THREAD_LOCAL.get();
        if (batch != null && create) {
            if (!batch.isInRange(pos)) {
                trap();
            }
        }
    }

    public Optional<INavChunk> getNavChunk(ChunkPos pos, boolean create) {
        // checkBoundedAccess(pos, create);
        // When create is only attempted on the main thread, there's no trouble
        return !create || (navChunks.size() >= NavConfig.NAV_CONFIG.getLeft().maxNavChunks.get()) ?
                Optional.ofNullable(navChunks.get(pos)) : Optional.of(navChunks.computeIfAbsent(pos, NavChunk::new));
    }

    public Optional<ILayeredNavChunk> getNavChunk(ChunkPos pos, int layer) {
        return Optional.ofNullable(navChunks.get(pos))
                .flatMap(navChunk -> navChunk.getLayer(layer));
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

        // Require: on the ground, body not occupied by blocks
        if (NavUtil.isNoCollision(level, groundPos) &&
                !NavUtil.isNoCollision(level, groundPos.offset(0, 1, 0)) &&
                !NavUtil.isNoCollision(level, groundPos.offset(0, 2, 0))) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.failed"));
            return false;
        }

        net.minecraft.core.BlockPos finalGroundPos = groundPos;
        final var optionalNavChunk = getNavChunk(ChunkPos.containing(groundPos), true);
        if (optionalNavChunk.isPresent()) {
            final var navChunk = optionalNavChunk.get();
            final var optionalLayered = navChunk.getLayer(layer, LayeredNavChunk::getDefault);
            if (optionalLayered.isPresent()) {
                final var layered = optionalLayered.get();
                if (!(layered instanceof LayeredNavChunk chunk)) {
                    SimplePathFinder.LOGGER.error("Why isn't it LayeredNavChunk when building for player? Answer me!!!");
                    return false;
                }
                chunk.setParentChunk(navChunk);
                chunk.setLayer(layer);
                chunk.parse(level, finalGroundPos.offset(0, 1, 0));
                player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.success"));
                return true;
            }
        }
        player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.limited"));
        return false;
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

    public byte buildFromLayerStart(Level level, LevelNavData levelNavData, byte layer, ChunkPos acp) {
        final var optionalNavChunk = getNavChunk(acp, true);
        if (optionalNavChunk.isEmpty()) {
            return Byte.MIN_VALUE;
        }
        final var navChunk = optionalNavChunk.get();
        final var optionalLayered = navChunk.getLayer(layer, LayeredNavChunk::getDefault);
        if (optionalLayered.isEmpty()) {
            return Byte.MIN_VALUE;
        }
        final var layered = optionalLayered.get();
        if (!(layered instanceof LayeredNavChunk chunk)) {
            SimplePathFinder.LOGGER.error("Why isn't it LayeredNavChunk when batch building? Answer me!!!");
            return Byte.MIN_VALUE;
        }
        chunk.setParentChunk(navChunk);
        chunk.setLayer(layer);
        final var check = levelNavData.getNavChunk(new ChunkPos(acp.x() - 1, acp.z()), layer)
                .filter(navChunk1 -> navChunk1.canWalk(15, 0));
        BlockPos pos = null;
        if (check.isPresent()) {
            final var nav1 = check.get();
            for (int i = 0; i < 16; i++) {
                int y = nav1.getWalkY(15, i);
                if (y == ILayeredNavChunk.INVALID_WALK_Y) continue;
                pos = new BlockPos(acp.getBlockX(0), y + 2, acp.getBlockZ(i));
                break;
            }
        } else {
            final var check2 = levelNavData.getNavChunk(new ChunkPos(acp.x(), acp.z() - 1), layer);
            if (check2.isPresent()) {
                final var nav2 = check2.get();
                for (int i = 0; i < 16; i++) {
                    int y = nav2.getWalkY(i, 15);
                    if (y == ILayeredNavChunk.INVALID_WALK_Y) continue;
                    pos = new BlockPos(acp.getBlockX(i), y + 2, acp.getBlockZ(0));
                    break;
                }
            }
        }
        byte result = 0;
        if (pos != null) {
            final var groundPos = getGroundPos(level, pos);
            result = layered.parse(level, groundPos.offset(0, 1, 0));
        }
        if (!chunk.isAnyValid()) {
            navChunk.removeNavChunk(chunk);
        }
        return result;
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
