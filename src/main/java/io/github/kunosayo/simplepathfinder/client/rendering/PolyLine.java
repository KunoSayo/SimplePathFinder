package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.kunosayo.simplepathfinder.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record PolyLine(
        List<Vec3> points,
        int thickness,
        int startColor,
        int endColor
) implements IRenderElement {
    public PolyLine {
        points = List.copyOf(points);
    }

    @Override
    public void addVertex(PoseStack.Pose pose, VertexConsumer vertex) {

    }

    @Override
    public void addVertex(PoseStack.Pose pose, VertexConsumer vertex, CameraRenderState camera) {
        int lineCount = points.size() - 1;
        for (int index = 1; index < points.size(); index++) {
            Vec3 start = points.get(index - 1);
            Vec3 end = points.get(index);


            if (start.equals(end)) {
                continue;
            }

            double startRatio = (double) (index - 1) / lineCount;
            double endRatio = (double) index / lineCount;
            float dx = (float) (start.x - end.x);
            float dy = (float) (start.y - end.y);
            float dz = (float) (start.z - end.z);
            float length = (float) start.distanceTo(end);

            vertex.addVertex(pose.pose(), (float) start.x, (float) start.y, (float) start.z)
                    .setColor(colorFromRatio(startRatio, true))
                    .setLineWidth(thickness)
                    .setNormal(pose, dx /= length, dy /= length, dz /= length);
            vertex.addVertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                    .setLineWidth(thickness)
                    .setColor(colorFromRatio(endRatio, true))
                    .setNormal(pose, dx, dy, dz);
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderTypes.lines();
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (points.size() < 2) {
            return;
        }

        collector.submitCustomGeometry(
                poseStack,
                this.getRenderType(),
                (pose, vertex) -> {
                    int lineCount = points.size() - 1;
                    int chunkDistance = ClientConfig.CLIENT_CONFIG.getLeft().pathResultChunkDistance.get();
                    if (chunkDistance == 0) {
                        chunkDistance = (int) Minecraft.getInstance().levelRenderer.getLastViewDistance();
                    }
                    for (int index = 1; index < points.size(); index++) {
                        Vec3 start = points.get(index - 1);
                        Vec3 end = points.get(index);
                        Vec3 cameraPos = camera.pos;
                        double distanceStart = start.distanceTo(cameraPos);
                        double distanceEnd = end.distanceTo(cameraPos);

                        if (chunkDistance > 0) {
                            double rd = chunkDistance * 16;
                            if (distanceEnd > rd && distanceStart > rd) {
                                continue;
                            }
                        }

                        if (start.equals(end)) {
                            continue;
                        }

                        double startRatio = (double) (index - 1) / lineCount;
                        double endRatio = (double) index / lineCount;
                        float dx = (float) (start.x - end.x);
                        float dy = (float) (start.y - end.y);
                        float dz = (float) (start.z - end.z);
                        float length = (float) start.distanceTo(end);

                        vertex.addVertex(pose.pose(), (float) start.x, (float) start.y, (float) start.z)
                                .setColor(colorFromRatio(startRatio, true))
                                .setLineWidth(thickness)
                                .setNormal(pose, dx /= length, dy /= length, dz /= length);
                        vertex.addVertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                                .setLineWidth(thickness)
                                .setColor(colorFromRatio(endRatio, true))
                                .setNormal(pose, dx, dy, dz);
                    }
                }
        );
    }

    public static int colorFromRatio(double ratio, boolean oneIsGreen) {
        double p = ratio;

        if (!oneIsGreen) {
            p = 1 - p;
        }

        int r = (int) (255d * (Math.clamp(2 - 2 * p, 0, 1)));
        int g = (int) (255d * (Math.clamp(2 * p, 0, 1)));

        return 0xFF000000 + (r << 16) + (g << 8);
    }
}
