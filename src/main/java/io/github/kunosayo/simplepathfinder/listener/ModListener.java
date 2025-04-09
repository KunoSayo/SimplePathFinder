package io.github.kunosayo.simplepathfinder.listener;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.network.SyncLevelNavDataPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SimplePathFinder.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModListener {
    public static final String NETWORK_VERSION = "1.0.0";


    @SubscribeEvent
    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(SyncLevelNavDataPacket.NETWORK_TYPE, SyncLevelNavDataPacket.STREAM_CODEC, SyncLevelNavDataPacket::clientHandler);
    }

}
