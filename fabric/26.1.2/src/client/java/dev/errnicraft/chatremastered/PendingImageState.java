package dev.errnicraft.chatremastered;

import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PendingImageState {

    public static final class PendingImage {
        private static final java.util.concurrent.atomic.AtomicLong UID_GEN = new java.util.concurrent.atomic.AtomicLong(1);

        private final long uid;
        private final File file;
        private final Identifier textureId;
        private final int width;
        private final int height;
        private final int textureWidth;
        private final int textureHeight;
        private final byte[] previewBytes;
        private final byte[] rawBytes;
        private final boolean rawReady;
        private final int origWidth;
        private final int origHeight;
        private final boolean sizeKnown;

        private final float progress;

        public PendingImage(File file, Identifier textureId, int width, int height,
                             int textureWidth, int textureHeight, byte[] previewBytes, byte[] rawBytes,
                             boolean rawReady, int origWidth, int origHeight, boolean sizeKnown) {
            this(UID_GEN.getAndIncrement(), file, textureId, width, height, textureWidth, textureHeight,
                    previewBytes, rawBytes, rawReady, origWidth, origHeight, sizeKnown, -1f);
        }

        public PendingImage(File file, Identifier textureId, int width, int height,
                             int textureWidth, int textureHeight, byte[] previewBytes, byte[] rawBytes,
                             boolean rawReady, int origWidth, int origHeight, boolean sizeKnown, float progress) {
            this(UID_GEN.getAndIncrement(), file, textureId, width, height, textureWidth, textureHeight,
                    previewBytes, rawBytes, rawReady, origWidth, origHeight, sizeKnown, progress);
        }

        private PendingImage(long uid, File file, Identifier textureId, int width, int height,
                              int textureWidth, int textureHeight, byte[] previewBytes, byte[] rawBytes,
                              boolean rawReady, int origWidth, int origHeight, boolean sizeKnown, float progress) {
            this.uid = uid;
            this.file = file;
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.previewBytes = previewBytes;
            this.rawBytes = rawBytes;
            this.rawReady = rawReady;
            this.origWidth = origWidth;
            this.origHeight = origHeight;
            this.sizeKnown = sizeKnown;
            this.progress = progress;
        }

        public long getUid() {
            return uid;
        }

        public PendingImage withTexture(Identifier textureId, int textureWidth, int textureHeight,
                                         byte[] previewBytes, byte[] rawBytes) {
            byte[] newRaw = (rawBytes != null && rawBytes.length > 0) ? rawBytes : this.rawBytes;
            boolean newRawReady = this.rawReady || (rawBytes != null && rawBytes.length > 0);
            return new PendingImage(this.uid, this.file, textureId, this.width, this.height,
                    textureWidth, textureHeight, previewBytes, newRaw,
                    newRawReady, this.origWidth, this.origHeight, this.sizeKnown, this.progress);
        }

        public PendingImage withProgress(float newProgress) {
            return new PendingImage(this.uid, this.file, this.textureId, this.width, this.height,
                    this.textureWidth, this.textureHeight, this.previewBytes, this.rawBytes,
                    this.rawReady, this.origWidth, this.origHeight, this.sizeKnown, newProgress);
        }

        public PendingImage withSize(int newWidth, int newHeight, int newOrigWidth, int newOrigHeight, boolean newSizeKnown) {
            return new PendingImage(this.uid, this.file, this.textureId, newWidth, newHeight,
                    this.textureWidth, this.textureHeight, this.previewBytes, this.rawBytes,
                    this.rawReady, newOrigWidth, newOrigHeight, newSizeKnown, this.progress);
        }

        public PendingImage withRawBytesOnly(byte[] newRawBytes, boolean newRawReady) {
            return new PendingImage(this.uid, this.file, this.textureId, this.width, this.height,
                    this.textureWidth, this.textureHeight, this.previewBytes, newRawBytes,
                    newRawReady, this.origWidth, this.origHeight, this.sizeKnown, this.progress);
        }

        public PendingImage withRawBytesAndOrig(byte[] newRawBytes, boolean newRawReady, int newOrigWidth, int newOrigHeight) {
            return new PendingImage(this.uid, this.file, this.textureId, this.width, this.height,
                    this.textureWidth, this.textureHeight, this.previewBytes, newRawBytes,
                    newRawReady, newOrigWidth, newOrigHeight, this.sizeKnown, this.progress);
        }

        public PendingImage withFile(File newFile, int newOrigWidth, int newOrigHeight) {
            return new PendingImage(this.uid, newFile, this.textureId, this.width, this.height,
                    this.textureWidth, this.textureHeight, this.previewBytes, this.rawBytes,
                    this.rawReady, newOrigWidth, newOrigHeight, this.sizeKnown, this.progress);
        }

        public float getProgress() {
            return progress;
        }

        public File getFile() {
            return file;
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

        public int getTextureWidth() {
            return textureWidth;
        }

        public int getTextureHeight() {
            return textureHeight;
        }

        public byte[] getPreviewBytes() {
            return previewBytes;
        }

        public byte[] getRawBytes() {
            return rawBytes;
        }

        public boolean isRawReady() {
            return rawReady;
        }

        public boolean getRawReady() {
            return rawReady;
        }

        public int getOrigWidth() {
            return origWidth;
        }

        public int getOrigHeight() {
            return origHeight;
        }

        public boolean isSizeKnown() {
            return sizeKnown;
        }

        public boolean getSizeKnown() {
            return sizeKnown;
        }

        public boolean isLoaded() {
            return textureId != null;
        }

        public boolean canSend() {
            return rawReady && rawBytes.length > 0;
        }
    }

    private static final List<PendingImage> _queue = Collections.synchronizedList(new ArrayList<>());

    public static volatile float uploadProgress = -1f;

    private static final List<AtomicBoolean> _cancelTokens = Collections.synchronizedList(new ArrayList<>());

    private PendingImageState() {
    }

    public static AtomicBoolean newCancelToken() {
        AtomicBoolean token = new AtomicBoolean(false);
        _cancelTokens.add(token);
        return token;
    }

    public static boolean isCancelled(AtomicBoolean token) {
        return token.get();
    }

    public static PendingImage getPending() {
        synchronized (_queue) {
            return _queue.isEmpty() ? null : _queue.get(_queue.size() - 1);
        }
    }

    public static int indexOfFile(File file) {
        synchronized (_queue) {
            for (int i = 0; i < _queue.size(); i++) {
                PendingImage img = _queue.get(i);
                if (img != null && img.getFile() != null && img.getFile().equals(file)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static int indexOfUid(long uid) {
        synchronized (_queue) {
            for (int i = 0; i < _queue.size(); i++) {
                PendingImage img = _queue.get(i);
                if (img != null && img.getUid() == uid) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static PendingImage getAt(int index) {
        synchronized (_queue) {
            if (index < 0 || index >= _queue.size()) {
                return null;
            }
            return _queue.get(index);
        }
    }

    public static PendingImage getByUid(long uid) {
        synchronized (_queue) {
            int idx = indexOfUid(uid);
            return idx >= 0 ? _queue.get(idx) : null;
        }
    }

    public static void setPending(PendingImage img) {
        synchronized (_queue) {
            releaseAllTextures();
            _queue.clear();
            if (img != null) {
                _queue.add(img);
            } else {
                uploadProgress = -1f;
            }
        }
    }

    public static boolean addPending(PendingImage img, int maxCount) {
        synchronized (_queue) {
            if (_queue.size() >= Math.max(1, maxCount)) {
                return false;
            }
            _queue.add(img);
            return true;
        }
    }

    public static List<PendingImage> getAll() {
        synchronized (_queue) {
            return new ArrayList<>(_queue);
        }
    }

    public static int size() {
        synchronized (_queue) {
            return _queue.size();
        }
    }

    public static boolean isEmpty() {
        synchronized (_queue) {
            return _queue.isEmpty();
        }
    }

    public static void replaceAt(int index, PendingImage img) {
        synchronized (_queue) {
            if (index >= 0 && index < _queue.size()) {
                PendingImage old = _queue.get(index);
                if (old != null && old.getTextureId() != null
                        && (img == null || img.getTextureId() != old.getTextureId())) {
                    releaseTexture(old.getTextureId());
                }
                _queue.set(index, img);
            }
        }
    }

    public static void replaceByUid(long uid, PendingImage img) {
        synchronized (_queue) {
            int index = indexOfUid(uid);
            if (index < 0) {
                return;
            }
            PendingImage old = _queue.get(index);
            if (old != null && old.getTextureId() != null
                    && (img == null || img.getTextureId() != old.getTextureId())) {
                releaseTexture(old.getTextureId());
            }
            _queue.set(index, img);
        }
    }

    public static void removeAt(int index) {
        synchronized (_queue) {
            if (index >= 0 && index < _queue.size()) {
                PendingImage img = _queue.remove(index);
                if (img != null && img.getTextureId() != null) {
                    releaseTexture(img.getTextureId());
                }
            }
            if (_queue.isEmpty()) {
                uploadProgress = -1f;
            }
        }
    }

    public static void removeByUid(long uid) {
        synchronized (_queue) {
            int index = indexOfUid(uid);
            if (index >= 0) {
                PendingImage img = _queue.remove(index);
                if (img != null && img.getTextureId() != null) {
                    releaseTexture(img.getTextureId());
                }
            }
            if (_queue.isEmpty()) {
                uploadProgress = -1f;
            }
        }
    }

    public static void removeByUidKeepTexture(long uid) {
        synchronized (_queue) {
            int index = indexOfUid(uid);
            if (index >= 0) {
                _queue.remove(index);
            }
            if (_queue.isEmpty()) {
                uploadProgress = -1f;
            }
        }
    }

    public static void clear() {
        synchronized (_cancelTokens) {
            for (AtomicBoolean token : _cancelTokens) {
                if (token != null) token.set(true);
            }
            _cancelTokens.clear();
        }
        synchronized (_queue) {
            releaseAllTextures();
            _queue.clear();
        }
        uploadProgress = -1f;
    }

    private static void releaseAllTextures() {
        for (PendingImage img : _queue) {
            if (img != null && img.getTextureId() != null) {
                releaseTexture(img.getTextureId());
            }
        }
    }

    private static void releaseTexture(Identifier textureId) {
        try {
            net.minecraft.client.Minecraft.getInstance().getTextureManager().release(textureId);
        } catch (Exception ignored) {
        }
    }

    public static void releaseTextureIfUnused(Identifier textureId) {
        if (textureId == null) return;
        synchronized (_queue) {
            for (PendingImage img : _queue) {
                if (img != null && textureId.equals(img.getTextureId())) {
                    return;
                }
            }
        }
        releaseTexture(textureId);
    }

    public static void updateTexture(Identifier textureId, int textureWidth, int textureHeight,
                                      byte[] previewBytes, byte[] rawBytes) {
        synchronized (_queue) {
            if (_queue.isEmpty()) {
                return;
            }
            int idx = _queue.size() - 1;
            PendingImage cur = _queue.get(idx);
            _queue.set(idx, cur.withTexture(textureId, textureWidth, textureHeight, previewBytes, rawBytes));
        }
    }

    public static void updateTextureAt(int index, Identifier textureId, int textureWidth, int textureHeight,
                                        byte[] previewBytes, byte[] rawBytes) {
        synchronized (_queue) {
            if (index < 0 || index >= _queue.size()) {
                return;
            }
            PendingImage cur = _queue.get(index);
            _queue.set(index, cur.withTexture(textureId, textureWidth, textureHeight, previewBytes, rawBytes));
        }
    }

    public static void updateTextureForUid(long uid, Identifier textureId, int textureWidth, int textureHeight,
                                            byte[] previewBytes, byte[] rawBytes) {
        synchronized (_queue) {
            int idx = indexOfUid(uid);
            if (idx < 0) {
                return;
            }
            PendingImage cur = _queue.get(idx);
            _queue.set(idx, cur.withTexture(textureId, textureWidth, textureHeight, previewBytes, rawBytes));
        }
    }

    public static void setProgress(float value) {
        if (value < 0f) {
            uploadProgress = -1f;
        } else {
            uploadProgress = Math.max(0f, Math.min(1f, value));
        }
    }

    public static void setProgressForFile(File file, float value) {
        if (file == null) {
            return;
        }
        float clamped = value < 0f ? -1f : Math.max(0f, Math.min(1f, value));
        synchronized (_queue) {
            int idx = indexOfFile(file);
            if (idx >= 0) {
                PendingImage cur = _queue.get(idx);
                if (cur != null) {
                    _queue.set(idx, cur.withProgress(clamped));
                }
            }
        }
    }

    public static void setProgressForUid(long uid, float value) {
        float clamped = value < 0f ? -1f : Math.max(0f, Math.min(1f, value));
        synchronized (_queue) {
            int idx = indexOfUid(uid);
            if (idx >= 0) {
                PendingImage cur = _queue.get(idx);
                if (cur != null) {
                    _queue.set(idx, cur.withProgress(clamped));
                }
            }
        }
    }

}
