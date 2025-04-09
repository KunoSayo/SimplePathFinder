package io.github.kunosayo.simplepathfinder.nav;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ModNavResult {
    public final List<BlockPos> posInThePath;

    public ModNavResult(List<BlockPos> posInThePath) {
        this.posInThePath = posInThePath;
    }

    public ModNavResult(SearchNode endNode) {
        List<BlockPos> tempPaths;
        tempPaths = new ArrayList<>();
        SearchNode cur = endNode;
        while (cur != null) {
            tempPaths.add(cur.pos);
            cur = cur.lastNode;
        }
        tempPaths = tempPaths.reversed();
        posInThePath = tempPaths;
    }

    public void render(LevelRenderer lr, Player player) {
        for (BlockPos blockPos : posInThePath) {
            lr.addParticle(new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f), false, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, 0.0, 0.0, 0.0);
        }
    }
}
