package io.github.kunosayo.simplepathfinder.client;

import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.client.Minecraft;

public class ClientUtil {
    public static boolean isClientPlayerHoldingNavBarrier() {
        var player = Minecraft.getInstance().player;

        if (player != null) {
            var item = player.getMainHandItem();
            return item.is(ModItems.NAVIGATION_BARRIER_BLOCK);
        }
        return false;
    }
}
