package io.github.kunosayo.simplepathfinder.client.x3d;

import com.xkball.x3dmap.api.client.IX3dMapPlugin;
import com.xkball.x3dmap.api.client.X3dMapPlugin;
import com.xkball.x3dmap.api.client.gui.IMapScreenExtension;
import com.xkball.x3dmap.api.client.map.WaypointDetailWindowCreateEvent;
import com.xkball.x3dmap.api.client.registration.IMapGuiRegistration;
import com.xkball.x3dmap.api.client.registration.IMapLayerRegistration;
import com.xkball.x3dmap.api.client.render.Map3dLayerPhase;
import com.xkball.x3dmap.api.client.render.MapViewportPresets;
import com.xkball.x3dmap.client.terrain.ChunkComplier;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.NonNull;

import java.util.Set;

@X3dMapPlugin
public class X3dPathPlugin implements IX3dMapPlugin {
    public X3dPathPlugin() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public @NonNull Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "x3d_plugin");
    }

    @Override
    public void registerGui(IMapGuiRegistration registration) {
        registration.addScreenExtension(
                Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "path_result"),
                2,
                (context) -> new IMapScreenExtension() {
                    @Override
                    public void onOpen() {
                        context.addLayerToggle(Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "path_result"), Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation"), "simple_path_finder.x3dmap.toggle");
                    }
                }
        );
    }

    @Override
    public void registerLayers(@NonNull IMapLayerRegistration registration) {
        registration.add3d(
                Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "path_result"),
                Set.of(MapViewportPresets.WORLD_MAP, MapViewportPresets.MINIMAP),
                Map3dLayerPhase.AFTER_TERRAIN,
                new PathResultRenderer()
        );
    }

    @SubscribeEvent
    public void onOpenWaypointWindow(WaypointDetailWindowCreateEvent event) {
        event.addButton("simple_path_finder.x3dmap.nav", (waypoint) -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.connection.sendCommand("navserver " + waypoint.pos().getX() + " " + waypoint.pos().getY() + " " + waypoint.pos().getZ());
        });
    }

    @SubscribeEvent
    public void onOpenWaypointWindow(ChunkComplier.RegisterChunkComplierBlackListEvent event) {
        event.add(ModBlocks.NAVIGATION_BARRIER_BLOCK.get());
    }

}
