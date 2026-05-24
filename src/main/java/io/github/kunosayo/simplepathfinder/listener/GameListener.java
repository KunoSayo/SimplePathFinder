package io.github.kunosayo.simplepathfinder.listener;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.UUID;

@EventBusSubscriber(modid = SimplePathFinder.MOD_ID)
public class GameListener {

    private static final HashSet<UUID> sentPlayers = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {

    }

}
