package io.github.kunosayo.simplepathfinder.nav.finder;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class ModNavResult {
    public final List<BlockPos> posInThePath;
    public final BlockPos navTarget;

    ModNavResult(NavPathFinder.SearchNode endNode, BlockPos navTarget) {
        List<BlockPos> tempPaths;
        tempPaths = new ArrayList<>();
        NavPathFinder.SearchNode cur = endNode;
        while (cur != null) {
            tempPaths.add(cur.pos());
            cur = cur.lastNode();
        }
        tempPaths = tempPaths.reversed();
        posInThePath = tempPaths;
        this.navTarget = navTarget;
    }

    /**
     * Stream codec for serializing/deserializing ModNavResult.
     * Used for network packet transmission.
     */
    public static final StreamCodec<ByteBuf, ModNavResult> STREAM_CODEC = StreamCodec.of(
            (buf, result) -> {
                // Encode path list (size first, then each position)
                ByteBufCodecs.VAR_INT.encode(buf, result.posInThePath.size());
                for (BlockPos pos : result.posInThePath) {
                    BlockPos.STREAM_CODEC.encode(buf, pos);
                }
                // Encode nav target
                BlockPos.STREAM_CODEC.encode(buf, result.navTarget);
            },
            (buf) -> {
                // Decode path list
                int pathSize = ByteBufCodecs.VAR_INT.decode(buf);
                ArrayList<BlockPos> path = new ArrayList<>(pathSize);
                for (int i = 0; i < pathSize; i++) {
                    path.add(BlockPos.STREAM_CODEC.decode(buf));
                }
                // Decode nav target
                BlockPos navTarget = BlockPos.STREAM_CODEC.decode(buf);
                return new ModNavResult(path, navTarget);
            }
    );

    /**
     * Constructor for StreamCodec deserialization.
     */
    ModNavResult(ArrayList<BlockPos> posInThePath, BlockPos navTarget) {
        this.posInThePath = posInThePath;
        this.navTarget = navTarget;
    }

    public void render(LevelRenderer lr, Player player) {
        for (BlockPos blockPos : posInThePath) {
            player.level().addParticle(new DustParticleOptions(0xffffffff, 1.0f), false, false, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, 0.0, 0.0, 0.0);
        }
    }
}
