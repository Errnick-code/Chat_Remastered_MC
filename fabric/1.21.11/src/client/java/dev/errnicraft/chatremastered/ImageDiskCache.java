package dev.errnicraft.chatremastered;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ImageDiskCache {

    public static final int MAX_RAM = 20;

    private static final long DISK_TTL_MS = 24L * 60 * 60 * 1000;

    private static final LinkedHashMap<String, byte[]> ramCache = new LinkedHashMap<>(MAX_RAM + 4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_RAM;
        }
    };
    private static final Object ramLock = new Object();

    private ImageDiskCache() {
    }

    public static File cacheDir() {
        File dir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("chat-remastered-cache").toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File fileFor(String imageId) {
        return new File(cacheDir(), imageId);
    }

    public static File getFile(String imageId) {
        return fileFor(imageId);
    }

    public static void save(String imageId, byte[] data) {
        synchronized (ramLock) {
            ramCache.put(imageId, data);
        }
        try {
            File f = fileFor(imageId);
            if (!f.exists()) {
                Files.write(f.toPath(), data);
            }
        } catch (Exception ignored) {
        }
    }

    public static byte[] load(String imageId) {
        synchronized (ramLock) {
            byte[] cached = ramCache.get(imageId);
            if (cached != null) {
                return cached;
            }
        }
        try {
            File f = fileFor(imageId);
            if (!f.exists()) {
                return null;
            }
            if (System.currentTimeMillis() - f.lastModified() > DISK_TTL_MS) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
                return null;
            }
            byte[] bytes = Files.readAllBytes(f.toPath());
            synchronized (ramLock) {
                ramCache.put(imageId, bytes);
            }
            return bytes;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean exists(String imageId) {
        synchronized (ramLock) {
            if (ramCache.containsKey(imageId)) {
                return true;
            }
        }
        File f = fileFor(imageId);
        if (!f.exists()) {
            return false;
        }
        if (System.currentTimeMillis() - f.lastModified() > DISK_TTL_MS) {
            try {
                f.delete();
            } catch (Exception ignored) {
            }
            return false;
        }
        return true;
    }

    public static Set<String> ramIds() {
        synchronized (ramLock) {
            return new LinkedHashSet<>(ramCache.keySet());
        }
    }

    public static int clearDisk() {
        Set<String> keep = ramIds();
        int deleted = 0;
        try {
            File[] files = cacheDir().listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!keep.contains(f.getName())) {
                        try {
                            if (f.delete()) {
                                deleted++;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return deleted;
    }

    public static void clearAll() {
        synchronized (ramLock) {
            ramCache.clear();
        }
        try {
            File[] files = cacheDir().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void delete(String imageId) {
        synchronized (ramLock) {
            ramCache.remove(imageId);
        }
        try {
            fileFor(imageId).delete();
        } catch (Exception ignored) {
        }
    }

    public static CacheStats stats() {
        int ramCount;
        long ramBytes;
        synchronized (ramLock) {
            ramCount = ramCache.size();
            long sum = 0L;
            for (byte[] v : ramCache.values()) {
                sum += v.length;
            }
            ramBytes = sum;
        }
        int diskCount = 0;
        long diskBytes = 0L;
        try {
            File[] files = cacheDir().listFiles();
            if (files != null) {
                for (File f : files) {
                    diskCount++;
                    diskBytes += f.length();
                }
            }
        } catch (Exception ignored) {
        }
        return new CacheStats(ramCount, ramBytes, diskCount, diskBytes);
    }

    public static final class CacheStats {
        private final int ramCount;
        private final long ramBytes;
        private final int diskCount;
        private final long diskBytes;

        public CacheStats(int ramCount, long ramBytes, int diskCount, long diskBytes) {
            this.ramCount = ramCount;
            this.ramBytes = ramBytes;
            this.diskCount = diskCount;
            this.diskBytes = diskBytes;
        }

        public int getRamCount() {
            return ramCount;
        }

        public long getRamBytes() {
            return ramBytes;
        }

        public int getDiskCount() {
            return diskCount;
        }

        public long getDiskBytes() {
            return diskBytes;
        }

        public String ramMb() {
            return String.format("%.1f", ramBytes / 1_048_576.0);
        }

        public String diskMb() {
            return String.format("%.1f", diskBytes / 1_048_576.0);
        }
    }
}
