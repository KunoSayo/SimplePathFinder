package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.PlayerLocatorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimplePathFinder.MOD_ID);
    public static DeferredItem<Item> DEBUG_NAV = ITEMS.registerSimpleItem("debug_nav");
    public static DeferredItem<Item> NAVIGATION = ITEMS.register("navigation", () -> new NavigationItem(
            new Item.Properties().stacksTo(1)
    ));
    public static DeferredItem<Item> PLAYER_LOCATOR = ITEMS.register("player_locator", () -> new PlayerLocatorItem(
            new Item.Properties().stacksTo(1)
    ));

}
