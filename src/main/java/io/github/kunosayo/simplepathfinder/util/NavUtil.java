package io.github.kunosayo.simplepathfinder.util;

import io.github.kunosayo.simplepathfinder.block.NavigationBarrierBlock;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class NavUtil {
    public static boolean isNoCollision(Level level, BlockPos pos) {
        return isNoCollision(level, pos, level.getBlockState(pos));
    }

    public static boolean isNoCollision(Level level, BlockPos pos, BlockState state) {
        var block = state.getBlock();
        if (block instanceof NavigationBarrierBlock) {
            return false;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean considerSafeCross(Level level, BlockPos pos, BlockState state) {
        return isNoCollision(level, pos, state) && state.getFluidState().isEmpty();
    }

    public static boolean considerSafeCross(Level level, BlockPos pos) {
        return considerSafeCross(level, pos, level.getBlockState(pos));
    }

    public static boolean considerSafeGround(Level level, BlockPos pos, BlockState state) {
        return !isNoCollision(level, pos, state) || !state.getFluidState().isEmpty();
    }

    public static boolean isSameChunk(BlockPos a, BlockPos b) {
        final int ax = a.getX(), bx = b.getX(), az = a.getZ(), bz = b.getZ();
        return isSameChunk(ax, az, bx, bz);
    }

    public static boolean isSameChunk(int ax, int az, int bx, int bz) {
        return SectionPos.blockToSectionCoord(ax) == SectionPos.blockToSectionCoord(bx) &&
                SectionPos.blockToSectionCoord(az) == SectionPos.blockToSectionCoord(bz);
    }

    public static ChunkPos containingChunkPos(int tx, int tz) {
        return new ChunkPos(SectionPos.blockToSectionCoord(tx), SectionPos.blockToSectionCoord(tz));
    }

    public static int distManhattan(BlockPos pos, int x, int y, int z) {
        int xd = Math.abs(pos.getX() - x);
        int yd = Math.abs(pos.getY() - y);
        int zd = Math.abs(pos.getZ() - z);
        return (int) (xd + yd + zd);
    }

    public static boolean shouldShowNav(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.is(ModItems.DEBUG_NAV) || item.is(ModItems.NAVIGATION);

    }
}
