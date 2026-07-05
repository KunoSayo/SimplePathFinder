package io.github.kunosayo.simplepathfinder.nav.finder;

public abstract class CachedVisitObject {
    public int[] visited = new int[NavPathFinder.VISIT_CACHE_SIZE];

    /**
     *
     * @return true if not visited before;
     */
    public boolean markVisited(NavPathFinder finder) {
        int idx = finder.getCacheIndex();
        if (idx == -1) {
            return finder.visitedObjects.add(this);
        } else {
            int cnt = finder.getCacheVisitCount();
            boolean isSame = visited[idx] != cnt;
            visited[idx] = cnt;
            return !isSame;
        }
    }
}
