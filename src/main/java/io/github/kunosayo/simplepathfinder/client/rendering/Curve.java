package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record Curve(
        List<Vec3> points,
        int startColor,
        int endColor
) implements IRenderElement {
    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector) {
    }
}
