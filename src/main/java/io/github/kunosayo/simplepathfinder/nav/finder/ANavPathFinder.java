package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import io.github.kunosayo.simplepathfinder.nav.layered.AbstractLayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.progress.PathfindingContext;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Optional;

public class ANavPathFinder extends NavPathFinder {
    public ANavPathFinder(LevelNavData levelNavData, ResourceKey<Level> dimension, BlockPos start, BlockPos end, PathfindingContext ctx) {
        super(levelNavData, dimension, start, end, ctx);
    }

    public ANavPathFinder(LevelNavData levelNavData, BlockPos start, BlockPos end, PathfindingContext ctx) {
        super(levelNavData, start, end, ctx);
    }

    @Override
    protected void init() {
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
                    SearchNode startNode = new SearchNode(0, h, start.getX(), start.getY(), start.getZ(), layeredNavChunk, null, null);
                    if (this.cacheIndex == -1) {
                        layeredNavChunk.putSearchNode(this, startNode);
                    } else {
                        layeredNavChunk.putSearchNodeEnsureCached(this, startNode);
                    }
                    searchNodes.push(startNode, priority, h);
                }));
    }

    @Override
    protected Optional<NavResult> _search() {
        adjustStartEnd();
        if (!checkConnectivity()) {
            ctx.markCompleted();
            return Optional.empty();
        }
        addCacheCount(this.cacheIndex);
        init();


        while (!searchNodes.isEmpty()) {
            var node = searchNodes.pop();
            currentSearchingNode = node;
            ctx.onNodePopped(node.hValue);

            if (NavUtil.distManhattan(this.end, node.x, node.y, node.z) <= 1) {
                ctx.markCompleted();
                if (this.cacheIndex != -1 && requireDebug) {
                    bugNodes = new ArrayList<>(VISIT_NODE_CACHE.get(this.cacheIndex));
                }
                return Optional.of(new NavResult(node, this.end));
            }
            getEdge(node, finalEdgeConsumer);
//            node.layer.checkExtraPath(this, node, this);
        }
        ctx.markCompleted();
        // this should be bug.
        if (this.cacheIndex != -1 && requireDebug) {
            bugNodes = new ArrayList<>(VISIT_NODE_CACHE.get(this.cacheIndex));
        }
        return Optional.empty();
    }


    @Override
    public void acceptEdge(int distance, int tx, int ty, int tz, AbstractLayeredNavChunk layer, NavLinkType type) {
        // By default, we use cached method.

        var node = currentSearchingNode;
        SearchNode existingNode = layer.getSearchNodeEnsureCached(this, tx, tz);

        if (existingNode == null) {
            long extraCost = node.getExtraCost(tx, ty, tz);
            long new_g = extraCost + distance + node.cost;
            long h = getHeuristic(tx, ty, tz);
            long new_f = new_g + (h * HEURISTIC_WEIGHT_PERCENT) / 100L;
            SearchNode targetNode = new SearchNode(new_g, h, tx, ty, tz, layer, node, type);
            layer.putSearchNodeEnsureCached(this, targetNode);
            searchNodes.push(targetNode, new_f, h);
        } else {
            if (existingNode.heapIndex == -2) {
                return;
            }
            long extraCost = node.getExtraCost(tx, ty, tz);
            long new_g = extraCost + distance + node.cost;
            if (new_g < existingNode.cost) {
                existingNode.cost = new_g;
                existingNode.lastNode = node;
                final int heapIdx = existingNode.heapIndex;
                final long newPriority = new_g + (searchNodes.getHValue(heapIdx) * HEURISTIC_WEIGHT_PERCENT) / 100L;
                searchNodes.decreaseKey(heapIdx, newPriority);
            }
        }
    }
}

