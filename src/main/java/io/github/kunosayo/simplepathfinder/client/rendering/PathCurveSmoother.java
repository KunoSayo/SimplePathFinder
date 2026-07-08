package io.github.kunosayo.simplepathfinder.client.rendering;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class PathCurveSmoother {
    private PathCurveSmoother() {
    }

    public static List<Vec3> smoothPath(List<Vec3> points, int chaikinIterations) {
        return postProcessPoints(smoothChaikin(points, chaikinIterations));
    }

    public static List<Vec3> smoothPathRdp(List<Vec3> points, double rdpTolerance, int chaikinIterations) {
        return postProcessPoints(smoothChaikin(simplifyRdp(points, rdpTolerance), chaikinIterations));
    }

    public static List<Vec3> simplifyRdp(List<Vec3> points, double tolerance) {
        if (points.size() <= 2 || tolerance <= 0.0) {
            return List.copyOf(points);
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifySection(points, 0, points.size() - 1, tolerance * tolerance, keep);

        List<Vec3> result = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            if (keep[index]) {
                result.add(points.get(index));
            }
        }
        return result;
    }

    private static void simplifySection(
            List<Vec3> points,
            int startIndex,
            int endIndex,
            double toleranceSqr,
            boolean[] keep
    ) {
        if (endIndex <= startIndex + 1) {
            return;
        }

        Vec3 start = points.get(startIndex);
        Vec3 end = points.get(endIndex);
        double maxDistanceSqr = -1.0;
        int maxIndex = -1;
        for (int index = startIndex + 1; index < endIndex; index++) {
            double distanceSqr = distanceToSegmentSqr(points.get(index), start, end);
            if (distanceSqr > maxDistanceSqr) {
                maxDistanceSqr = distanceSqr;
                maxIndex = index;
            }
        }

        if (maxDistanceSqr > toleranceSqr) {
            keep[maxIndex] = true;
            simplifySection(points, startIndex, maxIndex, toleranceSqr, keep);
            simplifySection(points, maxIndex, endIndex, toleranceSqr, keep);
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr == 0.0) {
            return point.distanceToSqr(start);
        }
        double t = point.subtract(start).dot(segment) / lengthSqr;
        t = Math.clamp(t, 0.0, 1.0);
        Vec3 projection = start.add(segment.scale(t));
        return point.distanceToSqr(projection);
    }

    public static List<Vec3> smoothChaikin(List<Vec3> points, int iterations) {
        if (points.size() <= 2 || iterations <= 0) {
            return List.copyOf(points);
        }

        List<Vec3> current = List.copyOf(points);
        for (int iteration = 0; iteration < iterations; iteration++) {
            List<Vec3> next = new ArrayList<>();
            next.add(current.getFirst());
            for (int index = 0; index < current.size() - 1; index++) {
                Vec3 start = current.get(index);
                Vec3 end = current.get(index + 1);
                next.add(start.scale(0.75).add(end.scale(0.25)));
                next.add(start.scale(0.25).add(end.scale(0.75)));
            }
            next.add(current.getLast());
            current = next;
        }
        return current;
    }

    public static List<Vec3> postProcessPoints(List<Vec3> points) {
        if (points.size() <= 2) {
            return List.copyOf(points);
        }

        List<Vec3> processed = new ArrayList<>();
        processed.add(points.getFirst());
        for (int index = 1; index < points.size() - 1; index++) {
            Vec3 start = processed.getLast();
            Vec3 middle = points.get(index);
            Vec3 end = points.get(index + 1);
            if (!isSameDirection(start, middle, end)) {
                processed.add(middle);
            }
        }
        processed.add(points.getLast());
        return processed;
    }

    private static boolean isSameDirection(Vec3 start, Vec3 middle, Vec3 end) {
        Vec3 first = middle.subtract(start);
        Vec3 second = end.subtract(middle);
        double fl = first.lengthSqr();
        double sl = second.lengthSqr();
        if (fl == 0.0 || sl == 0.0 || (fl + sl) >= 16384.0) {
            return false;
        }
        return first.dot(second) > 0.0 && first.cross(second).lengthSqr() < 1.0E-12;
    }
}
