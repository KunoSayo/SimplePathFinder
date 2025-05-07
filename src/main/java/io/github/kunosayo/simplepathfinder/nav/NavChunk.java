package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.config.NavBuildConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class NavChunk {
    private static final StreamCodec<ByteBuf, LayeredNavChunk> TYPED_LAYERED_NAV_CHUNK_CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeByte(0);
        LayeredNavChunk.STREAM_CODEC.encode(buffer, value);
    }, buffer -> {
        // todo: use interface.
        buffer.readByte();
        return LayeredNavChunk.STREAM_CODEC.decode(buffer);
    });
    public static final StreamCodec<ByteBuf, NavChunk> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.<ByteBuf, LayeredNavChunk>list().apply(TYPED_LAYERED_NAV_CHUNK_CODEC),
                    navChunk -> navChunk.layers, NavChunk::new);
    public List<LayeredNavChunk> layers = new ArrayList<>();
    public ChunkPos chunkPos;

    public NavChunk(ChunkPos pos) {
        this.chunkPos = pos;
    }

    private NavChunk(List<LayeredNavChunk> layers) {
        this.layers = layers;
        for (LayeredNavChunk layer : this.layers) {
            layer.parentChunk = this;
        }
    }

    public Optional<LayeredNavChunk> getLayer(int layer, Supplier<LayeredNavChunk> supplier) {
        for (LayeredNavChunk layeredNavChunk : layers) {
            if (layeredNavChunk.layer == layer) {
                return Optional.of(layeredNavChunk);
            }
        }
        if (layers.size() >= NavBuildConfig.NAV_BUILD_CONFIG.getLeft().maxLayers.get()) {
            return Optional.empty();
        }
        var result = supplier.get();
        if (result == null) {
            return Optional.empty();
        }
        layers.add(supplier.get());
        return Optional.of(layers.getLast());
    }

    private static boolean isInRange(int a, int l, int r) {
        return l <= a && a <= r;
    }

    public Optional<LayeredNavChunk> getLayerNav(BlockPos pos) {
        var inner = new ChunkInnerPos(pos);
        // return the layer with walk y in range and possible max.
        return layers.stream().filter(layeredNavChunk -> isInRange(layeredNavChunk.getWalkY(inner.x, inner.z), pos.getY() - 2, pos.getY()))
                .max(Comparator.comparingInt(o -> o.getWalkY(inner.x, inner.z)));
    }

    public Stream<LayeredNavChunk> getLayers(BlockPos target) {
        var inner = new ChunkInnerPos(target);
        return this.layers.stream().filter(layer -> Math.abs(layer.getWalkY(inner.x, inner.z) - target.getY()) <= 1);
    }

    public void getLayers(BlockPos target, Consumer<LayeredNavChunk> layeredChunkConsumer) {
        var inner = new ChunkInnerPos(target);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < this.layers.size(); i++) {
            var layer = layers.get(i);

            if (Math.abs(layer.getWalkY(inner.x, inner.z) - target.getY() ) <= 1) {
                layeredChunkConsumer.accept(layer);
            }
        }
    }

    public void getLayers(BlockPos target, int distance, Consumer<NavPathFinder.EdgeInfo> edgeInfoConsumer) {
        int innerX = ChunkInnerPos.getInnerPos(target.getX());
        int innerZ = ChunkInnerPos.getInnerPos(target.getZ());
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < this.layers.size(); i++) {
            var layer = layers.get(i);

            final int y = layer.getWalkY(innerX, innerZ);
            final int delta = y - target.getY();
            if (-1 <= delta && delta <= 1) {
                edgeInfoConsumer.accept(new NavPathFinder.EdgeInfo(distance, new BlockPos(target.getX(), y, target.getZ()), this, layer));
            }
        }
    }


    public Optional<LayeredNavChunk> getNearestLayer(int bx, int y, int bz) {
        var pos = new ChunkInnerPos(bx, bz);
        return layers.stream().filter(layeredNavChunk -> Math.abs(y - layeredNavChunk.getWalkY(pos.x, pos.z)) <= 1).findAny();
    }

    public OptionalInt getNearestWalkY(int bx, int y, int bz) {
        var pos = new ChunkInnerPos(bx, bz);
        return layers.stream()
                .mapToInt(layeredNavChunk -> layeredNavChunk.getWalkY(pos.x, pos.z))
                .filter(layeredNavChunk -> Math.abs(y - layeredNavChunk) <= 1).findAny();
    }

    /***
     * Return the distance sampled from the pos with +x or +z
     * @param pos the pos to sample distance
     * @param isZ is +z
     * @return the distance or -1 if not found
     */
    public int getDistance(BlockPos pos, boolean isZ) {
        var inner = new ChunkInnerPos(pos);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < layers.size(); i++) {
            var layeredNavChunk = layers.get(i);
            final int delta = (layeredNavChunk.getWalkY(inner.x, inner.z) - pos.getY());
            if (-1 <= delta && delta <= 1) {
                // we checked for the walk y is checked.
                return layeredNavChunk.getDistance(inner.x, inner.z, isZ);
            }
        }
        return -1;
    }

    /***
     * Return the distance sampled from the pos with +x or +z
     * @param pos the pos to sample distance
     * @param isZ is +z
     * @return the distance or -1 if not found or cannot walk
     */
    public int getDistanceChecked(BlockPos pos, boolean isZ) {
        var inner = new ChunkInnerPos(pos);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < layers.size(); i++) {
            var layeredNavChunk = layers.get(i);
            final int delta = layeredNavChunk.getWalkY(inner.x, inner.z) - pos.getY();
            if (-1 <= delta && delta <= 1) {
                // we checked for the walk y is checked.
                return layeredNavChunk.getDistanceChecked(inner.x, inner.z, isZ);
            }
        }
        return -1;
    }

    public void removeNavChunk(LayeredNavChunk layeredNavChunk) {
        this.layers.remove(layeredNavChunk);
    }
}
