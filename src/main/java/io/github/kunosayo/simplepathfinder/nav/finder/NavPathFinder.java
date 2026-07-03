package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class NavPathFinder {
    // Wh 权重参数，100L 代表 1.0，150L 代表 1.5
    public static final long HEURISTIC_WEIGHT_PERCENT = 110L;
    public static final int VISIT_CACHE_SIZE = 3;
    private static final AtomicBoolean[] USING_CACHE_VISIT;
    private static final int[] CACHE_COUNT = new int[VISIT_CACHE_SIZE];

    static {
        USING_CACHE_VISIT = new AtomicBoolean[VISIT_CACHE_SIZE];
        for (int i = 0; i < VISIT_CACHE_SIZE; i++) {
            USING_CACHE_VISIT[i] = new AtomicBoolean();
        }
    }

    private final Long2ObjectOpenHashMap<SearchNode> visitedNodes = new Long2ObjectOpenHashMap<>(1024, 0.5f);
    private final LevelNavData levelNavData;
    private final SearchNodeHeap searchNodes = new SearchNodeHeap(1024);
    private final BlockPos start;
    private final BlockPos end;
    private final ResourceKey<Level> dimension;
    private int cacheIndex = -1;

    public NavPathFinder(LevelNavData levelNavData, BlockPos start, BlockPos end) {
        this.levelNavData = levelNavData;
        this.start = start;
        this.end = end;
        this.dimension = null; // Dimension will be set from level context
    }

    public NavPathFinder(LevelNavData levelNavData, ResourceKey<Level> dimension, BlockPos start, BlockPos end) {
        this.levelNavData = levelNavData;
        this.dimension = dimension;
        this.start = start;
        this.end = end;
    }

    private long getHeuristic(BlockPos pos) {
        long horizontal = Math.abs(pos.getX() - end.getX()) + Math.abs(pos.getZ() - end.getZ());
        long vertical = Math.abs(pos.getY() - end.getY());

        // TODO: 10L
        long h = Math.max(horizontal, vertical) * 10L;

        if (HEURISTIC_WEIGHT_PERCENT == 100L) {
            long dx1 = pos.getX() - end.getX();
            long dz1 = pos.getZ() - end.getZ();
            long dx2 = start.getX() - end.getX();
            long dz2 = start.getZ() - end.getZ();
            long cross = Math.abs(dx1 * dz2 - dx2 * dz1);
            return h + (cross / 1000L); // TODO: 1000L
        }

        return h;
    }

    private void init() {
        var startChunk = ChunkPos.containing(start);
        levelNavData.getNavChunk(startChunk, false)
                .flatMap(navChunk -> navChunk.getLayerNav(start))
                .ifPresent(layeredNavChunk -> {
                    if (layeredNavChunk instanceof LayeredNavChunk) {
                        long h = getHeuristic(start);
                        long priority = (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
                        SearchNode startNode = new SearchNode(0, priority, h, start, (LayeredNavChunk) layeredNavChunk, null);
                        long startKey = SearchedPos.toLong(layeredNavChunk.getLayer(), start);
                        visitedNodes.put(startKey, startNode);
                        searchNodes.push(startNode);
                    }
                });
    }

    private void getEdge(INavChunk navChunk, INavChunk bNavChunk, BlockPos a, BlockPos b, Consumer<EdgeInfo> edgeInfoConsumer) {
        // the y of b should be the same as a

        int situation = LayeredNavChunk.getPosSituation(a, b);
        boolean isZ = (situation & 1) == 1;
        int distance;
        if (situation > 1) {
            distance = bNavChunk.getDistance(b, isZ);
        } else {
            distance = navChunk.getDistance(a, isZ);
        }

        if (distance < 0) {
            return;
        }
        bNavChunk.getLayers(b, distance, edgeInfoConsumer);
    }

    /**
     * Get edges from navigation links at the current position.
     * This allows the pathfinder to consider teleports, vehicles, and other travel methods.
     */
    private void getNavLinkEdges(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, BlockPos a, Consumer<EdgeInfo> edgeInfoConsumer) {
        var chunkInnerPos = new ChunkInnerPos(a);

        // Get all nav links from this position
        for (var navLink : navChunk.getNavLinks(chunkInnerPos)) {
            var dest = navLink.dest();
            var destPos = dest.pos();

            // Skip if destination is in wrong dimension
            if (dimension != null && !dest.dimension().equals(dimension)) {
                continue;
            }

            // Calculate cost based on link type
            double costMultiplier = navLink.getCostMultiplier();
            int linkCost = (int) (100 * costMultiplier); // Base cost for using nav link

            // Get the nav chunk at destination
            var destChunkPos = ChunkPos.containing(destPos);
            var destNavChunkOpt = levelNavData.getNavChunk(destChunkPos, false);
            if (destNavChunkOpt.isEmpty()) {
                continue;
            }
            var destNavChunk = destNavChunkOpt.get();

            // Find the layer at destination
            destNavChunk.getLayerNav(destPos).ifPresentOrElse(
                    destLayer -> {
                        // Add edge info for the nav link
                        edgeInfoConsumer.accept(new EdgeInfo(
                                linkCost,
                                destPos,
                                destNavChunk,
                                destLayer,
                                navLink.type()
                        ));
                    },
                    () -> {
                        // No layer at destination, try to find nearest layer
                        destNavChunk.getNearestLayer(destPos.getX(), destPos.getY(), destPos.getZ())
                                .ifPresent(destLayer -> {
                                    edgeInfoConsumer.accept(new EdgeInfo(
                                            linkCost + 5, // Add penalty for layer mismatch
                                            destPos,
                                            destNavChunk,
                                            destLayer,
                                            navLink.type()
                                    ));
                                });
                    }
            );
        }
    }

    private void getEdge(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, BlockPos a, @Nullable BlockPos lastPos, Consumer<EdgeInfo> edgeInfoConsumer) {
        // First, get edges from navigation links (teleports, vehicles, etc.)
        getNavLinkEdges(navChunk, layeredNavChunk, a, edgeInfoConsumer);

        // Then, get normal walking edges
        for (int i = 0; i < 4; i++) {
            var t = a.offset(LayeredNavChunk.SEARCH_DX[i], 0, LayeredNavChunk.SEARCH_DZ[i]);
            if (lastPos != null) {
                if (lastPos.getX() == t.getX() && lastPos.getZ() == t.getZ()) {
                    continue;
                }
            }
            boolean isSame = NavUtil.isSameChunk(a, t);
            var thatChunk = navChunk;
            if (!isSame) {
                Optional<INavChunk> thatChunkOpt = levelNavData.getNavChunk(ChunkPos.containing(t), false);
                if (thatChunkOpt.isEmpty()) {
                    continue;
                }
                thatChunk = thatChunkOpt.get();
            }

            getEdge(navChunk, thatChunk, a, t, edgeInfoConsumer);
        }
    }

    private void getEdge(SearchNode node, Consumer<EdgeInfo> edgeInfoConsumer) {
        getEdge(node.layer.getParentChunk(), node.layer, node.pos, node.lastNode != null ? node.lastNode.pos : null, edgeInfoConsumer);
    }

    private boolean checkConnectivity() {
        // Target check
        var endChunkPos = ChunkPos.containing(end);
        var endChunkOpt = levelNavData.getNavChunk(endChunkPos, false);
        if (endChunkOpt.isEmpty()) {
            return false;
        }
        var endLayerOpt = endChunkOpt.get().getLayerNav(end);
        if (endLayerOpt.isEmpty()) {
            return false;
        }

        // Start check
        var startChunkPos = ChunkPos.containing(start);
        var startChunkOpt = levelNavData.getNavChunk(startChunkPos, false);
        if (startChunkOpt.isEmpty()) {
            return false;
        }
        var startLayerOpt = startChunkOpt.get().getLayerNav(start);
        if (startLayerOpt.isEmpty()) {
            return false;
        }

        long startKey = SearchedPos.toLong(startLayerOpt.get().getLayer(), start);
        long targetKey = SearchedPos.toLong(endLayerOpt.get().getLayer(), end);

        if (startKey == targetKey) {
            return true;
        }

        LongArrayList queue = new LongArrayList(1024);
        LongOpenHashSet visited = new LongOpenHashSet(1024);

        queue.add(startKey);
        SearchedPos.markVisited(this, visited, startLayerOpt.get(), start);

        int head = 0;
        while (head < queue.size()) {
            long currentKey = queue.getLong(head++);

            if (currentKey == targetKey) {
                return true;
            }

            // Unpack currentKey
            int cx = (int) ((currentKey >> 27) & 0x7FFFFFF);
            if ((cx & 0x4000000) != 0) cx |= 0xF8000000;
            int cz = (int) (currentKey & 0x7FFFFFF);
            if ((cz & 0x4000000) != 0) cz |= 0xF8000000;
            byte clayer = (byte) (currentKey >> 54);

            ChunkPos ccp = new ChunkPos(cx >> 4, cz >> 4);
            var currentChunkOpt = levelNavData.getNavChunk(ccp, false);
            if (currentChunkOpt.isEmpty()) continue;
            var currentChunk = currentChunkOpt.get();
            var currentLayerOpt = levelNavData.getNavChunk(ccp, clayer);
            if (currentLayerOpt.isEmpty()) continue;
            var currentLayer = currentLayerOpt.get();

            int y = currentLayer.getWalkY(cx & 15, cz & 15);
            if (!currentLayer.isWalkYValid(y)) continue;
            BlockPos cpos = new BlockPos(cx, y, cz);

            getEdge(currentChunk, currentLayer, cpos, null, edgeInfo -> {
                long nextKey = SearchedPos.toLong(edgeInfo.targetLayeredChunk.getLayer(), edgeInfo.targetPos);
                if (SearchedPos.markVisited(this, visited, edgeInfo.targetLayeredChunk, edgeInfo.targetPos)) {
                    queue.add(nextKey);
                }
            });
        }

        return false;
    }

    private Optional<NavResult> _search() {
        if (!checkConnectivity()) {
            return Optional.empty();
        }

        init();

        while (!searchNodes.isEmpty()) {
            var node = searchNodes.pop();

            if (node.pos.distManhattan(this.end) <= 1) {
                return Optional.of(new NavResult(node, this.end));
            }
            getEdge(node, edgeInfo -> {
                long vKey = SearchedPos.toLong(edgeInfo.targetLayeredChunk.getLayer(), edgeInfo.targetPos);
                SearchNode existingNode = visitedNodes.get(vKey);

                if (existingNode != null && existingNode.heapIndex == -2) {
                    return;
                }

                long extraCost = node.getExtraCost(edgeInfo.targetPos);
                long new_g = extraCost + edgeInfo.distance + node.cost;

                if (existingNode == null) {
                    long h = getHeuristic(edgeInfo.targetPos);
                    long new_f = new_g + (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
                    SearchNode targetNode = new SearchNode(new_g, new_f, h, edgeInfo.targetPos, edgeInfo.targetLayeredChunk, node);
                    visitedNodes.put(vKey, targetNode);
                    searchNodes.push(targetNode);
                } else if (new_g < existingNode.cost) {
                    existingNode.cost = new_g;
                    existingNode.priority = new_g + (existingNode.hValue * HEURISTIC_WEIGHT_PERCENT) / 100L;
                    existingNode.lastNode = node;
                    searchNodes.decreaseKey(existingNode);
                }
            });
        }
        return Optional.empty();
    }

    public Optional<NavResult> search() {
        this.cacheIndex = -1;
        for (int i = 0; i < VISIT_CACHE_SIZE; i++) {
            if (USING_CACHE_VISIT[i].compareAndSet(false, true)) {
                this.cacheIndex = i;
                CACHE_COUNT[i] += 1;
                break;
            }
        }
        try {

            return _search();
        } finally {
            if (this.cacheIndex != -1) {
                USING_CACHE_VISIT[this.cacheIndex].set(false);
            }
        }
    }

    public record EdgeInfo(int distance, BlockPos targetPos, INavChunk targetNavChunk,
                           ILayeredNavChunk targetLayeredChunk, NavLinkType linkType) {
        public EdgeInfo(int distance, BlockPos targetPos, INavChunk targetNavChunk,
                        ILayeredNavChunk targetLayeredChunk) {
            this(distance, targetPos, targetNavChunk, targetLayeredChunk, null);
        }
    }

    public record SearchedPos(int layer, BlockPos pos) {
        /**
         * @return true if not visited before.
         */
        private static boolean markVisited(NavPathFinder finder, LongOpenHashSet visited, ILayeredNavChunk iLayeredNavChunk, BlockPos start) {
            if (finder.cacheIndex == -1) {
                return visited.add(toLong(iLayeredNavChunk.getLayer(), start));
            }
            return iLayeredNavChunk.markVisited(finder.cacheIndex, NavPathFinder.CACHE_COUNT[finder.cacheIndex], start);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SearchedPos that = (SearchedPos) o;
            return layer == that.layer && Objects.equals(pos, that.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(layer, pos);
        }

        public static long toLong(byte layer, BlockPos pos) {
            // y use 8 bit
            // 27 bit for x and y
            return (((long) pos.getX() & 0x7FFFFFF) << 27) | (pos.getZ() & 0x7FFFFFF) | ((long) layer << 54);
        }

    }

    public static class SearchNode implements Comparable<SearchNode> {
        public long cost; // g(u)
        public long priority; // f(u)
        public final long hValue; // h(u)
        public final BlockPos pos;
        public final ILayeredNavChunk layer;
        public SearchNode lastNode;
        public int heapIndex = -1;

        public SearchNode(long cost, long priority, long hValue, BlockPos pos, ILayeredNavChunk layer, SearchNode lastNode) {
            this.cost = cost;
            this.priority = priority;
            this.hValue = hValue;
            this.pos = pos;
            this.layer = layer;
            this.lastNode = lastNode;
        }

        public BlockPos pos() {
            return pos;
        }

        public ILayeredNavChunk layer() {
            return layer;
        }

        public SearchNode lastNode() {
            return lastNode;
        }

        public long cost() {
            return cost;
        }

        @Override
        public int compareTo(@NotNull SearchNode o) {
            int cmp = Long.compare(this.priority, o.priority);
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(this.hValue, o.hValue);
        }

        public long getExtraCost(BlockPos next) {
            if (lastNode == null) {
                return 0;
            }
            int nx = next.getX();
            int ny = next.getY();
            int nz = next.getZ();
            int px = pos.getX();
            int py = pos.getY();
            int pz = pos.getZ();
            int lx = lastNode.pos.getX();
            int ly = lastNode.pos.getY();
            int lz = lastNode.pos.getZ();
            if (nx - px == px - lx
                    && ny - py == py - ly
                    && nz - pz == pz - lz) {
                return 0;
            }
            return 37;
        }
    }

    public static class SearchNodeHeap {
        private SearchNode[] heap;
        private int size;

        public SearchNodeHeap(int capacity) {
            this.heap = new SearchNode[capacity];
            this.size = 0;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public void push(SearchNode node) {
            if (size == heap.length) {
                SearchNode[] newHeap = new SearchNode[heap.length * 2];
                System.arraycopy(heap, 0, newHeap, 0, heap.length);
                heap = newHeap;
            }
            heap[size] = node;
            node.heapIndex = size;
            size++;
            siftUp(size - 1);
        }

        public SearchNode pop() {
            if (size == 0) return null;
            SearchNode minNode = heap[0];
            minNode.heapIndex = -2;
            SearchNode lastNode = heap[size - 1];
            size--;
            if (size > 0) {
                heap[0] = lastNode;
                lastNode.heapIndex = 0;
                siftDown(0);
            }
            heap[size] = null;
            return minNode;
        }

        public void decreaseKey(SearchNode node) {
            if (node.heapIndex >= 0) {
                siftUp(node.heapIndex);
            }
        }

        private void siftUp(int index) {
            SearchNode node = heap[index];
            while (index > 0) {
                int parentIndex = (index - 1) >>> 1;
                SearchNode parent = heap[parentIndex];
                if (node.compareTo(parent) >= 0) {
                    break;
                }
                heap[index] = parent;
                parent.heapIndex = index;
                index = parentIndex;
            }
            heap[index] = node;
            node.heapIndex = index;
        }

        private void siftDown(int index) {
            SearchNode node = heap[index];
            int half = size >>> 1;
            while (index < half) {
                int childIndex = (index << 1) + 1;
                SearchNode child = heap[childIndex];
                int rightIndex = childIndex + 1;
                if (rightIndex < size && heap[rightIndex].compareTo(child) < 0) {
                    childIndex = rightIndex;
                    child = heap[rightIndex];
                }
                if (node.compareTo(child) <= 0) {
                    break;
                }
                heap[index] = child;
                child.heapIndex = index;
                index = childIndex;
            }
            heap[index] = node;
            node.heapIndex = index;
        }
    }
}
