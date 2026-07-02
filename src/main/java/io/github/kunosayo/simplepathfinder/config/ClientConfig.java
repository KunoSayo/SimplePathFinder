package io.github.kunosayo.simplepathfinder.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ClientConfig {
    public static final Pair<ClientConfig, ModConfigSpec> CLIENT_CONFIG = new ModConfigSpec.Builder()
            .configure(ClientConfig::new);

    public final ModConfigSpec.ConfigValue<Boolean> smoothPath;

    ClientConfig(ModConfigSpec.Builder builder) {
        smoothPath = builder
                .comment("Enable smooth navigation path rendering")
                .define("smooth_path", true);
    }
}
