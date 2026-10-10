package ru.decide.ui.theme;

public final class ThemeManager {
    private static final long SWITCH_MS = 500L;
    private static final int SHADE_COUNT = 7;
    private static Theme current = Theme.DECIDEVISUALS;
    private static int[] currentShades = Theme.DECIDEVISUALS.shades();
    private static float[] fromShades = unpack(Theme.DECIDEVISUALS.shades());
    private static int[] fromPalette = Theme.DECIDEVISUALS.palette();
    private static long switchStart = System.currentTimeMillis() - 10000L;
    private static Runnable onChange = () -> {
    };

    private ThemeManager() {
    }

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static void set(Theme theme) {
        if (theme != null && theme != current) {
            fromShades = blendedShades();
            fromPalette = blendedPalette();
            current = theme;
            currentShades = theme.shades();
            switchStart = System.currentTimeMillis();
            onChange.run();
        }
    }

    public static Theme current() {
        return current;
    }

    public static int toggleOn(float alpha) {
        return shade(4, alpha);
    }

    private static int rgbLerp(int c1, int c2, float t) {
        float p = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        int r1 = c1 >> 16 & 0xFF;
        int g1 = c1 >> 8 & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = c2 >> 16 & 0xFF;
        int g2 = c2 >> 8 & 0xFF;
        int b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * p);
        int g = Math.round(g1 + (g2 - g1) * p);
        int b = Math.round(b1 + (b2 - b1) * p);
        return r << 16 | g << 8 | b;
    }

    private static int shade(int index, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        if (a <= 0) {
            return 0;
        } else {
            int target = currentShades[index];
            float p = progress();
            if (p >= 1.0F) {
                return a << 24 | target & 16777215;
            } else {
                int off = index * 3;
                int r = Math.round(fromShades[off] + ((target >>> 16 & 0xFF) - fromShades[off]) * p);
                int g = Math.round(fromShades[off + 1] + ((target >>> 8 & 0xFF) - fromShades[off + 1]) * p);
                int b = Math.round(fromShades[off + 2] + ((target & 0xFF) - fromShades[off + 2]) * p);
                return a << 24 | r << 16 | g << 8 | b;
            }
        }
    }

    public static int gradientA(float alpha) {
        return shade(5, alpha);
    }

    public static int gradientB(float alpha) {
        return shade(6, alpha);
    }

    public static int rgba(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return a <= 0 ? 0 : a << 24 | color & 16777215;
    }

    private static float[] unpack(int[] shades) {
        float[] rgb = new float[21];
        for (int i = 0; i < 7; i++) {
            rgb[i * 3] = shades[i] >>> 16 & 0xFF;
            rgb[i * 3 + 1] = shades[i] >>> 8 & 0xFF;
            rgb[i * 3 + 2] = shades[i] & 0xFF;
        }
        return rgb;
    }

    public static int mix(int c1, int c2, float t) {
        float p = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        int a1 = c1 >>> 24 & 0xFF;
        int r1 = c1 >>> 16 & 0xFF;
        int g1 = c1 >>> 8 & 0xFF;
        int b1 = c1 & 0xFF;
        int a2 = c2 >>> 24 & 0xFF;
        int r2 = c2 >>> 16 & 0xFF;
        int g2 = c2 >>> 8 & 0xFF;
        int b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * p);
        int r = Math.round(r1 + (r2 - r1) * p);
        int g = Math.round(g1 + (g2 - g1) * p);
        int b = Math.round(b1 + (b2 - b1) * p);
        return a <= 0 ? 0 : a << 24 | r << 16 | g << 8 | b;
    }

    public static int accent(float alpha) {
        return shade(0, alpha);
    }

    public static int accentSoft(float alpha) {
        return shade(2, alpha);
    }

    public static int accentBright(float alpha) {
        return shade(1, alpha);
    }

    public static int accentFill(float alpha) {
        return shade(3, alpha);
    }

    private static float[] blendedShades() {
        float p = progress();
        float[] out = new float[21];
        int[] cur = currentShades;
        for (int i = 0; i < 7; i++) {
            int off = i * 3;
            out[off] = fromShades[off] + ((cur[i] >>> 16 & 0xFF) - fromShades[off]) * p;
            out[off + 1] = fromShades[off + 1] + ((cur[i] >>> 8 & 0xFF) - fromShades[off + 1]) * p;
            out[off + 2] = fromShades[off + 2] + ((cur[i] & 0xFF) - fromShades[off + 2]) * p;
        }
        return out;
    }

    private static int samplePalette(int[] palette, float t) {
        if (palette == null || palette.length == 0) {
            return 0;
        } else if (palette.length == 1) {
            return palette[0] & 16777215;
        } else {
            float p = (t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t)) * (palette.length - 1);
            int i = (int) Math.floor(p);
            int j = Math.min(i + 1, palette.length - 1);
            return rgbLerp(palette[i], palette[j], p - i);
        }
    }

    public static int[] blendedPalette() {
        int[] target = current.palette();
        float p = progress();
        if (p >= 1.0F) {
            return target;
        } else {
            int n = Math.max(target.length, fromPalette.length);
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                float t = n <= 1 ? 0.0F : (float) i / (n - 1);
                out[i] = rgbLerp(samplePalette(fromPalette, t), samplePalette(target, t), p);
            }
            return out;
        }
    }

    public static int accentOpaque() {
        return 0xFF000000 | shade(0, 255.0F) & 16777215;
    }

    private static float progress() {
        float p = (float) (System.currentTimeMillis() - switchStart) / 500.0F;
        if (p <= 0.0F) {
            return 0.0F;
        } else {
            return p >= 1.0F ? 1.0F : p * p * (3.0F - 2.0F * p);
        }
    }
}