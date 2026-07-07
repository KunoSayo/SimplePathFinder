package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.item.LocatorItem;
import io.github.kunosayo.simplepathfinder.item.NavBrushItem;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
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
    public static DeferredItem<Item> NAV_BRUSH = ITEMS.register("nav_brush", () -> new NavBrushItem(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "nav_brush")
    ));

    public static DeferredItem<BlockItem> PATH_FINDER_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.PATH_FINDER_BLOCK);
    public static DeferredItem<BlockItem> NAVIGATION_BARRIER_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.NAVIGATION_BARRIER_BLOCK);

}
