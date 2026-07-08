package io.github.kunosayo.simplepathfinder.nav.progress;

public class ClientProgressManager {
    private volatile PathfindingContext currentCtx;

    public void start(PathfindingContext ctx) {
        currentCtx = ctx;
    }

    public PathfindingContext getCurrent() {
        return currentCtx;
    }
    public void clear() {
        currentCtx = null;
    }
}