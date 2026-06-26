package io.github.kunosayo.simplepathfinder.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Combined navigation configuration.
 * Contains all navigation-related settings.
 */
public class NavConfig {
    public static final Pair<NavConfig, ModConfigSpec> NAV_CONFIG = new ModConfigSpec.Builder()
        .configure(NavConfig::new);

    // Build settings
    public final ModConfigSpec.ConfigValue<Integer> maxNavChunks;
    public final ModConfigSpec.ConfigValue<Integer> maxLayers;

    // Item usage settings
    public final ModConfigSpec.ConfigValue<Boolean> requireCreativeMode;

    NavConfig(ModConfigSpec.Builder builder) {
        // Build settings
        maxNavChunks = builder
            .comment("The maximum number of navigation chunks allowed")
            .define("max_nav_chunks", (4096 / 16 + 1) * (4096 / 16 + 1));

        maxLayers = builder
            .comment("The maximum number of layers per chunk")
            .define("max_layers", 9);

        // Item usage settings
        requireCreativeMode = builder
            .comment(
                "Require creative mode to use navigation item operations (add/remove navigation). Set to false to allow usage in any game mode.")
            .define("require_creative_mode", false);
    }
}
