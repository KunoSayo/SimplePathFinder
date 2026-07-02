package io.github.kunosayo.simplepathfinder.client.rendering;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathCurveSmootherTest {
    @Test
    void rdpKeepsEndpointsAndDropsNearStraightMiddlePoints() {
        List<Vec3> points = List.of(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.05),
                new Vec3(2.0, 0.0, 0.0),
                new Vec3(3.0, 0.0, 0.0)
        );

        List<Vec3> simplified = PathCurveSmoother.simplifyRdp(points, 0.1);

        assertEquals(List.of(points.getFirst(), points.getLast()), simplified);
    }

    @Test
    void rdpKeepsMeaningfulCornerPoint() {
        List<Vec3> points = List.of(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 2.0)
        );

        List<Vec3> simplified = PathCurveSmoother.simplifyRdp(points, 0.25);

        assertEquals(points.getFirst(), simplified.getFirst());
        assertEquals(points.getLast(), simplified.getLast());
        assertTrue(simplified.contains(points.get(1)) || simplified.contains(points.get(2)));
        assertTrue(simplified.size() >= 3);
    }

    @Test
    void chaikinKeepsEndpointsAndAddsCornerCutPoints() {
        List<Vec3> points = List.of(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 1.0)
        );

        List<Vec3> smoothed = PathCurveSmoother.smoothChaikin(points, 1);

        assertEquals(points.getFirst(), smoothed.getFirst());
        assertEquals(points.getLast(), smoothed.getLast());
        assertEquals(6, smoothed.size());
        assertEquals(new Vec3(0.25, 0.0, 0.0), smoothed.get(1));
        assertEquals(new Vec3(0.75, 0.0, 0.0), smoothed.get(2));
    }

    @Test
    void smoothPathRunsRdpThenChaikin() {
        List<Vec3> points = List.of(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.05),
                new Vec3(2.0, 0.0, 0.0),
                new Vec3(2.0, 0.0, 1.0)
        );

        List<Vec3> smoothed = PathCurveSmoother.smoothPath(points, 0.1, 1);

        assertEquals(points.getFirst(), smoothed.getFirst());
        assertEquals(points.getLast(), smoothed.getLast());
        assertTrue(smoothed.size() > 2);
    }
}
