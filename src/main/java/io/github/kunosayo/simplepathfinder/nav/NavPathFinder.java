package io.github.kunosayo.simplepathfinder.nav;

import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.util.NavUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
    private final ResourceKey<Level> dimension;

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

    private void init() {
        var startChunk = ChunkPos.containing(start);
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

    /**
     * Get edges from navigation links at the current position.
     * This allows the pathfinder to consider teleports, vehicles, and other travel methods.
     */
    private void getNavLinkEdges(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, BlockPos a, ChunkPos ca, Consumer<EdgeInfo> edgeInfoConsumer) {
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

    private void getEdge(INavChunk navChunk, ILayeredNavChunk layeredNavChunk, BlockPos a, ChunkPos ca, Consumer<EdgeInfo> edgeInfoConsumer) {
        // First, get edges from navigation links (teleports, vehicles, etc.)
        getNavLinkEdges(navChunk, layeredNavChunk, a, ca, edgeInfoConsumer);

        // Then, get normal walking edges
        for (int i = 0; i < 4; i++) {
            var t = a.offset(LayeredNavChunk.SEARCH_DX[i], 0, LayeredNavChunk.SEARCH_DZ[i]);
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
            getEdge(node.layer().getParentChunk(), node.layer(), node.pos(), ChunkPos.containing(node.pos()), edgeInfo -> {
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
                           ILayeredNavChunk targetLayeredChunk, NavLinkType linkType) {
        public EdgeInfo(int distance, BlockPos targetPos, INavChunk targetNavChunk,
                        ILayeredNavChunk targetLayeredChunk) {
            this(distance, targetPos, targetNavChunk, targetLayeredChunk, null);
        }
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
}

