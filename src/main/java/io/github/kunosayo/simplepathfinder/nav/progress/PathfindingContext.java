package io.github.kunosayo.simplepathfinder.nav.progress;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PathfindingContext {
    public static final PathfindingContext DUMMY = new PathfindingContext(null) {
        @Override
        public void setInitialH(long h) {
        }

        @Override
        public void onNodePopped(long hValue) {
        }

        @Override
        public void markCompleted() {
        }

        @Override
        public int getProgress() {
            return 0;
        }

        @Override
        public boolean isCompleted() {
            return true;
        }
    };

    @Nullable
    private final UUID playerId;

    private long initialH;
    private long currentMinH;
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public PathfindingContext(@Nullable UUID playerId) {
        this.playerId = playerId;
    }


    public void setInitialH(long h) {
        this.initialH = h;
        this.currentMinH = h;
    }

    public void onNodePopped(long hValue) {
        if (hValue > currentMinH) return;
        this.currentMinH = hValue;
        int pct = initialH > 0 ? (int) ((initialH - hValue) * 100 / initialH) : 0;
        this.progress.set(Mth.clamp(pct, 0, 99));
    }

    public int getProgress() {
        return progress.get();
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void markCompleted() {
        progress.set(100);
        completed.set(true);
    }

    public @Nullable UUID getPlayerId() {
        return playerId;
    }
}