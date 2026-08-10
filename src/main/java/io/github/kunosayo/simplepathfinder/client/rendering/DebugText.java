package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

public class DebugText implements IRenderElement {
    public final Vec3 pos;
    public final String text;
    public final int color;
    public final float scale;

    public DebugText(Vec3 pos, String text, int color) {
        this(pos, text, color, 1.0f);
    }

    public DebugText(Vec3 pos, String text, int color, float scale) {
        this.pos = pos;
        this.text = text;
        this.color = color;
        this.scale = scale;
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
        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, pos.z);
        float fontScale = 0.03125f;
        poseStack.scale(fontScale, fontScale, fontScale);
        poseStack.scale(-1.0f, -1.0f, -1.0f);
        poseStack.scale(this.scale, this.scale, this.scale);
        poseStack.mulPose(camera.orientation);
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
//        poseStack.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);

        float width = Minecraft.getInstance().font.width(this.text);
        float height = Minecraft.getInstance().font.lineHeight;
        collector.submitText(poseStack, -width / 2.0f, -height / 2.0f,
                FormattedCharSequence.forward(this.text, Style.EMPTY.withColor(color)),
                false, Font.DisplayMode.NORMAL, LightCoordsUtil.pack(15, 15), 0xffffffff, 0, 0);
        poseStack.popPose();
    }
}
