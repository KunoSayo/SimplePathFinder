package io.github.kunosayo.simplepathfinder.nav.layered;

/**
 * Result of chunk scanning operation.
 */
public class ScanResult {
    private final int layerCount;
    private final int minLayer;
    private final int maxLayer;
    private final boolean success;

    public ScanResult(int layerCount, int minLayer, int maxLayer, boolean success) {
        this.layerCount = layerCount;
        this.minLayer = minLayer;
        this.maxLayer = maxLayer;
        this.success = success;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public int getMinLayer() {
        return minLayer;
    }

    public int getMaxLayer() {
        return maxLayer;
    }

    public boolean isSuccess() {
        return success;
    }

    /**
     * Create a failed result.
     */
    public static ScanResult failed() {
        return new ScanResult(0, 0, 0, false);
    }
}
