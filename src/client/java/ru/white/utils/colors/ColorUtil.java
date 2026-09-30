package ru.white.utils.colors;

import ru.white.module.impl.display.Hud;
import ru.white.screen.Menu;
import ru.white.theme.Theme;
import ru.white.theme.ThemeColor;
import ru.white.utils.math.Interpolator;
import it.unimi.dsi.fastutil.chars.Char2IntArrayMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
@UtilityClass
public class ColorUtil {
    public static int[] solid8(int color) {
        return new int[]{color, color, color, color, color, color, color, color};
    }public static int interpolateColorsBackAndForth(int speed, int index, int startColor, int endColor, boolean trueColor) {
        int angle = (int) (((System.currentTimeMillis() / speed) + index) % 360);

        angle = (angle >= 180 ? 360 - angle : angle) * 2;

        return interpolateColor(
                startColor,
                endColor,
                angle / 360f,
                trueColor
        );
    }
    public static int getHealthColor(float health, float maxHealth) {
        float progress = MathHelper.clamp(health / maxHealth, 0F, 1F);

        // Красный -> Оранжевый -> Жёлтый -> Зелёный
        if (progress <= 0.5F) {
            return interpolateColor2(
                    new Color(255, 0, 0).getRGB(),      // red
                    new Color(255, 255, 0).getRGB(),    // yellow
                    progress / 0.5F
            );
        } else {
            return interpolateColor2(
                    new Color(255, 255, 0).getRGB(),    // yellow
                    new Color(0, 255, 0).getRGB(),      // green
                    (progress - 0.5F) / 0.5F
            );
        }
    }



    public int client() {
        ru.white.manager.Theme theme = getTheme();
        ru.white.manager.Theme preTheme = getPreTheme();
        return interpolate(theme.getClient(),
                preTheme.getClient(), 1 - Menu.animation14.getOutput());
    }

    public int background() {
        ru.white.manager.Theme theme = getTheme();
        ru.white.manager.Theme preTheme = getPreTheme();
        return interpolate(theme.getRect(),
                preTheme.getRect(), 1 - Menu.animation14.getOutput());
    }

    private static ru.white.manager.Theme getTheme() {
        return ru.white.screen.Menu.selectedTheme != null ? ru.white.screen.Menu.selectedTheme : ru.white.manager.Theme.NIGHT;
    }

    private static ru.white.manager.Theme getPreTheme() {
        return Menu.preSelectedTheme != null ? Menu.preSelectedTheme : ru.white.manager.Theme.NIGHT;
    }
    public static int interpolateColor2(int color1, int color2, float amount) {
        amount = MathHelper.clamp(amount, 0F, 1F);

        Color c1 = new Color(color1, true);
        Color c2 = new Color(color2, true);

        int red = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * amount);
        int green = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * amount);
        int blue = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * amount);
        int alpha = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * amount);

        return new Color(red, green, blue, alpha).getRGB();
    }
    public static int interpolateColor(int color1, int color2, float amount, boolean trueColor) {
        amount = Math.min(1, Math.max(0, amount));

        Color c1 = new Color(color1, true);
        Color c2 = new Color(color2, true);

        return new Color(
                (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * amount),
                (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * amount),
                (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * amount),
                (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * amount)
        ).getRGB();
    }

    private final long CACHE_EXPIRATION_TIME = 60 * 1000;
    private final ConcurrentHashMap<ColorKey, CacheEntry> colorCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleaner = Executors.newScheduledThreadPool(1);
    private final DelayQueue<CacheEntry> cleanupQueue = new DelayQueue<>();

    public static int applyOpacity(int color, float opacity) {
        // Извлекаем текущую альфу (сдвигаем на 24 бита вправо и берем последние 8 бит)
        int alpha = (color >> 24) & 0xFF;

        // Умножаем текущую альфу на коэффициент и проверяем, чтобы она не вышла за
        // рамки 0-255
        int newAlpha = (int) (alpha * opacity);
        newAlpha = Math.max(0, Math.min(255, newAlpha));

        // Склеиваем обратно: (Цвет без альфы) | (Новая альфа сдвинутая влево)
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    public static int interpolateColor(int color1, int color2, float factor) {
        // Ограничиваем фактор от 0 до 1
        factor = Math.min(1.0f, Math.max(0.0f, factor));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public Char2IntArrayMap colorCodes = new Char2IntArrayMap() {
        {
            put('0', 0x000000);
            put('1', 0x0000AA);
            put('2', 0x00AA00);
            put('3', 0x00AAAA);
            put('4', 0xAA0000);
            put('5', 0xAA00AA);
            put('6', 0xFFAA00);
            put('7', 0xAAAAAA);
            put('8', 0x555555);
            put('9', 0x5555FF);
            put('A', 0x55FF55);
            put('B', 0x55FFFF);
            put('C', 0xFF5555);
            put('D', 0xFF55FF);
            put('E', 0xFFFF55);
            put('F', 0xFFFFFF);
        }
    };

    public static int[] solid(int color) {
        return new int[]{color, color, color, color, color, color, color, color, color};
    }
    public static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(?i)§[0-9a-f-or]");

    public String removeFormatting(String text) {
        return text == null || text.isEmpty() ? null : FORMATTING_CODE_PATTERN.matcher(text).replaceAll("");
    }

    public int multRed(int color, float percent01) {
        return getColorRaw(red(color), Math.min(255, Math.round(green(color) / percent01)),
                Math.min(255, Math.round(blue(color) / percent01)), alpha(color));
    }

    public static int reAlphaInt(final int color,
                                 final int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 16777215);
    }

    public static int swapAlpha(int color, float alpha) {
        int f = color >> 16 & 0xFF;
        int f1 = color >> 8 & 0xFF;
        int f2 = color & 0xFF;
        return getColor2(f, f1, f2, (int) alpha);
    }

    public static int getColor2(int red, int green, int blue, int alpha) {
        int color = 0;
        color |= alpha << 24;
        color |= red << 16;
        color |= green << 8;
        return color |= blue;
    }

    public static Color TwoColoreffect(final Color color, final Color color2, final double n) {
        final float clamp = MathHelper.clamp((float) Math.sin(18.84955592153876 * (n / 4.0 % 1.0)) / 2.0f + 0.5f, 0.0f,
                1.0f);
        return new Color(MathHelper.lerp(color.getRed() / 255.0f, color2.getRed() / 255.0f, clamp),
                MathHelper.lerp(color.getGreen() / 255.0f, color2.getGreen() / 255.0f, clamp),
                MathHelper.lerp(color.getBlue() / 255.0f, color2.getBlue() / 255.0f, clamp),
                MathHelper.lerp(color.getAlpha() / 255.0f, color2.getAlpha() / 255.0f, clamp));
    }

    public static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
        return interpolateD(oldValue, newValue, (float) interpolationValue).intValue();
    }

    public static Double interpolateD(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static int glColor(int color) {
        float alpha = (float) (color >> 24 & 0xFF) / 255.0f;
        float red = (float) (color >> 16 & 0xFF) / 255.0f;
        float green = (float) (color >> 8 & 0xFF) / 255.0f;
        float blue = (float) (color & 0xFF) / 255.0f;
        GL11.glColor4f(red, green, blue, alpha);
        return color;
    }

    public static int skyRainbow(int speed, int index) {
        double angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        return Color.getHSBColor(
                ((angle %= 360) / 360.0) < 0.5 ? -((float) (angle / 360.0)) : (float) (angle / 360.0),
                0.5F,
                1.0F).hashCode();
    }

    public float[] normalize(Color color) {
        return new float[] { color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f,
                color.getAlpha() / 255.0f };
    }

    public float[] normalize(int color) {
        int[] components = unpack(color);
        return new float[] { components[0] / 255.0f, components[1] / 255.0f, components[2] / 255.0f,
                components[3] / 255.0f };
    }

    public int[] unpack(int color) {
        return new int[] { color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF };
    }

    public static int fadeBetween(float speed, int offset, int color1, int color2) {
        long time = System.currentTimeMillis() + offset;
        double factor = (Math.sin(time * 0.001 * speed) + 1) / 2.0; // колебание от 0 до 1

        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (r << 16) | (g << 8) | b;
    }

    public static int fadeBetween(int from, int to, float fraction) {
        fraction = clamp01(fraction);

        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * fraction);
        int r = (int) (r1 + (r2 - r1) * fraction);
        int g = (int) (g1 + (g2 - g1) * fraction);
        int b = (int) (b1 + (b2 - b1) * fraction);

        return ((a & 0xFF) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    private static float clamp01(float v) {
        if (v < 0F)
            return 0F;
        if (v > 1F)
            return 1F;
        return v;
    }

    static {
        cacheCleaner.scheduleWithFixedDelay(() -> {
            CacheEntry entry = cleanupQueue.poll();
            while (entry != null) {
                if (entry.isExpired()) {
                    colorCache.remove(entry.getKey());
                }
                entry = cleanupQueue.poll();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static float[] rgba(final int color) {
        return new float[] {
                (color >> 16 & 0xFF) / 255f,
                (color >> 8 & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                (color >> 24 & 0xFF) / 255f
        };
    }

    public static int[] rgbas(final int color) {
        return new int[] {
                (int) ((color >> 16 & 0xFF) / 255f),
                (int) ((color >> 8 & 0xFF) / 255f),
                (int) ((color & 0xFF) / 255f),
                (int) ((color >> 24 & 0xFF) / 255f)
        };
    }

    public final int RED = getColor(255, 0, 0);
    public final int GREEN = getColor(0, 255, 0);
    public final int BLUE = getColor(0, 0, 255);
    public final int YELLOW = getColor(255, 255, 0);
    public final int WHITE = getColor(255);
    public final int BLACK = getColor(0);

    public int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public int blue(int c) {
        return c & 0xFF;
    }

    public int alpha(int c) {
        return (c >> 24) & 0xFF;
    }

    public float redf(int c) {
        return red(c) / 255.0f;
    }

    public float greenf(int c) {
        return green(c) / 255.0f;
    }

    public float bluef(int c) {
        return blue(c) / 255.0f;
    }

    public float alphaf(int c) {
        return alpha(c) / 255.0f;
    }

    public int[] getRGBA(int c) {
        return new int[] { red(c), green(c), blue(c), alpha(c) };
    }

    public int[] getRGB(int c) {
        return new int[] { red(c), green(c), blue(c) };
    }

    public float[] getRGBAf(int c) {
        return new float[] { redf(c), greenf(c), bluef(c), alphaf(c) };
    }

    public float[] getRGBf(int c) {
        return new float[] { redf(c), greenf(c), bluef(c) };
    }

    public int getColor(float red, float green, float blue, float alpha) {
        return getColor(Math.round(red * 255), Math.round(green * 255), Math.round(blue * 255),
                Math.round(alpha * 255));
    }

    public int getColor(int red, int green, int blue, float alpha) {
        return getColor(red, green, blue, Math.round(alpha * 255));
    }

    public int getColor(float red, float green, float blue) {
        return getColor(red, green, blue, 1.0F);
    }

    public int getColor(int brightness, int alpha) {
        return getColor(brightness, brightness, brightness, alpha);
    }

    public int getColor(int brightness, float alpha) {
        return getColor(brightness, Math.round(alpha * 255));
    }



    public int getColor(int brightness) {
        return getColor(brightness, brightness, brightness);
    }

    public int replAlpha(int color, int alpha) {
        return getColorRaw(red(color), green(color), blue(color), alpha);
    }

    public int replAlpha(int color, float alpha) {
        return getColorRaw(red(color), green(color), blue(color), Math.round(alpha * 255));
    }

    public int multAlpha(int color, float percent01) {
        return getColorRaw(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
    }

    public int toGray(int color, float percent01) {
        int r = red(color);
        int g = green(color);
        int b = blue(color);
        int a = alpha(color);

        // целевой серый
        int target = 128;

        // интерполяция к серому
        r = Math.round(r + (target - r) * percent01);
        g = Math.round(g + (target - g) * percent01);
        b = Math.round(b + (target - b) * percent01);

        // чуть затемняем (например на 10%)
        float darkFactor = (percent01 / 2);
        r = Math.round(r * darkFactor);
        g = Math.round(g * darkFactor);
        b = Math.round(b * darkFactor);

        return getColorRaw(r, g, b, a);
    }

    public int multDark(int color, float percent01) {
        return getColorRaw(
                Math.round(red(color) * percent01),
                Math.round(green(color) * percent01),
                Math.round(blue(color) * percent01),
                alpha(color));
    }

    public int multBright(int color, float percent01) {
        return getColorRaw(
                Math.min(255, Math.round(red(color) / percent01)),
                Math.min(255, Math.round(green(color) / percent01)),
                Math.min(255, Math.round(blue(color) / percent01)),
                alpha(color));
    }

    public int overCol(int color1, int color2, float percent01) {
        final float percent = MathHelper.clamp(percent01, 0F, 1F);
        return getColorRaw(
                Interpolator.lerp(red(color1), red(color2), percent),
                Interpolator.lerp(green(color1), green(color2), percent),
                Interpolator.lerp(blue(color1), blue(color2), percent),
                Interpolator.lerp(alpha(color1), alpha(color2), percent));
    }

    public int overCol(int color1, int color2) {
        return overCol(color1, color2, 0.5f);
    }

    public int[] genGradientForText(int color1, int color2, int length) {
        int[] gradient = new int[length];
        for (int i = 0; i < length; i++) {
            float pc = (float) i / (length - 1);
            gradient[i] = overCol(color1, color2, pc);
        }
        return gradient;
    }

    public static int interpolate(int color1, int color2, double amount) {
        amount = (float) MathHelper.clamp(amount, 0, 1);
        return getColorRaw(
                Interpolator.lerp(red(color1), red(color2), amount),
                Interpolator.lerp(green(color1), green(color2), amount),
                Interpolator.lerp(blue(color1), blue(color2), amount),
                Interpolator.lerp(alpha(color1), alpha(color2), amount));
    }

    public int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        float hue = angle / 360f;
        int color = Color.HSBtoRGB(hue, saturation, brightness);
        return getColor(red(color), green(color), blue(color), Math.round(opacity * 255));
    }

    public int fade(int speed, int index, int first, int second) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        angle = angle >= 180 ? 360 - angle : angle;
        return overCol(first, second, angle / 180f);
    }

    public int fade(int index) {
        return ColorUtil.fade(10,index,getClientColor1(index),ColorUtil.multBright(getClientColor1(index),0.75F));
    }

    public int getClientColor1(int index) {

        return Hud.getVisualsColor();
    }
    public int getRectColor(float alpha) {
        return ColorUtil.replAlpha(ThemeColor.getBackgroundColor(),alpha * Hud.getAlpha());
    }
    public int getClientColor(int index) {

        return Hud.getHudColor();
    }
    public static int gradient(int c1, int c2, int c3, int c4, int c5, int index, int speed) {
        // 1. Рассчитываем время (0.0 - 1.0)
        double time = (System.currentTimeMillis() + (index * 50L)) % (long) speed;
        float ratio = (float) (time / (double) speed);

        // 2. Определяем, между какими цветами мы сейчас находимся
        int color1, color2;
        float localRatio;

        if (ratio < 0.2f) {
            color1 = c1; color2 = c2; localRatio = ratio / 0.2f;
        } else if (ratio < 0.4f) {
            color1 = c2; color2 = c3; localRatio = (ratio - 0.2f) / 0.2f;
        } else if (ratio < 0.6f) {
            color1 = c3; color2 = c4; localRatio = (ratio - 0.4f) / 0.2f;
        } else if (ratio < 0.8f) {
            color1 = c4; color2 = c5; localRatio = (ratio - 0.6f) / 0.2f;
        } else {
            color1 = c5; color2 = c1; localRatio = (ratio - 0.8f) / 0.2f;
        }

        // 3. Плавная интерполяция через HSB (чтобы не было серых пятен)
        return getHSBColor(color1, color2, localRatio);
    }

    private static int getHSBColor(int color1, int color2, float ratio) {
        float[] hsb1 = Color.RGBtoHSB((color1 >> 16) & 0xFF, (color1 >> 8) & 0xFF, color1 & 0xFF, null);
        float[] hsb2 = Color.RGBtoHSB((color2 >> 16) & 0xFF, (color2 >> 8) & 0xFF, color2 & 0xFF, null);

        // Сглаживаем ratio для мягкости
        float smooth = (float) (1 - Math.cos(ratio * Math.PI)) / 2f;

        // Интерполируем Hue, Saturation и Brightness отдельно
        float h = interpolateFloat(hsb1[0], hsb2[0], smooth);
        float s = interpolateFloat(hsb1[1], hsb2[1], smooth);
        float b = interpolateFloat(hsb1[2], hsb2[2], smooth);

        return Color.HSBtoRGB(h, s, b);
    }

    private static float interpolateFloat(float f1, float f2, float ratio) {
        // Проверка, чтобы Hue не крутился по кругу через весь спектр
        if (Math.abs(f2 - f1) > 0.5) {
            if (f2 > f1) f1 += 1.0f; else f2 += 1.0f;
        }
        float res = f1 + (f2 - f1) * ratio;
        return (res > 1.0f) ? res - 1.0f : res;
    }

    private static int interpolateChannel(int b1, int b2, float fraction) {
        return (int) (b1 + (b2 - b1) * fraction);
    }
    public int getColorRectMain(float alpha) {

        return ColorUtil.replAlpha(getClientColor(1),alpha);
    }





    public static int gradient(int first, int second, int third, int index, int speed) {
        float angle = ((System.currentTimeMillis() / (float) speed + index) % 360) / 360f;

        int color;

        if (angle < 0.5f) {
            color = interpolate(first, second, angle * 2f);
        } else {
            color = interpolate(second, third, (angle - 0.5f) * 2f);
        }

        float[] hs = rgba(color);
        float[] hsb = Color.RGBtoHSB(
                (int) (hs[0] * 255),
                (int) (hs[1] * 255),
                (int) (hs[2] * 255),
                null
        );

        hsb[1] *= 1.5F;
        hsb[1] = Math.min(hsb[1], 1.0f);

        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    public static int gradient(int start, int end, int index, int speed) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int color = interpolate(start, end, MathHelper.clamp(angle / 180f - 1, 0, 1));
        float[] hs = rgba(color);
        float[] hsb = Color.RGBtoHSB((int) (hs[0] * 255), (int) (hs[1] * 255), (int) (hs[2] * 255), null);

        hsb[1] *= 1.5F;
        hsb[1] = Math.min(hsb[1], 1.0f);

        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }



    public static int getColor(int red, int green, int blue, int alpha) {
        ColorKey key = new ColorKey(red, green, blue, alpha);
        CacheEntry cacheEntry = colorCache.computeIfAbsent(key, k -> {
            CacheEntry newEntry = new CacheEntry(k, computeColor(red, green, blue, alpha), CACHE_EXPIRATION_TIME);
            cleanupQueue.offer(newEntry);
            return newEntry;
        });
        return cacheEntry.getColor();
    }
    public int getColorRaw(int red, int green, int blue, int alpha) {
        return ((MathHelper.clamp(alpha, 0, 255) << 24) |
                (MathHelper.clamp(red, 0, 255) << 16) |
                (MathHelper.clamp(green, 0, 255) << 8) |
                MathHelper.clamp(blue, 0, 255));
    }

    public static int getColorRaw(float red, float green, float blue, float alpha) {
        return ((MathHelper.clamp(Math.round(alpha), 0, 255) << 24) |
                (MathHelper.clamp(Math.round(red), 0, 255) << 16) |
                (MathHelper.clamp(Math.round(green), 0, 255) << 8) |
                MathHelper.clamp(Math.round(blue), 0, 255));
    }

    public int getColor(int red, int green, int blue) {
        return getColor(red, green, blue, 255);
    }

    private static int computeColor(int red, int green, int blue, int alpha) {
        return ((MathHelper.clamp(alpha, 0, 255) << 24) |
                (MathHelper.clamp(red, 0, 255) << 16) |
                (MathHelper.clamp(green, 0, 255) << 8) |
                MathHelper.clamp(blue, 0, 255));
    }
    private String generateKey(int red, int green, int blue, int alpha) {
        return red + "," + green + "," + blue + "," + alpha;
    }


    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    private static class ColorKey {
        final int red, green, blue, alpha;
    }
    @Getter
    private static class CacheEntry implements Delayed {
        private final ColorKey key;
        private final int color;
        private final long expirationTime;

        CacheEntry(ColorKey key, int color, long ttl) {
            this.key = key;
            this.color = color;
            this.expirationTime = System.currentTimeMillis() + ttl;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delay = expirationTime - System.currentTimeMillis();
            return unit.convert(delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other instanceof CacheEntry) {
                return Long.compare(this.expirationTime, ((CacheEntry) other).expirationTime);
            }
            return 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

    }

    public void shutdownCacheCleaner() {
        cacheCleaner.shutdown();
    }
}
