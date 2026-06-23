package io.github.kunosayo.simplepathfinder.client.property;

import com.mojang.serialization.MapCodec;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.NeedleDirectionHelper;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;

/**
 * Item property for navigation item that switches models based on navigation mode.
 * Returns a float value (0.0-3.0) corresponding to the NavigationMode ordinal.
 */
public class NavigationModelProperty extends NeedleDirectionHelper implements RangeSelectItemModelProperty {

    public static final MapCodec<NavigationModelProperty> MAP_CODEC = MapCodec.unit(new NavigationModelProperty());

    public NavigationModelProperty() {
        super(true);
    }

    @Override
    protected float calculate(ItemStack itemStack, ClientLevel level, int seed, ItemOwner owner) {
        if (itemStack.getItem() instanceof NavigationItem) {
            NavigationMode mode = NavigationItem.getNavigationMode(itemStack);
            return (float) mode.ordinal();
        }
        return 0.0f; // Default to DEFAULT mode (ordinal 0)
    }

    @Override
    public MapCodec<NavigationModelProperty> type() {
        return MAP_CODEC;
    }

    /**
     * Registers this property with the item model system.
     * Call this from RegisterRangeSelectItemModelPropertyEvent handler.
     */
    public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation_mode"),
                MAP_CODEC
        );
    }
}
