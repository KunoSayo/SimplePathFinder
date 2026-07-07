package io.github.kunosayo.simplepathfinder.block;


import io.github.kunosayo.simplepathfinder.init.ModBlocks;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;


public class NavigationBarrierBlock extends TransparentBlock {


    public NavigationBarrierBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any());
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(ModItems.NAVIGATION_BARRIER_BLOCK.get()) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Check if any player is holding the barrier block item
        for (var player : level.players()) {
            if (!player.isLocalPlayer()) {
                return;
            }
            if (player.getMainHandItem().is(ModItems.NAVIGATION_BARRIER_BLOCK.get()) ||
                    player.getOffhandItem().is(ModItems.NAVIGATION_BARRIER_BLOCK.get())) {

                // Spawn particle at the center of the block
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 0.5;
                double z = pos.getZ() + 0.5;

                var particleOption = new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state);
                level.addParticle(particleOption, true, true, x, y, z, 0.0, 0.0, 0.0);
                break;
            }
        }
    }
}

