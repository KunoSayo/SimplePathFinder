package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.util.NavUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class NavPathFinder {
    private final HashSet<SearchedPos> visitedPos = new HashSet<>();
    private final LevelNavData levelNavData;
    private final PriorityQueue<SearchNode> searchNodes = new PriorityQueue<>();
    private final BlockPos start;
    private final BlockPos end;

    public NavPathFinder(LevelNavData levelNavData, BlockPos start, BlockPos end) {
        this.levelNavData = levelNavData;
        this.start = start;
        this.end = end;
    }

    private void init() {
        var startChunk = new ChunkPos(start);
        levelNavData.getNavChunk(startChunk, false).ifPresent(navChunk -> {
            navChunk.getLayerNav(start).ifPresent(layeredNavChunk -> {
                searchNodes.add(new SearchNode(0, start, layeredNavChunk, null));
            });
        });

    }

    private Stream<EdgeInfo> getEdge(NavChunk navChunk, NavChunk bNavChunk, BlockPos a, BlockPos b) {


        int situation = LayeredNavChunk.getPosSituation(a, b);
        boolean isZ = (situation & 1) == 1;
        int distance;
        if (situation > 1) {
            distance = bNavChunk.getDistanceChecked(b, isZ);
        } else {
            distance = navChunk.getDistanceChecked(a, isZ);
        }

        if (distance < 0.0) {
            return Stream.empty();
        }
        return bNavChunk.getLayers(b).map(layeredNavChunk -> new EdgeInfo(distance, b, bNavChunk, layeredNavChunk));
    }

    private List<EdgeInfo> getEdge(NavChunk navChunk, LayeredNavChunk layeredNavChunk, BlockPos a, ChunkPos ca) {
        List<EdgeInfo> list = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            var t = a.offset(LayeredNavChunk.SEARCH_DX[i], 0, LayeredNavChunk.SEARCH_DZ[i]);
            boolean isSame = NavUtil.isSameChunk(a, t);
            NavChunk thatChunk = navChunk;
            if (!isSame) {
                thatChunk = levelNavData.getNavChunk(new ChunkPos(t), false).orElse(null);
                if (thatChunk == null) {
                    continue;
                }
            }
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                var b = t.offset(0, yOffset, 0);
                list.addAll(getEdge(navChunk, thatChunk, a, b).toList());
            }
        }
        return list;
    }

    Optional<NavResult> search() {
        init();

        while (!searchNodes.isEmpty()) {
            var node = searchNodes.poll();

            if (!this.visitedPos.add(new SearchedPos(node.layer.layer, node.pos))) {
                continue;
            }
            if (node.pos.distManhattan(this.end) <= 1) {
                return Optional.of(new NavResult(node, this.end));
            }
            for (EdgeInfo edgeInfo : getEdge(node.layer.parentChunk, node.layer, node.pos, new ChunkPos(node.pos))) {
                if (!edgeInfo.isValid()) {
                    continue;
                }
                long extraCost = node.getExtraCost(edgeInfo.targetPos);
                searchNodes.add(new SearchNode(extraCost + edgeInfo.distance + node.cost, edgeInfo.targetPos, edgeInfo.targetLayeredChunk, node));
            }

        }
        return Optional.empty();
    }

    public record EdgeInfo(int distance, BlockPos targetPos, NavChunk targetNavChunk,
                           LayeredNavChunk targetLayeredChunk) {
        boolean isValid() {
            return distance >= 0;
        }
    }
}

record SearchedPos(int layer, BlockPos pos) {
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
}

class SearchNode implements Comparable<SearchNode> {
    final long cost;
    final BlockPos pos;
    final LayeredNavChunk layer;
    @Nullable
    final SearchNode lastNode;

    public SearchNode(long cost, BlockPos pos, LayeredNavChunk layer, @Nullable SearchNode lastNode) {
        this.cost = cost;
        this.pos = pos;
        this.layer = layer;
        this.lastNode = lastNode;
    }

    @Override
    public int compareTo(@NotNull SearchNode o) {
        return Long.compare(cost, o.cost);
    }

    public long getExtraCost(BlockPos next) {
        if (lastNode == null) {
            return 0;
        }
        if ((next.subtract(pos)).equals(pos.subtract(lastNode.pos))) {
            return 0;
        }
        return 3;
    }

}