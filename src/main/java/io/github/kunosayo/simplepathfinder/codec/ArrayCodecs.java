package io.github.kunosayo.simplepathfinder.codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

public final class ArrayCodecs {

    public static int shortToInt(short value) {
        int val = 0;
        val |= value;
        return val;
    }

    public static short intToShort(int value) {
        short val = 0;
        val |= (short) (value & 0xffff);
        return val;
    }
    public static StreamCodec<ByteBuf, int[]> intArrayCodec(int len) {
        return StreamCodec.of((buffer, value) -> {
            for (int i = 0; i < Math.min(value.length, len); i++) {
                VarInt.write(buffer, value[i]);
            }
            for (int i = value.length; i < len; i++) {
                VarInt.write(buffer, 0);
            }
        }, buffer -> {
            int[] arr = new int[len];
            for (int i = 0; i < len; i++) {
                arr[i] = VarInt.read(buffer);
            }
            return arr;
        });
    }
    public static StreamCodec<ByteBuf, short[]> shortArrayCodec(int len) {
        return StreamCodec.of((buffer, value) -> {
            for (int i = 0; i < Math.min(value.length, len); i++) {
                VarInt.write(buffer, shortToInt(value[i]));
            }
            for (int i = value.length; i < len; i++) {
                VarInt.write(buffer, 0);
            }
        }, buffer -> {
            short[] arr = new short[len];
            for (int i = 0; i < len; i++) {
                arr[i] = intToShort(VarInt.read(buffer));
            }
            return arr;
        });
    }

    public static StreamCodec<ByteBuf, float[]> floatArrayCodec(int len) {
        return StreamCodec.of((buffer, value) -> {
            for (int i = 0; i < Math.min(value.length, len); i++) {
                buffer.writeFloat(value[i]);
            }
            for (int i = value.length; i < len; i++) {
                buffer.writeFloat(0.0f);
            }
        }, buffer -> {
            float[] arr = new float[len];
            for (int i = 0; i < len; i++) {
                arr[i] = buffer.readFloat();
            }
            return arr;
        });
    }
}
