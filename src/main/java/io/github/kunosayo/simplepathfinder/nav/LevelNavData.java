package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.config.NavBuildConfig;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Optional;

public class LevelNavData {
    public static final StreamCodec<ByteBuf, ChunkPos> CHUNK_POS_STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.VAR_LONG, ChunkPos::toLong, ChunkPos::new);

    public static final StreamCodec<ByteBuf, LevelNavData> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.map(HashMap::new, CHUNK_POS_STREAM_CODEC, NavChunk.STREAM_CODEC),
            levelNavData -> levelNavData.navChunks, LevelNavData::new);
    @ParametersAreNonnullByDefault
    public static final StreamCodec<ByteBuf, LevelNavData> VERSION_STREAM_CODEC = new StreamCodec<ByteBuf, LevelNavData>() {
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

    private HashMap<ChunkPos, NavChunk> navChunks = new HashMap<>();

    public LevelNavData() {

    }

    public Optional<NavChunk> getNavChunk(ChunkPos pos, boolean create) {
        return Optional.ofNullable(navChunks.computeIfAbsent(pos, chunkPos -> {
            if (!create || (navChunks.size() >= NavBuildConfig.NAV_BUILD_CONFIG.getLeft().maxNavChunks.get())) {
                return null;
            }
            return new NavChunk(pos);
        }));
    }

    public Optional<LayeredNavChunk> getNavChunk(ChunkPos pos, int layer) {
        return Optional.ofNullable(navChunks.get(pos))
                .flatMap(navChunk -> navChunk.layers.stream().filter(navChunk1 -> navChunk1.layer == layer).findAny());
    }

    public LevelNavData(HashMap<ChunkPos, NavChunk> navChunks) {
        this.navChunks = navChunks;
        navChunks.forEach((chunkPos, navChunk) -> {
            navChunk.chunkPos = chunkPos;
        });
    }

    private static BlockPos getGroundPos(Level level, BlockPos groundPos) {
        while (groundPos.getY() >= -64 && NavUtil.considerSafeCross(level, groundPos)) {
            groundPos = groundPos.offset(0, -1, 0);
        }
        return groundPos;
    }

    public boolean buildForPlayer(Player player, int layer) {
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
        getNavChunk(new ChunkPos(groundPos), true).ifPresentOrElse(navChunk -> navChunk
                .getLayer(layer, LayeredNavChunk::getDefault).ifPresentOrElse(layeredNavChunk -> {
                    layeredNavChunk.parentChunk = navChunk;
                    layeredNavChunk.layer = layer;
                    layeredNavChunk.parse(level, finalGroundPos.offset(0, 1, 0));
                    player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.success"));
                    result[0] = true;
                }, () -> player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.limited"))), () -> player.sendSystemMessage(Component.translatable("simple_path_finder.build.nav.limited")));


        return result[0];
    }

    public Optional<NavResult> findNav(BlockPos from, BlockPos to) {
        var startChunk = new ChunkPos(from);
        var startNavChunk = this.navChunks.get(startChunk);
        if (startNavChunk == null) {
            return Optional.empty();
        }

        var finder = new NavPathFinder(this, from, to);

        return finder.search();
    }

    public boolean buildFromLayerStart(Level level, LevelNavData levelNavData, int layer, ChunkPos acp) {

        boolean[] result = new boolean[]{false};
        getNavChunk(acp, true).ifPresent(navChunk -> navChunk
                .getLayer(layer, LayeredNavChunk::getDefault).ifPresent(layeredNavChunk -> {
                    layeredNavChunk.parentChunk = navChunk;
                    layeredNavChunk.layer = layer;
                    levelNavData.getNavChunk(new ChunkPos(acp.x - 1, acp.z), layer)
                            .ifPresentOrElse(navChunk1 -> {
                                int y = navChunk1.getWalkY(15, 0);
                                var blockPos = new BlockPos(acp.getBlockX(0), y + 2, acp.getBlockZ(0));
                                var groundPos = getGroundPos(level, blockPos);
                                layeredNavChunk.parse(level, groundPos.offset(0, 1, 0));
                                result[0] = true;
                            }, () -> levelNavData.getNavChunk(new ChunkPos(acp.x, acp.z - 1), layer).ifPresent(navChunk1 -> {
                                int y = navChunk1.getWalkY(0, 15);
                                var blockPos = new BlockPos(acp.getBlockX(0), y + 2, acp.getBlockZ(0));
                                var groundPos = getGroundPos(level, blockPos);
                                layeredNavChunk.parse(level, groundPos.offset(0, 1, 0));
                                result[0] = true;
                            }));


                }));


        return result[0];
    }
}
