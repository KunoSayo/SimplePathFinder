package io.github.kunosayo.simplepathfinder.client.x3d;

import com.xkball.x3dmap.api.client.render.IMap3dLayer;
import com.xkball.x3dmap.api.client.render.IMap3dLayerFactory;
import com.xkball.x3dmap.api.client.render.IMapLayerContext;
import org.jspecify.annotations.NonNull;

public class PathResultLayerFactory implements IMap3dLayerFactory {
    PathResultLayer pathResultLayer = new PathResultLayer();

    @Override
    public @NonNull IMap3dLayer create(@NonNull IMapLayerContext context) {
        return pathResultLayer;
    }
}
