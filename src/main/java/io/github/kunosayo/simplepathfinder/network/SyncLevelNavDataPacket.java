package io.github.kunosayo.simplepathfinder.network;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;


public class SyncLevelNavDataPacket implements CustomPacketPayload {
    public static final Type<SyncLevelNavDataPacket> NETWORK_TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplePathFinder.MOD_ID, "sync_level_nav"));


    public static final StreamCodec<ByteBuf, SyncLevelNavDataPacket> STREAM_CODEC = new StreamCodec<ByteBuf, SyncLevelNavDataPacket>() {
        @Override
        public void encode(ByteBuf buffer, SyncLevelNavDataPacket value) {
            // 先将数据编码到临时缓冲区
            ByteBuf tempBuffer = Unpooled.buffer();
            LevelNavData.STREAM_CODEC.encode(tempBuffer, value.levelNavData);

            // 压缩数据
            byte[] input = new byte[tempBuffer.readableBytes()];
            tempBuffer.readBytes(input);
            tempBuffer.release();


            try (Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION)) {
                deflater.setInput(input);
                deflater.finish();

                byte[] output = new byte[input.length];

                buffer.markWriterIndex();
                buffer.writeInt(0);

                int totalSize = 0;
                int lastIndex = 0;
                while (!deflater.finished()) {
                    int compressedSize = deflater.deflate(output);
                    buffer.writeBytes(output, 0, compressedSize);
                    totalSize += compressedSize;
                    lastIndex = buffer.writerIndex();
                }
                buffer.resetWriterIndex();
                buffer.writeInt(totalSize);
                buffer.writerIndex(lastIndex);
                deflater.end();

            }

        }

        @Override
        public SyncLevelNavDataPacket decode(ByteBuf buffer) {
            int len = buffer.readInt();
            byte[] compressed = new byte[len];
            buffer.readBytes(compressed);

            // 解压数据
            try (Inflater inflater = new Inflater()) {
                inflater.setInput(compressed);
                byte[] output = new byte[32 * 1024];
                ByteArrayOutputStream result = new ByteArrayOutputStream();

                try {
                    while (!inflater.finished()) {
                        int count = inflater.inflate(output);
                        if (count == 0) {
                            break;
                        }
                        result.write(output, 0, count);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to decompress nav data", e);
                } finally {
                    inflater.end();
                }

                // 从解压后的数据解码
                ByteBuf decompressedBuffer = Unpooled.wrappedBuffer(result.toByteArray());
                LevelNavData levelNavData = LevelNavData.STREAM_CODEC.decode(decompressedBuffer);
                decompressedBuffer.release();
                return new SyncLevelNavDataPacket(levelNavData);
            }

        }
    };


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
