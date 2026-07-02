package io.github.kunosayo.simplepathfinder.client.rendering;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class PathRenderElementFactory {
    private static final int PATH_CHAIKIN_ITERATIONS = 3;

    private PathRenderElementFactory() {
    }

    static List<IRenderElement> createPathElements(List<Vec3> points, Line firstLine, Line lastLine, boolean smoothPath) {
        if (points.size() < 2) {
            return List.of();
        }
        if (smoothPath) {
            List<Vec3> smoothedPoints = PathCurveSmoother.smoothPath(points, PATH_CHAIKIN_ITERATIONS);
            return List.of(new PolyLine(
                    smoothedPoints,
                    firstLine.thickness(),
                    firstLine.startColor(),
                    lastLine.endColor()
            ));
        }

        List<IRenderElement> lines = new ArrayList<>();
        int lineCount = points.size() - 1;
        for (int index = 1; index < points.size(); index++) {
            double startRatio = (double) (index - 1) / lineCount;
            double endRatio = (double) index / lineCount;
            lines.add(new Line(
                    points.get(index - 1),
                    points.get(index),
                    firstLine.thickness(),
                    NavRenderingSupport.colorFromRatio(startRatio, true),
                    NavRenderingSupport.colorFromRatio(endRatio, true)
            ));
        }
        return lines;
    }
}
