package ru.decide.utils.render.killeffect;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Режим Kill Effect «Осколки».
 *
 * На месте убитой цели вспыхивает разлёт осколочных линий: сначала расходятся
 * кольца и вертикальный столб света, затем осколки улетают с гравитацией и
 * затягиваются в вихрь, в конце — схлопывающееся кольцо.
 * Всё рисуется одним слоем QUADS: кольца — плоские кольца, линии — билборд-квады.
 */
public final class ShardsEffect {

    private static final int[] RING_COUNTS = {10, 16, 14, 8};
    private static final float[] RING_HEIGHTS = {0.2F, 0.7F, 1.3F, 1.9F};
    private static final int[] RING_HUES = {0, 20, -20, 40};

    private static final RenderPipeline SHARDS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("decide", "kill_effect_shards"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final ShardsEffect INSTANCE = new ShardsEffect();

    private final BufferAllocator allocator = new BufferAllocator(1 << 16);
    private final List<Burst> bursts = new ArrayList<>();
    private final Random random = new Random();

    private VertexConsumerProvider.Immediate provider;
    private RenderLayer cachedLayer;

    private ShardsEffect() {
    }

    public static ShardsEffect get() {
        return INSTANCE;
    }

    public synchronized void spawn(Vec3d pos, Settings settings) {
        if (pos == null || settings == null) return;

        float density = Math.max(0.1F, settings.density());
        List<Shard> shards = new ArrayList<>();

        for (int r = 0; r < RING_COUNTS.length; r++) {
            int count = Math.max(2, Math.round(RING_COUNTS[r] * density));
            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2) / count * i + random.nextDouble() * 0.5;
                float speed = 1.2F + (float) random.nextDouble() * 2.0F;
                double yVel = -0.1 + random.nextDouble() * 0.8;
                Vec3d dir = new Vec3d(Math.cos(angle), yVel, Math.sin(angle)).normalize();
                float rotSpeed = (float) (random.nextDouble() * 8.0 - 4.0);
                float length = 0.2F + (float) random.nextDouble() * 0.6F;
                float grav = 0.5F + (float) random.nextDouble() * 1.0F;
                shards.add(new Shard(
                        pos.add(0.0, RING_HEIGHTS[r], 0.0),
                        dir, speed, rotSpeed, length, grav, RING_HUES[r]));
            }
        }

        bursts.add(new Burst(pos, System.currentTimeMillis(), shards));
    }

    public synchronized void clear() {
        bursts.clear();
    }

    public synchronized void render(MatrixStack matrices, Vec3d cam, Settings settings) {
        if (matrices == null || cam == null || settings == null) return;
        bursts.removeIf(b -> System.currentTimeMillis() - b.bornMs >= settings.durationMs());
        if (bursts.isEmpty()) return;

        Matrix4f mat = matrices.peek().getPositionMatrix();

        if (provider == null) provider = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buf = provider.getBuffer(layer());

        long now = System.currentTimeMillis();
        int theme = settings.color();
        boolean drew = false;

        for (Burst burst : bursts) {
            long elapsed = now - burst.bornMs;
            float t = MathHelper.clamp(elapsed / (float) settings.durationMs(), 0.0F, 1.0F);
            if (t >= 1.0F) continue;

            drew |= renderBurst(buf, mat, burst, t, elapsed, theme, cam);
        }

        if (drew) provider.draw(layer());
    }

    private boolean renderBurst(VertexConsumer buf, Matrix4f mat, Burst burst,
                                float t, long elapsed, int theme, Vec3d cam) {
        // матрица события уже сдвинута на -камеру, поэтому всё рисуем в относительных координатах
        Vec3d pos = burst.pos.subtract(cam);
        float fadeIn = KillEffectMath.smoothStep(0.0F, 0.06F, t);
        boolean drew = false;

        // ── стартовые кольца и столб света ──
        if (t < 0.25F) {
            float bt = t / 0.25F;
            float ease = KillEffectMath.easeOutExpo(bt);

            for (int ring = 0; ring < 4; ring++) {
                float rr = ease * (0.8F + ring * 0.5F);
                float ra = (1.0F - bt) * (1.0F - ring * 0.2F);
                float lw = 3.5F - ring * 0.6F;
                int rc = KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, ring * 22F), 240.0F * ra / 255.0F);
                drew |= ring(buf, mat, pos.x, pos.y + 0.9, pos.z, rr, 40, 0.0, rc, lw * 0.05F);
            }

            float vba = 1.0F - bt;
            drew |= line(buf, mat, pos.x, pos.y - 0.5, pos.z, pos.x, pos.y + 3.5, pos.z,
                    KillEffectMath.withAlpha(theme, 180.0F * vba / 255.0F), 0.075F);
            drew |= line(buf, mat, pos.x, pos.y - 0.5, pos.z, pos.x, pos.y + 3.5, pos.z,
                    KillEffectMath.withAlpha(0xFFFFFFFF, 120.0F * vba / 255.0F), 0.025F);
        }

        // ── осколки ──
        float gravity = t * t * 4.5F;
        float vortexPull = KillEffectMath.smoothStep(0.35F, 0.9F, t) * 2.2F;
        float ease = KillEffectMath.easeOutCubic(Math.min(t * 1.6F, 1.0F));
        float seconds = elapsed / 1000.0F;

        for (int i = 0; i < burst.shards.size(); i++) {
            Shard s = burst.shards.get(i);
            double vx = s.dir.x * s.speed * ease * (1.0 - vortexPull * 0.45);
            double vz = s.dir.z * s.speed * ease * (1.0 - vortexPull * 0.45);
            double vy = s.dir.y * s.speed * ease - gravity * s.gravMult + vortexPull * 3.0;

            double sx = s.origin.x + vx - cam.x;
            double sy = s.origin.y + vy - cam.y;
            double sz = s.origin.z + vz - cam.z;

            // в оригинале угол копился per-frame; здесь — по времени, чтобы скорость
            // не зависела от fps
            double rot = Math.toRadians(s.rotSpeed * 6.0 * seconds);
            double perpX = -s.dir.z;
            double perpZ = s.dir.x;

            double ex = sx + (Math.cos(rot) * s.dir.x + Math.sin(rot) * perpX) * s.length;
            double ey = sy + Math.sin(rot * 0.7) * s.length * 0.35;
            double ez = sz + (Math.cos(rot) * s.dir.z + Math.sin(rot) * perpZ) * s.length;

            float alphaF = (1.0F - t) * (1.0F - t);
            alphaF *= 0.65F + (float) Math.sin(elapsed * 0.009 + i * 0.8) * 0.35F;
            int a = (int) (235.0F * alphaF * fadeIn);
            if (a <= 0) continue;

            float lw = 1.0F + (1.0F - t) * 2.2F;
            int col = KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, s.hueShift + (i % 3) * 15), a / 255.0F);
            drew |= line(buf, mat, sx, sy, sz, ex, ey, ez, col, lw * 0.016F);

            double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len > 1.0E-4) {
                double nx = vx / len;
                double ny = vy / len;
                double nz = vz / len;
                drew |= line(buf, mat, sx, sy, sz,
                        sx - nx * 0.25 * ease, sy - ny * 0.25 * ease, sz - nz * 0.25 * ease,
                        KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, s.hueShift + 20F), (a / 3) / 255.0F), lw * 0.006F);
                drew |= line(buf, mat, sx, sy, sz,
                        sx - nx * 0.12 * ease, sy - ny * 0.12 * ease, sz - nz * 0.12 * ease,
                        KillEffectMath.withAlpha(0xFFFFFFFF, (a / 6) / 255.0F), lw * 0.003F);
            }
        }

        // ── вихрь ──
        float vortexT = KillEffectMath.smoothStep(0.28F, 0.88F, t);
        if (vortexT > 0.01F) {
            int spiralSegs = 48;
            float spiralH = 4.5F;
            double spiralPhase = elapsed * 0.006;

            for (int ix = 0; ix < spiralSegs - 1; ix++) {
                float fA = ix / (float) spiralSegs;
                float fB = (ix + 1) / (float) spiralSegs;

                for (int strand = 0; strand < 2; strand++) {
                    double stOff = strand * Math.PI;
                    double aA = fA * Math.PI * 10.0 + spiralPhase + stOff;
                    double aB = fB * Math.PI * 10.0 + spiralPhase + stOff;
                    float rA = (0.9F - fA * 0.82F) * vortexT;
                    float rB = (0.9F - fB * 0.82F) * vortexT;

                    int va = (int) (160.0F * vortexT * (float) Math.sin(fA * Math.PI) * (1.0F - fA * 0.5F));
                    if (va <= 0) continue;

                    int vc = KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, strand * 30F + fA * 25.0F), va / 255.0F);
                    drew |= line(buf, mat,
                            pos.x + Math.cos(aA) * rA, pos.y + fA * spiralH, pos.z + Math.sin(aA) * rA,
                            pos.x + Math.cos(aB) * rB, pos.y + fB * spiralH, pos.z + Math.sin(aB) * rB,
                            vc, 0.011F);
                }
            }

            int ringCount = 7;
            for (int v = 0; v < ringCount; v++) {
                float vf = v / (float) ringCount;
                float vR = (0.9F - vf * 0.82F) * vortexT;
                double ph = elapsed * 0.005 + v * 0.6;
                int va = (int) (80.0F * vortexT * (1.0F - vf * 0.6F));
                if (va <= 0) continue;
                int vc = KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, v * 20F), va / 255.0F);
                drew |= ring(buf, mat, pos.x, pos.y + vf * spiralH, pos.z, vR, 24, ph, vc, 0.009F);
            }
        }

        // ── сфера в центре ──
        float sphereAlpha = ((float) Math.sin(elapsed * 0.008) * 0.3F + 0.5F) * (1.0F - t) * fadeIn;
        if (sphereAlpha > 0.02F) {
            float sr = 0.18F + (float) Math.sin(elapsed * 0.01) * 0.06F;
            for (int axis = 0; axis < 3; axis++) {
                double phaseOff = elapsed * 0.003 + axis * Math.PI / 3.0;
                int sa = (int) (180.0F * sphereAlpha);
                int sc = KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, axis * 30F), sa / 255.0F);
                drew |= ring(buf, mat, pos.x, pos.y + 0.9, pos.z, sr, 24, phaseOff, sc, 0.015F);
            }
        }

        // ── финальные кольца ──
        if (t > 0.85F) {
            float ft = (t - 0.85F) / 0.15F;
            float e = KillEffectMath.easeOutExpo(ft);
            for (int ringIdx = 0; ringIdx < 3; ringIdx++) {
                float fr = e * (1.0F + ringIdx * 0.6F);
                float fa = (1.0F - ft) * (1.0F - ringIdx * 0.3F);
                int fc = KillEffectMath.withAlpha(KillEffectMath.shiftHue(theme, ringIdx * 25F), 200.0F * fa / 255.0F);
                drew |= ring(buf, mat, pos.x, pos.y + 0.9, pos.z, fr, 40, 0.0, fc, 0.025F);
            }
        }

        return drew;
    }

    private RenderLayer layer() {
        if (cachedLayer == null) {
            RenderSetup setup = RenderSetup.builder(SHARDS_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 16)
                    .build();
            cachedLayer = RenderLayer.of("kill_effect_shards", setup);
        }
        return cachedLayer;
    }

    // ───────────────────────────── примитивы ─────────────────────────────

    /** Плоское кольцо в плоскости XZ с толщиной width. */
    private static boolean ring(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                                float radius, int segments, double phase, int color, float width) {
        if (radius <= width || color >>> 24 == 0) return false;
        float inner = radius - width;

        for (int i = 0; i < segments; i++) {
            double a1 = (Math.PI * 2) / segments * i + phase;
            double a2 = (Math.PI * 2) / segments * (i + 1) + phase;
            double o1x = Math.cos(a1), o1z = Math.sin(a1);
            double o2x = Math.cos(a2), o2z = Math.sin(a2);

            buf.vertex(mat, (float) (cx + o1x * radius), (float) cy, (float) (cz + o1z * radius)).color(color);
            buf.vertex(mat, (float) (cx + o1x * inner), (float) cy, (float) (cz + o1z * inner)).color(color);
            buf.vertex(mat, (float) (cx + o2x * inner), (float) cy, (float) (cz + o2z * inner)).color(color);
            buf.vertex(mat, (float) (cx + o2x * radius), (float) cy, (float) (cz + o2z * radius)).color(color);
        }
        return true;
    }

    /**
     * Линия-билборд: квад, развёрнутый перпендикулярно направлению взгляда.
     * Координаты уже camera-relative (матрица события сдвинута на -камеру),
     * поэтому вектор взгляда — это просто позиция точки.
     */
    private static boolean line(VertexConsumer buf, Matrix4f mat,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                int color, float width) {
        if (color >>> 24 == 0 || width <= 0.0F) return false;

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        if (dx * dx + dy * dy + dz * dz < 1.0E-10) return false;

        double sx = dy * z1 - dz * y1;
        double sy = dz * x1 - dx * z1;
        double sz = dx * y1 - dy * x1;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-6) return false;
        sx = sx / sl * width;
        sy = sy / sl * width;
        sz = sz / sl * width;

        buf.vertex(mat, (float) (x1 - sx), (float) (y1 - sy), (float) (z1 - sz)).color(color);
        buf.vertex(mat, (float) (x1 + sx), (float) (y1 + sy), (float) (z1 + sz)).color(color);
        buf.vertex(mat, (float) (x2 + sx), (float) (y2 + sy), (float) (z2 + sz)).color(color);
        buf.vertex(mat, (float) (x2 - sx), (float) (y2 - sy), (float) (z2 - sz)).color(color);
        return true;
    }

    // ───────────────────────────── данные ─────────────────────────────

    /** Параметры режима «Осколки». */
    public record Settings(int color, float density, long durationMs) {
    }

    private static final class Burst {
        final Vec3d pos;
        final long bornMs;
        final List<Shard> shards;

        Burst(Vec3d pos, long bornMs, List<Shard> shards) {
            this.pos = pos;
            this.bornMs = bornMs;
            this.shards = shards;
        }
    }

    private static final class Shard {
        final Vec3d origin;
        final Vec3d dir;
        final float speed;
        final float rotSpeed;
        final float length;
        final float gravMult;
        final int hueShift;

        Shard(Vec3d origin, Vec3d dir, float speed, float rotSpeed,
              float length, float gravMult, int hueShift) {
            this.origin = origin;
            this.dir = dir;
            this.speed = speed;
            this.rotSpeed = rotSpeed;
            this.length = length;
            this.gravMult = gravMult;
            this.hueShift = hueShift;
        }
    }
}