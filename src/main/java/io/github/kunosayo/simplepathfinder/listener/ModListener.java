package io.github.kunosayo.simplepathfinder.listener;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.network.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SimplePathFinder.MOD_ID)
public class ModListener implements IModBusEvent {
    public static final String NETWORK_VERSION = "1.0.0";


    @SubscribeEvent
    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(SyncLevelNavDataPacket.NETWORK_TYPE, SyncLevelNavDataPacket.STREAM_CODEC, SyncLevelNavDataPacket::clientHandler);
        registrar.playToClient(SyncSingleChunkPacket.NETWORK_TYPE, SyncSingleChunkPacket.STREAM_CODEC, SyncSingleChunkPacket::clientHandler);
        registrar.playToClient(PlayerLocationPacket.NETWORK_TYPE, PlayerLocationPacket.STREAM_CODEC, PlayerLocationPacket::clientHandler);
        // Server-side pathfinding packets
        registrar.playToClient(PathfindingResultPacket.NETWORK_TYPE, PathfindingResultPacket.STREAM_CODEC, PathfindingResultPacket::clientHandler);
        registrar.playToServer(PathfindingRequestPacket.NETWORK_TYPE, PathfindingRequestPacket.STREAM_CODEC, PathfindingRequestPacket::serverHandler);
        registrar.playToServer(UpdateItemPropertiesPacket.NETWORK_TYPE, UpdateItemPropertiesPacket.STREAM_CODEC, UpdateItemPropertiesPacket::serverHandler);
        // Player-specific block distance config packet (bidirectional)
        registrar.playBidirectional(SyncBlockDistanceConfigPacket.NETWORK_TYPE, SyncBlockDistanceConfigPacket.STREAM_CODEC,
                SyncBlockDistanceConfigPacket::serverHandler,
                SyncBlockDistanceConfigPacket::clientHandler);
    }


    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == NavConfig.NAV_CONFIG.getRight()) {
            NavConfig.NAV_CONFIG.getLeft().update();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == NavConfig.NAV_CONFIG.getRight()) {
            NavConfig.NAV_CONFIG.getLeft().update();
        }
    }
}
