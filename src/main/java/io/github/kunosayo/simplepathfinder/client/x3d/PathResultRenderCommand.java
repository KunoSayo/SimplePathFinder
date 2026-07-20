package io.github.kunosayo.simplepathfinder.client.x3d;

import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMap3dRenderContext;
import io.github.kunosayo.simplepathfinder.client.rendering.NavRenderingSupport;
import org.jspecify.annotations.NonNull;

public class PathResultRenderCommand implements IMap3dRenderCommand {
    @Override
    public void render(@NonNull IMap3dRenderContext context) {
        var elementArr = NavRenderingSupport.INSTANCE.getPathResultLineElements();
        if (elementArr != null) {
            var pose = context.poseStack().last();
            var camera = context.cameraRenderState();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < elementArr.size(); i++) {
                var element = elementArr.get(i);
                var consumer = context.bufferSource().getBuffer(element.getRenderType());
                element.addVertex(pose, consumer, camera);
            }
        }
    }
}
