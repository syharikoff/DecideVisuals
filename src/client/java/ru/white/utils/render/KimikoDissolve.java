package ru.white.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Порт Kimiko KillEffectDissolveEffect — режим «Растворение».
 *
 * Захваченная модель убитой цели рассыпается на светящиеся частицы:
 * холд на месте -> подъём с дрейфом -> плавное затухание.
 * Частицы рисуются билборд-квадами с glow-текстурой.
 *
 * Захват модели переиспользует наш ModelBoxCapture (ориентированные боксы),
 * точки сэмплируются внутри объёма боксов — визуально совпадает с
 * Kimiko-овским вершинным захватом без 663 строк кода.
 */
public final class KimikoDissolve {

    private static final int MAX_PARTICLES = 4000;
    private static final int MAX_LIGHT = 0xF000F0;
    private static final Identifier GLOW_TEXTURE = Identifier.of("client", "textures/particles/glow.png");

    private static final KimikoDissolve INSTANCE = new KimikoDissolve();

    private final BufferAllocator allocator = new BufferAllocator(1 << 16);
    private VertexConsumerProvider.Immediate provider;

    private final List<ModelParticle> particles = new ArrayList<>();
    private final List<LivingEntity> pending = new ArrayList<>();
    private final Random random = new Random();

    private KimikoDissolve() {
    }

    public static KimikoDissolve get() {
        return INSTANCE;
    }

    public synchronized void queue(LivingEntity entity, float partialTick, Settings settings) {
        if (entity == null || settings == null) return;
        // захват модели делаем на рендер-потоке: к моменту обработки пакета смерти
        // сущность уже может не отдавать модель (у мобов особенно)
        pending.add(entity);
    }

    private synchronized void spawnPending(float partialTick, Settings settings) {
        if (pending.isEmpty()) return;
        List<LivingEntity> due = new ArrayList<>(pending);
        pending.clear();
        for (LivingEntity entity : due) {
            spawn(entity, partialTick, settings);
        }
    }

    private synchronized void spawn(LivingEntity entity, float partialTick, Settings settings) {
        List<ModelBoxCapture.Box> boxes;
        try {
            boxes = ModelBoxCapture.capture(entity, partialTick, false);
        } catch (Throwable ignored) {
            return;
        }
        if (boxes.isEmpty()) return;

        // боксы приходят в локальном пространстве модели — сдвигаем на интерполированную позицию
        Vec3d origin = new Vec3d(
                MathHelper.lerp(partialTick, entity.lastRenderX, entity.getX()),
                MathHelper.lerp(partialTick, entity.lastRenderY, entity.getY()),
                MathHelper.lerp(partialTick, entity.lastRenderZ, entity.getZ()));

        List<float[]> volumes = new ArrayList<>(boxes.size());
        for (ModelBoxCapture.Box box : boxes) {
            float[] half = box.half();
            volumes.add(new float[]{half[0] * half[1] * half[2]});
        }

        float chaos = Math.max(0.0F, settings.chaos());
        int budget = MAX_PARTICLES - particles.size();
        if (budget <= 0) return;
        int count = Math.min(Math.max(1, settings.count()), budget);

        for (int i = 0; i < count; i++) {
            Vec3d point = sample(boxes, volumes, origin);

            Vec3d away = point.subtract(center(boxes, origin));
            if (away.lengthSquared() < 1.0E-5) {
                away = new Vec3d(random.nextDouble() - 0.5, random.nextDouble() * 0.4, random.nextDouble() - 0.5);
            }
            double driftScale = (0.32 + random.nextDouble() * 0.58) * chaos;
            Vec3d drift = away.normalize().multiply(driftScale).add(
                    random.nextDouble() - 0.5,
                    random.nextDouble() * 0.35,
                    random.nextDouble() - 0.5).multiply(0.12);

            particles.add(new ModelParticle(point, drift, System.currentTimeMillis(),
                    random.nextInt(0xFFFFFF),
                    random.nextFloat() * 360.0F,
                    (random.nextFloat() - 0.5F) * 90.0F,
                    random.nextFloat() * 6.2832F,
                    0.7F + random.nextFloat() * 0.6F));
        }
    }

    private Vec3d sample(List<ModelBoxCapture.Box> boxes, List<float[]> volumes, Vec3d origin) {
        float total = 0.0F;
        for (float[] v : volumes) total += v[0];
        if (total <= 0.0F) return origin;

        float pick = random.nextFloat() * total;
        ModelBoxCapture.Box chosen = boxes.get(0);
        for (int i = 0; i < volumes.size(); i++) {
            pick -= volumes.get(i)[0];
            if (pick <= 0.0F) { chosen = boxes.get(i); break; }
        }

        float[] half = chosen.half();
        float[] axes = chosen.axes();
        float a = random.nextFloat() * 2.0F - 1.0F;
        float b = random.nextFloat() * 2.0F - 1.0F;
        float c = random.nextFloat() * 2.0F - 1.0F;

        double px = chosen.cx() + axes[0] * a * half[0] + axes[3] * b * half[1] + axes[6] * c * half[2];
        double py = chosen.cy() + axes[1] * a * half[0] + axes[4] * b * half[1] + axes[7] * c * half[2];
        double pz = chosen.cz() + axes[2] * a * half[0] + axes[5] * b * half[1] + axes[8] * c * half[2];

        return new Vec3d(origin.x + px, origin.y + py, origin.z + pz);
    }

    private Vec3d center(List<ModelBoxCapture.Box> boxes, Vec3d origin) {
        double sx = 0.0, sy = 0.0, sz = 0.0;
        for (ModelBoxCapture.Box box : boxes) {
            sx += box.cx();
            sy += box.cy();
            sz += box.cz();
        }
        int n = Math.max(1, boxes.size());
        return new Vec3d(origin.x + sx / n, origin.y + sy / n, origin.z + sz / n);
    }

    public synchronized boolean isIdle() {
        return particles.isEmpty();
    }

public synchronized void render(MatrixStack matrices, Vec3d cam, Settings settings,
                      java.util.function.IntUnaryOperator colorProvider, float partialTick) {
        if (matrices == null || settings == null) return;
        spawnPending(partialTick, settings);
        if (particles.isEmpty()) return;

        long now = System.currentTimeMillis();
        long holdMs = Math.max(0L, settings.holdMs());
        long fadeMs = Math.max(1L, settings.fadeMs());
        long maxLife = holdMs + fadeMs;

        particles.removeIf(p -> p.isDead(now, maxLife));
        if (particles.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Quaternionf rotation = mc.gameRenderer.getCamera().getRotation();
        Vector3f right = rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));

        RenderLayer layer = renderLayer();
        // собственный буфер: не трогаем общий Immediate мира, flush которого
        // вызывается из постороннего getBuffer() и роняет рендер
        if (provider == null) provider = VertexConsumerProvider.immediate(allocator);
        VertexConsumer consumer = provider.getBuffer(layer);
        MatrixStack.Entry pose = matrices.peek();

        boolean drew = false;
        for (ModelParticle particle : particles) {
            float alpha = particle.alpha(now, holdMs, fadeMs);
            if (alpha <= 0.004F) continue;

            Vec3d position = particle.position(now, holdMs, fadeMs, settings);
            float half = particle.size(now, holdMs, fadeMs, settings.size()) * 0.5F;
            int color = multAlpha(colorProvider.applyAsInt(particle.colorSeed), alpha);

            float angle = (float) Math.toRadians(particle.angle(now));
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);

            float rx = (right.x() * cos - up.x() * sin) * half;
            float ry = (right.y() * cos - up.y() * sin) * half;
            float rz = (right.z() * cos - up.z() * sin) * half;
            float ux = (right.x() * sin + up.x() * cos) * half;
            float uy = (right.y() * sin + up.y() * cos) * half;
            float uz = (right.z() * sin + up.z() * cos) * half;

            float nx = ry * uz - rz * uy;
            float ny = rz * ux - rx * uz;
            float nz = rx * uy - ry * ux;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1.0E-5F) { nx /= nl; ny /= nl; nz /= nl; } else { nz = 1.0F; }

            float cx = (float) (position.x - cam.x);
            float cy = (float) (position.y - cam.y);
            float cz = (float) (position.z - cam.z);

            consumer.vertex(pose, cx - rx - ux, cy - ry - uy, cz - rz - uz)
                    .color(color).texture(0.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV).light(MAX_LIGHT).normal(pose, nx, ny, nz);
            consumer.vertex(pose, cx - rx + ux, cy - ry + uy, cz - rz + uz)
                    .color(color).texture(0.0F, 1.0F)
                    .overlay(OverlayTexture.DEFAULT_UV).light(MAX_LIGHT).normal(pose, nx, ny, nz);
            consumer.vertex(pose, cx + rx + ux, cy + ry + uy, cz + rz + uz)
                    .color(color).texture(1.0F, 1.0F)
                    .overlay(OverlayTexture.DEFAULT_UV).light(MAX_LIGHT).normal(pose, nx, ny, nz);
            consumer.vertex(pose, cx + rx - ux, cy + ry - uy, cz + rz - uz)
                    .color(color).texture(1.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV).light(MAX_LIGHT).normal(pose, nx, ny, nz);
            drew = true;
        }

        if (drew) provider.draw(layer);
    }

    private static int multAlpha(int color, float alpha) {
        int a = Math.round(MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static float smoothStep(float value) {
        float t = MathHelper.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutCubic(float value) {
        float inv = 1.0F - MathHelper.clamp(value, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    public static void clear() {
        synchronized (INSTANCE) {
            INSTANCE.particles.clear();
        }
    }

    private static RenderLayer cachedLayer;

    private static RenderLayer renderLayer() {
        if (cachedLayer == null) {
            RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT_NO_CULL)
                    .texture("Sampler0", GLOW_TEXTURE)
                    .translucent()
                    .expectedBufferSize(1 << 18)
                    .build();
            cachedLayer = RenderLayer.of("kimiko_dissolve_glow", renderSetup);
        }
        return cachedLayer;
    }

    /** Параметры режима «Растворение». */
    public record Settings(int count, float size, long holdMs, long fadeMs,
                           float rise, float chaos) {
    }

    private static final class ModelParticle {
        private final Vec3d anchor;
        private final Vec3d drift;
        private final long bornMs;
        private final int colorSeed;
        private final float startAngle;
        private final float spin;
        private final float phase;
        private final float sizeMul;

        ModelParticle(Vec3d anchor, Vec3d drift, long bornMs, int colorSeed,
                      float startAngle, float spin, float phase, float sizeMul) {
            this.anchor = anchor;
            this.drift = drift;
            this.bornMs = bornMs;
            this.colorSeed = colorSeed;
            this.startAngle = startAngle;
            this.spin = spin;
            this.phase = phase;
            this.sizeMul = sizeMul;
        }

        int colorSeed() { return colorSeed; }

        boolean isDead(long nowMs, long maxLifeMs) {
            return nowMs - bornMs >= maxLifeMs;
        }

        float evaporation(long nowMs, long holdMs, long fadeMs) {
            return MathHelper.clamp((float) (nowMs - bornMs - holdMs) / (float) fadeMs, 0.0F, 1.0F);
        }

        Vec3d position(long nowMs, long holdMs, long fadeMs, Settings settings) {
            float evap = evaporation(nowMs, holdMs, fadeMs);
            if (evap <= 0.0F) return anchor;

            float chaos = Math.max(0.0F, settings.chaos());
            float eased = easeOutCubic(evap);
            double wave = Math.sin((nowMs - bornMs) * 0.012 + phase) * 0.075 * chaos * eased;
            double sideWave = Math.cos((nowMs - bornMs) * 0.009 + phase * 1.37) * 0.055 * chaos * eased;

            return anchor.add(drift.multiply(eased)).add(
                    sideWave,
                    settings.rise() * eased * (0.75 + 0.25 * Math.sin(phase)),
                    wave);
        }

        float alpha(long nowMs, long holdMs, long fadeMs) {
            float fadeIn = MathHelper.clamp((float) (nowMs - bornMs) / 80.0F, 0.0F, 1.0F);
            float evap = evaporation(nowMs, holdMs, fadeMs);
            if (evap <= 0.0F) return fadeIn;
            return fadeIn * (1.0F - smoothStep(evap));
        }

        float size(long nowMs, long holdMs, long fadeMs, float baseSize) {
            float evap = evaporation(nowMs, holdMs, fadeMs);
            float pulse = 1.0F + (float) Math.sin((nowMs - bornMs) * 0.018F + phase) * 0.08F;
            float dissolveScale = 1.0F - smoothStep(evap) * 0.45F;
            return baseSize * sizeMul * pulse * dissolveScale;
        }

        float angle(long nowMs) {
            return startAngle + (float) (nowMs - bornMs) / 1000.0F * spin;
        }
    }
}