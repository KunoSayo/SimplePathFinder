package io.github.kunosayo.simplepathfinder.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class NavBuildConfig {
    public static final Pair<NavBuildConfig, ModConfigSpec> NAV_BUILD_CONFIG = new ModConfigSpec.Builder()
            .configure(NavBuildConfig::new);
    public final ModConfigSpec.ConfigValue<Integer> maxNavChunks;
    public final ModConfigSpec.ConfigValue<Integer> maxLayers;

    NavBuildConfig(ModConfigSpec.Builder builder) {
        maxNavChunks = builder
                .comment("The most nav chunk we can have")
                .define("max_nav_chunks", (4096 / 16 + 1) * (4096 / 16 + 1));
        maxLayers = builder
                .comment("The max layers per chunk")
                .define("max layers", 9);
    }

}
