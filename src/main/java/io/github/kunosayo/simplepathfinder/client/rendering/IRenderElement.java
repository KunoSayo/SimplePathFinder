package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

public interface IRenderElement extends SubmitNodeCollector.CustomGeometryRenderer {

    void addVertex(PoseStack.Pose pose, VertexConsumer vertex);

    RenderType getRenderType();

    default void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(
                poseStack,
                this.getRenderType(),
                this
        );
    }

    @Override
    default void render(PoseStack.@NonNull Pose pose, @NonNull VertexConsumer vertex) {
        addVertex(pose, vertex);
    }

}
