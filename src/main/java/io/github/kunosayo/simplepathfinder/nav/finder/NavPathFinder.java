package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import io.github.kunosayo.simplepathfinder.nav.layered.AbstractLayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.progress.PathfindingContext;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import io.github.kunosayo.simplepathfinder.util.UncheckedArrayList;
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
    public static final UncheckedArrayList<List<SearchNode>> VISIT_NODE_CACHE = new UncheckedArrayList<>(VISIT_CACHE_SIZE);

    static {
        for (int i = 0; i < VISIT_CACHE_SIZE; i++) {
            VISIT_NODE_CACHE.set(i, new ArrayList<>());
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
    private final PathfindingContext ctx;
    EdgeConsumer finalEdgeConsumer = this;

    public NavPathFinder(LevelNavData levelNavData, ResourceKey<Level> dimension, BlockPos start, BlockPos end, PathfindingContext ctx) {
        this.levelNavData = levelNavData;
        this.dimension = dimension;
        this.start = start;
        this.end = end;
        this.ctx = ctx;
    }

    public NavPathFinder(LevelNavData levelNavData, BlockPos start, BlockPos end, PathfindingContext ctx) {
        this.levelNavData = levelNavData;
        this.start = start;
        this.end = end;
        this.dimension = null;
        this.ctx = ctx;
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
                    ctx.setInitialH(h);
                    long priority = (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
                    SearchNode startNode = new SearchNode(0, priority, h, start.getX(), start.getY(), start.getZ(), layeredNavChunk, null, null);
                    if (this.cacheIndex == -1) {
                        layeredNavChunk.putSearchNode(this, startNode);
                    } else {
                        layeredNavChunk.putSearchNodeEnsureCached(this, startNode);
                    }
                    searchNodes.push(startNode);
                }));
    }

    private void getEdge(INavChunk chunk, int ax, int az, int bx, int bz, int y, int currentDistance, int lastDistance, EdgeConsumer edgeInfoConsumer) {
        if (chunk == null || currentDistance < 0) {
            return;
        }
        if (currentDistance > lastDistance) {
            currentDistance += (currentDistance - lastDistance) * 20;
        }
        chunk.getEdgeForLayers(bx, y, bz, currentDistance, edgeInfoConsumer);
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
    private void getNavLinkEdges(INavChunk navChunk, int x, int y, int z, EdgeConsumer edgeInfoConsumer) {

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

    private void getEdgeForCheckConnection(INavChunk navChunk, int x, int y, int z, EdgeConsumer edgeInfoConsumer) {
        // First, get edges from navigation links (teleports, vehicles, etc.)
        getNavLinkEdges(navChunk, x, y, z, edgeInfoConsumer);

        // Then, get normal walking edges
        for (int i = 0; i < 4; i++) {
            int tx = x + LayeredNavChunk.SEARCH_DX[i];
            int tz = z + LayeredNavChunk.SEARCH_DZ[i];
            boolean isSame = NavUtil.isSameChunk(x, z, tx, tz);
            var thatChunk = navChunk;
            if (!isSame) {
                thatChunk = levelNavData.readNavChunk(tx >> 4, tz >> 4);
                if (thatChunk == null) {
                    continue;
                }
            }

            int distance = getDistance(navChunk, thatChunk, x, z, tx, tz, y);
            if (distance < 0) continue;
            thatChunk.getEdgeForLayers(tx, y, tz, distance, edgeInfoConsumer);

        }
    }

    private void getEdge(INavChunk navChunk, AbstractLayeredNavChunk layeredNavChunk, int x, int y, int z, int lx, int lz, EdgeConsumer edgeInfoConsumer) {
        // First, get edges from navigation links (teleports, vehicles, etc.)
        getNavLinkEdges(navChunk, x, y, z, edgeInfoConsumer);
        int lastDistance = 0;
        {
            // check last distance.
            var navChunk1 = levelNavData.readNavChunk(lx >> 4, lz >> 4);
            if (navChunk1 != null) {
                lastDistance = getDistance(navChunk1, navChunk, lx, lz, x, z, y);
            }
        }

        // Then, get normal walking edges, We expanded these codes.
//        for (int i = 0; i < 4; i++) {
//            int tx = x + LayeredNavChunk.SEARCH_DX[i];
//            int tz = z + LayeredNavChunk.SEARCH_DZ[i];
//            if (tx == lx && tz == lz) {
//                continue;
//            }
//            boolean isSame = NavUtil.isSameChunk(x, z, tx, tz);
//            var thatChunk = navChunk;
//            if (!isSame) {
//                thatChunk = levelNavData.readNavChunk(tx >> 4, tz >> 4);
//                if (thatChunk == null) {
//                    continue;
//                }
//            }
//
//            int distance = getDistance(navChunk, thatChunk, x, z, tx, tz, y);
//            if (distance < 0) continue;
//            getEdge(thatChunk, x, z, tx, tz, y, distance, lastDistance, edgeInfoConsumer);
//
//
//            for (int j = 1; j >= -1; j -= 2) {
//                //反转 xz，并乘+-1，获取共轭向量
//                int diagX = tx + LayeredNavChunk.SEARCH_DZ[i] * j;
//                int diagZ = tz + LayeredNavChunk.SEARCH_DX[i] * j;
//
//                boolean isSameDiag = NavUtil.isSameChunk(tx, tz, diagX, diagZ);
//                var diagChunk = thatChunk;
//                if (!isSameDiag) {
//                    var thatChunkOpt = levelNavData.readNavChunk(diagX >> 4, diagZ >> 4);
//                    if (thatChunkOpt == null) {
//                        continue;
//                    }
//                    diagChunk = thatChunkOpt;
//                }
//                int distance2 = getDistance(thatChunk, diagChunk, tx, tz, diagX, diagZ, y);
//                if (distance2 < 0) continue;
//                getEdge(diagChunk, tx, tz, diagX, diagZ, y, Math.max(distance2, distance), lastDistance, edgeInfoConsumer);
//            }
//        }

        int px = x + 1;
        int pz = z + 1;
        int nx = x - 1;
        int nz = z - 1;
        var pxData = navChunk;
        var pzData = navChunk;
        var nxData = navChunk;
        var nzData = navChunk;
        var nXpZData = navChunk;
        var pXnZData = navChunk;
        var nXnZData = navChunk;
        var pXpZData = navChunk;


        if (NavUtil.isSameChunk(x, z, px, z)) {
            if (NavUtil.isSameChunk(x, z, nx, z)) {
                if (NavUtil.isSameChunk(x, z, x, pz)) {
                    // CASE 0: no code, all x & z in same chunk.
                    if (!NavUtil.isSameChunk(x, z, x, nz)) {
                        // CASE 1: -z different chunk.
                        pXnZData = nXnZData = nzData = levelNavData.readNavChunkWorldPos(x, nz);
                    }
                } else {
                    // CASE 2: +z different chunk
                    nXpZData = pzData = levelNavData.readNavChunkWorldPos(x, pz);
                }
            } else {
                nxData = levelNavData.readNavChunkWorldPos(nx, z);
                if (NavUtil.isSameChunk(x, z, x, pz)) {
                    if (NavUtil.isSameChunk(x, z, x, nz)) {
                        // CASE 3: -x different chunk
                        nXpZData = nXnZData = nxData;
                    } else {
                        // -z is same chunk, so we need check -z
                        // CASE 4: -x -z different chunk
                        pXnZData = nzData = levelNavData.readNavChunkWorldPos(x, nz);
                        nXpZData = nxData;
                        nXnZData = levelNavData.readNavChunkWorldPos(nx, nz);
                    }
                } else {
                    // pz is different chunk
                    // CASE 5: -x +z different chunk
                    pXpZData = pzData = levelNavData.readNavChunkWorldPos(x, pz);
                    nXnZData = nxData;
                    nXpZData = levelNavData.readNavChunkWorldPos(nx, pz);
                }
            }
        } else {
            pxData = levelNavData.readNavChunkWorldPos(px, z);
            if (NavUtil.isSameChunk(x, z, x, pz)) {
                if (NavUtil.isSameChunk(x, z, x, nz)) {
                    // CASE 6: +x different chunk
                    pXpZData = pXnZData = pxData;
                } else {
                    // -z is same chunk, so we need check -z
                    // CASE 7: +x -z different chunk
                    nXnZData = nzData = levelNavData.readNavChunkWorldPos(x, nz);
                    pXpZData = pxData;
                    pXnZData = levelNavData.readNavChunkWorldPos(px, nz);
                }
            } else {
                // +z is different chunk
                // CASE 8: +x +z different chunk
                nXpZData = pzData = levelNavData.readNavChunkWorldPos(x, pz);
                pXnZData = pxData;
                pXpZData = levelNavData.readNavChunkWorldPos(px, pz);
            }
        }


        int ix = x & 15;
        int iz = z & 15;
        int ipx = px & 15;
        int ipz = pz & 15;
        int inx = nx & 15;
        int inz = nz & 15;

        int pXpZDistance = -1;
        int nXpZDistance = -1;
        int pXnZDistance = -1;
        int nXnZDistance = -1;
        int pxDistance = layeredNavChunk.getPositiveDistanceX(ix, iz);
        int pzDistance = layeredNavChunk.getPositiveDistanceZ(ix, iz);
        int nxDistance = -1;
        int nzDistance = -1;

        // we have
        // .-.-.
        // | | |
        // .-.-.
        // | | |
        // .-.-.
        // 12 distances in total.

        if (pxData != null && pxDistance >= 0) {
            int pzDis = pxData.getPositiveDistanceZ(y, ipx, iz);
            if (pzDis >= 0) {
                pXpZDistance = Math.max(pxDistance, pzDis);
            }
        }
        if (pzData != null && pzDistance >= 0) {
            // [+x] from (x, pz) to (px, pz)
            int dis = pzData.getPositiveDistanceX(y, ix, ipz);
            if (dis >= 0) {
                if (pXpZDistance >= 0) {
                    pXpZDistance = Math.min(pXpZDistance, Math.max(pzDistance, dis));
                } else {
                    pXpZDistance = Math.max(pzDistance, dis);
                }
            }
        }
        if (nxData != null) {
            // [+x] from (nx, z) to (x, z)
            nxDistance = nxData.getPositiveDistanceX(y, inx, iz);
            if (nxDistance >= 0) {
                {
                    // [+z] from (nx, z) to (nx, pz)
                    int dis = nxData.getPositiveDistanceZ(y, inx, iz);
                    if (dis >= 0) {
                        nXpZDistance = Math.max(nxDistance, dis);
                    }
                }
            }
        }
        if (nzData != null) {
            // [+z] from (x, nz) to (x, z)
            nzDistance = nzData.getPositiveDistanceZ(y, ix, inz);
            if (nzDistance >= 0) {
                {
                    // [+x] from (x, nz) to (px, nz)
                    int dis = nzData.getPositiveDistanceX(y, ix, inz);
                    if (dis >= 0) {
                        pXnZDistance = Math.max(nzDistance, dis);
                    }
                }
            }
        }

        if (nXnZData != null) {
            if (nzDistance >= 0) {
                // [+x] from (nx, nz) to (x, nz)
                int dis = nXnZData.getPositiveDistanceX(y, inx, inz);
                if (dis >= 0) {
                    nXnZDistance = Math.max(nzDistance, dis);
                }
            }
            if (nxDistance >= 0) {
                // [+z] from (nx, nz) to (nx, z)
                int dis = nXnZData.getPositiveDistanceZ(y, inx, inz);
                if (dis >= 0) {
                    if (nXnZDistance >= 0) {
                        nXnZDistance = Math.min(nXnZDistance, Math.max(nxDistance, dis));
                    } else {
                        nXnZDistance = Math.max(nxDistance, dis);
                    }
                }
            }
        }

        if (nXpZData != null && pzDistance >= 0) {
            // [+x] from (nx, pz) to (x, pz)
            int dis = nXpZData.getPositiveDistanceX(y, inx, ipz);
            if (dis >= 0) {
                if (nXpZDistance >= 0) {
                    nXpZDistance = Math.min(nXpZDistance, Math.max(pzDistance, dis));
                } else {
                    nXpZDistance = Math.max(pzDistance, dis);
                }
            }
        }

        if (pXnZData != null && pxDistance >= 0) {
            // [+z] from (px, nz) to (px, z)
            int dis = pXnZData.getPositiveDistanceZ(y, ipx, inz);
            if (dis >= 0) {
                if (pXnZDistance >= 0) {
                    pXnZDistance = Math.min(pXnZDistance, Math.max(pxDistance, dis));
                } else {
                    pXnZDistance = Math.max(pxDistance, dis);
                }
            }
        }

        if (lx == px) {
            if (lz == pz) {
                // not to +x +z
                // and not to around.
                // so we only go -x -z
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXpZLayer, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            } else if (lz == nz) {
                // not to +x -z
                // and not to around.
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXnZLayer, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            } else {
                // not to px
//                getEdge(pxLayer, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            }
        } else if (lx == nx) {
            if (lz == pz) {
                // not to -x +z
                // and not to around.
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXpZLayer, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            } else if (lz == nz) {
                // not to -x -z
                // and not to around.
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXnZLayer, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            } else {
                // not to -x
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nxLayer, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            }
        } else {
            if (lz == pz) {
                // not to +z
                // and not to around.
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pzLayer, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            } else if (lz == nz) {
                // not to -z
                // and not to around.
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nzLayer, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);          just nz!
                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
//                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            } else {
                // full
                getEdge(pxData, x, z, px, z, y, pxDistance, lastDistance, edgeInfoConsumer);
                getEdge(pzData, x, z, x, pz, y, pzDistance, lastDistance, edgeInfoConsumer);
                getEdge(nxData, x, z, nx, z, y, nxDistance, lastDistance, edgeInfoConsumer);
                getEdge(nzData, x, z, x, nz, y, nzDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXpZData, x, z, px, pz, y, pXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXpZData, x, z, nx, pz, y, nXpZDistance, lastDistance, edgeInfoConsumer);
                getEdge(pXnZData, x, z, px, nz, y, pXnZDistance, lastDistance, edgeInfoConsumer);
                getEdge(nXnZData, x, z, nx, nz, y, nXnZDistance, lastDistance, edgeInfoConsumer);
            }
        }
    }

    private void getEdge(SearchNode node, EdgeConsumer edgeInfoConsumer) {
        if (node.lastNode == null) {
            getEdge(node.layer.getParentChunk(), node.layer(), node.x, node.y, node.z,
                    Integer.MIN_VALUE + 9,
                    Integer.MIN_VALUE + 9, edgeInfoConsumer);
        } else {
            getEdge(node.layer.getParentChunk(), node.layer(), node.x, node.y, node.z,
                    node.lastNode.x,
                    node.lastNode.z, edgeInfoConsumer);
        }

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
        EdgeConsumer edgeConsumer = visited != null ? (_, tx, _, tz, layerChunk, _) -> {
            if (SearchedPos.markVisited(this, visited, layerChunk, tx, tz)) {
                long nextKey = SearchedPos.toLong(layerChunk.getLayer(), tx, tz);
                queue.enqueue(nextKey);
            }
        } : (_, tx, _, tz, layerChunk, _) -> {
            if (SearchedPos.markVisitedCached(this, layerChunk, tx, tz)) {
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
            getEdgeForCheckConnection(currentChunk, cx, y, cz, edgeConsumer);
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
            ctx.markCompleted();
            return Optional.empty();
        }
        addCacheCount(this.cacheIndex, 1);
        init();


        while (!searchNodes.isEmpty()) {
            var node = searchNodes.pop();
            currentSearchingNode = node;
            ctx.onNodePopped(node.hValue);

            if (NavUtil.distManhattan(this.end, node.x, node.y, node.z) <= 1) {
                ctx.markCompleted();
                return Optional.of(new NavResult(node, this.end));
            }
            getEdge(node, finalEdgeConsumer);
//            node.layer.checkExtraPath(this, node, this);
        }
        ctx.markCompleted();
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
        if (this.cacheIndex == -1) {
            finalEdgeConsumer = (int distance, int tx, int ty, int tz, AbstractLayeredNavChunk layer, NavLinkType type) -> {
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
            };
        }
        try {
            return _search();
        } finally {
            if (this.cacheIndex != -1) {
                VISIT_NODE_CACHE.get(this.cacheIndex).clear();
                USING_CACHE_VISIT[this.cacheIndex].set(false);
            }
        }
    }

    private static void addCacheCount(int i, int cnt) {
        if (i >= 0) {
            CACHE_COUNT[i] += cnt;
        }
    }

    @Override
    public void acceptEdge(int distance, int tx, int ty, int tz, AbstractLayeredNavChunk layer, NavLinkType type) {
        // By default, we use cached method.

        var node = currentSearchingNode;
        SearchNode existingNode = layer.getSearchNodeEnsureCached(this, tx, tz);

        if (existingNode != null && existingNode.heapIndex == -2) {
            return;
        }

        long extraCost = node.getExtraCost(tx, ty, tz);
        long new_g = extraCost + distance + node.cost;

        if (existingNode == null) {
            long h = getHeuristic(tx, ty, tz);
            long new_f = new_g + (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
            SearchNode targetNode = new SearchNode(new_g, new_f, h, tx, ty, tz, layer, node, type);
            layer.putSearchNodeEnsureCached(this, targetNode);
            searchNodes.push(targetNode);
        } else if (new_g < existingNode.cost) {
            existingNode.cost = new_g;
            existingNode.priority = new_g + (existingNode.hValue * HEURISTIC_WEIGHT_PERCENT) / 100L;
            existingNode.lastNode = node;
            searchNodes.decreaseKey(existingNode);
        }
    }

    public record SearchedPos(int layer, BlockPos pos) {
        /**
         * @return true if not visited before.
         */
        private static boolean markVisited(NavPathFinder finder, LongOpenHashSet visited, AbstractLayeredNavChunk layerChunk, BlockPos start) {
            if (finder.cacheIndex == -1) {
                return visited.add(toLong(layerChunk.getLayer(), start));
            }
            return layerChunk.markVisited(finder.cacheIndex, NavPathFinder.CACHE_COUNT[finder.cacheIndex], start);
        }

        public static boolean markVisited(NavPathFinder finder, LongOpenHashSet visited, AbstractLayeredNavChunk layerChunk, int tx, int tz) {
            return visited.add(toLong(layerChunk.getLayer(), tx, tz));
        }

        public static boolean markVisitedCached(NavPathFinder finder, AbstractLayeredNavChunk layerChunk, int tx, int tz) {
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
        public final AbstractLayeredNavChunk layer;
        public final NavLinkType navLinkType;
        public SearchNode lastNode;
        public int heapIndex = -1;

        public SearchNode(long cost, long priority, long hValue, int x, int y, int z, AbstractLayeredNavChunk layer, SearchNode lastNode, NavLinkType navLinkType) {
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

        public AbstractLayeredNavChunk layer() {
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

        public boolean isGreaterEqual(@NotNull SearchNode o) {
            if (this.priority == o.priority) {
                return this.hValue >= o.hValue;
            }
            return this.priority > o.priority;
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
            int a = 30;
            int px = x;
            int pz = z;
            int lx = lastNode.x;
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
            return cy + 2 * a;
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
                if (node.isGreaterEqual(parent)) {
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
                if (rightIndex < size && !heap[rightIndex].isGreaterEqual(child)) {
                    childIndex = rightIndex;
                    child = heap[rightIndex];
                }
                if (child.isGreaterEqual(node)) {
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
