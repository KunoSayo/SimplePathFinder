package io.github.kunosayo.simplepathfinder.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public class ClientWrapperUtil {
    public static boolean isClientPlayerHoldingNavBarrier() {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            return ClientUtil.isClientPlayerHoldingNavBarrier();
        }
        return false;
    }
}
