package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import static org.apache.commons.lang3.math.NumberUtils.max;

/**
 * Renders an arrow from start to end position with gradient coloring
 */
public record Arrow(
        Vec3 start,
        Vec3 end,
        float thickness,
        int startColor,
        int endColor,
        float arrowHeadSize
) implements IRenderElement {

    public Arrow(Vec3 start, Vec3 end, int startColor, int endColor) {
        this(start, end, 3, startColor, endColor, 0.3f);
    }

    @Override
    public void addVertex(PoseStack.Pose pose, VertexConsumer vertex) {

        float dx = (float) (this.end().x - this.start().x);
        float dy = (float) (this.end().y - this.start().y);
        float dz = (float) (this.end().z - this.start().z);
        float length = (float) this.start().distanceTo(this.end());

        if (length < 0.001f) {
            return;
        }

        float dirX = dx / length;
        float dirY = dy / length;
        float dirZ = dz / length;

        // Main line from start to arrow base
        vertex.addVertex(
                        pose.pose(),
                        (float) (this.start().x),
                        (float) (this.start().y),
                        (float) (this.start().z)
                )
                .setColor(startColor)
                .setLineWidth(this.thickness)
                .setNormal(pose, dirX, dirY, dirZ);

        // Arrow base (where the arrow head starts)
        float arrowBaseX = (float) (this.end().x) - dirX * this.arrowHeadSize;
        float arrowBaseY = (float) (this.end().y) - dirY * this.arrowHeadSize;
        float arrowBaseZ = (float) (this.end().z) - dirZ * this.arrowHeadSize;

        vertex.addVertex(
                        pose.pose(),
                        arrowBaseX,
                        arrowBaseY,
                        arrowBaseZ
                )
                .setColor(endColor)
                .setLineWidth(this.thickness)
                .setNormal(pose, dirX, dirY, dirZ);

        // Arrow head - left wing
        Vec3 leftWing = calculateArrowHeadPoint(this.end(), dirX, dirY, dirZ, this.arrowHeadSize, true);
        vertex.addVertex(
                        pose.pose(),
                        (float) (this.end().x),
                        (float) (this.end().y),
                        (float) (this.end().z)
                )
                .setColor(endColor)
                .setLineWidth(this.thickness)
                .setNormal(pose, dirX, dirY, dirZ);

        vertex.addVertex(
                        pose.pose(),
                        (float) leftWing.x,
                        (float) leftWing.y,
                        (float) leftWing.z
                )
                .setColor(endColor)
                .setLineWidth(this.thickness)
                .setNormal(pose, dirX, dirY, dirZ);

        // Arrow head - right wing
        Vec3 rightWing = calculateArrowHeadPoint(this.end(), dirX, dirY, dirZ, this.arrowHeadSize, false);
        vertex.addVertex(
                        pose.pose(),
                        (float) (this.end().x),
                        (float) (this.end().y),
                        (float) (this.end().z)
                )
                .setColor(endColor)
                .setLineWidth(this.thickness)
                .setNormal(pose, dirX, dirY, dirZ);

        vertex.addVertex(
                        pose.pose(),
                        (float) rightWing.x,
                        (float) rightWing.y,
                        (float) rightWing.z
                )
                .setColor(endColor)
                .setLineWidth(this.thickness)
                .setNormal(pose, dirX, dirY, dirZ);
    }

    @Override
    public RenderType getRenderType() {
        return RenderTypes.lines();
    }

    /**
     * Calculate a point for the arrow head wing
     *
     * @param tip    The arrow tip position
     * @param dirX   Direction X component (normalized)
     * @param dirY   Direction Y component (normalized)
     * @param dirZ   Direction Z component (normalized)
     * @param size   Size of the arrow head
     * @param isLeft Whether this is the left wing (true) or right wing (false)
     * @return The calculated wing position
     */
    private Vec3 calculateArrowHeadPoint(Vec3 tip, float dirX, float dirY, float dirZ, float size, boolean isLeft) {
        // Find a perpendicular vector for the arrow head width
        float perpX, perpY, perpZ;

        // Use Y-axis as reference when direction is not vertical
        if (Math.abs(dirY) < 0.9f) {
            // Cross product with up vector (0, 1, 0)
            perpX = dirZ;
            perpY = 0;
            perpZ = -dirX;
        } else {
            // Cross product with X-axis (1, 0, 0) for vertical arrows
            perpX = 0;
            perpY = dirZ;
            perpZ = -dirY;
        }

        float perpLength = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        if (perpLength > 0.001f) {
            perpX /= perpLength;
            perpY /= perpLength;
            perpZ /= perpLength;
        }

        // Calculate wing position: go back from tip, then sideways
        float wingOffset = size * 0.5f;
        if (!isLeft) {
            wingOffset = -wingOffset;
        }

        return new Vec3(
                tip.x - dirX * size + perpX * wingOffset,
                tip.y - dirY * size + perpY * wingOffset,
                tip.z - dirZ * size + perpZ * wingOffset
        );
    }
}
