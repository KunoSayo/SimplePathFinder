package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class SyncLevelNavDataPacket implements CustomPacketPayload {
    public static final Type<SyncLevelNavDataPacket> NETWORK_TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "sync_level_nav"));


    public static final StreamCodec<ByteBuf, SyncLevelNavDataPacket> STREAM_CODEC = StreamCodec
            .composite(LevelNavData.STREAM_CODEC, syncLevelNavDataPacket -> syncLevelNavDataPacket.levelNavData, SyncLevelNavDataPacket::new);


    LevelNavData levelNavData;

    public SyncLevelNavDataPacket(LevelNavData levelNavData) {
        this.levelNavData = levelNavData;
    }

    public static void clientHandler(final SyncLevelNavDataPacket updatePacket, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                SimplePathFinder.clientNavData = updatePacket.levelNavData;
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return NETWORK_TYPE;
    }

}
