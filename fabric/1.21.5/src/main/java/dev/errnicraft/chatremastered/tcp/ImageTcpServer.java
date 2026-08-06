package dev.errnicraft.chatremastered.tcp;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ImageTcpServer {

    private static final ImageTcpCache CACHE = new ImageTcpCache();

    private static volatile long maxUploadBytes = 8L * 1024 * 1024;

    private static final java.util.List<Consumer<String>> onImageReadyListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private static volatile ServerSocket serverSocket;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ChatRemastered-TCP");
        t.setDaemon(true);
        return t;
    });

    public static void startIfNeeded(int port) {
        ServerSocket existing = serverSocket;
        if (existing != null && !existing.isClosed()) {
            return;
        }
        try {
            ServerSocket ss = new ServerSocket(port);
            serverSocket = ss;
            System.out.println("[Chat Remastered] TCP server started on port " + port);
            EXECUTOR.submit(() -> {
                while (!ss.isClosed()) {
                    try {
                        Socket client = ss.accept();
                        ImageTcpClientHandler handler = new ImageTcpClientHandler(CACHE, imageId -> {
                            for (Consumer<String> listener : onImageReadyListeners) {
                                try {
                                    listener.accept(imageId);
                                } catch (Exception e) {
                                    System.out.println("[Chat Remastered] onImageReady listener error: " + e.getMessage());
                                }
                            }
                        });
                        EXECUTOR.submit(() -> handler.handle(client));
                    } catch (SocketException e) {
                        break;
                    } catch (Exception e) {
                        System.out.println("[Chat Remastered] TCP accept error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Failed to start TCP server on port " + port + ": " + e.getMessage());
        }
    }

    public static void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
        serverSocket = null;
        System.out.println("[Chat Remastered] TCP server stopped");
    }

    public static void initCacheDir(File serverDir) {
        CACHE.initCacheDir(serverDir);
    }

    public static void setMaxUploadBytes(long value) {
        maxUploadBytes = value;
    }

    public static long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    @Deprecated
    public static void setOnImageReady(Consumer<String> callback) {
        onImageReadyListeners.add(callback);
    }

    public static void addOnImageReadyListener(Consumer<String> callback) {
        onImageReadyListeners.add(callback);
    }

    public static void addToken(String token) {
        CACHE.addToken(token);
    }

    public static void removeToken(String token) {
        CACHE.removeToken(token);
    }

    public static boolean hasCached(String imageId) {
        return CACHE.hasCached(imageId);
    }

    public static byte[] getCachedBytes(String imageId) {
        return CACHE.getCachedBytes(imageId);
    }

    public static void storeBytes(String imageId, byte[] data) {
        CACHE.storeBytes(imageId, data);
    }

    public static void deleteImage(String imageId) {
        CACHE.deleteImage(imageId);
    }

    public static void evictOld(int keepCount) {
        CACHE.evictOld(keepCount);
    }

    private ImageTcpServer() {
    }
}
