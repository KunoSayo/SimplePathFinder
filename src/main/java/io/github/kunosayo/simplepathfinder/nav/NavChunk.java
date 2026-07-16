package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.nav.finder.EdgeConsumer;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("ForLoopReplaceableByForEach")
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

    private static final StreamCodec<ByteBuf, Map<ChunkInnerPos, List<NavLink>>> NAV_LINKS_MAP_OLD_CODEC = ByteBufCodecs.map(
            HashMap::new,
            ChunkInnerPos.STREAM_CODEC,
            ByteBufCodecs.<ByteBuf, NavLink>list().apply(NavLink.STREAM_CODEC)
    );


    private static final StreamCodec<ByteBuf, Map<ChunkInnerPosWithY, List<NavLink>>> NAV_LINKS_MAP_NEW_CODEC = ByteBufCodecs.map(
            HashMap::new,
            ChunkInnerPosWithY.STREAM_CODEC,
            ByteBufCodecs.<ByteBuf, NavLink>list().apply(NavLink.STREAM_CODEC)
    );

    private static final StreamCodec<ByteBuf, Map<ChunkInnerPosWithY, List<NavLink>>> NAV_LINKS_MAP_CODEC = NAV_LINKS_MAP_NEW_CODEC;

    public static final StreamCodec<ByteBuf, NavChunk> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.<ByteBuf, ILayeredNavChunk>list().apply(TYPED_LAYERED_NAV_CHUNK_CODEC),
                    navChunk -> navChunk.layers,
                    NAV_LINKS_MAP_CODEC,
                    navChunk -> {
                        var map = new HashMap<ChunkInnerPosWithY, List<NavLink>>(navChunk.navLinks.size());
                        navChunk.navLinks.forEach((integer, navLinks1) -> {
                            map.put(ChunkInnerPosWithY.unpack(integer), navLinks1);
                        });
                        return map;
                    },
                    NavChunk::new
            );


    private List<ILayeredNavChunk> layers = new ArrayList<>();
    public ChunkPos chunkPos;

    private Int2ObjectOpenHashMap<List<NavLink>> navLinks = new Int2ObjectOpenHashMap<>();


    public NavChunk(ChunkPos pos) {
        this.chunkPos = pos;
    }

    private NavChunk(List<ILayeredNavChunk> layers, Map<ChunkInnerPosWithY, List<NavLink>> navLinks) {
        this.layers = new ArrayList<>(layers);
        List<ILayeredNavChunk> iLayeredNavChunks = this.layers;
        for (int i = 0; i < iLayeredNavChunks.size(); i++) {
            ILayeredNavChunk layer = iLayeredNavChunks.get(i);
            layer.setParentChunk(this);
        }
        this.navLinks.ensureCapacity(navLinks.size());
        navLinks.forEach((chunkInnerPosWithY, navLinks1) -> {
            this.navLinks.put(chunkInnerPosWithY.pack(), navLinks1);
        });
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
        var layers = this.layers;
        for (int i = 0, layersSize = layers.size(); i < layersSize; i++) {
            ILayeredNavChunk layerChunk = layers.get(i);
            if (layerChunk.getLayer() == layer) {
                return Optional.of(layerChunk);
            }
        }


        if (layers.size() >= NavConfig.NAV_CONFIG.getLeft().maxLayers.get()) {
            return Optional.empty();
        }
        var result = supplier.get();
        if (result == null) {
            return Optional.empty();
        }

        layers.add(supplier.get());
        this.layers = layers;
        return Optional.of(layers.getLast());
    }

    @Override
    public Optional<ILayeredNavChunk> getLayer(int layer) {
        var layers = this.layers;
        for (int i = 0, layersSize = layers.size(); i < layersSize; i++) {
            ILayeredNavChunk layerChunk = layers.get(i);
            if (layerChunk.getLayer() == layer) {
                return Optional.of(layerChunk);
            }
        }
        return Optional.empty();
    }

    private static boolean isInRange(int a, int l, int r) {
        return l <= a && a <= r;
    }

    @Override
    public Stream<ILayeredNavChunk> getLayerNav(BlockPos pos) {
        var inner = ChunkInnerPos.get(pos);
        // return the layer with walk y in range and possible max.
        return layers.stream().filter(layeredNavChunk -> isInRange(layeredNavChunk.getWalkY(inner.x, inner.z), pos.getY() - 1, pos.getY()));
    }

    @Override
    public Collection<ILayeredNavChunk> getLayersCollection() {
        return this.layers;
    }

    @Override
    public void getEdgeForLayers(int x, int y, int z, int distance, EdgeConsumer edgeInfoConsumer) {
        int innerX = ChunkInnerPos.getInnerPos(x);
        int innerZ = ChunkInnerPos.getInnerPos(z);
        List<ILayeredNavChunk> iLayeredNavChunks = this.layers;
        for (int i = 0, iLayeredNavChunksSize = iLayeredNavChunks.size(); i < iLayeredNavChunksSize; i++) {
            ILayeredNavChunk layer = iLayeredNavChunks.get(i);
            final int wy = layer.getWalkY(innerX, innerZ);
            final int delta = y - wy;
            if (Math.abs(delta) <= 1) {
                edgeInfoConsumer.acceptEdge(distance, x, wy, z, layer, null);
            }
        }
    }


    @Override
    public Optional<ILayeredNavChunk> getNearestLayer(int bx, int y, int bz) {
        var pos = ChunkInnerPos.getWithModulo(bx, bz);
        return layers.stream().filter(layeredNavChunk -> Math.abs(y - layeredNavChunk.getWalkY(pos.x, pos.z)) <= 1)
                .findAny();
    }

    public OptionalInt getNearestWalkY(int bx, int y, int bz) {
        var pos = ChunkInnerPos.getWithModulo(bx, bz);
        return layers.stream()
                .mapToInt(layeredNavChunk -> layeredNavChunk.getWalkY(pos.x, pos.z))
                .filter(layeredNavChunk -> Math.abs(y - layeredNavChunk) <= 1).findAny();
    }

    public int getDistance(int x, int y, int z, boolean isZ) {
        var inner = ChunkInnerPos.getWithModulo(x, z);
        var layers = this.layers;
        for (int i = 0, layersSize = layers.size(); i < layersSize; i++) {
            ILayeredNavChunk layeredNavChunk = layers.get(i);
            final int delta = (layeredNavChunk.getWalkY(inner.x, inner.z) - y);
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
            var layers = new ArrayList<>(this.layers);
            layers.remove(layeredNavChunk);
            this.layers = layers;
        }
    }

    @Override
    public int getLayerCount() {
        return this.layers.size();
    }

    @Override
    public List<NavLink> getNavLinks(int x, int y, int z) {
        return navLinks.getOrDefault(ChunkInnerPosWithY.pack(x, y, z), Collections.emptyList());
    }

    @Override
    public Map<ChunkInnerPosWithY, List<NavLink>> getAllNavLinks() {
        var map = new HashMap<ChunkInnerPosWithY, List<NavLink>>(this.navLinks.size());
        this.navLinks.forEach((integer, navLinks1) -> map.put(ChunkInnerPosWithY.unpack(integer), navLinks1));
        return map;
    }

    @Override
    public void addNavLink(ChunkInnerPosWithY from, NavLink link) {
        var map = new Int2ObjectOpenHashMap<>(this.navLinks);
        // CoW list.
        var list = new ArrayList<>(map.computeIfAbsent(from.pack(), _ -> new ArrayList<>()));
        list.add(link);
        map.put(from.pack(), list);
        this.navLinks = map;
    }

    @Override
    public boolean removeNavLinks(ChunkInnerPosWithY pos) {
        if (this.navLinks.containsKey(pos.pack())) {
            var map = new Int2ObjectOpenHashMap<>(this.navLinks);
            map.remove(pos.pack());
            this.navLinks = map;
            return true;
        }
        return false;
    }

    @Override
    public void clearNavLinks() {
        navLinks = new Int2ObjectOpenHashMap<>();
    }
}
