package io.github.kunosayo.simplepathfinder.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class NavUtil {
    public static boolean isNoCollision(Level level, BlockPos pos) {
        return isNoCollision(level, pos, level.getBlockState(pos));
    }

    public static boolean isNoCollision(Level level, BlockPos pos, BlockState state) {
        return state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean considerSafeCross(Level level, BlockPos pos, BlockState state) {
        return isNoCollision(level, pos, state) && state.getFluidState().isEmpty();
    }

    public static boolean considerSafeCross(Level level, BlockPos pos) {
        return considerSafeCross(level, pos, level.getBlockState(pos));
    }

    public static boolean considerSafeGround(Level level, BlockPos pos, BlockState state) {
        return !isNoCollision(level, pos, state) || state.getFluidState().is(FluidTags.WATER);
    }

    public static boolean isSameChunk(BlockPos a, BlockPos b) {
        return (a.getX() >> 4) == (b.getX() >> 4) && (a.getZ() >> 4) == (b.getZ() >> 4);
    }
}
