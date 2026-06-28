package io.github.kunosayo.simplepathfinder.client.property;

import com.mojang.serialization.MapCodec;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LocatorData;
import io.github.kunosayo.simplepathfinder.item.LocatorItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Item property for locator item that switches models based on bound state.
 * Returns a float value corresponding to the locator state:
 * 0.0 = unbound (no data)
 * 1.0 = player bound
 * 2.0 = position bound
 */
public class LocatorModelProperty implements RangeSelectItemModelProperty {

    public static final MapCodec<LocatorModelProperty> MAP_CODEC = MapCodec.unit(new LocatorModelProperty());

    public LocatorModelProperty() {
    }

    @Override
    public float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (itemStack.getItem() instanceof LocatorItem) {
            LocatorData data = LocatorItem.getLocatorData(itemStack);
            if (data == null) {
                return 0.0f; // Unbound
            } else if (data.isPlayerBound()) {
                return 1.0f; // Player bound
            } else {
                return 2.0f; // Position bound
            }
        }
        return 0.0f; // Default to unbound
    }

    @Override
    public MapCodec<LocatorModelProperty> type() {
        return MAP_CODEC;
    }

    /**
     * Registers this property with the item model system.
     * Call this from RegisterRangeSelectItemModelPropertyEvent handler.
     */
    public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "locator_state"),
                MAP_CODEC
        );
    }
}
