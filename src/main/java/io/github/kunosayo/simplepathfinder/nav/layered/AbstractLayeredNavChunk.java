package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;

public abstract class AbstractLayeredNavChunk implements ILayeredNavChunk {
    private final int[][] visitedCache = new int[NavPathFinder.VISIT_CACHE_SIZE][256];


    @Override
    public boolean markVisited(int cacheIndex, int cnt, int tx, int tz) {
        final int[] data = visitedCache[cacheIndex];
        final int idx = convertToIndex(tx & 15, tz & 15);
        boolean result = data[idx] != cnt;
        data[idx] = cnt;
        return result;
    }

    static int convertToIndex(int x, int z) {
        return (x << 4) | z;
    }

}
