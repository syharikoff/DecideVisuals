package ru.white.utils.render.post;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import ru.white.utils.render.voronoi.VoronoiOfQuad;

public final class GuiShatterAnimation {
    public static final int MAX_SHARDS = 28;
    public static final int VERTEX_BYTES = 24;
    private static final int MAX_POLY_VERTICES = 24;
    public static final int MAX_VERTICES = 1848;
    private static final int MIN_COUNT = 22;
    private static final int MAX_COUNT = 28;
    private static final float RELAX_STRENGTH = 0.45F;
    private static final float SEED_JITTER = 0.32F;
    private static final float MIN_DISTANCE = 0.68F;
    private static final float EXPAND = 0.34F;
    private static final float SPREAD = 0.028F;
    private static final float MARGIN = 0.004F;
    private static final float CURL_DEGREES = 26.0F;
    private static final float CURL_DEPTH = 0.1F;
    private static final float SCREEN_PERSPECTIVE = 0.9F;
    private static final long GATHER_NS = 360000000L;
    private static final float MAX_BLUR_RADIUS = 9.0F;
    private static final Random RANDOM = new Random();
    private static final State LOCAL = new State();

    private GuiShatterAnimation() {}

    public static void begin(float x, float y, float w, float h, float pad, float sw, float sh, long seed) { LOCAL.begin(x, y, w, h, pad, sw, sh, seed); }
    public static void begin(float x, float y, float w, float h, float pad, float sw, float sh) { LOCAL.begin(x, y, w, h, pad, sw, sh, RANDOM.nextLong()); }
    public static void cancel() { LOCAL.cancel(); }
    public static State create() { return new State(); }
    public static boolean isActive() { return LOCAL.isActive(); }
    public static long seed() { return LOCAL.seed(); }
    public static State local() { return LOCAL; }
    public static void gather(long ns) { LOCAL.gather(ns); }
    public static float clamp01(float f) { return Math.max(0.0F, Math.min(1.0F, f)); }
    public static float blurRadius() { return LOCAL.blurRadius(); }
    public static float[] beginRect() { return LOCAL.beginRect(); }
    public static boolean resume() { return LOCAL.resume(); }
    public static float progress(float f) { return LOCAL.progress(f); }
    public static int buildGeometry(ByteBuffer buf, float p, float sw, float sh, boolean flag) { return LOCAL.buildGeometry(buf, p, sw, sh, flag, null); }
    public static int buildGeometry(ByteBuffer buf, float p, float sw, float sh, boolean flag, Remap remap) { return LOCAL.buildGeometry(buf, p, sw, sh, flag, remap); }
    public static float clampSigned(float f) { return Math.max(-1.0F, Math.min(1.0F, f)); }
    public static boolean isGathering() { return LOCAL.isGathering(); }

    public record Remap(float posOffsetX, float posOffsetY, float posScaleX, float posScaleY, float uvOffsetX, float uvOffsetY, float uvScaleX, float uvScaleY) {}

    public static final class State {
        private final List<VoronoiOfQuad.Polygon> shards = new ArrayList<>();
        private float[] baseAngle = new float[0];
        private float[] baseRadius = new float[0];
        private float[] fade = new float[0];
        private float[] radial = new float[0];
        private boolean gathering;
        private long gatherStartNs;
        private long gatherNanos = 360000000L;
        private float gatherFrom;
        private float resumeFrom;
        private float lastProgress;
        private float centerX;
        private float centerY;
        private float halfExtentX = 1.0F;
        private float halfExtentY = 1.0F;
        private float aspect = 1.0F;
        private boolean active;
        private long currentSeed;
        private float beginX;
        private float beginY;
        private float beginWidth;
        private float beginHeight;
        private float beginPad;
        private float beginScreenWidth;
        private float beginScreenHeight;

        public State() {}

        public void begin(float x, float y, float w, float h, float pad, float sw, float sh, long seed) {
            this.cancel();
            if (w <= 1.0F || h <= 1.0F || sw <= 1.0F || sh <= 1.0F) return;
            Random random = new Random(seed);
            this.currentSeed = seed;
            this.beginX = x;
            this.beginY = y;
            this.beginWidth = w;
            this.beginHeight = h;
            this.beginPad = pad;
            this.beginScreenWidth = sw;
            this.beginScreenHeight = sh;
            float nx1 = GuiShatterAnimation.clamp01(x / sw);
            float ny1 = GuiShatterAnimation.clamp01(y / sh);
            float nx2 = GuiShatterAnimation.clamp01((x + w) / sw);
            float ny2 = GuiShatterAnimation.clamp01((y + h) / sh);
            this.centerX = (nx1 + nx2) * 0.5F;
            this.centerY = (ny1 + ny2) * 0.5F;
            this.halfExtentX = Math.max(0.001F, (nx2 - nx1) * 0.5F);
            this.halfExtentY = Math.max(0.001F, (ny2 - ny1) * 0.5F);
            this.aspect = Math.max(0.001F, sw / sh);
            int count = 22 + random.nextInt(7);
            VoronoiOfQuad voronoi = VoronoiOfQuad.spread(
                nx1, ny1, nx2, ny2,
                nx1 - 0.004F, ny1 - 0.004F, nx2 + 0.004F, ny2 + 0.004F,
                count, this.aspect, 0.45F, 0.32F, 0.68F, random
            );
            this.shards.clear();
            this.shards.addAll(voronoi.getPolygons());
            int n = this.shards.size();
            this.baseAngle = new float[n];
            this.baseRadius = new float[n];
            this.fade = new float[n];
            this.radial = new float[n];
            for (int i = 0; i < n; i++) {
                VoronoiOfQuad.Polygon poly = this.shards.get(i);
                float dx = (poly.center.x - this.centerX) * this.aspect;
                float dy = poly.center.y - this.centerY;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                this.baseAngle[i] = (float)Math.atan2(dy, dx) + (random.nextFloat() - 0.5F) * 0.35F;
                this.baseRadius[i] = dist;
                this.fade[i] = 0.55F + 0.45F * random.nextFloat();
                this.radial[i] = 0.65F + 0.7F * random.nextFloat();
            }
            this.active = true;
        }

        public void cancel() {
            this.active = false;
            this.gathering = false;
            this.lastProgress = 0.0F;
            this.shards.clear();
        }

        public boolean isActive() { return this.active; }
        public long seed() { return this.currentSeed; }

        public void gather(long ns) {
            if (this.active) {
                this.gathering = true;
                this.gatherStartNs = System.nanoTime();
                this.gatherNanos = Math.max(1000000L, ns);
                this.gatherFrom = this.lastProgress;
            }
        }

        public boolean isGathering() { return this.active && this.gathering; }

        public float blurRadius() { return !this.active ? 0.0F : 9.0F * (1.0F - this.lastProgress); }
        public float lastProgress() { return this.lastProgress; }

        public float[] beginRect() {
            return new float[]{this.beginX, this.beginY, this.beginWidth, this.beginHeight, this.beginPad, this.beginScreenWidth, this.beginScreenHeight};
        }

        public boolean resume() {
            if (!this.active) return false;
            this.gathering = false;
            this.resumeFrom = this.lastProgress;
            return true;
        }

        public float progress(float f) {
            if (!this.active) return 0.0F;
            if (this.gathering) {
                long elapsed = System.nanoTime() - this.gatherStartNs;
                float t = GuiShatterAnimation.clamp01((float)elapsed / (float)this.gatherNanos);
                this.lastProgress = this.gatherFrom * (1.0F - easeOutCubic(t));
                if (t >= 1.0F) { this.cancel(); return 0.0F; }
                return this.lastProgress;
            }
            this.lastProgress = f;
            return f;
        }

        public int buildGeometry(ByteBuffer buf, float progressVal, float sw, float sh, boolean flag, Remap remap) {
            if (!this.active || this.shards.isEmpty()) return 0;
            buf.clear();
            int count = 0;
            float p = this.progress(progressVal);
            float ease = easeOutCubic(p);
            for (int i = 0; i < this.shards.size(); i++) {
                VoronoiOfQuad.Polygon poly = this.shards.get(i);
                List<VoronoiOfQuad.Vec2f> verts = poly.getAllVertices();
                if (verts.size() >= 3) {
                    float ang = this.baseAngle[i];
                    float rad = this.baseRadius[i] + ease * 0.34F * this.radial[i];
                    float ox = this.centerX + (float)Math.cos(ang) * rad / this.aspect - poly.center.x;
                    float oy = this.centerY + (float)Math.sin(ang) * rad - poly.center.y;
                    float alpha = 1.0F - ease * this.fade[i];
                    if (alpha <= 0.0F) continue;
                    VoronoiOfQuad.Vec2f v0 = verts.get(0);
                    for (int j = 1; j < verts.size() - 1; j++) {
                        VoronoiOfQuad.Vec2f v1 = verts.get(j);
                        VoronoiOfQuad.Vec2f v2 = verts.get(j + 1);
                        putVertex(buf, v0, ox, oy, alpha, sw, sh, remap);
                        putVertex(buf, v1, ox, oy, alpha, sw, sh, remap);
                        putVertex(buf, v2, ox, oy, alpha, sw, sh, remap);
                        count += 3;
                    }
                }
            }
            buf.flip();
            return count;
        }

        private void putVertex(ByteBuffer buf, VoronoiOfQuad.Vec2f vec, float ox, float oy, float alpha, float sw, float sh, Remap remap) {
            float px = (vec.x + ox) * sw;
            float py = (vec.y + oy) * sh;
            float u = vec.x;
            float v = vec.y;
            if (remap != null) {
                px = remap.posOffsetX() + px * remap.posScaleX();
                py = remap.posOffsetY() + py * remap.posScaleY();
                u = remap.uvOffsetX() + u * remap.uvScaleX();
                v = remap.uvOffsetY() + v * remap.uvScaleY();
            }
            buf.putFloat(px);
            buf.putFloat(py);
            buf.putFloat(0.0F);
            buf.putFloat(u);
            buf.putFloat(v);
            buf.putFloat(alpha);
        }
    }

    private static float easeOutCubic(float f) {
        float g = 1.0F - f;
        return 1.0F - g * g * g;
    }
}
