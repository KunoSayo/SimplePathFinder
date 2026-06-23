package io.github.kunosayo.simplepathfinder.client.property;

import com.mojang.serialization.MapCodec;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.item.NavBrushItem;
import io.github.kunosayo.simplepathfinder.item.NavBrushMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Item property for nav brush item that switches models based on brush mode.
 * Returns true when mode is SINGLE_EDGE, false when ALL_EDGES.
 */
public class NavBrushModelProperty implements ConditionalItemModelProperty {

    /**
     * Map codec for serialization
     */
    public static final MapCodec<NavBrushModelProperty> MAP_CODEC = MapCodec.unit(new NavBrushModelProperty());

    public NavBrushModelProperty() {
    }

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        if (stack.getItem() instanceof NavBrushItem) {
            NavBrushMode mode = NavBrushItem.getBrushData(stack).mode();
            return mode == NavBrushMode.SINGLE_EDGE;
        }
        return false; // Default to ALL_EDGES (false)
    }

    @Override
    public MapCodec<? extends ConditionalItemModelProperty> type() {
        return MAP_CODEC;
    }

    /**
     * Registers this property with the item model system.
     * Call this from RegisterConditionalItemModelPropertyEvent handler.
     */
    public static void register(RegisterConditionalItemModelPropertyEvent event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "nav_brush_mode"),
                MAP_CODEC
        );
    }
}
