package dev.errnicraft.chatremastered;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PendingCardAnimator {

    public static final long FALL_DELAY_MS = 1400L;
    public static final long SPAWN_MS = 600L;

    public static final long SHATTER_MS = 1200L;

    private static final float SLIDE_SPEED = 0.045f;

    public static final class SpawnState {
        public final long startMs = System.currentTimeMillis();

        public float progress() {
            long elapsed = System.currentTimeMillis() - startMs;
            return Math.min(1f, elapsed / (float) SPAWN_MS);
        }

        public boolean isDone() {
            return progress() >= 1f;
        }

        public float scale() {
            float p = progress();
            return p * p * (3f - 2f * p);
        }
    }

    public static final class Shard {
        public final int minX, minY, maxX, maxY;
        public final boolean[] mask;
        public final int maskW, maskH;
        public final float launchVX;
        public final float launchVY;
        public final float spinSpeed;
        public final float delayFrac;

        public Shard(int minX, int minY, int maxX, int maxY, boolean[] mask,
                     float launchVX, float launchVY, float spinSpeed, float delayFrac) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
            this.maskW = maxX - minX;
            this.maskH = maxY - minY;
            this.mask = mask;
            this.launchVX = launchVX; this.launchVY = launchVY;
            this.spinSpeed = spinSpeed;
            this.delayFrac = delayFrac;
        }
    }

    public static final class RemoveState {
        public final long uid;
        public final int cardX;
        public final int cardTop;
        public final int cardW;
        public final int cardH;
        public final long startMs = System.currentTimeMillis();

        public boolean actuallyRemoved = false;

        public final float driftX;

        public final float spinSpeed;

        public final Shard[] shards;

        public final Identifier tex;
        public final int texW;
        public final int texH;
        public final boolean isLoaded;
        public final boolean sizeKnown;
        public final float progressSnapshot;

        public RemoveState(long uid, int cardX, int cardTop, int cardW, int cardH,
                            Identifier tex, int texW, int texH, boolean isLoaded,
                            boolean sizeKnown, float progressSnapshot, int animMode) {
            this.uid = uid;
            this.cardX = cardX;
            this.cardTop = cardTop;
            this.cardW = cardW;
            this.cardH = cardH;
            this.driftX = (float) ((Math.random() - 0.5) * 2.0) * 90f;
            this.spinSpeed = (float) ((Math.random() - 0.5) * 2.0) * 260f;
            this.tex = tex;
            this.texW = texW;
            this.texH = texH;
            this.isLoaded = isLoaded;
            this.sizeKnown = sizeKnown;
            this.progressSnapshot = progressSnapshot;
            this.shards = animMode == 1 ? cr$buildShards(cardW, cardH, SEED_STEP_SMALL)
                    : animMode == 2 ? cr$buildShards(cardW, cardH, SEED_STEP_LARGE)
                    : null;
        }

        private static final float SEED_STEP_SMALL = 5f;
        private static final float SEED_STEP_LARGE = SEED_STEP_SMALL * 3f;

        private static Shard[] cr$buildShards(int cardW, int cardH, float seedStep) {
            int seedCount = Math.max(4, Math.min(400, Math.round((cardW * cardH) / (seedStep * seedStep))));
            float[] seedX = new float[seedCount];
            float[] seedY = new float[seedCount];
            for (int i = 0; i < seedCount; i++) {
                seedX[i] = (float) (Math.random() * cardW);
                seedY[i] = (float) (Math.random() * cardH);
            }

            int[] owner = new int[cardW * cardH];
            for (int y = 0; y < cardH; y++) {
                for (int x = 0; x < cardW; x++) {
                    int best = 0;
                    float bestDist = Float.MAX_VALUE;
                    for (int i = 0; i < seedCount; i++) {
                        float dx = x + 0.5f - seedX[i];
                        float dy = y + 0.5f - seedY[i];
                        float d = dx * dx + dy * dy;
                        if (d < bestDist) {
                            bestDist = d;
                            best = i;
                        }
                    }
                    owner[y * cardW + x] = best;
                }
            }

            java.util.List<Shard> out = new java.util.ArrayList<>();
            for (int i = 0; i < seedCount; i++) {
                int minX = cardW, minY = cardH, maxX = 0, maxY = 0;
                boolean any = false;
                for (int y = 0; y < cardH; y++) {
                    for (int x = 0; x < cardW; x++) {
                        if (owner[y * cardW + x] == i) {
                            any = true;
                            if (x < minX) minX = x;
                            if (y < minY) minY = y;
                            if (x + 1 > maxX) maxX = x + 1;
                            if (y + 1 > maxY) maxY = y + 1;
                        }
                    }
                }
                if (!any) continue;

                int w = maxX - minX;
                int h = maxY - minY;
                boolean[] mask = new boolean[w * h];
                for (int y = minY; y < maxY; y++) {
                    for (int x = minX; x < maxX; x++) {
                        if (owner[y * cardW + x] == i) {
                            mask[(y - minY) * w + (x - minX)] = true;
                        }
                    }
                }

                float centerX = (minX + maxX) / 2f;
                float centerY = (minY + maxY) / 2f;
                float dx = centerX - cardW / 2f;
                float dy = centerY - cardH / 2f;
                float len = Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy));

                float horizSpeed = 30f + (float) Math.random() * 260f;
                float vx = (dx / len) * horizSpeed;
                float vy = -(100f + (float) Math.random() * 220f);
                float spin = (float) ((Math.random() - 0.5) * 2.0) * 640f;
                float delay = (float) Math.random() * 0.18f;

                out.add(new Shard(minX, minY, maxX, maxY, mask, vx, vy, spin, delay));
            }
            return out.toArray(new Shard[0]);
        }
        public long elapsedMs() {
            return System.currentTimeMillis() - startMs;
        }

        public boolean spaceFreed() {
            return elapsedMs() >= (shards != null ? SHATTER_MS : FALL_DELAY_MS);
        }

        public boolean isDone(int screenH) {
            if (shards != null) {
                return elapsedMs() >= SHATTER_MS;
            }
            return currentY(screenH) > screenH + cardH + 400;
        }

        private float shardLocalT(Shard s) {
            float t = elapsedMs() / 1000f;
            float delaySec = s.delayFrac * (SHATTER_MS / 1000f);
            return Math.max(0f, t - delaySec);
        }

        public float shardOffsetX(Shard s) {
            float t = shardLocalT(s);
            return s.launchVX * t;
        }

        public float shardOffsetY(Shard s) {
            float t = shardLocalT(s);
            float gravity = 900f;
            return s.launchVY * t + 0.5f * gravity * t * t;
        }

        public float shardRotation(Shard s) {
            float t = shardLocalT(s);
            return s.spinSpeed * t;
        }

        public float shardAlpha(Shard s) {
            float t = elapsedMs() / (float) SHATTER_MS;
            if (t < 0.55f) return 1f;
            return Math.max(0f, 1f - (t - 0.55f) / 0.45f);
        }

        public int currentY(int screenH) {
            float t = elapsedMs() / 1000f;
            float upMs = 0.18f;
            float upHeight = 30f;
            float y;
            if (t < upMs) {
                float p = t / upMs;
                float ease = 1f - (1f - p) * (1f - p);
                y = -upHeight * ease;
            } else {
                float ft = t - upMs;
                float peakVelocity = -upHeight / upMs * 0.4f;
                float gravity = 900f;
                y = -upHeight + peakVelocity * ft + 0.5f * gravity * ft * ft;
            }
            return cardTop + Math.round(y);
        }

        public int currentX() {
            float t = Math.min(1f, elapsedMs() / 700f);
            float ease = t * t;
            return cardX + Math.round(driftX * ease);
        }

        public float currentRotation() {
            float t = elapsedMs() / 1000f;
            return spinSpeed * t;
        }

        public float alpha(int screenH) {
            int y = currentY(screenH);
            if (y < screenH - 20) return 1f;
            float fade = (screenH - y) / 20f;
            return Math.max(0f, Math.min(1f, fade));
        }
    }

    private final List<RemoveState> removals = new ArrayList<>();
    private final Map<Long, SpawnState> spawns = new HashMap<>();
    private final java.util.Set<Long> knownUids = new java.util.HashSet<>();

    private final Map<Long, Float> smoothX = new HashMap<>();

    public void syncSpawns(List<PendingImageState.PendingImage> current) {
        java.util.Set<Long> currentUids = new java.util.HashSet<>();
        for (PendingImageState.PendingImage p : current) {
            long uid = p.getUid();
            currentUids.add(uid);
            if (!knownUids.contains(uid)) {
                spawns.put(uid, new SpawnState());
            }
        }
        knownUids.clear();
        knownUids.addAll(currentUids);
        spawns.keySet().removeIf(u -> !currentUids.contains(u));
        smoothX.keySet().removeIf(u -> !currentUids.contains(u));
    }

    public SpawnState getSpawn(long uid) {
        return spawns.get(uid);
    }

    public int peekSmoothX(long uid, int fallbackX) {
        Float cur = smoothX.get(uid);
        return cur == null ? fallbackX : Math.round(cur);
    }

    public int smoothX(long uid, int targetX) {
        Float cur = smoothX.get(uid);
        if (cur == null) {
            smoothX.put(uid, (float) targetX);
            return targetX;
        }
        float next = cur + (targetX - cur) * SLIDE_SPEED;
        if (Math.abs(targetX - next) < 0.5f) {
            next = targetX;
        }
        smoothX.put(uid, next);
        return Math.round(next);
    }

    public void startRemoval(long uid, int cardX, int cardTop, int cardW, int cardH,
                              Identifier tex, int texW, int texH, boolean isLoaded,
                              boolean sizeKnown, float progressSnapshot) {
        if (isFlying(uid)) return;
        int animMode = ChatRemasteredConfig.getRemoveAnimMode();
        removals.add(new RemoveState(uid, cardX, cardTop, cardW, cardH, tex, texW, texH, isLoaded, sizeKnown, progressSnapshot, animMode));
        knownUids.remove(uid);
        spawns.remove(uid);
        smoothX.remove(uid);
    }

    public List<RemoveState> getActiveRemovals(int screenH) {
        tickRemovals();
        removals.removeIf(r -> {
            if (!r.actuallyRemoved) {
                r.actuallyRemoved = true;
                PendingImageState.removeByUidKeepTexture(r.uid);
            }
            boolean done = r.isDone(screenH);
            if (done) {

                PendingImageState.releaseTextureIfUnused(r.tex);
            }
            return done;
        });
        return removals;
    }

    public boolean isFlying(long uid) {
        for (RemoveState r : removals) {
            if (r.uid == uid) return true;
        }
        return false;
    }

    public void tickRemovals() {
        for (RemoveState r : removals) {
            if (!r.actuallyRemoved && r.spaceFreed()) {
                r.actuallyRemoved = true;
                PendingImageState.removeByUidKeepTexture(r.uid);
            }
        }
    }

    public void clearAll() {
        removals.clear();
        spawns.clear();
        knownUids.clear();
        smoothX.clear();
    }
}
