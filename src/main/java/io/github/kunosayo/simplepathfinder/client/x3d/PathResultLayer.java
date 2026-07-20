package io.github.kunosayo.simplepathfinder.client.x3d;

import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dRenderCommand;
import com.xkball.x3dmap.api.client.render.IMapFrame;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class PathResultLayer implements IMap3dLayer {
    PathResultRenderCommand render = new PathResultRenderCommand();

    @Override
    public @Nullable IMap3dRenderCommand prepareRender(@NonNull IMapFrame frame) {
        return render;
    }
}
