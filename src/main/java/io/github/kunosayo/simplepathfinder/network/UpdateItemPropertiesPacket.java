package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.NavBrushData;
import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.init.ModDataComponents;
import io.github.kunosayo.simplepathfinder.item.NavBrushItem;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network packet to update item properties from client to server.
 * Used when client modifies navigation item or nav brush item properties through GUI.
 */
public class UpdateItemPropertiesPacket implements CustomPacketPayload {
    public static final Type<UpdateItemPropertiesPacket> NETWORK_TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "update_item_properties")
    );

    public static final StreamCodec<ByteBuf, UpdateItemPropertiesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                // Encode hand as ordinal (0 = MAIN_HAND, 1 = OFF_HAND)
                ByteBufCodecs.VAR_INT.encode(buf, packet.hand.ordinal());
                ByteBufCodecs.BOOL.encode(buf, packet.isNavBrush);

                if (packet.isNavBrush) {
                    // Encode NavBrushData
                    NavBrushData.STREAM_CODEC.encode(buf, packet.navBrushData);
                } else {
                    // Encode NavigationModeData
                    NavigationModeData.STREAM_CODEC.encode(buf, packet.navigationModeData);
                }
            },
            (buf) -> {
                // Decode hand from ordinal
                int handOrdinal = ByteBufCodecs.VAR_INT.decode(buf);
                InteractionHand hand = InteractionHand.values()[handOrdinal];
                boolean isNavBrush = ByteBufCodecs.BOOL.decode(buf);

                if (isNavBrush) {
                    NavBrushData brushData = NavBrushData.STREAM_CODEC.decode(buf);
                    return new UpdateItemPropertiesPacket(hand, brushData);
                } else {
                    NavigationModeData modeData = NavigationModeData.STREAM_CODEC.decode(buf);
                    return new UpdateItemPropertiesPacket(hand, modeData);
                }
            }
    );

    private final InteractionHand hand;
    private final boolean isNavBrush;

    // For NavBrushItem
    private final NavBrushData navBrushData;

    // For NavigationItem
    private final NavigationModeData navigationModeData;

    /**
     * Constructor for NavBrushItem
     */
    public UpdateItemPropertiesPacket(InteractionHand hand, NavBrushData navBrushData) {
        this.hand = hand;
        this.isNavBrush = true;
        this.navBrushData = navBrushData;
        this.navigationModeData = null;
    }

    /**
     * Constructor for NavigationItem
     */
    public UpdateItemPropertiesPacket(InteractionHand hand, NavigationModeData navigationModeData) {
        this.hand = hand;
        this.isNavBrush = false;
        this.navBrushData = null;
        this.navigationModeData = navigationModeData;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }

    /**
     * Server-side handler for the packet.
     * Updates the server-side item properties and syncs to other clients if needed.
     */
    public static void serverHandler(final UpdateItemPropertiesPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack stack = player.getItemInHand(packet.hand);

                if (stack.isEmpty()) {
                    return;
                }

                // Update item data based on type
                if (packet.isNavBrush) {
                    // Update NavBrushItem
                    if (stack.getItem() instanceof NavBrushItem) {
                        NavBrushItem.setBrushData(stack, packet.navBrushData);
                    }
                } else {
                    // Update NavigationItem
                    if (stack.getItem() instanceof NavigationItem) {
                        // Update mode and layer
                        NavigationItem.setNavigationMode(stack, packet.navigationModeData.mode());
                        NavigationItem.setNavigationLayer(stack, packet.navigationModeData.layer());
                    }
                }
            }
        });
    }
}
