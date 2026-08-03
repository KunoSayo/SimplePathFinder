package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

public class DebugText implements IRenderElement {
    public final Vec3 pos;
    public final String text;

    public DebugText(Vec3 pos, String text) {
        this.pos = pos;
        this.text = text;
    }

    @Override
    public void addVertex(PoseStack.Pose pose, VertexConsumer vertex) {

    }

    @Override
    public RenderType getRenderType() {
        return RenderTypes.textBackground();
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {

        collector.submitText(poseStack, 0.0f, 0.0f, FormattedCharSequence.forward(this.text, Style.EMPTY), true, Font.DisplayMode.NORMAL, 0, 0xffffffff, 0, 0xffffffff);
    }
}
