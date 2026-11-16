package dev.errnick.chatremasteredplugin.net;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class PacketByteBuf {

    private byte[] data;
    private int readerIndex;
    private int writerIndex;

    public PacketByteBuf() {
        this.data = new byte[32];
        this.readerIndex = 0;
        this.writerIndex = 0;
    }

    public PacketByteBuf(byte[] data) {
        this.data = data;
        this.readerIndex = 0;
        this.writerIndex = data.length;
    }

    private void ensureCapacity(int extra) {
        int required = writerIndex + extra;
        if (required <= data.length) {
            return;
        }
        int newLen = data.length * 2;
        while (newLen < required) {
            newLen *= 2;
        }
        byte[] newData = new byte[newLen];
        System.arraycopy(data, 0, newData, 0, writerIndex);
        data = newData;
    }

    public void writeByte(int value) {
        ensureCapacity(1);
        data[writerIndex++] = (byte) value;
    }

    public int readByte() {
        return data[readerIndex++];
    }

    public int readUnsignedByte() {
        return data[readerIndex++] & 0xFF;
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public void writeInt(int value) {
        ensureCapacity(4);
        data[writerIndex++] = (byte) (value >>> 24);
        data[writerIndex++] = (byte) (value >>> 16);
        data[writerIndex++] = (byte) (value >>> 8);
        data[writerIndex++] = (byte) value;
    }

    public int readInt() {
        int result = ((data[readerIndex] & 0xFF) << 24)
                | ((data[readerIndex + 1] & 0xFF) << 16)
                | ((data[readerIndex + 2] & 0xFF) << 8)
                | (data[readerIndex + 3] & 0xFF);
        readerIndex += 4;
        return result;
    }

    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte(value);
    }

    public int readVarInt() {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = (byte) readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return result;
    }

    public void writeUtf(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        ensureCapacity(bytes.length);
        System.arraycopy(bytes, 0, data, writerIndex, bytes.length);
        writerIndex += bytes.length;
    }

    public String readUtf() {
        int len = readVarInt();
        String value = new String(data, readerIndex, len, StandardCharsets.UTF_8);
        readerIndex += len;
        return value;
    }

    public byte[] toByteArray() {
        byte[] result = new byte[writerIndex];
        System.arraycopy(data, 0, result, 0, writerIndex);
        return result;
    }

    public boolean isReadable() {
        return readerIndex < writerIndex;
    }

    private void writeModifiedUtf(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ensureCapacity(2 + bytes.length);
        data[writerIndex++] = (byte) (bytes.length >>> 8);
        data[writerIndex++] = (byte) bytes.length;
        System.arraycopy(bytes, 0, data, writerIndex, bytes.length);
        writerIndex += bytes.length;
    }

    private String readModifiedUtf() {
        int len = ((data[readerIndex] & 0xFF) << 8) | (data[readerIndex + 1] & 0xFF);
        readerIndex += 2;
        String value = new String(data, readerIndex, len, StandardCharsets.UTF_8);
        readerIndex += len;
        return value;
    }

    public void writeComponentAsPlainStringTag(String jsonText) {
        writeUtf(jsonText);
    }

    public String readComponentJsonFromStringTag() {
        return readUtf();
    }
}
