package dev.errnicraft.chatremastered.tcp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class ImageTcpClientHandler {

    private final ImageTcpCache cache;
    private final Consumer<String> onImageReady;

    public ImageTcpClientHandler(ImageTcpCache cache, Consumer<String> onImageReady) {
        this.cache = cache;
        this.onImageReady = onImageReady;
    }

    public void handle(Socket socket) {
        try {
            socket.setSoTimeout(60_000);
            DataInputStream din = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            byte cmd = din.readByte();
            if (cmd == ImageTcpProtocol.CMD_PING) {
                handlePing(dout);
            } else if (cmd == ImageTcpProtocol.CMD_UPLOAD) {
                handleUpload(din, dout);
            } else if (cmd == ImageTcpProtocol.CMD_GET_FULL) {
                handleGetFull(din, dout);
            } else if (cmd == ImageTcpProtocol.CMD_GET_THUMB) {
                handleGetThumb(din, dout);
            }
            dout.flush();
        } catch (Exception ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handlePing(DataOutputStream dout) throws IOException {
        byte[] body = ImageTcpProtocol.PING_BODY.getBytes(StandardCharsets.UTF_8);
        dout.writeByte(ImageTcpProtocol.CMD_PING);
        dout.writeShort(body.length);
        dout.write(body);
    }

    private void handleUpload(DataInputStream din, DataOutputStream dout) throws IOException {
        int tokenLen = din.readShort() & 0xFFFF;
        String token = new String(din.readNBytes(tokenLen), StandardCharsets.UTF_8);

        int idLen = din.readByte() & 0xFF;
        String imageId = new String(din.readNBytes(idLen), StandardCharsets.UTF_8);

        int dataLen = din.readInt();

        if (!cache.isTokenValid(token)) {
            din.skipNBytes(dataLen);
            dout.writeByte(ImageTcpProtocol.RES_FORBIDDEN);
            return;
        }

        if ((long) dataLen > ImageTcpServer.getMaxUploadBytes()) {
            din.skipNBytes(dataLen);
            dout.writeByte(ImageTcpProtocol.RES_TOO_LARGE);
            return;
        }

        ImageTcpCache.InProgressUpload upload = cache.startInProgress(imageId, dataLen);
        byte[] chunk = new byte[65536];
        int received = 0;
        boolean ok = true;
        try {
            while (received < dataLen) {
                int toRead = Math.min(chunk.length, dataLen - received);
                int n = din.read(chunk, 0, toRead);
                if (n < 0) {
                    ok = false;
                    break;
                }
                upload.append(chunk, n);
                received += n;
            }
        } catch (Exception e) {
            ok = false;
        }

        if (!ok || received < dataLen) {
            upload.fail();
            cache.finishInProgress(imageId);
            dout.writeByte(ImageTcpProtocol.RES_NOT_FOUND);
            return;
        }

        byte[] data = upload.buffer;
        cache.storeBytes(imageId, data);
        cache.finishInProgress(imageId);

        dout.writeByte(ImageTcpProtocol.RES_OK);
        dout.flush();

        try {
            if (onImageReady != null) {
                onImageReady.accept(imageId);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleGetFull(DataInputStream din, DataOutputStream dout) throws IOException {
        int idLen = din.readByte() & 0xFF;
        String imageId = new String(din.readNBytes(idLen), StandardCharsets.UTF_8);

        byte[] data = cache.getCachedBytes(imageId);
        if (data != null) {
            dout.writeByte(ImageTcpProtocol.RES_OK);
            dout.writeLong(data.length);
            dout.write(data);
            return;
        }

        ImageTcpCache.InProgressUpload upload = cache.getInProgress(imageId);
        if (upload == null) {
            dout.writeByte(ImageTcpProtocol.RES_NOT_FOUND);
            return;
        }

        dout.writeByte(ImageTcpProtocol.RES_OK);
        dout.writeLong(upload.total);
        dout.flush();

        int sent = 0;
        while (sent < upload.total) {
            int available;
            synchronized (upload.lock) {
                while (upload.written <= sent && !upload.failed) {
                    try {
                        upload.lock.wait(30_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (upload.failed) {
                    return;
                }
                available = upload.written;
            }
            dout.write(upload.buffer, sent, available - sent);
            dout.flush();
            sent = available;
        }
    }

    private void handleGetThumb(DataInputStream din, DataOutputStream dout) throws IOException {
        int idLen = din.readByte() & 0xFF;
        String imageId = new String(din.readNBytes(idLen), StandardCharsets.UTF_8);
        boolean exists = cache.hasCached(imageId) || cache.existsOnDisk(imageId);
        dout.writeByte(exists ? ImageTcpProtocol.RES_OK : ImageTcpProtocol.RES_NOT_FOUND);
    }
}
