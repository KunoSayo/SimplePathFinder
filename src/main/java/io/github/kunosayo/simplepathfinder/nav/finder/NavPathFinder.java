package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class NavPathFinder implements EdgeConsumer {
    // Wh 权重参数，100L 代表 1.0，150L 代表 1.5
    public static final long HEURISTIC_WEIGHT_PERCENT = 110L;
    public static final int VISIT_CACHE_SIZE = 2;
    public static final int NULL_POS = Integer.MIN_VALUE + 9;
    private static final AtomicBoolean[] USING_CACHE_VISIT;
    private static final int[] CACHE_COUNT = new int[VISIT_CACHE_SIZE];
    public static final ArrayList<List<SearchNode>> VISIT_NODE_CACHE = new ArrayList<>();

    static {
        for (int i = 0; i < VISIT_CACHE_SIZE; i++) {
            VISIT_NODE_CACHE.add(new ArrayList<>());
        }
    }


    static {
        USING_CACHE_VISIT = new AtomicBoolean[VISIT_CACHE_SIZE];
        for (int i = 0; i < VISIT_CACHE_SIZE; i++) {
            USING_CACHE_VISIT[i] = new AtomicBoolean();
        }
    }

    public final HashSet<Object> visitedObjects = new HashSet<>();
    public final IdentityHashMap<Object, Object> extraFinderData = new IdentityHashMap<>();
    public final Long2ObjectOpenHashMap<SearchNode> visitedNodes = new Long2ObjectOpenHashMap<>(1024, 0.5f);
    private final LevelNavData levelNavData;
    private final SearchNodeHeap searchNodes = new SearchNodeHeap(1024);
    private SearchNode currentSearchingNode = null;
    private BlockPos start;
    private BlockPos end;
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

    public int getCacheIndex() {
        return cacheIndex;
    }

    public int getCacheVisitCount() {
        return CACHE_COUNT[cacheIndex];
    }

    public BlockPos getEnd() {
        return this.end;
    }

    private long getHeuristic(int tx, int ty, int tz) {
        long dx = Math.abs(tx - end.getX());
        long dz = Math.abs(tz - end.getZ());
        long dy = Math.abs(ty - end.getY());
        long horizontal = 10L * Math.max(dx, dz) + 5L * Math.min(dx, dz);
        long vertical = 10L * dy;

        // TODO: 10L
        long h = Math.max(horizontal, vertical);

        if (HEURISTIC_WEIGHT_PERCENT == 100L) {
            long dx1 = tx - end.getX();
            long dz1 = tz - end.getZ();
            long dx2 = start.getX() - end.getX();
            long dz2 = start.getZ() - end.getZ();
            long cross = Math.abs(dx1 * dz2 - dx2 * dz1);
            return h + (cross / 1000L); // TODO: 1000L
        }

        return h;
    }

    private void init() {
        if (this.cacheIndex != -1) {
            VISIT_NODE_CACHE.get(this.cacheIndex).clear();
        }
        var startChunk = ChunkPos.containing(start);
        levelNavData.getNavChunk(startChunk, false)
                .map(navChunk -> navChunk.getLayerNav(start))
                .ifPresent(layeredNavChunks -> layeredNavChunks.forEach(layeredNavChunk -> {
                    long h = getHeuristic(start.getX(), start.getY(), start.getZ());
                    long priority = (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
                    SearchNode startNode = new SearchNode(0, priority, h, start.getX(), start.getY(), start.getZ(), layeredNavChunk, null, null);
                    layeredNavChunk.putSearchNode(this, startNode);
                    searchNodes.push(startNode);
                }));
    }

    private void getEdge(INavChunk navChunk, INavChunk bNavChunk, int ax, int az, int bx, int bz, int y, EdgeConsumer edgeInfoConsumer) {
        // the y of b should be the same as a
        int distance = getDistance(navChunk, bNavChunk, ax, az, bx, bz, y);
        if (distance < 0) {
            return;
        }
        bNavChunk.getEdgeForLayers(bx, y, bz, distance, edgeInfoConsumer);
    }

    private void getEdgeThrough(INavChunk navChunk, INavChunk mNavChunk, INavChunk bNavChunk, int ax, int az, int mx, int mz, int bx, int bz, int y, EdgeConsumer edgeInfoConsumer) {
        // the y of b should be the same as a
        int distance = getDistance(navChunk, mNavChunk, ax, az, mx, mz, y);
        if (distance < 0) {
            return;
        }
        int distance2 = getDistance(mNavChunk, bNavChunk, mx, mz, bx, bz, y);
        if (distance2 < 0)
            return;
        bNavChunk.getEdgeForLayers(bx, y, bz, (int) ((distance + distance2) * 0.7), edgeInfoConsumer);
    }


    private int getDistance(INavChunk navChunk, INavChunk bNavChunk, int ax, int az, int bx, int bz, int y) {
        int situation = LayeredNavChunk.getPosSituation(ax, az, bx, bz);
        boolean isZ = (situation & 1) == 1;
        if (situation > 1) {
            return bNavChunk.getDistance(bx, y, bz, isZ);
        } else {
            return navChunk.getDistance(ax, y, az, isZ);
        }

    }

    /**
     * Get edges from navigation links at the current position.
     * This allows the pathfinder to consider teleports, vehicles, and other travel methods.
     */
    private void getNavLinkEdges(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, int x, int y, int z, EdgeConsumer edgeInfoConsumer) {

        // Get all nav links from this position
        for (var navLink : navChunk.getNavLinks(x, y, z)) {
            var destPos = navLink.dest();


            // Get the nav chunk at destination
            var destNavChunkOpt = levelNavData.getNavChunk(destPos.getX() >> 4, destPos.getZ() >> 4, false);
            if (destNavChunkOpt.isEmpty()) {
                continue;
            }
            var destNavChunk = destNavChunkOpt.get();

            // Find the layer at destination
            destNavChunk.getLayerNav(destPos).forEach(
                    destLayer -> {
                        // Add edge info for the nav link
                        edgeInfoConsumer.acceptEdge(0, destPos.getX(), destPos.getY(), destPos.getZ(), destLayer, navLink.type());
                    });
        }
    }

    private void getEdge(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, int x, int y, int z, int lx, int ly, int lz, EdgeConsumer edgeInfoConsumer) {
        // First, get edges from navigation links (teleports, vehicles, etc.)
        getNavLinkEdges(navChunk, layeredNavChunk, x, y, z, edgeInfoConsumer);

        // Then, get normal walking edges
        for (int i = 0; i < 4; i++) {
            int tx = x + LayeredNavChunk.SEARCH_DX[i];
            int tz = z + LayeredNavChunk.SEARCH_DZ[i];
            if (tx == lx && tz == lz) {
                continue;
            }
            boolean isSame = NavUtil.isSameChunk(x, z, tx, tz);
            var thatChunk = navChunk;
            if (!isSame) {
                Optional<INavChunk> thatChunkOpt = levelNavData.getNavChunk(tx >> 4, tz >> 4, false);
                if (thatChunkOpt.isEmpty()) {
                    continue;
                }
                thatChunk = thatChunkOpt.get();
            }

            getEdge(navChunk, thatChunk, x, z, tx, tz, y, edgeInfoConsumer);

            for (int j = 1; j >= -1; j -= 2) {
                //反转 xz，并乘+-1，获取共轭向量
                int diagX = tx + LayeredNavChunk.SEARCH_DZ[i] * j;
                int diagZ = tz + LayeredNavChunk.SEARCH_DX[i] * j;

                boolean isSameDiag = NavUtil.isSameChunk(tx, tz, diagX, diagZ);
                var diagChunk = thatChunk;
                if (!isSameDiag) {
                    Optional<INavChunk> thatChunkOpt = levelNavData.getNavChunk(diagX >> 4, diagZ >> 4, false);
                    if (thatChunkOpt.isEmpty()) {
                        continue;
                    }
                    diagChunk = thatChunkOpt.get();
                }

                getEdgeThrough(navChunk, thatChunk, diagChunk, x, z, tx, tz, diagX, diagZ, y, edgeInfoConsumer);
            }
        }
    }

    private void getEdge(SearchNode node, EdgeConsumer edgeInfoConsumer) {
        getEdge(node.layer.getParentChunk(), node.layer, node.x, node.y, node.z,
                node.lastNode != null ? node.lastNode.x : Integer.MIN_VALUE + 9,
                node.lastNode != null ? node.lastNode.y : Integer.MIN_VALUE + 9,
                node.lastNode != null ? node.lastNode.z : Integer.MIN_VALUE + 9, edgeInfoConsumer);
    }

    private boolean checkConnectivity() {
        // Target check
        var endChunkPos = ChunkPos.containing(end);
        var endChunkOpt = levelNavData.getNavChunk(endChunkPos, false);
        if (endChunkOpt.isEmpty()) {
            return false;
        }
        var endLayerOpt = endChunkOpt.get().getLayerNav(end).findAny();
        if (endLayerOpt.isEmpty()) {
            return false;
        }

        // Start check
        var startChunkPos = ChunkPos.containing(start);
        var startChunkOpt = levelNavData.getNavChunk(startChunkPos, false);
        if (startChunkOpt.isEmpty()) {
            return false;
        }
        var startLayerOpt = startChunkOpt.get().getLayerNav(start).findAny();
        if (startLayerOpt.isEmpty()) {
            return false;
        }

        long startKey = SearchedPos.toLong(startLayerOpt.get().getLayer(), start);
        long targetKey = SearchedPos.toLong(endLayerOpt.get().getLayer(), end);

        if (startKey == targetKey) {
            return true;
        }

        var queue = new LongArrayFIFOQueue(8192);
        LongOpenHashSet visited = this.cacheIndex == -1 ? new LongOpenHashSet(1024) : null;

        queue.enqueue(startKey);
        SearchedPos.markVisited(this, visited, startLayerOpt.get(), start);
        EdgeConsumer edgeConsumer = (_, tx, _, tz, layerChunk, _) -> {
            if (SearchedPos.markVisited(this, visited, layerChunk, tx, tz)) {
                long nextKey = SearchedPos.toLong(layerChunk.getLayer(), tx, tz);
                queue.enqueue(nextKey);
            }
        };
        while (!queue.isEmpty()) {
            long currentKey = queue.dequeueLong();

            if (currentKey == targetKey) {
                return true;
            }

            // Unpack currentKey
            int cx = (int) ((currentKey >> 27) & 0x7FFFFFF);
            if ((cx & 0x4000000) != 0) cx |= 0xF8000000;
            int cz = (int) (currentKey & 0x7FFFFFF);
            if ((cz & 0x4000000) != 0) cz |= 0xF8000000;
            byte clayer = (byte) (currentKey >> 54);

            var currentChunkOpt = levelNavData.getNavChunk(cx >> 4, cz >> 4, false);
            if (currentChunkOpt.isEmpty()) continue;
            var currentChunk = currentChunkOpt.get();
            var currentLayerOpt = levelNavData.getNavChunk(cx >> 4, cz >> 4, clayer);
            if (currentLayerOpt.isEmpty()) continue;
            var currentLayer = currentLayerOpt.get();

            int y = currentLayer.getWalkY(cx & 15, cz & 15);
            if (!currentLayer.isWalkYValid(y)) continue;
            getEdge(currentChunk, currentLayer, cx, y, cz, NULL_POS, NULL_POS, NULL_POS, edgeConsumer);
        }

        return false;
    }

    private void adjustStartEnd() {
        var startChunk = ChunkPos.containing(start);
        boolean isEmpty = levelNavData.getNavChunk(startChunk, false)
                .flatMap(navChunk -> navChunk.getLayerNav(start).findAny())
                .isEmpty();
        if (isEmpty) {
            start = new BlockPos(start.getX(), start.getY() + 1, start.getZ());
        }

        var endChunk = ChunkPos.containing(end);
        levelNavData.getNavChunk(endChunk, false)
                .map(navChunk -> navChunk.getLayerNav(end))
                .flatMap(Stream::findAny)
                .ifPresentOrElse(_ -> {
                }, () -> {
                    end = end.offset(0, 1, 0);
                    levelNavData.getNavChunk(endChunk, false)
                            .map(navChunk -> navChunk.getLayerNav(end))
                            .flatMap(Stream::findAny)
                            .ifPresentOrElse(_ -> {
                            }, () -> end = end.offset(0, -2, 0));
                });
    }


    private Optional<NavResult> _search() {
        adjustStartEnd();
        if (!checkConnectivity()) {
            return Optional.empty();
        }
        addCacheCount(this.cacheIndex, 1);
        init();


        while (!searchNodes.isEmpty()) {
            var node = searchNodes.pop();
            currentSearchingNode = node;

            if (NavUtil.distManhattan(this.end, node.x, node.y, node.z) <= 1) {
                return Optional.of(new NavResult(node, this.end));
            }
            getEdge(node, this);
//            node.layer.checkExtraPath(this, node, this);
        }
        return Optional.empty();
    }

    public Optional<NavResult> search() {
        this.cacheIndex = -1;
        for (int i = 0; i < VISIT_CACHE_SIZE; i++) {
            if (USING_CACHE_VISIT[i].compareAndSet(false, true)) {
                this.cacheIndex = i;
                addCacheCount(i, 1);
                break;
            }
        }
        try {

            return _search();
        } finally {
            if (this.cacheIndex != -1) {
                USING_CACHE_VISIT[this.cacheIndex].set(false);
                VISIT_NODE_CACHE.get(this.cacheIndex).clear();
            }
        }
    }

    private static void addCacheCount(int i, int cnt) {
        if (i >= 0) {
            CACHE_COUNT[i] += cnt;
        }
    }

    @Override
    public void acceptEdge(int distance, int tx, int ty, int tz, ILayeredNavChunk layer, NavLinkType type) {
        var node = currentSearchingNode;
        SearchNode existingNode = layer.getSearchNode(this, tx, tz);

        if (existingNode != null && existingNode.heapIndex == -2) {
            return;
        }

        long extraCost = node.getExtraCost(tx, ty, tz);
        long new_g = extraCost + distance + node.cost;

        if (existingNode == null) {
            long h = getHeuristic(tx, ty, tz);
            long new_f = new_g + (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
            SearchNode targetNode = new SearchNode(new_g, new_f, h, tx, ty, tz, layer, node, type);
            layer.putSearchNode(this, targetNode);
            searchNodes.push(targetNode);
        } else if (new_g < existingNode.cost) {
            existingNode.cost = new_g;
            existingNode.priority = new_g + (existingNode.hValue * HEURISTIC_WEIGHT_PERCENT) / 100L;
            existingNode.lastNode = node;
            searchNodes.decreaseKey(existingNode);
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
        private static boolean markVisited(NavPathFinder finder, LongOpenHashSet visited, ILayeredNavChunk layerChunk, BlockPos start) {
            if (finder.cacheIndex == -1) {
                return visited.add(toLong(layerChunk.getLayer(), start));
            }
            return layerChunk.markVisited(finder.cacheIndex, NavPathFinder.CACHE_COUNT[finder.cacheIndex], start);
        }

        public static boolean markVisited(NavPathFinder finder, LongOpenHashSet visited, ILayeredNavChunk layerChunk, int tx, int tz) {
            if (finder.cacheIndex == -1) {
                return visited.add(toLong(layerChunk.getLayer(), tx, tz));
            }
            return layerChunk.markVisited(finder.cacheIndex, NavPathFinder.CACHE_COUNT[finder.cacheIndex], tx, tz);
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

        public static long toLong(byte layer, int x, int z) {
            return (((long) x & 0x7FFFFFF) << 27) | (z & 0x7FFFFFF) | ((long) layer << 54);
        }
    }

    public static class SearchNode implements Comparable<SearchNode> {
        public long cost; // g(u)
        public long priority; // f(u)
        public final long hValue; // h(u)
        public final int x;
        public final int y;
        public final int z;
        public final ILayeredNavChunk layer;
        public final NavLinkType navLinkType;
        public SearchNode lastNode;
        public int heapIndex = -1;

        public SearchNode(long cost, long priority, long hValue, int x, int y, int z, ILayeredNavChunk layer, SearchNode lastNode, NavLinkType navLinkType) {
            this.cost = cost;
            this.priority = priority;
            this.hValue = hValue;
            this.x = x;
            this.y = y;
            this.z = z;
            this.layer = layer;
            this.lastNode = lastNode;
            this.navLinkType = navLinkType;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
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
            int px = x;
            int py = y;
            int pz = z;
            int lx = lastNode.x;
            int ly = lastNode.y;
            int lz = lastNode.z;
            if (nx - px == px - lx
                    && ny - py == py - ly
                    && nz - pz == pz - lz) {
                return 0;
            }
            return 37;
        }

        public long getExtraCost(int nx, int ny, int nz) {
            if (lastNode == null) {
                return 0;
            }
            int a = 10;
            int px = x;
            int py = y;
            int pz = z;
            int lx = lastNode.x;
            int ly = lastNode.y;
            int lz = lastNode.z;
            int cy = y != ny ? a : 0;

            int ddx = (px - lx) - (nx - px);
            int ddz = (pz - lz) - (nz - pz);
            if (ddx == 0 && ddz == 0) {
                return cy;
            }
            if ((ddx == 0 && (ddz == -1 || ddz == 1)) || (ddz == 0 && (ddx == -1 || ddx == 1))) {
                return cy + a;
            }
            return cy + 4 * a;
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
