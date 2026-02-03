package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class NavPathFinder {
    private final LongOpenHashSet visitedPos = new LongOpenHashSet(1024, 0.5f);
    private final LevelNavData levelNavData;
    private final ObjectHeapPriorityQueue<SearchNode> searchNodes = new ObjectHeapPriorityQueue<>();
    private final BlockPos start;
    private final BlockPos end;

    public NavPathFinder(LevelNavData levelNavData, BlockPos start, BlockPos end) {
        this.levelNavData = levelNavData;
        this.start = start;
        this.end = end;
    }

    private void init() {
        var startChunk = new ChunkPos(start);
        levelNavData.getNavChunk(startChunk, false)
                .flatMap(navChunk -> navChunk.getLayerNav(start))
                .ifPresent(layeredNavChunk -> {
                    if (layeredNavChunk instanceof LayeredNavChunk) {
                        searchNodes.enqueue(new SearchNode(0, start, (LayeredNavChunk) layeredNavChunk, null));
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

    private void getEdge(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, BlockPos a, ChunkPos ca, Consumer<EdgeInfo> edgeInfoConsumer) {
        for (int i = 0; i < 4; i++) {
            var t = a.offset(LayeredNavChunk.SEARCH_DX[i], 0, LayeredNavChunk.SEARCH_DZ[i]);
            boolean isSame = NavUtil.isSameChunk(a, t);
            var thatChunk = navChunk;
            if (!isSame) {
                Optional<NavChunk> thatChunkOpt = levelNavData.getNavChunk(new ChunkPos(t), false);
                if (thatChunkOpt.isEmpty()) {
                    continue;
                }
                thatChunk = thatChunkOpt.get();
            }

            getEdge(navChunk, thatChunk, a, t, edgeInfoConsumer);
        }
    }

    Optional<NavResult> search() {
        init();

        while (!searchNodes.isEmpty()) {
            var node = searchNodes.dequeue();

            if (!this.visitedPos.add(SearchedPos.toLong(node.layer().getLayer(), node.pos()))) {
                continue;
            }
            if (node.pos().distManhattan(this.end) <= 1) {
                return Optional.of(new NavResult(node, this.end));
            }
            getEdge(node.layer().getParentChunk(), node.layer(), node.pos(), new ChunkPos(node.pos()), edgeInfo -> {
                if (node.lastNode != null) {
                    var lastPos = node.lastNode.pos;
                    if (lastPos.getX() == edgeInfo.targetPos.getX() && lastPos.getZ() == edgeInfo.targetPos.getZ()) {
                        return;
                    }
                }
                if (visitedPos.contains(SearchedPos.toLong(edgeInfo.targetLayeredChunk.getLayer(), edgeInfo.targetPos))) {
                    return;
                }

                long extraCost = node.getExtraCost(edgeInfo.targetPos);
                var targetNode = new SearchNode(extraCost + edgeInfo.distance + node.cost, edgeInfo.targetPos, edgeInfo.targetLayeredChunk, node);
                searchNodes.enqueue(targetNode);
            });
        }
        return Optional.empty();
    }

    public record EdgeInfo(int distance, BlockPos targetPos, INavChunk targetNavChunk,
                           ILayeredNavChunk targetLayeredChunk) {
    }

    public record SearchedPos(int layer, BlockPos pos) {
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

//        return BlockPos.asLong(pos.getX(), ((int) layer) + 128, pos.getZ());

            // y use 8 bit
            // 27 bit for x and y
            return (((long) pos.getX() & 0x7FFFFFF) << 27) | (pos.getZ() & 0x7FFFFFF) | ((long) layer << 54);
        }
    }

    public record SearchNode(long cost, BlockPos pos, ILayeredNavChunk layer,
                             @Nullable SearchNode lastNode) implements Comparable<SearchNode> {
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
}

