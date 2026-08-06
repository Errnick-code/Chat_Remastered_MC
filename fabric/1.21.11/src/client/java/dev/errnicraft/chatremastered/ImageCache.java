package dev.errnicraft.chatremastered;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ImageCache {

    public static final class GifFrameEntry {
        private final Identifier textureId;
        private final int width;
        private final int height;
        private final int delayMs;

        public GifFrameEntry(Identifier textureId, int width, int height, int delayMs) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.delayMs = delayMs;
        }

        public Identifier getTextureId() {
            return textureId;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getDelayMs() {
            return delayMs;
        }
    }

    public enum DownloadState {
        IDLE, IN_PROGRESS, DONE
    }

    private static final Map<String, Identifier> previewTexture = new ConcurrentHashMap<>();
    private static final Map<String, IntPair> sizes = new ConcurrentHashMap<>();
    private static final Map<String, IntPair> origSizes = new ConcurrentHashMap<>();
    private static final Map<String, IntPair> texSizes = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> loadedFlags = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> fullDataMap = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> deletedFlags = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> deletedByAdminFlags = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> errorFlags = new ConcurrentHashMap<>();

    private static final Map<String, DownloadState> downloadState = new ConcurrentHashMap<>();
    private static final Map<String, Float> downloadProgress = new ConcurrentHashMap<>();

    private static final Map<String, Long> fileSizeBytes = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> uploadInProgress = new ConcurrentHashMap<>();
    private static final Map<String, Float> uploadProgressMap = new ConcurrentHashMap<>();

    private static final Map<String, List<GifFrameEntry>> gifFrames = new ConcurrentHashMap<>();

    private static final Map<String, Long> gifStartTime = new ConcurrentHashMap<>();

    private ImageCache() {
    }

    public static Identifier getTexture(String imageId) {
        return previewTexture.get(imageId);
    }

    public static IntPair getSize(String imageId) {
        return sizes.get(imageId);
    }

    public static IntPair getOrigSize(String imageId) {
        return origSizes.get(imageId);
    }

    public static IntPair getTexSize(String imageId) {
        return texSizes.get(imageId);
    }

    public static boolean isLoaded(String imageId) {
        return Boolean.TRUE.equals(loadedFlags.get(imageId));
    }

    public static byte[] getFullData(String imageId) {
        return fullDataMap.get(imageId);
    }

    public static boolean isDeleted(String imageId) {
        return Boolean.TRUE.equals(deletedFlags.get(imageId));
    }

    public static boolean isDeletedByAdmin(String imageId) {
        return Boolean.TRUE.equals(deletedByAdminFlags.get(imageId));
    }

    public static boolean isError(String imageId) {
        return Boolean.TRUE.equals(errorFlags.get(imageId));
    }

    public static boolean isVideo(String imageId) {
        return false;
    }

    public static DownloadState getDownloadState(String imageId) {
        DownloadState state = downloadState.get(imageId);
        if (state != null) {
            return state;
        }
        return ImageDiskCache.exists(imageId) ? DownloadState.DONE : DownloadState.IDLE;
    }

    public static float getDownloadProgress(String imageId) {
        Float p = downloadProgress.get(imageId);
        return p != null ? p : 0f;
    }

    public static long getFileSizeBytes(String imageId) {
        Long v = fileSizeBytes.get(imageId);
        return v != null ? v : 0L;
    }

    public static void setFileSizeBytes(String imageId, long size) {
        fileSizeBytes.put(imageId, size);
    }

    public static void startDownload(String imageId) {
        downloadState.put(imageId, DownloadState.IN_PROGRESS);
        downloadProgress.put(imageId, 0f);
    }

    public static void setDownloadProgress(String imageId, float p) {
        downloadProgress.put(imageId, Math.max(0f, Math.min(1f, p)));
    }

    public static void finishDownload(String imageId) {
        downloadState.put(imageId, DownloadState.DONE);
        downloadProgress.put(imageId, 1f);
    }

    public static void resetDownload(String imageId) {
        downloadState.remove(imageId);
        downloadProgress.remove(imageId);
    }

    public static boolean isUploading(String imageId) {
        return Boolean.TRUE.equals(uploadInProgress.get(imageId));
    }

    public static float getUploadProgress(String imageId) {
        Float p = uploadProgressMap.get(imageId);
        return p != null ? p : 0f;
    }

    public static void startUpload(String imageId) {
        uploadInProgress.put(imageId, Boolean.TRUE);
        uploadProgressMap.put(imageId, 0f);
    }

    public static void setUploadProgress(String imageId, float p) {
        uploadProgressMap.put(imageId, Math.max(0f, Math.min(1f, p)));
    }

    public static void finishUpload(String imageId) {
        uploadInProgress.put(imageId, Boolean.FALSE);
        uploadProgressMap.remove(imageId);
    }

    public static boolean isGif(String imageId) {
        return gifFrames.containsKey(imageId);
    }

    public static GifFrameEntry getCurrentGifFrame(String imageId) {
        List<GifFrameEntry> frames = gifFrames.get(imageId);
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        long startMs = gifStartTime.computeIfAbsent(imageId, k -> System.currentTimeMillis());
        long totalDelay = 0L;
        for (GifFrameEntry f : frames) {
            totalDelay += f.delayMs;
        }
        if (totalDelay <= 0L) {
            return frames.get(frames.size() - 1);
        }
        long elapsed = (System.currentTimeMillis() - startMs) % totalDelay;
        long acc = 0L;
        for (GifFrameEntry frame : frames) {
            acc += frame.delayMs;
            if (elapsed < acc) {
                return frame;
            }
        }
        return frames.get(frames.size() - 1);
    }

    public static void markDeleted(String imageId) {
        markDeleted(imageId, false);
    }

    public static void markDeleted(String imageId, boolean byAdmin) {
        deletedFlags.put(imageId, true);
        deletedByAdminFlags.put(imageId, byAdmin);

        Identifier old = previewTexture.remove(imageId);
        if (old != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(old);
            } catch (Exception ignored) {
            }
        }
        loadedFlags.put(imageId, false);

        fullDataMap.remove(imageId);

        texSizes.remove(imageId);
        downloadState.remove(imageId);
        downloadProgress.remove(imageId);
        fileSizeBytes.remove(imageId);
        uploadInProgress.remove(imageId);
        uploadProgressMap.remove(imageId);

        List<GifFrameEntry> frames = gifFrames.remove(imageId);
        if (frames != null) {
            for (GifFrameEntry frame : frames) {
                try {
                    Minecraft.getInstance().getTextureManager().release(frame.getTextureId());
                } catch (Exception ignored) {
                }
            }
        }
        gifStartTime.remove(imageId);

        Thread thread = new Thread(() -> {
            try {
                ImageDiskCache.delete(imageId);
            } catch (Exception ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public static void markError(String imageId) {
        errorFlags.put(imageId, true);
        loadedFlags.put(imageId, false);
    }

    public static void storeFullData(String imageId, byte[] data) {
        fullDataMap.put(imageId, data);
    }

    public static void upgradeToHiRes(String imageId, Identifier hiResTextureId, int hiResW, int hiResH) {
        Identifier old = previewTexture.get(imageId);
        if (old != null && !old.equals(hiResTextureId)) {
            try {
                Minecraft.getInstance().getTextureManager().release(old);
            } catch (Exception ignored) {
            }
        }
        previewTexture.put(imageId, hiResTextureId);
        origSizes.put(imageId, new IntPair(hiResW, hiResH));

    }

    public static void loadAndUpgradeHiRes(String imageId, byte[] bytes, TriConsumer<Identifier, Integer, Integer> onDone) {
        Thread thread = new Thread(() -> {
            try {
                BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
                if (buffered == null) {
                    throw new Exception("Cannot decode");
                }
                int hiResW = buffered.getWidth();
                int hiResH = buffered.getHeight();

                ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                BufferedImage pngImg;
                if (buffered.getType() != BufferedImage.TYPE_INT_ARGB) {
                    BufferedImage c = new BufferedImage(hiResW, hiResH, BufferedImage.TYPE_INT_ARGB);
                    var g = c.createGraphics();
                    g.drawImage(buffered, 0, 0, null);
                    g.dispose();
                    pngImg = c;
                } else {
                    pngImg = buffered;
                }
                ImageIO.write(pngImg, "png", pngOut);

                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngOut.toByteArray()));
                Identifier loc = Identifier.fromNamespaceAndPath("chat-remastered", "hires_" + imageId);

                Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.getTextureManager().register(loc, new SmoothDynamicTexture(() -> "chat-remastered-hires", nativeImage));
                    upgradeToHiRes(imageId, loc, hiResW, hiResH);
                    if (onDone != null) {
                        onDone.accept(loc, hiResW, hiResH);
                    }
                });
            } catch (Exception e) {
                System.out.println("[Chat Remastered] loadAndUpgradeHiRes error " + imageId + ": " + e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public static void registerPlaceholder(String imageId, int dispW, int dispH) {
        sizes.put(imageId, new IntPair(dispW, dispH));
        origSizes.put(imageId, new IntPair(dispW, dispH));
        texSizes.put(imageId, new IntPair(dispW, dispH));
        loadedFlags.put(imageId, false);

    }

    public static IntPair computeDispSize(byte[] thumbnailData) {
        try {
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(thumbnailData));
            if (buffered == null) {
                return null;
            }
            Minecraft mc = Minecraft.getInstance();
            int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
            int maxDispW = Math.max(Math.min((int) ((chatWidthPx - 8) * 0.9), ChatRemasteredConfig.getPreviewMaxW()), 40);
            float chatScale = Math.max((float) (double) mc.options.chatScale().get(), 1f);
            int texW = buffered.getWidth();
            int texH = buffered.getHeight();
            int dispWraw = Math.max((int) (texW / chatScale), 1);
            int dispHraw = Math.max((int) (texH / chatScale), 1);
            int maxDispH = Math.max(Math.min(mc.getWindow().getGuiScaledHeight() / 3, ChatRemasteredConfig.getPreviewMaxH()), 40);
            float sW = dispWraw > maxDispW ? (float) maxDispW / dispWraw : 1f;
            float sH = (int) (dispHraw * sW) > maxDispH ? (float) maxDispH / dispHraw : sW;
            float s = Math.min(sW, sH);
            int dispW = Math.max((int) (dispWraw * s), 1);
            int dispH = Math.max((int) (dispHraw * s), 1);
            return new IntPair(dispW, dispH);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getAllLoadedIds() {
        return new ArrayList<>(fullDataMap.keySet());
    }

    public static void upgradeToFullTexture(Minecraft mc, String imageId, byte[] fullBytes) {
        if (Boolean.TRUE.equals(deletedFlags.get(imageId)) || Boolean.TRUE.equals(errorFlags.get(imageId))) {
            return;
        }
        try {
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(fullBytes));
            if (buffered == null) {
                return;
            }
            float chatScale = clamp((float) (double) mc.options.chatScale().get(), 0.01f, 1f);
            float guiScale = Math.max((float) mc.getWindow().getGuiScale(), 1f);
            IntPair size = sizes.get(imageId);
            if (size == null) {
                return;
            }
            int dispW = size.getFirst();
            int dispH = size.getSecond();

            int targetTexW = Math.max((int) (dispW * guiScale * chatScale), 1);
            int targetTexH = Math.max((int) (dispH * guiScale * chatScale), 1);

            float scaleX = (float) targetTexW / buffered.getWidth();
            float scaleY = (float) targetTexH / buffered.getHeight();
            float s = Math.min(scaleX, scaleY);
            int fitW = Math.max((int) (buffered.getWidth() * s), 1);
            int fitH = Math.max((int) (buffered.getHeight() * s), 1);

            BufferedImage scaled;
            if (fitW != buffered.getWidth() || fitH != buffered.getHeight()) {
                BufferedImage out = new BufferedImage(fitW, fitH, BufferedImage.TYPE_INT_RGB);
                var g = out.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(buffered, 0, 0, fitW, fitH, null);
                g.dispose();
                scaled = out;
            } else {
                scaled = buffered;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", bos);
            byte[] pngBytes = bos.toByteArray();

            Identifier old = previewTexture.get(imageId);
            if (old != null) {
                try {
                    mc.getTextureManager().release(old);
                } catch (Exception ignored) {
                }
            }

            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));
            Identifier loc = Identifier.fromNamespaceAndPath("chat-remastered", "full_" + imageId);
            mc.getTextureManager().register(loc, new SmoothDynamicTexture(() -> "chat-remastered-full-" + imageId, nativeImage));
            previewTexture.put(imageId, loc);
            texSizes.put(imageId, new IntPair(fitW, fitH));
            loadedFlags.put(imageId, true);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] upgradeToFullTexture error for " + imageId + ": " + e.getMessage());
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Identifier loc : previewTexture.values()) {
            try {
                mc.getTextureManager().release(loc);
            } catch (Exception ignored) {
            }
        }

        for (List<GifFrameEntry> frames : gifFrames.values()) {
            for (GifFrameEntry frame : frames) {
                try {
                    mc.getTextureManager().release(frame.textureId);
                } catch (Exception ignored) {
                }
            }
        }
        previewTexture.clear();
        sizes.clear();
        origSizes.clear();
        texSizes.clear();
        loadedFlags.clear();
        fullDataMap.clear();
        deletedFlags.clear();
        deletedByAdminFlags.clear();
        errorFlags.clear();
        gifFrames.clear();
        gifStartTime.clear();
        downloadState.clear();
        downloadProgress.clear();
        fileSizeBytes.clear();
        uploadInProgress.clear();
        uploadProgressMap.clear();
    }

    public static void loadGif(String imageId, byte[] data, int targetDispW, int targetDispH) {
        Thread thread = new Thread(() -> {
            try {
                List<GifDecoder.GifFrame> rawFrames = GifDecoder.decode(data);
                if (rawFrames.isEmpty()) {
                    System.out.println("[Chat Remastered] GIF decode returned 0 frames for " + imageId);
                    markError(imageId);
                    return;
                }

                Minecraft mc = Minecraft.getInstance();
                float guiScale;
                try {
                    guiScale = Math.max((float) mc.getWindow().getGuiScale(), 1f);
                } catch (Exception e) {
                    guiScale = 1f;
                }
                float chatScale = clamp((float) (double) mc.options.chatScale().get(), 0.01f, 1f);

                int targetTexW = Math.max((int) (targetDispW * guiScale * chatScale), 1);
                int targetTexH = Math.max((int) (targetDispH * guiScale * chatScale), 1);

                List<GifFrameEntry> loadedFrames = new ArrayList<>();

                for (int idx = 0; idx < rawFrames.size(); idx++) {
                    GifDecoder.GifFrame frame = rawFrames.get(idx);
                    int srcW = frame.getImage().getWidth();
                    int srcH = frame.getImage().getHeight();
                    float scaleX = (float) targetTexW / srcW;
                    float scaleY = (float) targetTexH / srcH;
                    float s = Math.min(scaleX, scaleY);

                    int fitW = Math.max((int) (srcW * s), 1);
                    int fitH = Math.max((int) (srcH * s), 1);

                    BufferedImage scaled;
                    if (fitW != srcW || fitH != srcH) {
                        BufferedImage out = new BufferedImage(fitW, fitH, BufferedImage.TYPE_INT_ARGB);
                        var g = out.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g.drawImage(frame.getImage(), 0, 0, fitW, fitH, null);
                        g.dispose();
                        scaled = out;
                    } else {
                        scaled = frame.getImage();
                    }

                    ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                    ImageIO.write(scaled, "png", pngOut);
                    byte[] pngBytes = pngOut.toByteArray();

                    AtomicReference<GifFrameEntry> frameEntry = new AtomicReference<>();
                    CountDownLatch latch = new CountDownLatch(1);
                    int finalFitW = fitW;
                    int finalFitH = fitH;
                    int finalIdx = idx;

                    mc.execute(() -> {
                        try {
                            NativeImage nativeImg = NativeImage.read(new ByteArrayInputStream(pngBytes));
                            Identifier loc = Identifier.fromNamespaceAndPath("chat-remastered", "gif_" + imageId + "_" + finalIdx);
                            mc.getTextureManager().register(loc, new SmoothDynamicTexture(() -> "chat-remastered-gif-" + imageId, nativeImg));
                            frameEntry.set(new GifFrameEntry(loc, finalFitW, finalFitH, frame.getDelayMs()));
                        } catch (Exception e) {
                            System.out.println("[Chat Remastered] GIF frame " + finalIdx + " register error: " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });

                    latch.await(3, TimeUnit.SECONDS);
                    GifFrameEntry entry = frameEntry.get();
                    if (entry != null) {
                        loadedFrames.add(entry);
                    }
                }

                if (loadedFrames.isEmpty()) {
                    markError(imageId);
                    return;
                }

                gifFrames.put(imageId, loadedFrames);
                gifStartTime.put(imageId, System.currentTimeMillis());
                GifFrameEntry firstFrame = loadedFrames.get(0);

                mc.execute(() -> {
                    previewTexture.put(imageId, firstFrame.textureId);
                    origSizes.put(imageId, new IntPair(firstFrame.width, firstFrame.height));
                    texSizes.put(imageId, new IntPair(firstFrame.width, firstFrame.height));
                    loadedFlags.put(imageId, true);
                });

                System.out.println("[Chat Remastered] GIF loaded: " + imageId + " — " + loadedFrames.size() + " frames");
            } catch (Exception e) {
                System.out.println("[Chat Remastered] loadGif error " + imageId + ": " + e.getMessage());
                markError(imageId);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
