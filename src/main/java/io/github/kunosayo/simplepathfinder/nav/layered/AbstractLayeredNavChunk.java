package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;
import net.minecraft.core.BlockPos;

public abstract class AbstractLayeredNavChunk implements ILayeredNavChunk {
    private final int[][] visitedCache;

    public AbstractLayeredNavChunk() {
        this.visitedCache = new int[NavPathFinder.VISIT_CACHE_SIZE][];
        for (int i = 0; i < NavPathFinder.VISIT_CACHE_SIZE; i++) {
            this.visitedCache[i] = new int[16 * 16];
        }
    }

    @Override
    public boolean markVisited(int cacheIndex, int cnt, BlockPos pos) {
        final int[] data = visitedCache[cacheIndex];
        final int idx = convertToIndex(pos.getX() & 15, pos.getZ() & 15);
        boolean result = data[idx] != cnt;
        data[idx] = cnt;
        return result;
    }

    static int convertToIndex(int x, int z) {
        return (x << 4) | z;
    }

}
