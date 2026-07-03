package io.github.kunosayo.simplepathfinder.item;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.config.NavConfig;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.data.NavBrushData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.nav.ChunkInnerPos;
import io.github.kunosayo.simplepathfinder.nav.INavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.LayeredNavChunk;
import io.github.kunosayo.simplepathfinder.network.UpdateItemPropertiesPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Navigation brush item for modifying navigation data.
 * Supports different brush modes and operations for editing navigation edges.
 * <p>
 * Controls:
 * - Shift + Right-click (in air): Open configuration GUI
 * - Right-click (on block): Apply brush operation
 * - Left-click: Does not break blocks
 */
public class NavBrushItem extends Item {

    public NavBrushItem(Identifier id) {
        super(new Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return super.useOn(context);
        }

        Level level = context.getLevel();
        ItemStack stack = player.getItemInHand(context.getHand());
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            // Server-side: Apply brush operation
            if (canUseNavBrush(sp)) {
                return applyBrushOperation((ServerLevel) level, sp, stack, clickedPos, clickedFace);
            } else {
                sp.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.creative_required"));
                return InteractionResult.FAIL;
            }
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Only open GUI if player is holding Shift
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            // Open GUI on client
            io.github.kunosayo.simplepathfinder.client.gui.NavBrushScreen.open(stack, hand);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        // Get current brush data
        NavBrushData brushData = getBrushData(itemStack);

        // Add mode display
        builder.accept(Component.translatable("tooltip.nav_brush.mode")
                .append(Component.translatable(brushData.mode().getTranslationKey())
                        .withColor(modeToColor(brushData.mode()))));

        // Add operation display
        builder.accept(Component.translatable("tooltip.nav_brush.operation")
                .append(Component.translatable(brushData.operation().getTranslationKey())
                        .withColor(operationToColor(brushData.operation()))));

        // Add weight value display (only for ADJUST_WEIGHT operation)
        if (brushData.operation() == NavBrushOperation.ADJUST_WEIGHT) {
            builder.accept(Component.translatable("tooltip.nav_brush.weight_value", brushData.weightValue())
                    .withStyle(style -> style.withColor(0xFFFF00)));
        }

        // Add control hints
        builder.accept(Component.translatable("tooltip.nav_brush.controls")
                .withStyle(style -> style.withColor(0x7F7F7F)));
    }

    /**
     * Get brush data from item stack, creates default if null
     */
    public static NavBrushData getBrushData(ItemStack stack) {
        NavBrushData data = stack.get(ModDataComponents.NAV_BRUSH_COMPONENT.get());
        if (data == null) {
            data = NavBrushData.createDefault();
            stack.set(ModDataComponents.NAV_BRUSH_COMPONENT.get(), data);
        }
        return data;
    }

    /**
     * Set brush data to item stack
     */
    public static void setBrushData(ItemStack stack, NavBrushData data) {
        stack.set(ModDataComponents.NAV_BRUSH_COMPONENT.get(), data);
    }

    /**
     * Check if player can use nav brush
     */
    private static boolean canUseNavBrush(Player player) {
        if (!NavConfig.NAV_CONFIG.getLeft().requireCreativeMode.get()) {
            return true;
        }
        return player.isCreative();
    }

    /**
     * Apply brush operation to the target position
     */
    private InteractionResult applyBrushOperation(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos clickedPos, Direction clickedFace) {
        NavBrushData brushData = getBrushData(stack);

        var chunkPos = ChunkPos.containing(clickedPos);
        var data = LevelNavDataSavedData.loadFromLevel(level);

        // Get nav chunk
        var navChunkOpt = data.levelNavData.getNavChunk(chunkPos, false);
        if (navChunkOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.no_nav_data"));
            return InteractionResult.FAIL;
        }

        var navChunk = navChunkOpt.get();

        // Apply operation based on mode
        return switch (brushData.mode()) {
            case ALL_EDGES -> applyAllEdgesOperation(level, player, navChunk, clickedPos, brushData);
            case SINGLE_EDGE -> applySingleEdgeOperation(level, player, navChunk, clickedPos, clickedFace, brushData);
        };
    }

    /**
     * Apply operation to all edges at position
     */
    private InteractionResult applyAllEdgesOperation(ServerLevel level, ServerPlayer player, INavChunk navChunk, BlockPos clickedPos, NavBrushData brushData) {
        var chunkInnerPos = ChunkInnerPos.get(clickedPos);

        // Get the layer at this position
        var layerOpt = navChunk.getLayerNav(clickedPos).findAny();
        if (layerOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.no_layer_at_pos"));
            return InteractionResult.FAIL;
        }

        var layer = layerOpt.get();
        int modifiedCount = 0;

        // Apply to all 4 cardinal directions
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int isZ = dir.getAxis() == Direction.Axis.Z ? 1 : 0;

            modifiedCount += switch (brushData.operation()) {
                case DELETE -> {
                    if (layer instanceof LayeredNavChunk layeredChunk) {
                        layeredChunk.setDistance(chunkInnerPos.x, chunkInnerPos.z, isZ == 1, (short) -1);
                        yield 1;
                    }
                    yield 0;
                }
                case ADD -> {
                    if (layer instanceof LayeredNavChunk layeredChunk) {
                        layeredChunk.setDistance(chunkInnerPos.x, chunkInnerPos.z, isZ == 1, (short) 1);
                        yield 1;
                    }
                    yield 0;
                }
                case ADJUST_WEIGHT -> {
                    if (layer instanceof LayeredNavChunk layeredChunk) {
                        layeredChunk.setDistance(chunkInnerPos.x, chunkInnerPos.z, isZ == 1, (short) brushData.weightValue());
                        yield 1;
                    }
                    yield 0;
                }
            };
        }

        player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.modified_edges", modifiedCount));
        SimplePathFinder.playerMadeServerNavDirty(player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Apply operation to a single edge
     */
    private InteractionResult applySingleEdgeOperation(ServerLevel level, ServerPlayer player, INavChunk navChunk, BlockPos clickedPos, Direction clickedFace, NavBrushData brushData) {
        var chunkInnerPos = ChunkInnerPos.get(clickedPos);

        // Get the layer at this position
        var layerOpt = navChunk.getLayerNav(clickedPos).findAny();
        if (layerOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.no_layer_at_pos"));
            return InteractionResult.FAIL;
        }

        var layer = layerOpt.get();
        int isZ = clickedFace.getAxis() == Direction.Axis.Z ? 1 : 0;

        switch (brushData.operation()) {
            case DELETE -> {
                if (layer instanceof LayeredNavChunk layeredChunk) {
                    layeredChunk.setDistance(chunkInnerPos.x, chunkInnerPos.z, isZ == 1, (short) -1);
                    player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.edge_deleted", clickedFace.getName()));
                }
            }
            case ADD -> {
                if (layer instanceof LayeredNavChunk layeredChunk) {
                    layeredChunk.setDistance(chunkInnerPos.x, chunkInnerPos.z, isZ == 1, (short) 1);
                    player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.edge_added", clickedFace.getName()));
                }
            }
            case ADJUST_WEIGHT -> {
                if (layer instanceof LayeredNavChunk layeredChunk) {
                    layeredChunk.setDistance(chunkInnerPos.x, chunkInnerPos.z, isZ == 1, (short) brushData.weightValue());
                    player.sendSystemMessage(Component.translatable("simple_path_finder.nav_brush.edge_weight_set", clickedFace.getName(), brushData.weightValue()));
                }
            }
        }

        SimplePathFinder.playerMadeServerNavDirty(player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Get color for brush mode
     */
    private static int modeToColor(NavBrushMode mode) {
        return switch (mode) {
            case ALL_EDGES -> 0x00FFFF; // Cyan
            case SINGLE_EDGE -> 0xFFFF00; // Yellow
        };
    }

    /**
     * Get color for brush operation
     */
    private static int operationToColor(NavBrushOperation operation) {
        return switch (operation) {
            case DELETE -> 0xFF0000; // Red
            case ADD -> 0x00FF00; // Green
            case ADJUST_WEIGHT -> 0xFFA500; // Orange
        };
    }

    /**
     * Set brush data and sync with server.
     * This method updates the local item data and sends a packet to the server.
     *
     * @param stack     The item stack to update
     * @param hand      The hand holding the item
     * @param brushData The new brush data
     */
    public static void setBrushDataSync(ItemStack stack, InteractionHand hand, NavBrushData brushData) {
        // Update local item data
        setBrushData(stack, brushData);

        // Send packet to server
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            var packet = new UpdateItemPropertiesPacket(hand, brushData);
            net.minecraft.client.Minecraft.getInstance().getConnection().send(packet);
        }
    }
}
