package io.github.kunosayo.simplepathfinder.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.NonNull;


public class NavBrushItem extends Item {

    public NavBrushItem(Identifier id) {
        var key = ResourceKey.create(Registries.ITEM, id);
        super(new Properties().setId(key).stacksTo(1));
    }

    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        var player = context.getPlayer();
        if (player != null) {
            var hand = context.getHand();
            var level = context.getLevel();

            ItemStack stack = player.getItemInHand(hand);

            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                // todo
            }
        }

        return super.useOn(context);
    }
}
