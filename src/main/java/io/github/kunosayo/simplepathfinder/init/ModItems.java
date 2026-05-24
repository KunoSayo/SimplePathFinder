package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.LocatorItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimplePathFinder.MOD_ID);
    public static DeferredItem<Item> DEBUG_NAV = ITEMS.registerSimpleItem("debug_nav");
    public static DeferredItem<Item> NAVIGATION = ITEMS.register("navigation", () -> new NavigationItem(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "navigation")
    ));
    public static DeferredItem<Item> LOCATOR = ITEMS.register("locator", () -> new LocatorItem(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "locator")

    ));

}
