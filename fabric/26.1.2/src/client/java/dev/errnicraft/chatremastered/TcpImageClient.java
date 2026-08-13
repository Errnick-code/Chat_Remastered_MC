package dev.errnicraft.chatremastered;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class TcpImageClient {

    private static final byte CMD_PING = 0x01;
    private static final byte CMD_UPLOAD = 0x02;
    private static final byte CMD_GET_FULL = 0x03;
    private static final byte CMD_GET_THUMB = 0x04;

    private static final byte RES_OK = 0x00;
    private static final byte RES_NOT_FOUND = 0x01;
    private static final byte RES_FORBIDDEN = 0x01;

    private static final int UPLOAD_MAX_ATTEMPTS = 4;
    private static final long UPLOAD_BASE_DELAY_MS = 1_000L;
    private static final int DOWNLOAD_MAX_ATTEMPTS = 5;
    private static final long DOWNLOAD_BASE_DELAY_MS = 500L;

    private TcpImageClient() {
    }

    private static Socket connect(int timeout) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(ChatRemasteredConfig.getServerHost(), ChatRemasteredConfig.getImagePort()), timeout);
        s.setSoTimeout(timeout);
        return s;
    }

    public static boolean ping() {
        try (Socket socket = connect(5000)) {
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            DataInputStream din = new DataInputStream(socket.getInputStream());
            dout.writeByte(CMD_PING);
            dout.flush();
            byte cmd = din.readByte();
            if (cmd != CMD_PING) {
                return false;
            }
            int len = din.readShort() & 0xFFFF;
            String body = new String(din.readNBytes(len), StandardCharsets.UTF_8);
            return body.equals("chatmedia-ok");
        } catch (Exception e) {
            return false;
        }
    }

    public static String upload(String imageId, String token, byte[] data) {
        return upload(imageId, token, data, null);
    }

    public static String upload(String imageId, String token, byte[] data, Consumer<Float> onProgress) {
        String lastError = "unknown";
        for (int attempt = 1; attempt <= UPLOAD_MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                long delayMs = UPLOAD_BASE_DELAY_MS * (1L << (attempt - 2));
                System.out.println("[Chat Remastered] Upload attempt " + attempt + "/" + UPLOAD_MAX_ATTEMPTS
                        + " for " + imageId + " (retry in " + delayMs + "ms)");
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                }
            }
            String result = uploadOnce(imageId, token, data, onProgress);
            switch (result) {
                case "ok" -> {
                    return "ok";
                }
                case "forbidden" -> {
                    return "forbidden";
                }
                case "too_large" -> {
                    return "too_large";
                }
                default -> {
                    lastError = result;
                    System.out.println("[Chat Remastered] Upload error (attempt " + attempt + "): " + result);
                }
            }
        }
        System.out.println("[Chat Remastered] Upload failed after " + UPLOAD_MAX_ATTEMPTS + " attempts for " + imageId + ": " + lastError);
        return lastError;
    }

    private static String uploadOnce(String imageId, String token, byte[] data, Consumer<Float> onProgress) {
        try (Socket socket = connect(10_000)) {
            socket.setSoTimeout(30_000);
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            DataInputStream din = new DataInputStream(socket.getInputStream());

            byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
            byte[] idBytes = imageId.getBytes(StandardCharsets.UTF_8);

            dout.writeByte(CMD_UPLOAD);
            dout.writeShort(tokenBytes.length);
            dout.write(tokenBytes);
            dout.writeByte(idBytes.length);
            dout.write(idBytes);
            dout.writeInt(data.length);
            if (onProgress == null || data.length <= 0) {
                dout.write(data);
            } else {
                int chunk = 65536;
                int sent = 0;
                while (sent < data.length) {
                    int toWrite = Math.min(chunk, data.length - sent);
                    dout.write(data, sent, toWrite);
                    sent += toWrite;
                    onProgress.accept((float) sent / data.length);
                }
            }
            dout.flush();

            byte resp = din.readByte();
            if (resp == 0x00) {
                return "ok";
            } else if (resp == 0x01) {
                return "forbidden";
            } else if (resp == 0x02) {
                return "too_large";
            } else {
                return "error";
            }
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Upload exception: " + e);
            return "exception: " + e.getMessage();
        }
    }

    public static byte[] getFull(String imageId, Consumer<Float> onProgress) {
        for (int attempt = 1; attempt <= DOWNLOAD_MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                long delayMs = DOWNLOAD_BASE_DELAY_MS * (1L << (attempt - 2));
                System.out.println("[Chat Remastered] Download attempt " + attempt + "/" + DOWNLOAD_MAX_ATTEMPTS
                        + " for " + imageId + " (retry in " + delayMs + "ms)");
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                }
            }
            byte[] result = getFullOnce(imageId, onProgress);
            if (result != null) {
                return result;
            }
            System.out.println("[Chat Remastered] Download miss (attempt " + attempt + "): " + imageId + " not ready yet");
        }
        System.out.println("[Chat Remastered] Download failed after " + DOWNLOAD_MAX_ATTEMPTS + " attempts for " + imageId);
        return null;
    }

    private static byte[] getFullOnce(String imageId, Consumer<Float> onProgress) {
        try (Socket socket = connect(5000)) {
            socket.setSoTimeout(60_000);
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            DataInputStream din = new DataInputStream(socket.getInputStream());

            byte[] idBytes = imageId.getBytes(StandardCharsets.UTF_8);
            dout.writeByte(CMD_GET_FULL);
            dout.writeByte(idBytes.length);
            dout.write(idBytes);
            dout.flush();

            if (din.readByte() != RES_OK) {
                return null;
            }
            long len = din.readLong();

            if (len > 0) {
                ImageCache.setFileSizeBytes(imageId, len);
            }

            if (onProgress == null || len <= 0L) {

                byte[] result = din.readNBytes((int) len);
                return result.length == len ? result : null;
            } else {

                byte[] buf = new byte[(int) len];
                int received = 0;
                int chunk = 16384;
                while (received < len) {
                    int toRead = Math.min(chunk, (int) len - received);
                    int n = din.read(buf, received, toRead);
                    if (n < 0) {
                        break;
                    }
                    received += n;
                    onProgress.accept((float) received / len);
                }
                return received < len ? null : buf;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] getThumb(String imageId) {
        try (Socket socket = connect(5000)) {
            socket.setSoTimeout(10_000);
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            DataInputStream din = new DataInputStream(socket.getInputStream());

            byte[] idBytes = imageId.getBytes(StandardCharsets.UTF_8);
            dout.writeByte(CMD_GET_THUMB);
            dout.writeByte(idBytes.length);
            dout.write(idBytes);
            dout.flush();

            if (din.readByte() != RES_OK) {
                return null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
