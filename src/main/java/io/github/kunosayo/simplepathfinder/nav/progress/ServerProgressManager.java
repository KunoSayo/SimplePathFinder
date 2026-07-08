package io.github.kunosayo.simplepathfinder.nav.progress;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerProgressManager {
    private final ConcurrentHashMap<UUID, PathfindingContext> activeCtxs = new ConcurrentHashMap<>();

    public void start(PathfindingContext ctx) {
        if (ctx != PathfindingContext.DUMMY) {
            if (ctx.getPlayerId() != null) {
                activeCtxs.put(ctx.getPlayerId(), ctx);
            }
        }
    }

    public PathfindingContext get(UUID playerId) {
        return activeCtxs.get(playerId);
    }

    public void remove(UUID playerId) {
        activeCtxs.remove(playerId);
    }
}