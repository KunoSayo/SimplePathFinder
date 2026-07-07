package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public record Line(
        Vec3 start,
        Vec3 end,
        float length,
        int thickness,
        int startColor,
        int endColor
) implements IRenderElement {

    public Line(Vec3 start, Vec3 end, int thickness, int startColor, int endColor) {
        this(start, end, (float) start.distanceTo(end), thickness, startColor, endColor);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.lines(),
                (pose, vertex) -> {
                    float dx = (float) (this.start().x - this.end().x);
                    float dy = (float) (this.start().y - this.end().y);
                    float dz = (float) (this.start().z - this.end().z);
                    vertex.addVertex(
                                    pose.pose(),
                                    (float) (this.start().x),
                                    (float) (this.start().y),
                                    (float) (this.start().z)
                            )
                            .setColor(startColor)
                            .setLineWidth(this.thickness)
                            .setNormal(pose, dx /= this.length(), dy /= this.length(), dz /= this.length());
                    vertex.addVertex(
                                    pose.pose(),
                                    (float) (this.end().x),
                                    (float) (this.end().y),
                                    (float) (this.end().z)
                            )
                            .setLineWidth(this.thickness)
                            .setColor(endColor)
                            .setNormal(pose, dx, dy, dz);
                }
        );
    }
}
