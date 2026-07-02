package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public interface IRenderElement {
    void render(PoseStack poseStack, SubmitNodeCollector collector);
}
