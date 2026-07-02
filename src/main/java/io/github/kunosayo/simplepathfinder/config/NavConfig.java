package io.github.kunosayo.simplepathfinder.config;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    public final ModConfigSpec.ConfigValue<Integer> maxConcurrentTasks;

    // Item usage settings
    public final ModConfigSpec.ConfigValue<Boolean> requireCreativeMode;

    // Pathfinding settings
    public final ModConfigSpec.ConfigValue<Boolean> serverSidePathfinding;
    public final ModConfigSpec.ConfigValue<List<? extends String>> blockDistance;
    public final ModConfigSpec.ConfigValue<Integer> defaultBlockDistance;
    public final HashMap<Identifier, Integer> blockDistanceMap = new HashMap<>();

    NavConfig(ModConfigSpec.Builder builder) {
        // Build settings
        maxNavChunks = builder
                .comment("The maximum number of navigation chunks allowed")
                .define("max_nav_chunks", (4096 / 16 + 1) * (4096 / 16 + 1));

        maxLayers = builder
                .comment("The maximum number of layers per chunk")
                .define("max_layers", 37);

        defaultBlockDistance = builder
                .comment("The default distance of block when parse nav chunk")
                .define("defaultBlockDistance", 10);

        maxConcurrentTasks = builder
                .comment("The maximum concurrent chunk solving tasks in a batch building progress")
                .define("max_concurrent_tasks", 4);

        // Item usage settings
        requireCreativeMode = builder
                .comment(
                        "Require creative mode to use navigation item operations (add/remove navigation). Set to false to allow usage in any game mode.")
                .define("require_creative_mode", false);

        // Pathfinding settings
        serverSidePathfinding = builder
                .comment("Execute pathfinding on server side. When true, nav data sync is disabled and pathfinding results are sent to clients.")
                .define("server_side_pathfinding", true);

        blockDistance = builder.comment("The block distance for each block.")
                .defineList("block_distance", new ArrayList<>() {{
                    add("minecraft:dirt_path:3");
                }}, () -> "", o -> {
                    if (o instanceof String s) {
                        String[] args = s.split(":", 3);
                        if (args.length == 3) {
                            try {
                                var id = Identifier.fromNamespaceAndPath(args[0], args[1]);
                                int value = Integer.parseInt(args[2]);
                                return true;
                            } catch (Exception ignored) {

                            }
                        }
                    }
                    return false;
                });
    }

    public void update() {
        blockDistanceMap.clear();
        for (String s : blockDistance.get()) {
            String[] args = s.split(":", 3);
            if (args.length == 3) {
                var id = Identifier.fromNamespaceAndPath(args[0], args[1]);
                int value = Integer.parseInt(args[2]);
                blockDistanceMap.put(id, value);
            }
        }
    }
}
