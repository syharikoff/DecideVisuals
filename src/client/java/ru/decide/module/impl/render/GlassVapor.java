package ru.decide.module.impl.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ColorSetting;
import ru.decide.module.api.settings.impl.DelimiterSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.theme.ThemeColor;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.other.Instance;
import ru.decide.utils.render.GlassVaporPipeline;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Порт Kimiko Glass Vapor — пар над водой: капли spawn'ятся у игрока, пока он двигается,
 * всплевываются, растягиваются в эллипсоиды и дают преломление, блик и хроматику.
 *
 * Режим «Клиент» в Kimiko брал цвет из анимированной палитры темы (ThemeWave).
 * ThemeWave не переносится, поэтому здесь «Клиент» — это двухцветная интерполяция
 * Decide ThemeColor.getVisualColor() с её более тёмным оттенком.
 * Режимы «Радуга» и «Свой» от темы не зависят и работают как в оригинале.
 */
@ModuleInfo(
        name = "Glass Vapor",
        desc = "Пар над водой: капли, преломление и хроматика у игрока при движении",
        category = Category.VISUALS
)
public final class GlassVapor extends Module {

    public static GlassVapor getInstance() {
        return Instance.get(GlassVapor.class);
    }

    private static final String COLOR_RAINBOW = "Радуга";
    private static final String COLOR_CLIENT = "Клиент";
    private static final String COLOR_CUSTOM = "Свой";

    private static final int CLIENT_COLOR_FIRST = ColorUtil.getColor2(127, 242, 255, 255);
    private static final int CLIENT_COLOR_SECOND = ColorUtil.getColor2(255, 50, 150, 255);
    private static final int DARK_SECOND_COLOR = new Color(16, 16, 16, 75).getRGB();

    private static final int MAX_ELEMENTS = 240;
    private static final int PALETTE_SIZE = 6;
    private static final int PALETTE_BASE = 3856;

    // ---------- Капли ----------
    public final DelimiterSetting dropsSeparator = new DelimiterSetting(this, "Капли");
    public final SliderSetting density = new SliderSetting(this, "Плотность", 5F, 1F, 12F, 1F);
    public final SliderSetting spawnRate = new SliderSetting(this, "Частота брызг", 14F, 4F, 30F, 1F);
    public final SliderSetting blobSize = new SliderSetting(this, "Размер", 0.3F, 0.15F, 0.55F, 0.01F);
    public final SliderSetting riseHeight = new SliderSetting(this, "Высота подъёма", 1.6F, 0.6F, 3.0F, 0.1F);
    public final SliderSetting lifetime = new SliderSetting(this, "Время жизни", 1600F, 900F, 3000F, 50F);

    // ---------- Стекло ----------
    public final DelimiterSetting glassSeparator = new DelimiterSetting(this, "Стекло");
    public final SliderSetting distort = new SliderSetting(this, "Преломление", 1.0F, 0.2F, 3.0F, 0.1F);
    public final SliderSetting ripple = new SliderSetting(this, "Волны", 1.0F, 0.0F, 3.0F, 0.1F);
    public final SliderSetting rim = new SliderSetting(this, "Кромка", 0.7F, 0.0F, 1.5F, 0.05F);
    public final SliderSetting chroma = new SliderSetting(this, "Хроматика", 0.35F, 0.0F, 1.0F, 0.05F);

    // ---------- Цвет ----------
    public final DelimiterSetting colorSeparator = new DelimiterSetting(this, "Цвет");
    public final SliderSetting tintStrength = new SliderSetting(this, "Сила тонировки", 45F, 0.0F, 100F, 1F);
    public final ModeSetting colorMode = new ModeSetting(this, "Цвет стекла", COLOR_CLIENT, COLOR_RAINBOW, COLOR_CUSTOM);
    public final BooleanSetting useSecondColor = new BooleanSetting(this, "Второй цвет", false)
            .setVisible(() -> colorMode.is(COLOR_CUSTOM));
    public final ColorSetting customColor = new ColorSetting(this, "Цвет", new Color(160, 220, 255, 255).getRGB())
            .setVisible(() -> colorMode.is(COLOR_CUSTOM));
    public final ColorSetting customSecondColor = new ColorSetting(this, "Цвет 2", DARK_SECOND_COLOR)
            .setVisible(() -> colorMode.is(COLOR_CUSTOM) && useSecondColor.getValue());

    private final float[] data = new float[GlassVaporPipeline.UNIFORM_FLOATS];
    private final List<Drop> drops = new ArrayList<>(64);

    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f centerClip = new Vector4f();
    private final Vector4f axisClip = new Vector4f();

    private float centerU;
    private float centerV;
    private float centerLin;
    private float axisV;

    private Vec3d lastPos;
    private long nextBurstMs;
    private long seq;
    private long enabledAtMs;

    @Override
    protected void onEnable() {
        enabledAtMs = System.currentTimeMillis();
        if (GlassVaporPipeline.deviceReady() && !GlassVaporPipeline.validate()) {
            ru.decide.utils.math.ChatUtils.addChatMessage("§c[Glass Vapor] пайплайн недоступен, эффект не отрисуется");
        }
    }

    @Override
    protected void onDisable() {
        drops.clear();
        lastPos = null;
        frameDataReady = false;
        GlassVaporPipeline.clear();
    }

    private float visualAlpha() {
        if (!isEnabled()) return 0.0f;
        long elapsed = System.currentTimeMillis() - enabledAtMs;
        if (elapsed >= 1000L) return 1.0f;
        float t = Math.max(0.0f, elapsed / 1000.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // ================= Рендер =================

    @EventHandler
    public void onRender(EventRender3D event) {
        Framebuffer renderTarget = mc.getFramebuffer();
        Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();
        if (renderTarget == null || camera == null || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        Vec3d pos = mc.player.getEntityPos();

        boolean moving = false;
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dz = pos.z - lastPos.z;
            moving = mc.player.isOnGround() && dx * dx + dz * dz > 1.0E-6;
        }
        lastPos = pos;

        if (isEnabled()) spawnDrops(pos, moving, now);
        drops.removeIf(d -> (float) (now - d.spawnMs) >= d.delayMs + d.burstMs + d.riseMs);
        if (drops.isEmpty()) return;

        this.viewProj.set(event.getProjectionMatrix()).mul(event.getMatrixStack().peek().getPositionMatrix());
        Vec3d cam = camera.getCameraPos();

        float far = Math.max(192.0f, (mc.options.getViewDistance().getValue() + 1) * 16.0f);
        boolean paletteOn = colorMode.is(COLOR_CLIENT);

        Arrays.fill(data, 0.0F);

        float riseH = riseHeight.getValue();
        int count = 0;

        for (int i = drops.size() - 1; i >= 0; i--) {
            if (count >= MAX_ELEMENTS) break;

            Drop d = drops.get(i);
            float age = now - d.spawnMs;
            if (age < d.delayMs) continue;

            float dropBase = d.dropR;
            float rx, ry, h, env, wobbleT;

            if (age < d.delayMs + d.burstMs) {
                // фаза всплеска: капля расплющивается у земли
                float b = smooth((age - d.delayMs) / d.burstMs);
                rx = dropBase * b;
                ry = rx * (0.55f + 0.3f * b);
                h = ry * 0.55f;
                env = smooth(Math.min(b * 2.0f, 1.0f));
                wobbleT = 0.15f * b;
            } else {
                // фаза подъёма: вытягивается вверх и тает
                float r = (age - d.delayMs - d.burstMs) / d.riseMs;
                rx = dropBase * (1.0f - 0.45f * r);
                float stretchCurve = (float) Math.sin(Math.PI * smooth(Math.min(r * 1.2f, 1.0f)));
                ry = rx * (0.85f + 1.15f * stretchCurve);
                h = dropBase * 0.85f * 0.55f + riseH * d.riseMul * (float) Math.pow(r, 1.3);
                env = 1.0f - smooth(MathHelper.clamp((r - 0.72f) / 0.28f, 0.0f, 1.0f));
                wobbleT = 0.15f + 0.85f * r;
            }

            if (env <= 0.01f || rx <= 0.01f) continue;

            float wobT = now * 0.001f;
            float wx = (float) Math.sin(wobT * d.wobSpeed + d.phase) * d.wobAmp * wobbleT;
            float wz = (float) Math.cos(wobT * d.wobSpeed * 0.83f + d.phase * 1.7f) * d.wobAmp * wobbleT;

            float rxS = rx * 1.85f;
            float ryS = ry * 1.85f;

            count = packEllipsoid(count, cam, d.x + wx, d.y + h, d.z + wz,
                    rxS, ryS, rxS, env, Math.max(rxS, ryS) * 0.55f,
                    d.colorIndex, paletteOn, d.phase, 0.1f, 1.0f, far);
        }

        if (count == 0) return;

        data[0] = count;
        data[1] = renderTarget.textureWidth / (float) Math.max(1, renderTarget.textureHeight);
        data[2] = (now % 100000L) / 1000.0f;

        float moduleFade = visualAlpha();
        data[3] = 0.016f * distort.getValue() * moduleFade;
        data[4] = rim.getValue() * moduleFade;
        data[5] = MathHelper.clamp(tintStrength.getValue() / 100.0f, 0.0f, 1.0f) * moduleFade;
        data[6] = 0.008f * ripple.getValue() * moduleFade;
        data[7] = 1.0f;
        data[8] = 0.05f;
        data[9] = far;
        data[10] = chroma.getValue() * 0.6f;
        data[11] = paletteOn ? 1.0f : 0.0f;
        data[12] = writePalette();          // header4.x = число цветов палитры
        data[13] = 0.0f;
        data[14] = 0.0f;
        data[15] = 0.0f;

        // Сцена в MinecraftClient.getFramebuffer() на этом этапе ещё не собрана,
        // поэтому сам проход делаем из GameRenderer после renderWorld.
        frameDataReady = true;
    }

    /** Вызывается из GameRendererMixin перед отрисовкой GUI, когда кадр уже готов. */
    public static void applyPending() {
        GlassVapor module = getInstance();
        if (module == null || !module.frameDataReady) return;
        module.frameDataReady = false;

        Framebuffer target = module.mc.getFramebuffer();
        if (target == null) return;
        GlassVaporPipeline.apply(target, module.data);
    }

    /** Режим «Клиент»: 6 стопов от акцентного цвета клиента и его тёмного оттенка. */
    private float writePalette() {
        int base = ThemeColor.getVisualColor();
        int first = CLIENT_COLOR_FIRST;
        int second = CLIENT_COLOR_SECOND;

        for (int i = 0; i < PALETTE_SIZE; i++) {
            int argb = ColorUtil.interpolateColor(first, second, i / (float) (PALETTE_SIZE - 1));
            int o = PALETTE_BASE + i * 4;
            data[o] = (argb >> 16 & 0xFF) / 255.0f;
            data[o + 1] = (argb >> 8 & 0xFF) / 255.0f;
            data[o + 2] = (argb & 0xFF) / 255.0f;
            data[o + 3] = 1.0f;
        }

        // крайние стопы — реальные цвета клиента, чтобы палитра совпадала с интерфейсом
        data[PALETTE_BASE] = (base >> 16 & 0xFF) / 255.0f;
        data[PALETTE_BASE + 1] = (base >> 8 & 0xFF) / 255.0f;
        data[PALETTE_BASE + 2] = (base & 0xFF) / 255.0f;

        return PALETTE_SIZE;
    }

    /** Проецирует эллипсоид в экранное пространство и пишет его в UBO. */
    private int packEllipsoid(int count, Vec3d cam, double wx, double wy, double wz,
                              float ax, float ay, float az, float envIn, float halfExtent,
                              int colorIndex, boolean paletteOn, float phase, float wobble,
                              float shine, float far) {
        float env = envIn;
        float relX = (float) (wx - cam.x);
        float relY = (float) (wy - cam.y);
        float relZ = (float) (wz - cam.z);

        env *= nearFade(relX, relY, relZ, halfExtent);
        if (env <= 0.01f) return count;
        if (!projectCenter(relX, relY, relZ, far)) return count;

        float cu = centerU;
        float cv = centerV;
        float lin = centerLin;

        float e1x = axisU(ax, 0.0f, 0.0f);
        float e1y = axisV;
        float e2x = axisU(0.0f, ay, 0.0f);
        float e2y = axisV;
        float e3x = axisU(0.0f, 0.0f, az);
        float e3y = axisV;

        // собственные векторы эллипсоида через разложение 2x2 матрицы осей
        float bxx = e1x * e1x + e2x * e2x + e3x * e3x;
        float bxy = e1x * e1y + e2x * e2y + e3x * e3y;
        float byy = e1y * e1y + e2y * e2y + e3y * e3y;

        float mid = 0.5f * (bxx + byy);
        float disc = (float) Math.sqrt(0.25f * (bxx - byy) * (bxx - byy) + bxy * bxy);
        float l1sq = mid + disc;
        float l2sq = Math.max(mid - disc, 1.0E-12f);

        float l1 = (float) Math.sqrt(l1sq);
        float l2 = (float) Math.sqrt(l2sq);
        if (l1 < 1.0E-5f || l2 < 1.0E-5f) return count;

        env *= sizeFade(l1, l2);
        if (env <= 0.01f) return count;

        float vx = 0.0f;
        float vy = 0.0f;
        if (Math.abs(bxy) > 1.0E-10f) {
            vx = bxy;
            vy = l1sq - bxx;
        } else if (bxx >= byy) {
            vx = 1.0f;
            vy = 0.0f;
        } else {
            vx = 0.0f;
            vy = 1.0f;
        }

        float vl = (float) Math.sqrt(vx * vx + vy * vy);
        if (vl < 1.0E-12f) {
            vx = 1.0f;
            vy = 0.0f;
            vl = 1.0f;
        }

        float p1x = (vx /= vl) * l1;
        float p1y = (vy /= vl) * l1;
        float p2x = -vy * l2;
        float p2y = vx * l2;

        if (!cullCenter(cu, cv, l1, l2)) return count;

        return write(count, cu, cv, lin, env, p1x, p1y, p2x, p2y, colorIndex, paletteOn, phase, wobble, shine);
    }

    private boolean projectCenter(float relX, float relY, float relZ, float far) {
        this.centerClip.set(relX, relY, relZ, 1.0f);
        this.viewProj.transform(this.centerClip);
        if (this.centerClip.w <= 0.05f) return false;

        float iw = 1.0f / this.centerClip.w;
        this.centerU = this.centerClip.x * iw * 0.5f + 0.5f;
        this.centerV = this.centerClip.y * iw * 0.5f + 0.5f;
        this.centerLin = linDepth(this.centerClip.z * iw * 0.5f + 0.5f, 0.05f, far);
        return true;
    }

    private float axisU(float ax, float ay, float az) {
        this.axisClip.set(ax, ay, az, 0.0f);
        this.viewProj.transform(this.axisClip);

        float iw = 1.0f / (this.centerClip.w * this.centerClip.w);
        float u = 0.5f * (this.axisClip.x * this.centerClip.w - this.centerClip.x * this.axisClip.w) * iw;
        this.axisV = 0.5f * (this.axisClip.y * this.centerClip.w - this.centerClip.y * this.axisClip.w) * iw;
        return u;
    }

    private int write(int count, float cu, float cv, float lin, float env,
                      float e1x, float e1y, float e2x, float e2y,
                      int colorIndex, boolean paletteOn, float phase, float wobble, float shine) {
        int base = 16 + count * 16;
        data[base] = cu;
        data[base + 1] = cv;
        data[base + 2] = lin;
        data[base + 3] = env;
        data[base + 4] = e1x;
        data[base + 5] = e1y;
        data[base + 6] = e2x;
        data[base + 7] = e2y;
        data[base + 8] = paletteOn ? paletteFadeT(8, colorIndex) : 0.0f;
        data[base + 9] = phase;
        data[base + 10] = wobble;
        data[base + 11] = shine;

        if (!paletteOn) {
            int color = getColor(colorIndex);
            data[base + 12] = (color >> 16 & 0xFF) / 255.0f;
            data[base + 13] = (color >> 8 & 0xFF) / 255.0f;
            data[base + 14] = (color & 0xFF) / 255.0f;
        }
        return count + 1;
    }

    // ================= Капли =================

    private void spawnDrops(Vec3d pos, boolean moving, long now) {
        if (!moving) return;

        float spacing = MathHelper.clamp(1.5f / Math.max(1.0f, density.getValue()), 0.1f, 1.5f);
        if (lastSpawnPos != null && lastSpawnPos.squaredDistanceTo(pos) < (double) (spacing * spacing)) return;
        lastSpawnPos = pos;

        long seed = seq++;
        long minStep = (long) (1000.0f / Math.max(1.0f, spawnRate.getValue()));
        long burstAt = Math.max(now + (long) (40.0f + 160.0f * hashf(seed + 2L)), nextBurstMs);
        if (burstAt - now > 350L) return;

        nextBurstMs = burstAt + (long) (minStep * (0.85f + 0.3f * hashf(seed + 11L)));

        Drop d = new Drop();
        d.x = pos.x + (hashf(seed) - 0.5f) * 0.9;
        d.y = pos.y + 0.02;
        d.z = pos.z + (hashf(seed + 1L) - 0.5f) * 0.9;
        d.spawnMs = now;
        d.delayMs = burstAt - now;
        d.burstMs = 320.0f * (0.8f + 0.4f * hashf(seed + 3L));
        d.riseMs = lifetime.getValue() * (0.85f + 0.3f * hashf(seed + 4L));
        d.dropR = blobSize.getValue() * (0.75f + 0.5f * hashf(seed + 6L));
        d.colorIndex = (int) (seed * 30L % 360L);
        d.riseMul = 0.85f + hashf(seed + 7L) * 0.35f;
        d.wobAmp = 0.1f + hashf(seed + 8L) * 0.12f;
        d.wobSpeed = 1.6f + hashf(seed + 9L) * 1.8f;
        d.phase = hashf(seed + 10L) * ((float) Math.PI * 2);
        drops.add(d);
    }

    private Vec3d lastSpawnPos;
    private boolean frameDataReady;

    // ================= Цвета =================

    private int getColor(int index) {
        if (colorMode.is(COLOR_RAINBOW)) return rainbow(8, index, 1.0f, 1.0f);

        if (colorMode.is(COLOR_CLIENT)) {
            int first = clientColor();
            int second = ColorUtil.interpolateColor(first, DARK_SECOND_COLOR, 0.7f);
            return fade(8, index, first, second);
        }

        int first = customColor.getValue();
        int second = useSecondColor.getValue() ? customSecondColor.getValue() : customColor.getValue();
        return fade(8, index, first, second);
    }

    private static int clientColor() {
        float wave = (MathHelper.sin(System.currentTimeMillis() / 520.0f) + 1.0f) / 2.0f;
        return ColorUtil.interpolateColor(CLIENT_COLOR_FIRST, CLIENT_COLOR_SECOND, wave);
    }

    private static int rainbow(int speed, int index, float saturation, float brightness) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360L);
        return Color.HSBtoRGB(angle / 360.0f, saturation, brightness) | 0xFF000000;
    }

    private static int fade(int speed, int index, int first, int second) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360L);
        angle = angle >= 180 ? 360 - angle : angle;
        return ColorUtil.interpolateColor(first, second, angle / 180.0f);
    }

    private static float paletteFadeT(int speed, int index) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360L);
        return angle / 360.0f;
    }

    // ================= Вспомогательное =================

    private static boolean cullCenter(float cu, float cv, float l1, float l2) {
        float ext = (l1 + l2) * 1.5f + 0.05f;
        return cu >= -ext && cu <= 1.0f + ext && cv >= -ext && cv <= 1.0f + ext;
    }

    private static float nearFade(float relX, float relY, float relZ, float halfExtent) {
        float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ) - halfExtent;
        return smooth(MathHelper.clamp((dist - 0.6f) / 0.75f, 0.0f, 1.0f));
    }

    private static float sizeFade(float l1, float l2) {
        float largest = Math.max(l1, l2);
        return 1.0f - smooth(MathHelper.clamp((largest - 0.5f) / 0.35f, 0.0f, 1.0f));
    }

    private static float linDepth(float d, float near, float far) {
        float z = d * 2.0f - 1.0f;
        return 2.0f * near * far / (far + near - z * (far - near));
    }

    private static float smooth(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static float hashf(long nIn) {
        long n = nIn;
        n = (n ^ n >>> 33) * -336448155523654707L;
        n ^= n >>> 33;
        return (float) (n >>> 8 & 0xFFFFFFL) / 1.6777216E7f;
    }

    private static final class Drop {
        double x, y, z;
        long spawnMs;
        float delayMs, burstMs, riseMs;
        float dropR;
        int colorIndex;
        float riseMul;
        float wobAmp, wobSpeed, phase;
    }
}