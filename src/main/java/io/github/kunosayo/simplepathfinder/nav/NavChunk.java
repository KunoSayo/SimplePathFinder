package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.config.NavBuildConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class NavChunk {
    public static final StreamCodec<ByteBuf, NavChunk> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.<ByteBuf, LayeredNavChunk>list().apply(LayeredNavChunk.STREAM_CODEC),
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
        return this.layers.stream().filter(layer -> layer.getWalkY(inner.x, inner.z) == target.getY());
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
        return layers.stream().filter(layeredNavChunk -> layeredNavChunk.getWalkY(inner.x, inner.z) == pos.getY())
                .map(layeredNavChunk -> layeredNavChunk.getDistance(inner.x, inner.z, isZ))
                .findAny().orElse(-1);
    }
}
