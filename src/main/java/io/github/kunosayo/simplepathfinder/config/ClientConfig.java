package io.github.kunosayo.simplepathfinder.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ClientConfig {
    public static final Pair<ClientConfig, ModConfigSpec> CLIENT_CONFIG = new ModConfigSpec.Builder()
            .configure(ClientConfig::new);

    public final ModConfigSpec.ConfigValue<Boolean> smoothPath;
    public final ModConfigSpec.ConfigValue<Integer> pathResultChunkDistance;

    ClientConfig(ModConfigSpec.Builder builder) {
        smoothPath = builder
                .comment("Enable smooth navigation path rendering")
                .define("smooth_path", true);
        pathResultChunkDistance = builder
                .comment("Enable smooth navigation path rendering (0 indicates same as level renderer, -1 indicates unlimit")
                .defineInRange("path_result_chunk_distance", 0, -1, 37);
    }
}
