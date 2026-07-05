package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class LayeredNavChunkTest {

    private LayeredNavChunk getChunk(String[] data) {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distances = new int[LevelNavData.CHUNK_AREA << 1];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                String line = data[z];
                if (line.charAt(x) == '0') {
                    walkY[convertToIndex(x, z)] = ILayeredNavChunk.INVALID_WALK_Y;
                } else {
                    walkY[convertToIndex(x, z)] = (short) line.charAt(x);
                }
                if (x + 1 < 16) {
                    distances[LayeredNavChunk.getDistanceIdx(x, z, false)] = line.charAt(x + 1) - line.charAt(x);
                }
                if (z + 1 < 16) {
                    distances[LayeredNavChunk.getDistanceIdx(x, z, true)] = data[z + 1].charAt(x) - line.charAt(x);
                }
            }
        }

        return new LayeredNavChunk(walkY, distances);
    }

    /**
     * Test case 1: All cells have the same walkY and distances
     * Expected: One rectangle covering the entire chunk (0,0) to (15,15)
     */
    @Test
    public void testUpdateChunkData_AllSameValues() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distances = new int[LevelNavData.CHUNK_AREA << 1];

        Arrays.fill(walkY, (short) 64);
        Arrays.fill(distances, 10);

        LayeredNavChunk chunk = new LayeredNavChunk(walkY, distances);

        Assertions.assertEquals(1, chunk.cellList.size());
        NavRectCell cell = chunk.cellList.get(0);
        Assertions.assertEquals(0, cell.minX);
        Assertions.assertEquals(0, cell.minZ);
        Assertions.assertEquals(15, cell.maxX);
        Assertions.assertEquals(15, cell.maxZ);
    }

    /**
     * Test case 2: All cells have different values
     * Expected: No rectangles added (all would have width or height of 0)
     */
    @Test
    public void testUpdateChunkData_AllDifferentValues() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distances = new int[LevelNavData.CHUNK_AREA << 1];

        for (int i = 0; i < LevelNavData.CHUNK_AREA; i++) {
            walkY[i] = (short) i;
            distances[i << 1] = i;
            distances[(i << 1) | 1] = i;
        }

        LayeredNavChunk chunk = new LayeredNavChunk(walkY, distances);

        Assertions.assertEquals(0, chunk.cellList.size());
    }

    /**
     * Test case 5: Rectangle with width > 1 but height = 1
     * Expected: Not added (height is 1, so height > 1 condition fails)
     */
    @Test
    public void testUpdateChunkData_SingleRowNotAdded() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distances = new int[LevelNavData.CHUNK_AREA << 1];

        Arrays.fill(walkY, (short) -9961);
        Arrays.fill(distances, -1);

        // Set a single row with same values
        for (int x = 0; x < 8; x++) {
            int idx = convertToIndex(x, 0);
            walkY[idx] = 64;
            distances[idx << 1] = 10;
            distances[(idx << 1) | 1] = 10;
        }

        LayeredNavChunk chunk = new LayeredNavChunk(walkY, distances);

        // Should not find any rectangle (height = 1)
        Assertions.assertEquals(0, chunk.cellList.size());
    }

    /**
     * Test case 6: Rectangle with height > 1 but width = 1
     * Expected: Not added (width is 1, so width > 1 condition fails)
     */
    @Test
    public void testUpdateChunkData_SingleColumnNotAdded() {
        short[] walkY = new short[LevelNavData.CHUNK_AREA];
        int[] distances = new int[LevelNavData.CHUNK_AREA << 1];

        Arrays.fill(walkY, (short) -9961);
        Arrays.fill(distances, -1);

        // Set a single column with same values
        for (int z = 0; z < 8; z++) {
            int idx = convertToIndex(0, z);
            walkY[idx] = 64;
            distances[idx << 1] = 10;
            distances[(idx << 1) | 1] = 10;
        }

        LayeredNavChunk chunk = new LayeredNavChunk(walkY, distances);

        // Should not find any rectangle (width = 1)
        Assertions.assertEquals(0, chunk.cellList.size());
    }

    @Test
    public void testRect() {
        {
            LayeredNavChunk chunk = getChunk(new String[]{
                    "1111000000000000",
                    "1110000000000000",
                    "1100000000000000",
                    "1000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000"
            });
            Assertions.assertEquals(2, chunk.cellList.size());
        }

        {
            LayeredNavChunk chunk = getChunk(new String[]{
                    "0111000000000000",
                    "1110000000000000",
                    "1110000000000000",
                    "1000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000",
                    "0000000000000000"
            });
            Assertions.assertEquals(2, chunk.cellList.size());
        }
    }

    @Test
    public void testMultiRegion() {

        LayeredNavChunk chunk = getChunk(new String[]{
                "1122000000000000",
                "1122000000000000",
                "3314400000000000",
                "3344400000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000",
                "0000000000000000"
        });
        Assertions.assertEquals(4, chunk.cellList.size());
        for (NavRectCell navRectCell : chunk.cellList) {
            Assertions.assertEquals(4, navRectCell.getArea());
            Assertions.assertEquals(2, navRectCell.getXLen());
            Assertions.assertEquals(2, navRectCell.getZLen());

        }

    }

    private int convertToIndex(int x, int z) {
        return (x << 4) | z;
    }
}
