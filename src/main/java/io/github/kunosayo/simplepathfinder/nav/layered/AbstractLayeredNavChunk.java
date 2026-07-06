package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.nav.finder.NavPathFinder;

import java.lang.ref.WeakReference;

public abstract class AbstractLayeredNavChunk implements ILayeredNavChunk {
    private final int[][] visitedCache = new int[NavPathFinder.VISIT_CACHE_SIZE][256];
    private final int[][] visitedSearchNode = new int[NavPathFinder.VISIT_CACHE_SIZE][256];


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

    @Override
    public NavPathFinder.SearchNode getSearchNode(NavPathFinder finder, int tx, int tz) {
        int idx = finder.getCacheIndex();
        if (idx == -1) {
            return ILayeredNavChunk.super.getSearchNode(finder, tx, tz);
        }

        int cnt = finder.getCacheVisitCount();
        final int pointIdx = convertToIndex(tx & 15, tz & 15);
        if (visitedCache[idx][pointIdx] == cnt) {
            return finder.visitedNodesByArr.get(visitedSearchNode[idx][pointIdx]);
        }
        return null;
    }

    @Override
    public void putSearchNode(NavPathFinder finder, NavPathFinder.SearchNode node) {
        int idx = finder.getCacheIndex();
        if (idx == -1) {
            ILayeredNavChunk.super.putSearchNode(finder, node);
            return;
        }
        final int pointIdx = convertToIndex(node.x & 15, node.z & 15);
        visitedCache[idx][pointIdx] = finder.getCacheVisitCount();
        int len = finder.visitedNodesByArr.size();
        finder.visitedNodesByArr.add(node);
        visitedSearchNode[idx][pointIdx] = len;
    }
}
