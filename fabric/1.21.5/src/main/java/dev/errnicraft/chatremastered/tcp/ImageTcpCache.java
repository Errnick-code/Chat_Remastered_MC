package dev.errnicraft.chatremastered.tcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageTcpCache {

    public static final class InProgressUpload {
        public final Object lock = new Object();
        public volatile byte[] buffer;
        public volatile int written = 0;
        public volatile int total = -1;
        public volatile boolean failed = false;

        InProgressUpload(int totalLen) {
            this.total = totalLen;
            this.buffer = new byte[totalLen];
        }

        void append(byte[] chunk, int len) {
            synchronized (lock) {
                System.arraycopy(chunk, 0, buffer, written, len);
                written += len;
                lock.notifyAll();
            }
        }

        void fail() {
            synchronized (lock) {
                failed = true;
                lock.notifyAll();
            }
        }

        boolean isDone() {
            return total >= 0 && written >= total;
        }
    }

    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InProgressUpload> inProgress = new ConcurrentHashMap<>();
    private File cacheDir;

    public void initCacheDir(File serverDir) {
        File dir = new File(serverDir, "srvcashe");
        dir.mkdirs();
        this.cacheDir = dir;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !cache.containsKey(f.getName())) {
                    try {
                        cache.put(f.getName(), Files.readAllBytes(f.toPath()));
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    public void addToken(String token) {
        validTokens.add(token);
    }

    public void removeToken(String token) {
        validTokens.remove(token);
    }

    public boolean isTokenValid(String token) {
        return validTokens.contains(token);
    }

    public boolean hasCached(String imageId) {
        return cache.containsKey(imageId);
    }

    public boolean existsOnDisk(String imageId) {
        return cacheDir != null && new File(cacheDir, imageId).exists();
    }

    public byte[] getCachedBytes(String imageId) {
        byte[] cached = cache.get(imageId);
        if (cached != null) {
            return cached;
        }
        if (cacheDir == null) {
            return null;
        }
        File f = new File(cacheDir, imageId);
        if (!f.exists()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            cache.put(imageId, bytes);
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

    public void storeBytes(String imageId, byte[] data) {
        cache.put(imageId, data);
        if (cacheDir != null) {
            try {
                Files.write(new File(cacheDir, imageId).toPath(), data);
            } catch (IOException ignored) {
            }
        }
    }

    public void deleteImage(String imageId) {
        cache.remove(imageId);
        if (cacheDir != null) {
            try {
                new File(cacheDir, imageId).delete();
            } catch (Exception ignored) {
            }
        }
    }

    public void evictOld(int keepCount) {
        if (cache.size() <= keepCount) {
            return;
        }
        List<String> keys = new ArrayList<>(cache.keySet());
        int toRemoveCount = keys.size() - keepCount;
        for (int i = 0; i < toRemoveCount; i++) {
            deleteImage(keys.get(i));
        }
    }

    public InProgressUpload startInProgress(String imageId, int totalLen) {
        InProgressUpload up = new InProgressUpload(totalLen);
        inProgress.put(imageId, up);
        return up;
    }

    public InProgressUpload getInProgress(String imageId) {
        return inProgress.get(imageId);
    }

    public void finishInProgress(String imageId) {
        inProgress.remove(imageId);
    }
}
