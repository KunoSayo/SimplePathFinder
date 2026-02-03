package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.config.NavBuildConfig;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class NavChunk implements INavChunk {
    private static final StreamCodec<ByteBuf, ILayeredNavChunk> TYPED_LAYERED_NAV_CHUNK_CODEC = StreamCodec.of((buffer, value) -> {
        if (value instanceof LayeredNavChunk layeredNavChunk) {
            buffer.writeByte(0);
            LayeredNavChunk.STREAM_CODEC.encode(buffer, layeredNavChunk);
            return;
        }

        throw new IllegalArgumentException("Not supported nav chunk");
    }, buffer -> {
        // todo: use interface.
        byte type = buffer.readByte();
        if (type == 0) {
            return LayeredNavChunk.STREAM_CODEC.decode(buffer);
        }

        throw new IllegalArgumentException("Not supported nav chunk");
    });
    public static final StreamCodec<ByteBuf, NavChunk> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.<ByteBuf, ILayeredNavChunk>list().apply(TYPED_LAYERED_NAV_CHUNK_CODEC),
                    navChunk -> navChunk.layers, NavChunk::new);
    public List<ILayeredNavChunk> layers = new ArrayList<>();
    public ChunkPos chunkPos;


    public NavChunk(ChunkPos pos) {
        this.chunkPos = pos;
    }

    private NavChunk(List<ILayeredNavChunk> layers) {
        this.layers = layers;
        for (ILayeredNavChunk layer : this.layers) {
            layer.setParentChunk(this);
        }
    }

    @Override
    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    @Override
    public void setChunkPos(ChunkPos chunkPos) {
        this.chunkPos = chunkPos;
    }

    @Override
    public Optional<ILayeredNavChunk> getLayer(int layer, Supplier<LayeredNavChunk> supplier) {
        for (var layeredNavChunk : layers) {
            if (layeredNavChunk.getLayer() == layer) {
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

    @Override
    public Optional<ILayeredNavChunk> getLayerNav(BlockPos pos) {
        var inner = new ChunkInnerPos(pos);
        // return the layer with walk y in range and possible max.
        return layers.stream().filter(layeredNavChunk -> isInRange(layeredNavChunk.getWalkY(inner.x, inner.z), pos.getY() - 2, pos.getY()))
                .max(Comparator.comparingInt(o -> o.getWalkY(inner.x, inner.z)))
                .map(layeredNavChunk -> (ILayeredNavChunk) layeredNavChunk);
    }

    @Override
    public Stream<ILayeredNavChunk> getLayers(BlockPos target) {
        var inner = new ChunkInnerPos(target);
        return this.layers.stream().filter(layer -> Math.abs(layer.getWalkY(inner.x, inner.z) - target.getY()) <= 1)
                .map(layer -> (ILayeredNavChunk) layer);
    }

    @Override
    public void getLayers(BlockPos target, Consumer<ILayeredNavChunk> consumer) {
        var inner = new ChunkInnerPos(target);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < this.layers.size(); i++) {
            var layer = layers.get(i);

            if (Math.abs(layer.getWalkY(inner.x, inner.z) - target.getY()) <= 1) {
                consumer.accept(layer);
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


    @Override
    public Optional<ILayeredNavChunk> getNearestLayer(int bx, int y, int bz) {
        var pos = new ChunkInnerPos(bx, bz);
        return layers.stream().filter(layeredNavChunk -> Math.abs(y - layeredNavChunk.getWalkY(pos.x, pos.z)) <= 1)
                .findAny()
                .map(layeredNavChunk -> (ILayeredNavChunk) layeredNavChunk);
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

    @Override
    public void removeNavChunk(ILayeredNavChunk layeredNavChunk) {
        if (layeredNavChunk instanceof LayeredNavChunk) {
            this.layers.remove((LayeredNavChunk) layeredNavChunk);
        }
    }

    @Override
    public int getLayerCount() {
        return this.layers.size();
    }
}
