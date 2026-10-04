package ru.white.module.impl.render.targetesp;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.white.module.impl.render.TargetEsp;

import static ru.white.module.impl.render.targetesp.RhombTargetEspRenderer.withAlpha;

/**
 * Пентаграмма — две звезды, каждая рисуется SDF-шейдером Dima на своём кваде.
 *
 * Faithful-порт из Dima 26.2: «Отображение цели» → case 9, класс oV1nLcU0paHSE9Oj,
 * тело метода з(TZhU50s8XmBQCMP5, Vec3, LivingEntity, int, float, long) + хелперы
 * ж(Vec3) / з(Vec3[], double) / з(BufferBuilder, Matrix4f, Vec3, Vec3, Vec3, float, int).
 * Пайплайн сД: POSITION_TEX_COLOR, QUADS, ADDITIVE, depth always, UBO Globals.
 *
 * Оригинал (строки 1627-1635):
 *   s13 = max(Ти, ширина*Т9 + То) * lerp(alpha, ТЩ, 1) * (1 + Т2*sin(t*Тф))
 *   звезда 1: центр = pos + (0, Т7, 0), базис от (0,1,0) с поворотом -t*ТО, размер s13*ТР
 *   звезда 2: центр = pos + (0, высота*Тм, 0), базис от наклонённой оси (t*Тж),
 *             ось наклоняется на (ТШ + Ты*sin(t*Ти)) и крутится t*Т6 + id*Т_, размер s13
 */
public final class PentagonTargetEspRenderer {

    // ── константы оригинала ──
    private static final float Тю = 0.01f;      // отсечка alpha
    private static final float ТЩ = 0.52f;      // lerp-цель радиуса
    private static final float Тф = 3.1f;       // частота пульсации радиуса
    private static final float Т2 = 0.04f;      // амплитуда пульсации
    private static final float Ти = 0.68f;      // минимальный радиус
    private static final float Т9 = 1.14f;      // множитель ширины
    private static final float То = 0.15f;      // добавка к радиусу
    private static final float Т7 = 0.035f;     // высота звезды 1
    private static final float ТР = 1.32f;      // размер звезды 1
    private static final float Т3 = 0.18f;      // сдвиг цвета звезды 1
    private static final float Тт = 0.5f;       // alpha звезды 1
    private static final float ТО = 0.55f;      // скорость поворота звезды 1
    private static final float Тм = 0.48f;      // высота звезды 2
    private static final float Тк = 0.16f;      // alpha звезды 2
    private static final float ТИ = 0.95f;      // доп. alpha звезды 2
    private static final float Тж = 0.82f;      // скорость поворота базиса звезды 2
    private static final float ТШ = 34.0f;      // базовый наклон звезды 2, градусы
    private static final float Ты = 9.0f;       // амплитуда наклона звезды 2
    private static final float Ти2 = 0.7f;      // частота наклона звезды 2
    private static final float Т6 = 1.28f;      // скорость вращения звезды 2
    private static final float Т_ = 0.17f;      // сдвиг фазы по id сущности

    private static final float Ож = 0.82f;      // делитель размера в хелпере
    private static final float кА = 1.3f;       // множитель полустороны
    private static final float IФ = 0.98f;      // порог fallback-базиса
    private static final float О8 = 1.0e-6f;    // порог вырожденности базиса

    /**
     * Точка отсчёта для таймера. В контексте frameTimeMs — это System.currentTimeMillis(),
     * абсолютная метка; напрямую в тригонометрию её подставлять нельзя (float теряет
     * точность на значениях ~1.7e9, и вращение выглядит замершим). Считаем время от старта.
     */
    private static final long START_MS = System.currentTimeMillis();

    private PentagonTargetEspRenderer() {
    }

    /** 9(float) оригинала: easeOutCubic. */
    private static float easeOut(float s) {
        float k = 1.0f - MathHelper.clamp(s, 0.0f, 1.0f);
        return 1.0f - k * k * k;
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate immediate,
                              TargetEspRenderContext ctx, float speed) {
        LivingEntity target = ctx.target();
        // s9 = 9(s5) — easeOutCubic от входной alpha
        float anim = MathHelper.clamp(easeOut(ctx.alpha()), 0.0f, 1.0f);
        if (target == null || anim <= Тю) {
            return;
        }

        float sp = Math.max(0.05f, speed);
        // время от старта класса, а не абсолютный epoch
        float t = (System.currentTimeMillis() - START_MS) / 1000.0f * sp;

        // Матрица уже переведена на интерполированную позицию цели (TargetEsp делает
        // matrices.translate(lerpedPos - cameraPos) перед вызовом), поэтому здесь
        // только локальные координаты. Вычитать камеру второй раз нельзя — это даёт
        // двойное смещение и ступеньки раз в тик (дёрганье при движении цели).
        float height = target.getHeight();

        // s11 = lerp(alpha, ТЩ, 1); s12 = 1 + Т2*sin(t*Тф)
        float radiusScale = MathHelper.lerp(anim, ТЩ, 1.0f);
        float pulse = 1.0f + Т2 * (float) Math.sin(t * Тф);
        // s13 = max(Ти, ширина*Т9 + То) * s11 * s12
        float s13 = Math.max(Ти, target.getWidth() * Т9 + То) * radiusScale * pulse;

        float hurt = MathHelper.clamp(ctx.hurtProgress(), 0.0f, 1.0f);
        int rgb = ctx.primaryColor() | 0xFF000000;
        if (hurt > 0.0f) {
            int r = MathHelper.clamp(Math.round(((rgb >> 16) & 0xFF) + 255.0f * hurt), 0, 255);
            int g = MathHelper.clamp(Math.round(((rgb >> 8) & 0xFF) * (1.0f - hurt)), 0, 255);
            int b = MathHelper.clamp(Math.round((rgb & 0xFF) * (1.0f - hurt)), 0, 255);
            rgb = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        Matrix4f m = stack.peek().getPositionMatrix();
        VertexConsumer buf = immediate.getBuffer(DimaEspPipelines.PENTAGRAM_LAYER);

        // ── звезда 1: плоскость от (0,1,0), поворот -t*ТО ──
        Vec3d[] b1 = basis(new Vec3d(0.0, 1.0, 0.0), -(double) (t * ТО));
        // ж(В(s4, Т3), s9 * Тт) — сдвиг цветов, затем alpha
        int c1 = withAlpha(brighten(rgb, Т3), Math.round(anim * Тт * 255.0f));
        quad(buf, m, new Vec3d(0.0, Т7, 0.0), b1, s13 * ТР, c1);

        // ── звезда 2: наклонённая и вращающаяся плоскость ──
        double spin = t * Т6 + target.getId() * Т_;
        double tilt = Math.toRadians(ТШ + Ты * Math.sin(t * Ти2));
        Vec3d dir = new Vec3d(
                Math.sin(tilt) * Math.cos(spin),
                Math.cos(tilt),
                Math.sin(tilt) * Math.sin(spin));
        Vec3d[] b2 = basis(dir, t * Тж);
        // ж(s4, s9 * (Тк + s6*ТИ)) — только alpha
        int c2 = withAlpha(rgb, Math.round(Math.min(1.0f, anim * (Тк + ТИ)) * 255.0f));
        quad(buf, m, new Vec3d(0.0, height * Тм, 0.0), b2, s13, c2);
    }

    /** Базис плоскости + поворот, порт ж(Vec3) и з(Vec3[], double). */
    private static Vec3d[] basis(Vec3d n, double roll) {
        Vec3d up = new Vec3d(0.0, 1.0, 0.0).subtract(n.multiply(n.y));
        Vec3d b0;
        Vec3d b1;
        if (up.lengthSquared() < О8) {
            Vec3d helper = Math.abs(n.y) > IФ ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
            b0 = helper.crossProduct(n).normalize();
            b1 = n.crossProduct(b0).normalize();
        } else {
            b0 = up.normalize();
            b1 = b0.crossProduct(n).normalize();
        }
        double c = Math.cos(roll);
        double s = Math.sin(roll);
        return new Vec3d[]{
                b0.multiply(c).add(b1.multiply(s)),
                b0.multiply(-s).add(b1.multiply(c))};
    }

    /**
     * Масштаб спрайта. В оригинале хелпер з(...) умножает размер на 2.0f/Ож, а эмиттер
     * квада — ещё на кА, итого half = size * 3.17. В игре это заметно крупнее оригинала,
     * поэтому стоит 1.0 (half = size * кА). Вернуть буквальное 2.0f/Ож — одна правка.
     */
private static final float SPRITE_WRAP_SCALE = 1.0f;

    /** Квад в плоскости базиса с UV 0..1, порт з(BufferBuilder, ..., float, int). */
    private static void quad(VertexConsumer buf, Matrix4f m, Vec3d c, Vec3d[] b,
                             float size, int color) {
        float half = size * SPRITE_WRAP_SCALE * кА;
        Vec3d v0 = c.subtract(b[0].multiply(half)).subtract(b[1].multiply(half));
        Vec3d v1 = c.subtract(b[0].multiply(half)).add(b[1].multiply(half));
        Vec3d v2 = c.add(b[0].multiply(half)).add(b[1].multiply(half));
        Vec3d v3 = c.add(b[0].multiply(half)).subtract(b[1].multiply(half));

        buf.vertex(m, (float) v0.x, (float) v0.y, (float) v0.z).texture(0.0f, 0.0f).color(color);
        buf.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).texture(0.0f, 1.0f).color(color);
        buf.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).texture(1.0f, 1.0f).color(color);
        buf.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).texture(1.0f, 0.0f).color(color);
    }

/** В() оригинала — сдвиг каналов цвета. */
private static int brighten(int color, float amount) {
        int r = MathHelper.clamp(Math.round(((color >> 16) & 0xFF) + 255.0f * amount), 0, 255);
        int g = MathHelper.clamp(Math.round(((color >> 8) & 0xFF) + 255.0f * amount), 0, 255);
        int b = MathHelper.clamp(Math.round((color & 0xFF) + 255.0f * amount), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}