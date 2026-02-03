package io.github.kunosayo.simplepathfinder.client.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Event triggered when navigation rendering should be checked
 * Client-side listeners can check if the player is holding a navigation item
 */
public class NavigationRenderTriggerEvent extends Event implements ICancellableEvent {
    private final Player player;

    public NavigationRenderTriggerEvent(Player player) {
        this.player = player;
    }

    /**
     * Get the player this event is for
     */
    public Player getPlayer() {
        return player;
    }
}