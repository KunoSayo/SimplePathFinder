package io.github.kunosayo.simplepathfinder.nav;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

public class NavResult {

    public final Path minecraftPath;
    public final ModNavResult modNavResult;

    public NavResult(Path minecraftPath) {
        this.minecraftPath = minecraftPath;
        this.modNavResult = null;
    }

    public NavResult(ModNavResult modNavResult) {
        this.modNavResult = modNavResult;
        this.minecraftPath = null;
    }

    public NavResult(SearchNode endNode) {
        this(new ModNavResult(endNode));
    }

    public void render(LevelRenderer lr, Player player) {
        if (modNavResult != null) {
            modNavResult.render(lr, player);
        }
    }
}
