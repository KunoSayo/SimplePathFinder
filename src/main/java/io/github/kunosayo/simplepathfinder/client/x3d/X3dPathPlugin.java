package io.github.kunosayo.simplepathfinder.client.x3d;

import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.render.Map3dLayerPhase;
import com.xkball.x3dmap.api.client.render.Map3dLayerSpec;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Set;

@X3dMapPlugin
public class X3dPathPlugin implements IX3dMapPlugin {
    @Override
    public @NonNull Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "x3d_plugin");
    }

    @Override
    public void registerLayers(@NonNull IMapLayerRegistration registration) {
        var worldMap = Set.of(MapViewportPresets.WORLD_MAP);

        registration.add3d(new Map3dLayerSpec(
                        Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "path_result"),
                        worldMap,
                        Map3dLayerPhase.AFTER_TERRAIN,
                        0,
                        0,
                        true),
                new PathResultLayerFactory());
    }

}
