package ru.white.ui.theme;

import java.awt.Color;

public final class ClientAccent {
    private static final long SWITCH_MS = 500L;
    private static final int SHADE_COUNT = 7;
    private static final int PALETTE_K = 6;
    private static final int[] displayedPaletteSamples = new int[PALETTE_K];
    private static final int[][] transitionPalettes = new int[10][];
    private static final int[] fallbackPalette = new int[]{16777215};
    private static final long RAINBOW_CYCLE_MS = 6000L;
    private static final int[] rainbowShades = new int[7];

    private static State state = State.THEMES;
    private static State transitionTarget = State.THEMES;
    private static boolean rainbowEnabled;
    private static boolean targetIsRainbow;
    private static float[] fromShades = unpack(Theme.DECIDEVISUALS.shades());
    private static int[] targetShades = Theme.DECIDEVISUALS.shades();
    private static float fromClosed;
    private static long switchStart = System.currentTimeMillis() - 10000L;
    private static int[] fromPaletteSamples;
    private static int fromPaletteCount = 1;
    private static int lastDisplayedCount = 1;
    private static boolean hasDisplayedPalette;
    private static long frameId;
    private static long paletteFrameId = Long.MIN_VALUE;
    private static int[] framePalette;
    private static double huePhase = 0.0;
    private static long hueLast = 0L;
    private static float frameHue;
    private static float shadesHue = Float.NaN;
    private static float shadesSpread = Float.NaN;
    private static float shadesSaturation = Float.NaN;
    private static float rainbowSpeed = 1.0F;
    private static float rainbowSpread = 0.18F;
    private static float rainbowSaturation = 0.85F;

    private ClientAccent() {
    }

    public static void setRainbowEnabled(boolean enabled) {
        rainbowEnabled = enabled;
    }

    public static boolean isRainbowEnabled() {
        return rainbowEnabled;
    }

    public static void setRainbowSpeed(float speed) {
        rainbowSpeed = Math.max(0.1F, speed);
    }

    public static void setRainbowSaturation(float sat) {
        rainbowSaturation = clamp01(sat);
    }

    public static void setRainbowSpread(float spread) {
        rainbowSpread = clamp01(spread);
    }

    private static float wrap(float f) {
        return f - (float) Math.floor(f);
    }

    public static int toggleOn(float f) {
        return shade(4, f);
    }

    private static int shade(int n, float f) {
        refreshState();
        if (state == State.THEMES) {
            return themeShade(n, f);
        } else {
            int a = Math.max(0, Math.min(255, Math.round(f)));
            if (a <= 0) {
                return 0;
            } else if (state == State.RAINBOW) {
                return a << 24 | rainbowShades()[n] & 16777215;
            } else {
                float p = progress();
                int target = (targetIsRainbow ? rainbowShades() : targetShades)[n];
                if (p >= 1.0F) {
                    return a << 24 | target & 16777215;
                } else {
                    int off = n * 3;
                    int r = Math.round(fromShades[off] + ((target >>> 16 & 0xFF) - fromShades[off]) * p);
                    int g = Math.round(fromShades[off + 1] + ((target >>> 8 & 0xFF) - fromShades[off + 1]) * p);
                    int b = Math.round(fromShades[off + 2] + ((target & 0xFF) - fromShades[off + 2]) * p);
                    return a << 24 | r << 16 | g << 8 | b;
                }
            }
        }
    }

    public static int gradientA(float f) {
        return shade(5, f);
    }

    public static int gradientB(float f) {
        return shade(6, f);
    }

    private static float clamp01(float f) {
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    public static int rgba(int color, float alpha) {
        return ThemeManager.rgba(color, alpha);
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
        return ThemeManager.mix(c1, c2, t);
    }

    public static void beginFrame() {
        long now = System.currentTimeMillis();
        if (hueLast != 0L) {
            long dt = now - hueLast;
            if (dt < 0L) {
                dt = 0L;
            } else if (dt > 200L) {
                dt = 200L;
            }
            huePhase += (float) dt * rainbowSpeed / 6000.0;
            huePhase = huePhase - Math.floor(huePhase);
        }
        hueLast = now;
        frameHue = (float) huePhase;
        frameId++;
        paletteFrameId = Long.MIN_VALUE;
    }

    public static int accent(float f) {
        return shade(0, f);
    }

    public static int accentSoft(float f) {
        return shade(2, f);
    }

    public static int accentBright(float f) {
        return shade(1, f);
    }

    public static int accentFill(float f) {
        return shade(3, f);
    }

    public static int[] currentPalette() {
        int[] palette = blendedClientPalette();
        if (palette != null && palette.length != 0) {
            return palette;
        } else {
            fallbackPalette[0] = accent(255.0F) & 16777215;
            return fallbackPalette;
        }
    }

    private static float[] blendedShades() {
        if (state == State.THEMES) {
            return unpack(sampleThemeShades());
        } else {
            float p = progress();
            int[] target = targetIsRainbow ? rainbowShades() : targetShades;
            float[] out = new float[21];
            for (int i = 0; i < 7; i++) {
                int off = i * 3;
                out[off] = fromShades[off] + ((target[i] >>> 16 & 0xFF) - fromShades[off]) * p;
                out[off + 1] = fromShades[off + 1] + ((target[i] >>> 8 & 0xFF) - fromShades[off + 1]) * p;
                out[off + 2] = fromShades[off + 2] + ((target[i] & 0xFF) - fromShades[off + 2]) * p;
            }
            return out;
        }
    }

    private static int samplePalette(int[] palette, float t) {
        if (palette == null || palette.length == 0) {
            return 0;
        } else if (palette.length == 1) {
            return palette[0] & 16777215;
        } else {
            float p = clamp01(t) * (palette.length - 1);
            int i = (int) p;
            if (i > palette.length - 2) {
                i = palette.length - 2;
            }
            return mixRgb(palette[i], palette[i + 1], p - i);
        }
    }

    public static int rainbowFlow(float offset, float alpha) {
        float h = wrap(frameHue + offset);
        return rgba(Color.HSBtoRGB(h, rainbowSaturation, 1.0F) & 16777215, alpha);
    }

    public static float rainbowBaseHue() {
        return frameHue;
    }

    public static int gradientColor(float position, float alpha) {
        int[] palette = currentPalette();
        if (palette.length <= 1) {
            return rgba(palette[0] & 16777215, alpha);
        } else {
            float p = clamp01(position) * (palette.length - 1);
            int i = (int) Math.floor(p);
            if (i > palette.length - 2) {
                i = palette.length - 2;
            }
            return rgba(mixRgb(palette[i], palette[i + 1], p - i), alpha);
        }
    }

    public static int[] shadesFromCustom(int primary, int secondary, boolean useSecond) {
        int p = primary & 16777215;
        int s = useSecond ? secondary & 16777215 : p;
        return new int[]{p, lighten(p, 0.65F, 1.15F, 0.1F), lighten(p, 0.45F, 1.25F, 0.15F), darken(p, 1.05F, 0.78F), darken(p, 1.1F, 0.55F), p, s};
    }

    private static int[] rainbowShades() {
        float spread = rainbowSpread;
        float sat = rainbowSaturation;
        float h = frameHue;
        if (h == shadesHue && spread == shadesSpread && sat == shadesSaturation) {
            return rainbowShades;
        } else {
            int c1 = Color.HSBtoRGB(h, sat, 1.0F) & 16777215;
            int c2 = Color.HSBtoRGB(wrap(h + spread), sat, 1.0F) & 16777215;
            rainbowShades[0] = c1;
            rainbowShades[1] = Color.HSBtoRGB(h, clamp01(sat * 0.7F), 1.0F) & 16777215;
            rainbowShades[2] = Color.HSBtoRGB(h, clamp01(sat * 0.5F), 1.0F) & 16777215;
            rainbowShades[3] = Color.HSBtoRGB(h, sat, 0.8F) & 16777215;
            rainbowShades[4] = Color.HSBtoRGB(h, sat, 0.6F) & 16777215;
            rainbowShades[5] = c1;
            rainbowShades[6] = c2;
            shadesHue = h;
            shadesSpread = spread;
            shadesSaturation = sat;
            return rainbowShades;
        }
    }

    public static int gradientStops() {
        return Math.max(1, currentPalette().length);
    }

    private static float currentClosed() {
        if (state == State.RAINBOW) {
            return 1.0F;
        } else if (state != State.TRANSITION) {
            return 0.0F;
        } else {
            float f = transitionTarget == State.RAINBOW ? 1.0F : 0.0F;
            return fromClosed + (f - fromClosed) * progress();
        }
    }

    private static int[] desiredPalette() {
        if (rainbowEnabled) {
            return generateRainbowPalette();
        }
        return ThemeManager.blendedPalette();
    }

    private static int[] generateRainbowPalette() {
        int count = PALETTE_K;
        int[] pal = new int[count];
        for (int i = 0; i < count; i++) {
            float t = count <= 1 ? 0F : (float) i / (count - 1);
            float h = wrap(frameHue + t * rainbowSpread);
            pal[i] = Color.HSBtoRGB(h, rainbowSaturation, 1.0F) & 16777215;
        }
        return pal;
    }

    private static void beginTransition(int[] target, boolean rainbow) {
        fromShades = blendedShades();
        fromClosed = currentClosed();
        if (hasDisplayedPalette) {
            fromPaletteSamples = displayedPaletteSamples.clone();
        } else {
            fromPaletteSamples = new int[PALETTE_K];
            resamplePalette(desiredPalette(), fromPaletteSamples);
        }
        fromPaletteCount = lastDisplayedCount;
        targetShades = target;
        targetIsRainbow = rainbow;
        switchStart = System.currentTimeMillis();
        state = State.TRANSITION;
    }

    private static int[] sampleThemeShades() {
        return new int[]{
                ThemeManager.accent(255.0F) & 16777215,
                ThemeManager.accentBright(255.0F) & 16777215,
                ThemeManager.accentSoft(255.0F) & 16777215,
                ThemeManager.accentFill(255.0F) & 16777215,
                ThemeManager.toggleOn(255.0F) & 16777215,
                ThemeManager.gradientA(255.0F) & 16777215,
                ThemeManager.gradientB(255.0F) & 16777215
        };
    }

    private static void refreshState() {
        State desired = rainbowEnabled ? State.RAINBOW : State.THEMES;
        if (state == desired) {
            return;
        } else if (state == State.TRANSITION && transitionTarget == desired) {
            if (progress() >= 1.0F) {
                state = desired;
            }
        } else {
            int[] target;
            boolean rainbow;
            if (desired == State.RAINBOW) {
                target = rainbowShades();
                rainbow = true;
            } else {
                target = sampleThemeShades();
                rainbow = false;
            }
            beginTransition(target, rainbow);
            transitionTarget = desired;
        }
    }

    public static int[] blendedClientPalette() {
        if (paletteFrameId == frameId && framePalette != null) {
            return framePalette;
        } else {
            refreshState();
            int[] computed = computeClientPalette();
            resamplePalette(computed, displayedPaletteSamples);
            hasDisplayedPalette = true;
            lastDisplayedCount = Math.max(1, computed.length);
            framePalette = computed;
            paletteFrameId = frameId;
            return computed;
        }
    }

    public static float closedFactor() {
        refreshState();
        return currentClosed();
    }

    private static int[] computeClientPalette() {
        int[] target = desiredPalette();
        float p;
        if (state == State.TRANSITION && fromPaletteSamples != null && (p = progress()) < 1.0F) {
            int n = Math.max(Math.max(1, fromPaletteCount), target.length);
            int[] out = transitionPalettes[n];
            if (out == null) {
                out = new int[n];
                transitionPalettes[n] = out;
            }
            for (int i = 0; i < n; i++) {
                float t = n <= 1 ? 0.0F : (float) i / (n - 1);
                out[i] = mixRgb(samplePalette(fromPaletteSamples, t), samplePalette(target, t), p);
            }
            return out;
        } else {
            return target;
        }
    }

    private static void resamplePalette(int[] source, int[] dest) {
        for (int i = 0; i < dest.length; i++) {
            float t = dest.length <= 1 ? 0.0F : (float) i / (dest.length - 1);
            dest[i] = samplePalette(source, t);
        }
    }

    public static boolean isModeTransitioning() {
        refreshState();
        return state == State.TRANSITION && progress() < 1.0F;
    }

    public static int accentOpaque() {
        return 0xFF000000 | shade(0, 255.0F) & 16777215;
    }

    private static int themeShade(int n, float f) {
        return switch (n) {
            case 0 -> ThemeManager.accent(f);
            case 1 -> ThemeManager.accentBright(f);
            case 2 -> ThemeManager.accentSoft(f);
            case 3 -> ThemeManager.accentFill(f);
            case 4 -> ThemeManager.toggleOn(f);
            case 5 -> ThemeManager.gradientA(f);
            default -> ThemeManager.gradientB(f);
        };
    }

    private static int mixRgb(int c1, int c2, float t) {
        float p = clamp01(t);
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

    private static float progress() {
        float p = (float) (System.currentTimeMillis() - switchStart) / 500.0F;
        if (p <= 0.0F) {
            return 0.0F;
        } else {
            return p >= 1.0F ? 1.0F : p * p * (3.0F - 2.0F * p);
        }
    }

    private static int lighten(int color, float satMul, float briMul, float briAdd) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, hsb);
        return Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * satMul), clamp01(hsb[2] * briMul + briAdd)) & 16777215;
    }

    private static int darken(int color, float satMul, float briMul) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, hsb);
        return Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * satMul), hsb[2] * briMul) & 16777215;
    }

    public static enum State {
        THEMES,
        RAINBOW,
        TRANSITION;
    }
}