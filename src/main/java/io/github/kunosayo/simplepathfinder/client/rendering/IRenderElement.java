package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public interface IRenderElement {

    void addVertex(PoseStack.Pose pose, VertexConsumer vertex);

    RenderType getRenderType();

    default void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(
                poseStack,
                this.getRenderType(),
                this::addVertex
        );
    }
}
