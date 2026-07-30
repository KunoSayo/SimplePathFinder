package io.github.kunosayo.simplepathfinder.client.x3d;

import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import com.xkball.x3dmap.client.b3d.pipeline.X3dMapRenderPipelines;
import com.xkball.x3dmap.client.render.pip.layers.GridRenderer;
import io.github.kunosayo.simplepathfinder.client.rendering.IRenderElement;
import io.github.kunosayo.simplepathfinder.client.rendering.Line;
import io.github.kunosayo.simplepathfinder.client.rendering.NavRenderingSupport;
import io.github.kunosayo.simplepathfinder.client.rendering.PolyLine;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.Lazy;
import org.jspecify.annotations.NonNull;

public class PathResultRenderer implements IMap3dRenderCommand {
    
    private static final Lazy<RenderType> LINE = Lazy.of(() -> RenderType.create("line", RenderSetup.builder(X3dMapRenderPipelines.LINE).createRenderSetup()));
    
    @Override
    public void render(@NonNull IMap3dRenderContext context) {
        var elementArr = NavRenderingSupport.INSTANCE.getPathResultLineElements();
        if (elementArr != null) {
            var pose = context.poseStack().last();
            var vertexConsumer = context.bufferSource().getBuffer(LINE.get());
            for (IRenderElement element : elementArr) {
                if (element instanceof Line line) {
                    GridRenderer.tryDrawLine3D(vertexConsumer, pose, (float) line.start().x, (float) line.start().y, (float) line.start().z, (float) line.end().x, (float) line.end().y, (float) line.end().z, line.startColor(), line.endColor());
                } else if (element instanceof PolyLine polyline) {
                    var points = polyline.points();
                    int lineCount = points.size() - 1;
                    for (int index = 1; index < points.size(); index++) {
                        Vec3 start = points.get(index - 1);
                        Vec3 end = points.get(index);
                        
                        if (start.equals(end)) {
                            continue;
                        }
                        
                        double startRatio = (double) (index - 1) / lineCount;
                        double endRatio = (double) index / lineCount;
                        GridRenderer.tryDrawLine3D(vertexConsumer, pose, (float) start.x, (float) start.y, (float) start.z, (float) end.x, (float) end.y, (float) end.z, PolyLine.colorFromRatio(startRatio, true), PolyLine.colorFromRatio(endRatio, true));
                    }
                }
            }
        }
    }
}
