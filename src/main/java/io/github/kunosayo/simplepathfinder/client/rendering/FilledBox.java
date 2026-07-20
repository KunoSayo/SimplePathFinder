package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public record FilledBox(
        Vec3 center,
        float size,
        int color
) implements IRenderElement {


    @Override
    public void addVertex(PoseStack.Pose pose, VertexConsumer vertex) {
        float radius = size * 0.5f;
        float minX = (float) center.x - radius;
        float minY = (float) center.y - radius;
        float minZ = (float) center.z - radius;
        float maxX = (float) center.x + radius;
        float maxY = (float) center.y + radius;
        float maxZ = (float) center.z + radius;
        quad(vertex, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ);
        quad(vertex, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        quad(vertex, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        quad(vertex, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        quad(vertex, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
        quad(vertex, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);

    }

    @Override
    public RenderType getRenderType() {
        return RenderTypes.debugFilledBox();
    }

    private void quad(
            VertexConsumer vertex,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4
    ) {
        vertex.addVertex(pose.pose(), x1, y1, z1).setColor(color);
        vertex.addVertex(pose.pose(), x2, y2, z2).setColor(color);
        vertex.addVertex(pose.pose(), x3, y3, z3).setColor(color);
        vertex.addVertex(pose.pose(), x4, y4, z4).setColor(color);
    }
}
